package com.cristianku.nav2pilot.geocode

import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.LatLng
import java.io.IOException

/** Address / place search. */
interface PlaceSearch {
    fun isAvailable(): Boolean

    /** [near], when known, is where the user is or looks on the map: places close to it come first. */
    @Throws(IOException::class)
    suspend fun search(query: String, maxResults: Int, near: LatLng?): List<Destination>
}
