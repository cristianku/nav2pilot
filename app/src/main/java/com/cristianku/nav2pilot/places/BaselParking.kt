package com.cristianku.nav2pilot.places

import com.cristianku.nav2pilot.mapbox.RouteShaping
import com.cristianku.nav2pilot.model.LatLng
import com.cristianku.nav2pilot.net.HttpClient
import com.cristianku.nav2pilot.net.HttpRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.Locale

/** Fetch the city feed, never the user's coordinates. Match on identity AND proximity, never proximity alone. */
class BaselParking(private val http: HttpClient, private val io: CoroutineDispatcher) {
    private val lock = Mutex()
    private var fetched = Instant.EPOCH
    private var records = emptyList<Garage>()

    suspend fun lookup(place: Place): Garage? {
        if (place.category != PlaceCategory.PARKING || place.latitude !in 47.52..47.59 || place.longitude !in 7.55..7.63) return null
        return load().singleOrNull { it.matches(place) }?.takeIf { it.isFresh(Instant.now()) }
    }

    suspend fun placesIn(area: MapArea): List<Place> {
        if (area.north < 47.52 || area.south > 47.59 || area.east < 7.55 || area.west > 7.63) return emptyList()
        return load().filter { it.point.latitude in area.south..area.north && it.point.longitude in area.west..area.east }
            .map { Place(PlaceCategory.PARKING, it.point.latitude, it.point.longitude, it.title, it.address, "amenity:parking") }
    }

    private suspend fun load(): List<Garage> = lock.withLock {
        val now = Instant.now()
        if (!now.isBefore(fetched.plusSeconds(60))) {
            records = withContext(io) {
                try {
                    val response = http.execute(HttpRequest("GET", ENDPOINT, timeoutMillis = 5000))
                    if (response.isSuccessful()) parse(response.body) else emptyList()
                } catch (_: IOException) { emptyList() }
            }
            fetched = now
        }
        records
    }

    data class Garage(val title: String, val address: String, val point: LatLng, val published: Instant,
                      val status: String, val free: Int?, val total: Int?) {
        fun isFresh(now: Instant) = !published.isAfter(now.plusSeconds(60)) && !published.isBefore(now.minusSeconds(300))
        fun matches(p: Place): Boolean {
            if (RouteShaping.distanceMeters(point, LatLng(p.latitude, p.longitude)) > 150) return false
            fun words(text: String) = text.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
            fun name(text: String) = words(text).split(' ').filterNot { it in setOf("parkhaus", "parking", "garage", "basel") }.joinToString(" ")
            val placeName = name(p.name)
            val garageName = name(title)
            val street = words(p.details.substringBefore(','))
            return (placeName.isNotEmpty() && placeName == garageName) ||
                (placeName.isEmpty() && street.any(Char::isDigit) && street == words(address))
        }
        fun availability(): String = when {
            status in setOf("zu", "geschlossen") -> "Closed"
            status != "offen" || free == null || free < 0 || (total != null && (total <= 0 || free > total)) -> "Availability unknown"
            free == 0 -> "Full"
            total != null -> "$free of $total spaces free"
            else -> "$free spaces free"
        }
    }

    companion object {
        const val ENDPOINT = "https://data.bs.ch/api/explore/v2.1/catalog/datasets/100088/records?limit=100"
        fun merge(official: List<Place>, osm: List<Place>): List<Place> {
            if (official.isEmpty()) return osm
            val remaining = osm.toMutableList()
            return official.map { pin ->
                val identity = Garage(pin.name, pin.details, LatLng(pin.latitude, pin.longitude), Instant.EPOCH, "", null, null)
                val match = remaining.singleOrNull(identity::matches)
                if (match == null) pin else {
                    remaining.remove(match)
                    pin.copy(info = match.info, parkingNotes = match.parkingNotes, osmId = match.osmId)
                }
            } + remaining
        }
        internal fun parse(body: String): List<Garage> {
            val rows = try { JSONObject(body).optJSONArray("results") } catch (_: org.json.JSONException) { null } ?: return emptyList()
            return buildList {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    if (row.isNull("name") || row.isNull("title")) continue
                    val point = row.optJSONObject("geo_point_2d") ?: continue
                    val lat = point.optDouble("lat")
                    val lon = point.optDouble("lon")
                    if (lat !in 47.52..47.59 || lon !in 7.55..7.63) continue
                    val published = try { Instant.parse(row.optString("published")) } catch (_: DateTimeParseException) { continue }
                    add(Garage(row.getString("title"), row.optString("address"), LatLng(lat, lon), published,
                        row.optString("status"), row.opt("free")?.toString()?.toIntOrNull(), row.opt("total")?.toString()?.toIntOrNull()))
                }
            }
        }
    }
}
