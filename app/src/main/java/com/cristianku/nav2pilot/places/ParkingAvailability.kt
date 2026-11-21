package com.cristianku.nav2pilot.places

import com.cristianku.nav2pilot.mapbox.RouteShaping
import com.cristianku.nav2pilot.model.LatLng
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.net.HttpClient
import com.cristianku.nav2pilot.net.HttpRequest
import com.cristianku.nav2pilot.net.HttpResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.IOException
import java.time.Instant
import java.util.Locale

enum class ParkingStatus { COUNTED, CLOSED, UNKNOWN }

enum class ParkingLevel { AVAILABLE, LOW, FULL, CLOSED, UNKNOWN }

data class ParkingState(val level: ParkingLevel, val free: Int? = null) {
    val pinLabel: String get() = when {
        free != null -> "P $free"
        level == ParkingLevel.CLOSED -> "P ×"
        else -> "P"
    }
}

fun ParkingReading?.stateAt(now: Instant): ParkingState {
    if (this == null || !isFresh(now)) return ParkingState(ParkingLevel.UNKNOWN)
    if (status == ParkingStatus.CLOSED) return ParkingState(ParkingLevel.CLOSED)
    val count = vacancies(now) ?: return ParkingState(ParkingLevel.UNKNOWN)
    return ParkingState(when {
        count == 0 -> ParkingLevel.FULL
        count <= 5 || (total != null && count.toLong() * 10 <= total) -> ParkingLevel.LOW
        else -> ParkingLevel.AVAILABLE
    }, count)
}

data class ParkingReading(
    val id: String, val title: String, val address: String, val point: LatLng,
    val published: Instant?, val status: ParkingStatus, val free: Int?, val total: Int?,
    val source: String, val zone: String, val maxAgeSeconds: Long,
    val structure: ParkingStructure = ParkingStructure.UNKNOWN,
) {
    fun isFresh(now: Instant): Boolean = published != null &&
        !published.isAfter(now.plusSeconds(60)) && !published.isBefore(now.minusSeconds(maxAgeSeconds))

    fun vacancies(now: Instant): Int? = free?.takeIf {
        isFresh(now) && status == ParkingStatus.COUNTED && it >= 0 && (total == null || total > 0 && it <= total)
    }

    fun matches(p: Place): Boolean {
        if (RouteShaping.distanceMeters(point, LatLng(p.latitude, p.longitude)) > 150) return false
        fun words(text: String) = text.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        fun name(text: String) = words(text).split(' ').filterNot {
            it in setOf("parkhaus", "parking", "garage", "parcheggio") || (zone == "Europe/Zurich" && it == "basel")
        }.joinToString(" ")
        val placeName = name(p.name)
        val street = words(p.details.substringBefore(','))
        return (placeName.isNotEmpty() && placeName == name(title)) ||
            (placeName.isEmpty() && street.any(Char::isDigit) && street == words(address.substringBefore(',')))
    }
}

/** City feeds and regional tiles are shared by map pins and visible parking favorites. */
class ParkingAvailability(private val http: HttpClient, private val io: CoroutineDispatcher) {
    private val lock = Mutex()
    private data class Cached<T>(val fetched: Instant, val value: T)
    private val cache = mutableMapOf<String, Cached<List<ParkingReading>>>()
    private var hkDirectory: Cached<HttpResponse>? = null

    private fun get(url: String): HttpResponse {
        val response = http.execute(HttpRequest("GET", url, headers = mapOf("Accept" to "application/json, application/rss+xml, application/xml"), timeoutMillis = 5000))
        if (!response.isSuccessful()) throw IOException("Parking feed HTTP ${response.code}")
        return response
    }

    private suspend fun readingsIn(area: MapArea): List<ParkingReading> = withContext(io) {
        val feeds = ParkingFeeds.forArea(area)
        lock.withLock {
            val now = Instant.now()
            feeds.flatMap { feed ->
                val old = cache[feed.key]
                if (old != null && now.isBefore(old.fetched.plusSeconds(60))) return@flatMap old.value
                val rows = try {
                    feed.read(now) { url ->
                        if (url != ParkingFeeds.HK_DIRECTORY) get(url) else {
                            hkDirectory?.takeIf { now.isBefore(it.fetched.plusSeconds(86400)) }?.value
                                ?: get(url).also { hkDirectory = Cached(now, it) }
                        }
                    }
                } catch (_: IOException) { old?.value.orEmpty() }
                  catch (_: JSONException) { old?.value.orEmpty() }
                cache[feed.key] = Cached(now, rows)
                rows
            }.asSequence().filter {
                it.point.latitude in area.south..area.north && it.point.longitude in area.west..area.east
            }.distinctBy { it.id }.toList()
        }
    }

    suspend fun placesIn(area: MapArea): List<Place> = withContext(io) {
        val center = area.center()
        readingsIn(area).sortedBy { RouteShaping.distanceMeters(center, it.point) }.take(100).map {
            Place(PlaceCategory.PARKING, it.point.latitude, it.point.longitude, it.title, it.address,
                "amenity:parking", parkingReading = it)
        }
    }

    suspend fun favorite(destination: Destination): ParkingReading? {
        if (!destination.isParking) return null
        val area = MapArea.around(LatLng(destination.latitude, destination.longitude), 250.0)
        return matchFavorite(destination, readingsIn(area))
    }

    companion object {
        fun matchFavorite(destination: Destination, candidates: List<ParkingReading>): ParkingReading? {
            if (!destination.isParking) return null
            if (destination.parkingId.isNotEmpty()) return candidates.singleOrNull { it.id == destination.parkingId }
            val pin = Place(PlaceCategory.PARKING, destination.latitude, destination.longitude, destination.name, destination.details)
            return candidates.singleOrNull { it.matches(pin) }
        }
        /** Keep ambiguous neighbours separate: the wrong garage's count is worse than no count. */
        fun merge(official: List<Place>, osm: List<Place>): List<Place> {
            if (official.isEmpty() || osm.isEmpty()) return official + osm
            val remaining = osm.toMutableList()
            return official.map { pin ->
                val match = osm.singleOrNull { pin.parkingReading?.matches(it) == true }
                if (match == null || official.count { it.parkingReading?.matches(match) == true } != 1) pin else {
                    remaining.remove(match)
                    pin.copy(info = match.info, parkingNotes = match.parkingNotes, osmId = match.osmId,
                        parkingCapacity = match.parkingCapacity,
                        parkingStructure = if (pin.parkingStructure != ParkingStructure.UNKNOWN) pin.parkingStructure else match.parkingStructure)
                }
            } + remaining
        }
    }
}
