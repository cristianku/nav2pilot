package com.cristianku.nav2pilot.parsing

import com.cristianku.nav2pilot.model.CoordinateRules
import com.cristianku.nav2pilot.model.LatLng

/**
 * Parses "latitude, longitude" in decimal degrees.
 *
 * Accepted separators: comma, semicolon or whitespace (the same as sunnypilot's on-device
 * parse_destination). An optional "loc:" prefix, used by old Google Maps links, is ignored.
 */
object CoordinateParser {
    private val coordinatePairPattern = Regex(
        """^\s*(?:loc:\s*)?([+-]?\d{1,3}(?:\.\d+)?)\s*[,;\s]\s*([+-]?\d{1,3}(?:\.\d+)?)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String): LatLng? {
        val match = coordinatePairPattern.matchEntire(text) ?: return null
        val latitude = match.groupValues[1].toDoubleOrNull() ?: return null
        val longitude = match.groupValues[2].toDoubleOrNull() ?: return null
        if (!CoordinateRules.isValid(latitude, longitude)) {
            return null
        }
        return LatLng(latitude, longitude)
    }
}
