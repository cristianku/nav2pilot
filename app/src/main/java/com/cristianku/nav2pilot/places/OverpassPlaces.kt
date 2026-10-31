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
import java.net.URLEncoder
import java.util.Locale

/** Public Overpass API servers (OpenStreetMap data, free and without a key), tried in this order. */
object OverpassEndpoints {
    val INTERPRETERS = listOf(
        "https://overpass-api.de/api/interpreter",
        // the main server answers 429 or 504 when it is busy
        "https://overpass.private.coffee/api/interpreter",
    )
}

/**
 * Points of interest from OpenStreetMap through the Overpass API: the places of one category in the part of the
 * map the user sees, nearest to its middle first. Works for everybody, with or without a Mapbox token.
 */
class OverpassPlaces(
    private val http: HttpClient,
    private val ioDispatcher: CoroutineDispatcher,
    private val endpoints: List<String> = OverpassEndpoints.INTERPRETERS,
) {
    suspend fun search(category: PlaceCategory, area: MapArea): TransportResult<List<Place>> {
        val body = "data=" + URLEncoder.encode(query(category, area), "UTF-8")
        var failure: TransportError = TransportError.Network("no Overpass server")
        for (endpoint in endpoints) {
            val request = HttpRequest(
                method = "POST",
                url = endpoint,
                headers = mapOf(
                    "Content-Type" to "application/x-www-form-urlencoded",
                    "Accept" to "application/json",
                ),
                body = body,
            )
            val response: HttpResponse
            try {
                response = withContext(ioDispatcher) { http.execute(request) }
            } catch (error: IOException) {
                failure = TransportError.Network(error.message ?: error.javaClass.simpleName)
                continue
            }
            // busy or down: the next server may answer
            if (response.code == 429) {
                failure = TransportError.RateLimited
                continue
            }
            if (response.code >= 500) {
                failure = TransportError.HttpFailure(response.code, ErrorText.fromHttpBody(response.body))
                continue
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
            val places = parsePlaces(json, category, area.center(), MAX_PLACES)
            // a query that ran out of time or memory comes back as 200 with a remark and no elements
            val remark = stringField(json, "remark")
            if (places.isEmpty() && remark.isNotEmpty()) {
                return TransportResult.Failed(TransportError.DeviceRejected(ErrorText.shorten(remark)))
            }
            return TransportResult.Ok(places)
        }
        return TransportResult.Failed(failure)
    }

    companion object {
        /** Places shown on the map at most; more would hide it. */
        const val MAX_PLACES = 50

        // fetched to pick the MAX_PLACES nearest to the middle
        private const val MAX_FETCHED = 200

        /** Overpass QL: nodes, ways and relations with the category's tags in [area]; ways and relations as their centre. */
        fun query(category: PlaceCategory, area: MapArea): String {
            return String.format(
                Locale.US,
                "[out:json][timeout:20];nwr%s(%.5f,%.5f,%.5f,%.5f);out center %d;",
                category.filter,
                area.south,
                area.west,
                area.north,
                area.east,
                MAX_FETCHED,
            )
        }

        fun parsePlaces(json: JSONObject, category: PlaceCategory, center: LatLng, limit: Int): List<Place> {
            val found = mutableListOf<Place>()
            val elements = json.optJSONArray("elements") ?: return found
            for (index in 0 until elements.length()) {
                val element = elements.optJSONObject(index) ?: continue
                // a node has its own position, a way or a relation the centre Overpass adds with "out center"
                var position = element
                val elementCenter = element.optJSONObject("center")
                if (elementCenter != null) {
                    position = elementCenter
                }
                val latitude = position.optDouble("lat", Double.NaN)
                val longitude = position.optDouble("lon", Double.NaN)
                if (!CoordinateRules.isValid(latitude, longitude)) {
                    continue
                }
                var tags = element.optJSONObject("tags")
                if (tags == null) {
                    tags = JSONObject()
                }
                found.add(Place(category, latitude, longitude, placeName(tags, category), address(tags)))
            }
            val nearest = found.sortedBy { place -> RouteShaping.distanceMeters(center, LatLng(place.latitude, place.longitude)) }
            return nearest.take(limit)
        }

        private fun placeName(tags: JSONObject, category: PlaceCategory): String {
            for (key in listOf("name", "brand", "operator")) {
                val value = stringField(tags, key)
                if (value.isNotEmpty()) {
                    return value
                }
            }
            return category.singular
        }

        /** "Hauptstrasse 12, 5070 Frick" from the addr:* tags; "" without them. */
        private fun address(tags: JSONObject): String {
            var street = stringField(tags, "addr:street")
            if (street.isEmpty()) {
                street = stringField(tags, "addr:place")
            }
            val number = stringField(tags, "addr:housenumber")
            if (street.isNotEmpty() && number.isNotEmpty()) {
                street = "$street $number"
            }
            val town = mutableListOf<String>()
            for (key in listOf("addr:postcode", "addr:city")) {
                val value = stringField(tags, key)
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
