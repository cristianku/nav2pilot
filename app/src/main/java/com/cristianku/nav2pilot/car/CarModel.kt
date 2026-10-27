package com.cristianku.nav2pilot.car

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.cristianku.nav2pilot.AppContainer
import com.cristianku.nav2pilot.data.DeviceRepository
import com.cristianku.nav2pilot.data.Favorite
import com.cristianku.nav2pilot.data.FavoritesRepository
import com.cristianku.nav2pilot.mapbox.RoutePlanning
import com.cristianku.nav2pilot.mapbox.RouteShaping
import com.cristianku.nav2pilot.model.LatLng
import com.cristianku.nav2pilot.resolve.DestinationResolver
import com.cristianku.nav2pilot.security.TokenStore
import com.cristianku.nav2pilot.transport.DestinationSender
import com.cristianku.nav2pilot.transport.TransportRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * What the car screens share with the phone app. Both run in the same process, so a favorite or a car added on the
 * phone shows up in the car at once. Setup (comma token, Mapbox token, cars) is only on the phone.
 */
class CarModel(
    val devices: DeviceRepository,
    val favorites: FavoritesRepository,
    private val commaToken: TokenStore,
    private val mapboxToken: TokenStore,
    val transports: TransportRegistry,
    val sender: DestinationSender,
    val resolver: DestinationResolver,
    /** False without a geocoder: the search button is hidden. */
    val searchAvailable: Boolean,
    val routePlanning: RoutePlanning?,
    val map: CarMap,
    private val ioDispatcher: CoroutineDispatcher,
) {
    constructor(container: AppContainer, map: CarMap) : this(
        devices = container.deviceRepository,
        favorites = container.favoritesRepository,
        commaToken = container.tokenStore,
        mapboxToken = container.mapbox.tokenStore,
        transports = container.transports,
        sender = container.sender,
        resolver = container.destinationResolver,
        searchAvailable = container.placeSearch.isAvailable(),
        routePlanning = container.routePlanning,
        map = map,
        ioDispatcher = container.ioDispatcher,
    )

    /** What is missing before anything can be sent, or null. Android Auto asks to look at the phone only when safe. */
    fun setupMessage(): String? {
        if (!commaToken.hasToken()) {
            return "Set up Nav2Pilot on your phone: add your comma token in Devices. $SAFE_TO_LOOK"
        }
        if (devices.selectedDevice() == null) {
            return "No car in Nav2Pilot yet: add it in Devices on your phone. $SAFE_TO_LOOK"
        }
        return null
    }

    /** The Mapbox public token, or "" without one; decrypted off the main thread. */
    suspend fun loadMapboxToken(): String {
        return withContext(ioDispatcher) { mapboxToken.load() } ?: ""
    }

    /** Null without the location permission (asked by the phone app) or without a fix. */
    suspend fun currentPosition(): LatLng? {
        val planning = routePlanning ?: return null
        return planning.location.currentLocation()
    }

    companion object {
        const val SAFE_TO_LOOK = "Only look at your phone when it's safe to do so."

        /** Android Auto draws the phone's position on its own maps only with the fine location permission. */
        fun hasFineLocation(context: Context): Boolean {
            return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        /** The nearest favorites first when the position is known, otherwise in the order they were added. */
        fun nearestFirst(favorites: List<Favorite>, position: LatLng?, limit: Int): List<Favorite> {
            var ordered = favorites
            if (position != null) {
                ordered = favorites.sortedBy { distanceMeters(it, position) }
            }
            if (ordered.size > limit) {
                return ordered.subList(0, limit)
            }
            return ordered
        }

        fun distanceMeters(favorite: Favorite, position: LatLng): Double {
            val destination = favorite.destination
            return RouteShaping.distanceMeters(position, LatLng(destination.latitude, destination.longitude))
        }
    }
}
