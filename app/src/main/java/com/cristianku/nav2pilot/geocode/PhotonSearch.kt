package com.cristianku.nav2pilot.geocode

import com.cristianku.nav2pilot.mapbox.RouteShaping
import com.cristianku.nav2pilot.model.CoordinateRules
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.LatLng
import com.cristianku.nav2pilot.net.HttpClient
import com.cristianku.nav2pilot.net.HttpRequest
import com.cristianku.nav2pilot.net.HttpResponse
import com.cristianku.nav2pilot.places.PlaceKinds
import com.cristianku.nav2pilot.transport.ErrorText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale

/**
 * Search by name or address through Photon, komoot's public search over OpenStreetMap. It is made for search as
 * you type and takes a point to prefer the places near it: "ikea" near Frick gives the IKEA in Pratteln, where the
 * platform's Geocoder, which knows no position, gives nothing.
 */
class PhotonSearch(
    private val http: HttpClient,
    private val ioDispatcher: CoroutineDispatcher,
    private val endpoint: String = ENDPOINT,
    private val language: String = Locale.getDefault().language,
) : PlaceSearch {

    override fun isAvailable(): Boolean {
        return true
    }

    override suspend fun search(query: String, maxResults: Int, near: LatLng?): List<Destination> {
        val request = HttpRequest(
            method = "GET",
            url = url(endpoint, query, near, maxResults, language),
            headers = mapOf("Accept" to "application/json"),
            timeoutMillis = TIMEOUT_MILLIS,
        )
        val response: HttpResponse = withContext(ioDispatcher) { http.execute(request) }
        if (!response.isSuccessful()) {
            throw IOException("HTTP ${response.code} " + ErrorText.fromHttpBody(response.body))
        }
        val json: JSONObject
        try {
            json = JSONObject(response.body)
        } catch (notJson: JSONException) {
            throw IOException("Unexpected answer: " + ErrorText.shorten(response.body))
        }
        return parse(json, maxResults)
    }

    companion object {
        const val ENDPOINT = "https://photon.komoot.io/api/"

        private const val TIMEOUT_MILLIS = 8_000

        // Photon answers 400 to any other language
        private val LANGUAGES = setOf("de", "en", "fr")

        // stops and platforms carry the name of what they serve ("IKEA" bus stop): not where a car goes
        private val LEFT_OUT = listOf(
            "highway:bus_stop",
            "highway:platform",
            "railway:platform",
            "railway:stop",
            "railway:tram_stop",
            "railway:halt",
            "public_transport",
            "tourism:information",
        )

        // results are asked for in excess: the same place mapped twice is dropped
        private const val EXTRA_RESULTS = 5

        // the same name this close to an earlier result is the same place (a shop and its car park)
        private const val SAME_PLACE_METERS = 500.0

        fun url(endpoint: String, query: String, near: LatLng?, maxResults: Int, language: String): String {
            val url = StringBuilder(endpoint)
            url.append("?q=").append(URLEncoder.encode(query.trim(), "UTF-8"))
            url.append("&limit=").append(maxResults + EXTRA_RESULTS)
            // about a kilometre is enough to put the nearest first, and says less about where the user is
            if (near != null) {
                url.append(String.format(Locale.US, "&lat=%.2f&lon=%.2f", near.latitude, near.longitude))
            }
            if (LANGUAGES.contains(language)) {
                url.append("&lang=").append(language)
            }
            for (tag in LEFT_OUT) {
                url.append("&osm_tag=!").append(tag)
            }
            return url.toString()
        }

        /** At most [maxResults] destinations, in Photon's order: the best match, near the point, first. */
        fun parse(json: JSONObject, maxResults: Int): List<Destination> {
            val found = mutableListOf<Destination>()
            val features = json.optJSONArray("features") ?: return found
            for (index in 0 until features.length()) {
                if (found.size >= maxResults) {
                    break
                }
                val feature = features.optJSONObject(index) ?: continue
                val coordinates = feature.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                val longitude = coordinates.optDouble(0, Double.NaN)
                val latitude = coordinates.optDouble(1, Double.NaN)
                if (!CoordinateRules.isValid(latitude, longitude)) {
                    continue
                }
                var properties = feature.optJSONObject("properties")
                if (properties == null) {
                    properties = JSONObject()
                }
                val destination = toDestination(properties, latitude, longitude) ?: continue
                val earlier = repeated(destination, found)
                if (earlier < 0) {
                    found.add(destination)
                } else if (PlaceKinds.isParking(found[earlier].kind) && !PlaceKinds.isParking(destination.kind)) {
                    // the car park of the shop came first: the shop takes its place
                    found[earlier] = destination
                }
            }
            return found
        }

        /** The name, or the street and number of an address; the rest of the address as the details. */
        private fun toDestination(properties: JSONObject, latitude: Double, longitude: Double): Destination? {
            var street = stringField(properties, "street")
            val number = stringField(properties, "housenumber")
            if (street.isNotEmpty() && number.isNotEmpty()) {
                street = "$street $number"
            }
            val town = listOf(stringField(properties, "postcode"), stringField(properties, "city"))
                .filter { part -> part.isNotEmpty() }
                .joinToString(" ")

            var name = stringField(properties, "name")
            val details = mutableListOf<String>()
            if (name.isEmpty()) {
                name = street
            } else if (street.isNotEmpty()) {
                details.add(street)
            }
            if (name.isEmpty()) {
                return null
            }
            if (town.isNotEmpty() && town != name) {
                details.add(town)
            }
            // a town, a region: where it is
            if (details.isEmpty()) {
                for (key in listOf("state", "country")) {
                    val value = stringField(properties, key)
                    if (value.isNotEmpty() && value != name) {
                        details.add(value)
                    }
                }
            }
            return Destination(
                latitude = latitude,
                longitude = longitude,
                name = name,
                details = details.joinToString(", "),
                kind = PlaceKinds.fromPhoton(properties),
                osmId = PlaceKinds.osmIdFromPhoton(properties),
            )
        }

        /** The index of an earlier result that is the same place, -1 when there is none. */
        private fun repeated(destination: Destination, earlier: List<Destination>): Int {
            val point = LatLng(destination.latitude, destination.longitude)
            for ((index, other) in earlier.withIndex()) {
                if (!other.name.equals(destination.name, ignoreCase = true)) {
                    continue
                }
                if (RouteShaping.distanceMeters(point, LatLng(other.latitude, other.longitude)) < SAME_PLACE_METERS) {
                    return index
                }
            }
            return -1
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
