package com.cristianku.nav2pilot.places

import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.LatLng
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * A kind of point of interest in the map's places menu.
 *
 * [filter] is the Overpass QL tag filter that makes it in OpenStreetMap; [singular] names a place that has no
 * name of its own there.
 */
enum class PlaceCategory(
    val label: String,
    val singular: String,
    val filter: String,
) {
    FUEL("Fuel stations", "Fuel station", """["amenity"="fuel"]"""),
    CHARGING("EV charging", "Charging station", """["amenity"="charging_station"]"""),
    // private car parks are mapped too, and nobody can use them
    PARKING("Parking", "Parking", """["amenity"="parking"]["access"!~"^(private|no)$"]"""),
    // motorway service areas ("services") and lay-bys with tables and toilets ("rest_area")
    REST_AREA("Rest areas", "Rest area", """["highway"~"^(services|rest_area)$"]"""),
    RESTAURANT("Restaurants", "Restaurant", """["amenity"~"^(restaurant|fast_food)$"]"""),
    CAFE("Cafés", "Café", """["amenity"="cafe"]"""),
    SUPERMARKET("Supermarkets", "Supermarket", """["shop"="supermarket"]"""),
    HOTEL("Hotels", "Hotel", """["tourism"~"^(hotel|motel)$"]"""),
    PHARMACY("Pharmacies", "Pharmacy", """["amenity"="pharmacy"]"""),
}

/** A point of interest from OpenStreetMap, drawn on the map. */
data class Place(
    val category: PlaceCategory,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    /** The address, "" when OpenStreetMap has none. */
    val details: String,
) {
    fun toDestination(): Destination {
        return Destination(latitude = latitude, longitude = longitude, name = name, details = details)
    }
}

/** A box of latitudes and longitudes: the part of the map the user sees, where the places are looked for. */
data class MapArea(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    fun center(): LatLng {
        return LatLng((south + north) / 2, (west + east) / 2)
    }

    /** The same centre, at most [maxHalfMeters] to each side: a zoomed-out map is searched in its middle. */
    fun limitedTo(maxHalfMeters: Double): MapArea {
        val center = center()
        val halfLatitude = min((north - south) / 2, maxHalfMeters / METERS_PER_DEGREE)
        val halfLongitude = min((east - west) / 2, maxHalfMeters / metersPerDegreeOfLongitude(center.latitude))
        return MapArea(
            south = max(-90.0, center.latitude - halfLatitude),
            west = center.longitude - halfLongitude,
            north = min(90.0, center.latitude + halfLatitude),
            east = center.longitude + halfLongitude,
        )
    }

    companion object {
        private const val METERS_PER_DEGREE = 111_320.0

        // near the poles a degree of longitude shrinks to nothing: kept above a kilometre
        private fun metersPerDegreeOfLongitude(latitude: Double): Double {
            return max(METERS_PER_DEGREE * cos(Math.toRadians(latitude)), 1_000.0)
        }
    }
}
