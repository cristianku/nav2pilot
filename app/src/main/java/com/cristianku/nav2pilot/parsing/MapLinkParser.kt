package com.cristianku.nav2pilot.parsing

import com.cristianku.nav2pilot.model.CoordinateRules
import com.cristianku.nav2pilot.model.Destination

sealed class LinkParseResult {
    /** Coordinates were found in the link. */
    data class Found(val destination: Destination) : LinkParseResult()

    /** The link only names a place or address; it has to be geocoded. */
    data class Query(val text: String) : LinkParseResult()

    object NotRecognized : LinkParseResult()
}

/**
 * Extracts a destination from a map link without any network access.
 *
 * Supported:
 * - geo: URIs (RFC 5870 and the Android "geo:0,0?q=..." form)
 * - google.navigation:q=...
 * - Google Maps URLs (Maps URLs API `api=1` links, /maps/place, /maps/search, /maps/dir, legacy q= / ll= links)
 *
 * Short links (maps.app.goo.gl) carry no coordinates; they are expanded by ShortLinkResolver first.
 */
object MapLinkParser {
    // Place pin inside the "data=" blob: ...!3d<latitude>!4d<longitude>...
    // Not a documented format; observed in links that maps.app.goo.gl redirects to. It is preferred over
    // the "@lat,lng,zoom" viewport because the viewport can be kilometres away from the place.
    private val placePinPattern = Regex("""!3d(-?\d{1,3}(?:\.\d+)?)!4d(-?\d{1,3}(?:\.\d+)?)""")

    // Map viewport centre: /@<latitude>,<longitude>,<zoom>z
    private val viewportPattern = Regex("""@(-?\d{1,3}(?:\.\d+)?),(-?\d{1,3}(?:\.\d+)?)""")

    // Parameters that name the destination, most specific first.
    private val destinationParameterNames = listOf("destination", "daddr", "query", "q")

    fun parse(link: String): LinkParseResult {
        val trimmed = link.trim()
        val lowercase = trimmed.lowercase()
        if (lowercase.startsWith("geo:")) {
            return parseGeoUri(trimmed)
        }
        if (lowercase.startsWith("google.navigation:")) {
            return parseGoogleNavigation(trimmed)
        }
        val parts = UrlParts.parse(trimmed) ?: return LinkParseResult.NotRecognized
        if (!GoogleHosts.isGoogleMapsUrl(parts)) {
            return LinkParseResult.NotRecognized
        }
        return parseGoogleMapsUrl(parts)
    }

    /** Higher is better: exact coordinates, then a viewport centre, then a text query. */
    fun rank(result: LinkParseResult): Int {
        if (result is LinkParseResult.Found) {
            if (result.destination.approximate) {
                return 2
            }
            return 3
        }
        if (result is LinkParseResult.Query) {
            return 1
        }
        return 0
    }

    private fun parseGoogleMapsUrl(parts: UrlParts): LinkParseResult {
        val decodedPath = UrlParts.decode(parts.path)
        val segments = mutableListOf<String>()
        for (segment in decodedPath.split('/')) {
            val cleaned = segment.trim()
            if (cleaned.isNotEmpty()) {
                segments.add(cleaned)
            }
        }
        val placeName = placeNameFromSegments(segments)

        // 1. place pin from the data blob (last one wins: it is the selected place)
        var pinLatitude: Double? = null
        var pinLongitude: Double? = null
        for (match in placePinPattern.findAll(decodedPath)) {
            val latitude = match.groupValues[1].toDoubleOrNull()
            val longitude = match.groupValues[2].toDoubleOrNull()
            if (latitude != null && longitude != null && CoordinateRules.isValid(latitude, longitude)) {
                pinLatitude = latitude
                pinLongitude = longitude
            }
        }
        if (pinLatitude != null && pinLongitude != null) {
            return LinkParseResult.Found(Destination(pinLatitude, pinLongitude, name = placeName))
        }

        // 2. destination / daddr / query / q parameters
        var queryText = ""
        for (parameterName in destinationParameterNames) {
            val value = parts.parameter(parameterName) ?: continue
            val cleaned = value.trim()
            if (cleaned.isEmpty()) {
                continue
            }
            val coordinates = CoordinateParser.parse(cleaned)
            if (coordinates != null) {
                return LinkParseResult.Found(Destination(coordinates.latitude, coordinates.longitude, name = placeName))
            }
            if (queryText.isEmpty()) {
                queryText = cleaned
            }
        }

        // 3. coordinates or a place written in the path (/maps/search/<x>, /maps/place/<x>, /maps/dir/.../<x>)
        val pathTarget = destinationFromSegments(segments)
        if (pathTarget.isNotEmpty()) {
            val coordinates = CoordinateParser.parse(pathTarget)
            if (coordinates != null) {
                return LinkParseResult.Found(Destination(coordinates.latitude, coordinates.longitude, name = placeName))
            }
            // a /maps/place/<name> segment is only a label; it is used as a last resort (step 7)
            if (queryText.isEmpty() && pathTarget != placeName) {
                queryText = pathTarget
            }
        }

        // 4. legacy ll= parameter (documented by Google as the map centre, hence approximate)
        val llValue = parts.parameter("ll")
        if (llValue != null) {
            val coordinates = CoordinateParser.parse(llValue)
            if (coordinates != null) {
                return LinkParseResult.Found(
                    Destination(coordinates.latitude, coordinates.longitude, name = placeName, approximate = true),
                )
            }
        }

        // 5. an explicit text query is a better destination than the viewport
        if (queryText.isNotEmpty()) {
            return LinkParseResult.Query(queryText)
        }

        // 6. viewport centre
        val viewport = viewportPattern.find(decodedPath)
        if (viewport != null) {
            val latitude = viewport.groupValues[1].toDoubleOrNull()
            val longitude = viewport.groupValues[2].toDoubleOrNull()
            if (latitude != null && longitude != null && CoordinateRules.isValid(latitude, longitude)) {
                return LinkParseResult.Found(Destination(latitude, longitude, name = placeName, approximate = true))
            }
        }

        if (placeName.isNotEmpty()) {
            return LinkParseResult.Query(placeName)
        }
        return LinkParseResult.NotRecognized
    }

    private fun isNonTargetSegment(segment: String): Boolean {
        if (segment.startsWith("@")) {
            return true
        }
        if (segment.startsWith("data=")) {
            return true
        }
        return false
    }

    /** The text after /maps/place/, unless it is a coordinate pair. */
    private fun placeNameFromSegments(segments: List<String>): String {
        val placeIndex = segments.indexOf("place")
        if (placeIndex < 0 || placeIndex + 1 >= segments.size) {
            return ""
        }
        val candidate = segments[placeIndex + 1]
        if (isNonTargetSegment(candidate)) {
            return ""
        }
        if (CoordinateParser.parse(candidate) != null) {
            return ""
        }
        return candidate
    }

    private fun destinationFromSegments(segments: List<String>): String {
        val directionsIndex = segments.indexOf("dir")
        if (directionsIndex >= 0) {
            var lastTarget = ""
            for (index in directionsIndex + 1 until segments.size) {
                val segment = segments[index]
                if (!isNonTargetSegment(segment)) {
                    lastTarget = segment
                }
            }
            return lastTarget
        }
        for (keyword in listOf("search", "place")) {
            val keywordIndex = segments.indexOf(keyword)
            if (keywordIndex >= 0 && keywordIndex + 1 < segments.size) {
                val candidate = segments[keywordIndex + 1]
                if (!isNonTargetSegment(candidate)) {
                    return candidate
                }
            }
        }
        return ""
    }

    private fun parseGeoUri(uri: String): LinkParseResult {
        val body = uri.substring("geo:".length)
        var coordinatePart = body
        var queryPart = ""
        val questionIndex = body.indexOf('?')
        if (questionIndex >= 0) {
            coordinatePart = body.substring(0, questionIndex)
            queryPart = body.substring(questionIndex + 1)
        }

        val query = parameterValue(UrlParts.parseQuery(queryPart), "q").trim()
        if (query.isNotEmpty()) {
            var coordinateText = query
            var label = ""
            val labelStart = query.indexOf('(')
            if (labelStart >= 0) {
                coordinateText = query.substring(0, labelStart)
                val labelEnd = query.lastIndexOf(')')
                if (labelEnd > labelStart) {
                    label = query.substring(labelStart + 1, labelEnd).trim()
                } else {
                    label = query.substring(labelStart + 1).trim()
                }
            }
            val coordinates = CoordinateParser.parse(coordinateText)
            if (coordinates != null) {
                return LinkParseResult.Found(Destination(coordinates.latitude, coordinates.longitude, name = label))
            }
            return LinkParseResult.Query(query)
        }

        // RFC 5870 parameters such as ";u=35" follow the coordinates
        val parameterStart = coordinatePart.indexOf(';')
        if (parameterStart >= 0) {
            coordinatePart = coordinatePart.substring(0, parameterStart)
        }
        val coordinates = CoordinateParser.parse(UrlParts.decode(coordinatePart)) ?: return LinkParseResult.NotRecognized
        if (coordinates.latitude == 0.0 && coordinates.longitude == 0.0) {
            return LinkParseResult.NotRecognized
        }
        return LinkParseResult.Found(Destination(coordinates.latitude, coordinates.longitude))
    }

    private fun parseGoogleNavigation(uri: String): LinkParseResult {
        val body = uri.substring("google.navigation:".length)
        val query = parameterValue(UrlParts.parseQuery(body), "q").trim()
        if (query.isEmpty()) {
            return LinkParseResult.NotRecognized
        }
        val coordinates = CoordinateParser.parse(query)
        if (coordinates != null) {
            return LinkParseResult.Found(Destination(coordinates.latitude, coordinates.longitude))
        }
        return LinkParseResult.Query(query)
    }

    private fun parameterValue(parameters: List<QueryParameter>, name: String): String {
        for (parameter in parameters) {
            if (parameter.name == name) {
                return parameter.value
            }
        }
        return ""
    }
}
