package com.cristianku.nav2pilot.model

import java.util.Locale
import kotlin.math.abs

data class LatLng(
    val latitude: Double,
    val longitude: Double,
)

/**
 * A navigation destination.
 *
 * Only [latitude] and [longitude] are sent to the device: athenad's setNavDestination accepts
 * exactly these two parameters (see README "Verified API"). [name] and [details] are kept for display
 * and favorites only.
 *
 * [approximate] is true when the coordinates are a map viewport centre rather than a place pin.
 */
data class Destination(
    val latitude: Double,
    val longitude: Double,
    val name: String = "",
    val details: String = "",
    val approximate: Boolean = false,
) {
    fun coordinatesText(): String {
        return String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
    }

    fun displayName(): String {
        if (name.isNotBlank()) {
            return name
        }
        return coordinatesText()
    }

    fun sameLocationAs(other: Destination): Boolean {
        val latitudeDifference = abs(latitude - other.latitude)
        val longitudeDifference = abs(longitude - other.longitude)
        return latitudeDifference < SAME_LOCATION_TOLERANCE_DEGREES && longitudeDifference < SAME_LOCATION_TOLERANCE_DEGREES
    }

    companion object {
        // About 0.1 m: two destinations closer than this are the same place.
        private const val SAME_LOCATION_TOLERANCE_DEGREES = 0.000001
    }
}

object CoordinateRules {
    /**
     * Same acceptance rule as athenad's setNavDestination
     * (`-90 <= latitude <= 90 and -180 <= longitude <= 180`), which also rejects NaN and infinity.
     */
    fun isValid(latitude: Double, longitude: Double): Boolean {
        if (latitude.isNaN() || longitude.isNaN()) {
            return false
        }
        if (latitude < -90.0 || latitude > 90.0) {
            return false
        }
        if (longitude < -180.0 || longitude > 180.0) {
            return false
        }
        return true
    }
}
