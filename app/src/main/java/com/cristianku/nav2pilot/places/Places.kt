package com.cristianku.nav2pilot.places

import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.LatLng
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * A kind of point of interest in the map's places menu.
 *
 * [filter] is the Overpass QL tag filter that makes it in OpenStreetMap, [tags] the same as Photon's key:value
 * pairs; [singular] names a place that has no name of its own there.
 */
enum class PlaceCategory(
    val label: String,
    val singular: String,
    val filter: String,
    val tags: List<String>,
) {
    FUEL("Fuel stations", "Fuel station", """["amenity"="fuel"]""", listOf("amenity:fuel")),
    CHARGING("EV charging", "Charging station", """["amenity"="charging_station"]""", listOf("amenity:charging_station")),
    // private car parks are mapped too, and nobody can use them
    PARKING("Parking", "Parking", """["amenity"="parking"]["access"!~"^(private|no)$"]""", listOf("amenity:parking")),
    // motorway service areas ("services") and lay-bys with tables and toilets ("rest_area")
    REST_AREA("Rest areas", "Rest area", """["highway"~"^(services|rest_area)$"]""", listOf("highway:services", "highway:rest_area")),
    RESTAURANT("Restaurants", "Restaurant", """["amenity"~"^(restaurant|fast_food)$"]""", listOf("amenity:restaurant", "amenity:fast_food")),
    CAFE("Cafés", "Café", """["amenity"="cafe"]""", listOf("amenity:cafe")),
    SUPERMARKET("Supermarkets", "Supermarket", """["shop"="supermarket"]""", listOf("shop:supermarket")),
    HOTEL("Hotels", "Hotel", """["tourism"~"^(hotel|motel)$"]""", listOf("tourism:hotel", "tourism:motel")),
    PHARMACY("Pharmacies", "Pharmacy", """["amenity"="pharmacy"]""", listOf("amenity:pharmacy")),
}

enum class ParkingStructure {
    COVERED, OUTDOOR, UNKNOWN;
    companion object {
        fun fromTags(parking: String, covered: String): ParkingStructure = when {
            covered == "yes" || parking in setOf("underground", "multi-storey", "carports", "garage_boxes") -> COVERED
            covered == "no" || parking in setOf("surface", "rooftop", "street_side", "layby", "lane", "shoulder") -> OUTDOOR
            else -> UNKNOWN
        }
    }
}

/** A point of interest from OpenStreetMap, drawn on the map. */
data class Place(
    val category: PlaceCategory,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    /** The address, "" when OpenStreetMap has none. */
    val details: String,
    /** Its main tag, "amenity:fast_food" (see [Destination.kind]); "" when unknown. */
    val kind: String = "",
    /** Its OpenStreetMap element, "node/123"; "" when unknown. */
    val osmId: String = "",
    /** Its opening hours, phone and website, as they came with it from Overpass; null from Photon, which has none. */
    val info: PlaceInfo? = null,
    val parkingNotes: String = "",
    val parkingCapacity: Int? = null,
    val parkingReading: ParkingReading? = null,
    val parkingStructure: ParkingStructure = parkingReading?.structure ?: ParkingStructure.UNKNOWN,
) {
    fun toDestination(): Destination {
        return Destination(latitude = latitude, longitude = longitude, name = name, details = details,
            kind = if (category == PlaceCategory.PARKING) "amenity:parking" else kind, osmId = osmId,
            parkingId = parkingReading?.id.orEmpty())
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

    /**
     * Whether this part of the map shows much that [searched], the part where places were looked up, did not:
     * its middle went a quarter of its height or width away, or the map was zoomed out to half as much again.
     */
    fun movedFrom(searched: MapArea): Boolean {
        val height = searched.north - searched.south
        val width = searched.east - searched.west
        if (height <= 0 || width <= 0) {
            return true
        }
        val center = center()
        val from = searched.center()
        if (abs(center.latitude - from.latitude) > height * MOVED_SHARE) {
            return true
        }
        if (abs(center.longitude - from.longitude) > width * MOVED_SHARE) {
            return true
        }
        return north - south > height * ZOOMED_OUT_FACTOR || east - west > width * ZOOMED_OUT_FACTOR
    }

    companion object {
        private const val METERS_PER_DEGREE = 111_320.0
        private const val MOVED_SHARE = 0.25
        private const val ZOOMED_OUT_FACTOR = 1.5

        /** The box [halfMeters] to each side of [center]: where the car looks for places, having no map of its own. */
        fun around(center: LatLng, halfMeters: Double): MapArea {
            val halfLatitude = halfMeters / METERS_PER_DEGREE
            val halfLongitude = halfMeters / metersPerDegreeOfLongitude(center.latitude)
            return MapArea(
                south = max(-90.0, center.latitude - halfLatitude),
                west = center.longitude - halfLongitude,
                north = min(90.0, center.latitude + halfLatitude),
                east = center.longitude + halfLongitude,
            )
        }

        // near the poles a degree of longitude shrinks to nothing: kept above a kilometre
        private fun metersPerDegreeOfLongitude(latitude: Double): Double {
            return max(METERS_PER_DEGREE * cos(Math.toRadians(latitude)), 1_000.0)
        }
    }
}
