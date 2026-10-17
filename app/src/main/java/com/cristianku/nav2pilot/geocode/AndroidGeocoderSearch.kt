package com.cristianku.nav2pilot.geocode

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import androidx.annotation.RequiresApi
import com.cristianku.nav2pilot.model.CoordinateRules
import com.cristianku.nav2pilot.model.Destination
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Search through the platform android.location.Geocoder.
 *
 * The Geocoder needs a backend service on the phone (normally Google Play services). Without one,
 * Geocoder.isPresent() is false and only coordinates and map links can be used.
 */
class AndroidGeocoderSearch(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
) : PlaceSearch {

    override fun isAvailable(): Boolean {
        return Geocoder.isPresent()
    }

    override suspend fun search(query: String, maxResults: Int): List<Destination> {
        val geocoder = Geocoder(context, Locale.getDefault())
        val addresses: List<Address>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addresses = searchWithListener(geocoder, query, maxResults)
        } else {
            addresses = withContext(ioDispatcher) { searchBlocking(geocoder, query, maxResults) }
        }

        val destinations = mutableListOf<Destination>()
        for (address in addresses) {
            if (!address.hasLatitude() || !address.hasLongitude()) {
                continue
            }
            if (!CoordinateRules.isValid(address.latitude, address.longitude)) {
                continue
            }
            destinations.add(toDestination(address))
        }
        return destinations
    }

    @Suppress("DEPRECATION")
    private fun searchBlocking(geocoder: Geocoder, query: String, maxResults: Int): List<Address> {
        val result = geocoder.getFromLocationName(query, maxResults)
        if (result == null) {
            return emptyList()
        }
        return result
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun searchWithListener(geocoder: Geocoder, query: String, maxResults: Int): List<Address> {
        return suspendCancellableCoroutine { continuation ->
            val listener = object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    if (continuation.isActive) {
                        continuation.resume(addresses)
                    }
                }

                override fun onError(errorMessage: String?) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(IOException(errorMessage ?: "Geocoder error"))
                    }
                }
            }
            geocoder.getFromLocationName(query, maxResults, listener)
        }
    }

    private fun toDestination(address: Address): Destination {
        val firstLine = address.getAddressLine(0) ?: ""
        var name = firstLine.trim()
        var details = ""
        val commaIndex = firstLine.indexOf(',')
        if (commaIndex > 0) {
            name = firstLine.substring(0, commaIndex).trim()
            details = firstLine.substring(commaIndex + 1).trim()
        }
        if (name.isEmpty()) {
            name = address.featureName ?: ""
        }
        return Destination(
            latitude = address.latitude,
            longitude = address.longitude,
            name = name,
            details = details,
        )
    }
}
