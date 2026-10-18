package com.cristianku.nav2pilot.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import com.cristianku.nav2pilot.model.LatLng
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** The phone's position, used as the start of the route previews (the phone is normally in the car). */
interface LocationProvider {
    /** False until the user allowed location access; the screen asks for it. */
    fun hasPermission(): Boolean

    /** Null when there is no permission or no fix. */
    suspend fun currentLocation(): LatLng?
}

/**
 * One-shot location through the platform LocationManager (no Google Play services needed).
 *
 * A recent last known fix is reused; otherwise the first fix of any enabled provider wins.
 */
class AndroidLocationProvider(context: Context) : LocationProvider {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    override fun hasPermission(): Boolean {
        val coarse = appContext.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        val fine = appContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        if (coarse == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        if (fine == PackageManager.PERMISSION_GRANTED) {
            return true
        }
        return false
    }

    @SuppressLint("MissingPermission") // checked by hasPermission() first
    override suspend fun currentLocation(): LatLng? {
        if (!hasPermission()) {
            return null
        }
        val lastKnown = freshestLastKnown()
        if (lastKnown != null && ageMillis(lastKnown) < MAX_AGE_MILLIS) {
            return toLatLng(lastKnown)
        }

        val fix = withTimeoutOrNull(FIX_TIMEOUT_MILLIS) { firstFix() }
        if (fix != null) {
            return toLatLng(fix)
        }
        // an older position is still a usable start for a preview
        if (lastKnown != null) {
            return toLatLng(lastKnown)
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun freshestLastKnown(): Location? {
        var best: Location? = null
        // all providers the app may use, enabled or not, passive included: any of them may hold a recent fix
        for (provider in manager.getProviders(false)) {
            val location: Location?
            try {
                location = manager.getLastKnownLocation(provider)
            } catch (notAllowed: SecurityException) {
                continue
            }
            if (location == null) {
                continue
            }
            val current = best
            if (current == null || location.elapsedRealtimeNanos > current.elapsedRealtimeNanos) {
                best = location
            }
        }
        return best
    }

    @SuppressLint("MissingPermission")
    private suspend fun firstFix(): Location? {
        val providers = mutableListOf<String>()
        for (provider in manager.getProviders(true)) {
            if (provider != LocationManager.PASSIVE_PROVIDER) {
                providers.add(provider)
            }
        }
        if (providers.isEmpty()) {
            return null
        }
        return suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    manager.removeUpdates(this)
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }

                // needed on API < 30, where these are abstract
                override fun onProviderEnabled(provider: String) {}

                override fun onProviderDisabled(provider: String) {}

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
            }
            var requested = false
            for (provider in providers) {
                try {
                    manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                    requested = true
                } catch (notAllowed: SecurityException) {
                    // e.g. gps with only approximate location granted
                } catch (unknown: IllegalArgumentException) {
                    // provider vanished
                }
            }
            if (!requested && continuation.isActive) {
                continuation.resume(null)
            }
            continuation.invokeOnCancellation {
                manager.removeUpdates(listener)
            }
        }
    }

    private fun ageMillis(location: Location): Long {
        return (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
    }

    private fun toLatLng(location: Location): LatLng {
        return LatLng(location.latitude, location.longitude)
    }

    companion object {
        // a fix younger than this is reused instead of waiting for a new one
        private const val MAX_AGE_MILLIS = 120_000L
        private const val FIX_TIMEOUT_MILLIS = 15_000L
    }
}
