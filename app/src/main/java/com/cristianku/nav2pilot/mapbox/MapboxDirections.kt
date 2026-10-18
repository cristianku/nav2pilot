package com.cristianku.nav2pilot.mapbox

import com.cristianku.nav2pilot.location.LocationProvider
import com.cristianku.nav2pilot.model.LatLng
import com.cristianku.nav2pilot.net.HttpClient
import com.cristianku.nav2pilot.net.HttpRequest
import com.cristianku.nav2pilot.net.HttpResponse
import com.cristianku.nav2pilot.transport.ErrorText
import com.cristianku.nav2pilot.transport.TransportError
import com.cristianku.nav2pilot.transport.TransportResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** One route of a Directions API answer, as drawn on the map. */
data class RoutePreview(
    /** The full route geometry. */
    val coordinates: List<LatLng>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    /** Main roads, from the legs' summaries ("A22, SS12"). */
    val summary: String,
)

/**
 * Route previews from the Mapbox Directions API with the user's own token.
 *
 * Same profile as the device's navd (mapbox/driving-traffic), so the route picked on the phone is the one the
 * device recomputes when it gets the destination and the waypoints.
 */
class MapboxDirections(
    private val http: HttpClient,
    private val tokenProvider: () -> String?,
    private val ioDispatcher: CoroutineDispatcher,
) {
    /** Fastest route first, then up to two alternatives (only without stops, as the device gets one route). */
    suspend fun routes(origin: LatLng, stops: List<LatLng>, destination: LatLng): TransportResult<List<RoutePreview>> {
        // the token provider decrypts with the Android Keystore, so it runs off the main thread
        val token = withContext(ioDispatcher) { tokenProvider() } ?: return TransportResult.Failed(TransportError.NoToken)
        val url = requestUrl(origin, stops, destination, token)
            ?: return TransportResult.Failed(TransportError.InvalidRequest("Too many stops"))

        val request = HttpRequest(method = "GET", url = url, headers = mapOf("Accept" to "application/json"))
        val response: HttpResponse
        try {
            response = withContext(ioDispatcher) { http.execute(request) }
        } catch (error: IOException) {
            return TransportResult.Failed(TransportError.Network(error.message ?: error.javaClass.simpleName))
        }
        if (response.code == 401) {
            return TransportResult.Failed(TransportError.Unauthorized)
        }
        if (response.code == 429) {
            return TransportResult.Failed(TransportError.RateLimited)
        }
        val json: JSONObject
        try {
            json = JSONObject(response.body)
        } catch (notJson: JSONException) {
            return TransportResult.Failed(TransportError.Malformed(ErrorText.shorten(response.body)))
        }
        // "Ok", or e.g. "NoRoute" / "NoSegment" with a message
        val code = stringField(json, "code")
        if (code != "Ok") {
            var message = stringField(json, "message")
            if (message.isEmpty()) {
                message = code
            }
            return TransportResult.Failed(TransportError.DeviceRejected(message))
        }
        return TransportResult.Ok(parseRoutes(json))
    }

    companion object {
        fun requestUrl(origin: LatLng, stops: List<LatLng>, destination: LatLng, token: String): String? {
            // Directions API: up to 25 coordinates
            if (stops.size > 23) {
                return null
            }
            val points = mutableListOf<LatLng>()
            points.add(origin)
            points.addAll(stops)
            points.add(destination)
            val pairs = mutableListOf<String>()
            for (point in points) {
                pairs.add(String.format(Locale.US, "%.6f,%.6f", point.longitude, point.latitude))
            }

            var alternatives = "false"
            if (stops.isEmpty()) {
                alternatives = "true"
            }
            return MapboxEndpoints.DIRECTIONS_URL + pairs.joinToString(";") +
                "?alternatives=" + alternatives +
                "&geometries=geojson" +
                "&overview=full" +
                "&steps=false" +
                "&access_token=" + URLEncoder.encode(token, "UTF-8")
        }

        fun parseRoutes(json: JSONObject): List<RoutePreview> {
            val previews = mutableListOf<RoutePreview>()
            val routes = json.optJSONArray("routes") ?: return previews
            for (routeIndex in 0 until routes.length()) {
                val route = routes.optJSONObject(routeIndex) ?: continue
                val geometry = route.optJSONObject("geometry") ?: continue
                val pairs = geometry.optJSONArray("coordinates") ?: continue
                val coordinates = mutableListOf<LatLng>()
                for (pairIndex in 0 until pairs.length()) {
                    val pair: JSONArray = pairs.optJSONArray(pairIndex) ?: continue
                    if (pair.length() < 2) {
                        continue
                    }
                    val longitude = pair.optDouble(0, Double.NaN)
                    val latitude = pair.optDouble(1, Double.NaN)
                    if (longitude.isNaN() || latitude.isNaN()) {
                        continue
                    }
                    coordinates.add(LatLng(latitude, longitude))
                }
                if (coordinates.size < 2) {
                    continue
                }

                val summaries = mutableListOf<String>()
                val legs = route.optJSONArray("legs")
                if (legs != null) {
                    for (legIndex in 0 until legs.length()) {
                        val leg = legs.optJSONObject(legIndex) ?: continue
                        val summary = stringField(leg, "summary")
                        if (summary.isNotEmpty()) {
                            summaries.add(summary)
                        }
                    }
                }

                previews.add(
                    RoutePreview(
                        coordinates = coordinates,
                        distanceMeters = route.optDouble("distance", 0.0),
                        durationSeconds = route.optDouble("duration", 0.0),
                        summary = summaries.joinToString(" · "),
                    ),
                )
            }
            return previews
        }

        /** optString returns "null" for JSON null on Android's org.json, so null is checked first. */
        private fun stringField(json: JSONObject, key: String): String {
            if (json.isNull(key)) {
                return ""
            }
            return json.optString(key, "")
        }
    }
}

/** A point the device's route has to go through (sunnypilot setNavRoute). */
data class RouteWaypoint(
    val latitude: Double,
    val longitude: Double,
    /** True for a silent point that only shapes the route, false for a stop. */
    val via: Boolean,
)

object RouteShaping {
    /**
     * Silent via points that make the device's Mapbox request follow [route]: points at 25 %, 50 % and 75 % of
     * its length. They lie on the route's own geometry, so Mapbox snaps them to the same roads.
     */
    fun viaPoints(route: RoutePreview, fractions: List<Double> = listOf(0.25, 0.5, 0.75)): List<RouteWaypoint> {
        val points = route.coordinates
        if (points.size < 2) {
            return emptyList()
        }
        val cumulative = mutableListOf(0.0)
        var index = 1
        while (index < points.size) {
            cumulative.add(cumulative[index - 1] + distanceMeters(points[index - 1], points[index]))
            index += 1
        }
        val total = cumulative[cumulative.size - 1]
        if (total <= 0) {
            return emptyList()
        }

        val waypoints = mutableListOf<RouteWaypoint>()
        for (fraction in fractions) {
            val target = total * fraction
            var segment = 1
            while (segment < points.size - 1 && cumulative[segment] < target) {
                segment += 1
            }
            val start = points[segment - 1]
            val end = points[segment]
            val length = cumulative[segment] - cumulative[segment - 1]
            var t = 0.0
            if (length > 0) {
                t = (target - cumulative[segment - 1]) / length
            }
            val latitude = start.latitude + (end.latitude - start.latitude) * t
            val longitude = start.longitude + (end.longitude - start.longitude) * t
            waypoints.add(RouteWaypoint(latitude, longitude, via = true))
        }
        return waypoints
    }

    /** Great-circle distance (haversine), metres. */
    fun distanceMeters(a: LatLng, b: LatLng): Double {
        val earthRadius = 6_371_000.0
        val latitude1 = Math.toRadians(a.latitude)
        val latitude2 = Math.toRadians(b.latitude)
        val deltaLatitude = Math.toRadians(b.latitude - a.latitude)
        val deltaLongitude = Math.toRadians(b.longitude - a.longitude)
        val h = sin(deltaLatitude / 2) * sin(deltaLatitude / 2) +
            cos(latitude1) * cos(latitude2) * sin(deltaLongitude / 2) * sin(deltaLongitude / 2)
        return 2 * earthRadius * asin(min(1.0, sqrt(h)))
    }
}

/** What the route map needs besides the token: the Directions API and the phone's position. */
class RoutePlanning(
    val directions: MapboxDirections,
    val location: LocationProvider,
)
