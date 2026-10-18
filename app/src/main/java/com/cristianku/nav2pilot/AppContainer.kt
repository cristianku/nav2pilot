package com.cristianku.nav2pilot

import android.app.Application
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.cristianku.nav2pilot.data.DeviceRepository
import com.cristianku.nav2pilot.data.FavoritesRepository
import com.cristianku.nav2pilot.data.SharedPreferencesStore
import com.cristianku.nav2pilot.geocode.AndroidGeocoderSearch
import com.cristianku.nav2pilot.geocode.PlaceSearch
import com.cristianku.nav2pilot.mapbox.MapboxSetup
import com.cristianku.nav2pilot.mapbox.MapboxTokenVerifier
import com.cristianku.nav2pilot.net.UrlConnectionHttpClient
import com.cristianku.nav2pilot.resolve.DestinationResolver
import com.cristianku.nav2pilot.resolve.ShortLinkResolver
import com.cristianku.nav2pilot.security.KeystoreTokenCipher
import com.cristianku.nav2pilot.security.TokenStore
import com.cristianku.nav2pilot.transport.CommaAthenaTransport
import com.cristianku.nav2pilot.transport.SunnylinkTransport
import com.cristianku.nav2pilot.transport.TransportRegistry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Manual dependency wiring: one instance of each service for the whole app. */
class AppContainer(context: Context) {
    val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    private val httpClient = UrlConnectionHttpClient(userAgent = "Nav2Pilot/" + appVersion(context) + " (Android)")

    private val dataStore = SharedPreferencesStore(
        context.getSharedPreferences(DATA_PREFERENCES, Context.MODE_PRIVATE),
    )
    private val secureStore = SharedPreferencesStore(
        context.getSharedPreferences(SECURE_PREFERENCES, Context.MODE_PRIVATE),
    )

    val tokenStore = TokenStore(secureStore, KeystoreTokenCipher())
    val mapbox = MapboxSetup(
        tokenStore = TokenStore(secureStore, KeystoreTokenCipher(), TokenStore.KEY_MAPBOX_TOKEN),
        verifier = MapboxTokenVerifier(httpClient, ioDispatcher),
    )
    val favoritesRepository = FavoritesRepository(dataStore)
    val deviceRepository = DeviceRepository(dataStore)

    val commaTransport = CommaAthenaTransport(
        http = httpClient,
        tokenProvider = { tokenStore.load() },
        ioDispatcher = ioDispatcher,
    )
    val transports = TransportRegistry(commaTransport, SunnylinkTransport())

    val placeSearch: PlaceSearch = AndroidGeocoderSearch(context.applicationContext, ioDispatcher)
    val destinationResolver = DestinationResolver(ShortLinkResolver(httpClient), placeSearch, ioDispatcher)

    companion object {
        private fun appVersion(context: Context): String {
            val packageInfo: PackageInfo
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageInfo = context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            }
            return packageInfo.versionName ?: "unknown"
        }

        private const val DATA_PREFERENCES = "nav2pilot_data"
        private const val SECURE_PREFERENCES = "nav2pilot_secure"
    }
}

class Nav2PilotApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
