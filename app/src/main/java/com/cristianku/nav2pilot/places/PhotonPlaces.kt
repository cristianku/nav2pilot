package com.cristianku.nav2pilot.places

import com.cristianku.nav2pilot.mapbox.RouteShaping
import com.cristianku.nav2pilot.model.CoordinateRules
import com.cristianku.nav2pilot.model.LatLng
import com.cristianku.nav2pilot.net.HttpClient
import com.cristianku.nav2pilot.net.HttpRequest
import com.cristianku.nav2pilot.net.HttpResponse
import com.cristianku.nav2pilot.transport.ErrorText
import com.cristianku.nav2pilot.transport.TransportError
import com.cristianku.nav2pilot.transport.TransportResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import kotlin.math.ceil

/**
 * Points of interest from Photon, komoot's public search over OpenStreetMap: the reserve when no Overpass server
 * answers. Quick and worldwide, but it knows only the places that have a name, and not who may use a car park.
 */
class PhotonPlaces(
    private val http: HttpClient,
    private val ioDispatcher: CoroutineDispatcher,
    private val endpoint: String = ENDPOINT,
) {
    suspend fun search(category: PlaceCategory, area: MapArea): TransportResult<List<Place>> {
        val request = HttpRequest(
            method = "GET",
            url = url(endpoint, category, area),
            headers = mapOf("Accept" to "application/json"),
            timeoutMillis = TIMEOUT_MILLIS,
        )
        val response: HttpResponse
        try {
            response = withContext(ioDispatcher) { http.execute(request) }
        } catch (error: IOException) {
            return TransportResult.Failed(TransportError.Network(error.message ?: error.javaClass.simpleName))
        }
        if (!response.isSuccessful()) {
            return TransportResult.Failed(TransportError.HttpFailure(response.code, ErrorText.fromHttpBody(response.body)))
        }
        val json: JSONObject
        try {
            json = JSONObject(response.body)
        } catch (notJson: JSONException) {
            return TransportResult.Failed(TransportError.Malformed(ErrorText.shorten(response.body)))
        }
        return TransportResult.Ok(parsePlaces(json, category, area))
    }

    companion object {
        const val ENDPOINT = "https://photon.komoot.io/reverse"

        private const val TIMEOUT_MILLIS = 8_000

        /** The circle around the area's middle that holds all of it, and the category's tags. */
        fun url(endpoint: String, category: PlaceCategory, area: MapArea): String {
            val center = area.center()
            val corner = LatLng(area.north, area.east)
            val radiusKm = ceil(RouteShaping.distanceMeters(center, corner) / 1000).toInt()
            val tags = category.tags.joinToString("") { tag -> "&osm_tag=$tag" }
            return String.format(
                Locale.US,
                "%s?lat=%.5f&lon=%.5f&radius=%d&limit=%d%s",
                endpoint,
                center.latitude,
                center.longitude,
                radiusKm,
                OverpassPlaces.MAX_PLACES,
                tags,
            )
        }

        /** The places inside [area], nearest to its middle first: the circle asked for is larger than the area. */
        fun parsePlaces(json: JSONObject, category: PlaceCategory, area: MapArea): List<Place> {
            val found = mutableListOf<Place>()
            val features = json.optJSONArray("features") ?: return found
            for (index in 0 until features.length()) {
                val feature = features.optJSONObject(index) ?: continue
                val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                val longitude = coordinates.optDouble(0, Double.NaN)
                val latitude = coordinates.optDouble(1, Double.NaN)
                if (!CoordinateRules.isValid(latitude, longitude)) {
                    continue
                }
                if (latitude < area.south || latitude > area.north || longitude < area.west || longitude > area.east) {
                    continue
                }
                var properties = feature.optJSONObject("properties")
                if (properties == null) {
                    properties = JSONObject()
                }
                var name = stringField(properties, "name")
                if (name.isEmpty()) {
                    name = category.singular
                }
                found.add(Place(category, latitude, longitude, name, address(properties)))
            }
            val center = area.center()
            return found.sortedBy { place -> RouteShaping.distanceMeters(center, LatLng(place.latitude, place.longitude)) }
        }

        /** "Hauptstrasse 12, 5070 Frick"; "" without an address. */
        private fun address(properties: JSONObject): String {
            var street = stringField(properties, "street")
            val number = stringField(properties, "housenumber")
            if (street.isNotEmpty() && number.isNotEmpty()) {
                street = "$street $number"
            }
            val town = mutableListOf<String>()
            for (key in listOf("postcode", "city")) {
                val value = stringField(properties, key)
                if (value.isNotEmpty()) {
                    town.add(value)
                }
            }
            val parts = mutableListOf<String>()
            if (street.isNotEmpty()) {
                parts.add(street)
            }
            if (town.isNotEmpty()) {
                parts.add(town.joinToString(" "))
            }
            return parts.joinToString(", ")
        }

        /** optString returns "null" for JSON null on Android's org.json, so null is checked first. */
        private fun stringField(json: JSONObject, key: String): String {
            if (json.isNull(key)) {
                return ""
            }
            return json.optString(key, "").trim()
        }
    }
}
