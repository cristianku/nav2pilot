package com.cristianku.nav2pilot.geocode

import com.cristianku.nav2pilot.model.Destination
import java.io.IOException

/** Address / place search. */
interface PlaceSearch {
    fun isAvailable(): Boolean

    @Throws(IOException::class)
    suspend fun search(query: String, maxResults: Int): List<Destination>
}
