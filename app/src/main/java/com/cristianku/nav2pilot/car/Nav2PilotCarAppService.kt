package com.cristianku.nav2pilot.car

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import androidx.car.app.AppManager
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator
import com.cristianku.nav2pilot.Nav2PilotApplication
import com.cristianku.nav2pilot.R

/**
 * The Android Auto screen: a point-of-interest app of the Car App Library (category POI in the manifest). The car
 * shows templates that Android Auto draws; the route map is the only thing the app draws itself.
 */
class Nav2PilotCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator {
        // debug builds also talk to the Desktop Head Unit and test hosts
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        }
        return HostValidator.Builder(applicationContext)
            .addAllowedHosts(R.array.car_hosts_allowlist)
            .build()
    }

    override fun onCreateSession(): Session {
        return Nav2PilotSession()
    }
}

class Nav2PilotSession : Session() {
    private var renderer: CarMapRenderer? = null

    override fun onCreateScreen(intent: Intent): Screen {
        val container = (carContext.applicationContext as Nav2PilotApplication).container
        val map = CarMapRenderer(carContext)
        renderer = map
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(map)
        lifecycle.addObserver(map)
        return DestinationsScreen(carContext, CarModel(container, map))
    }

    /** Day and night: the templates follow by themselves, the map is redrawn. */
    override fun onCarConfigurationChanged(newConfiguration: Configuration) {
        renderer?.refresh()
    }
}
