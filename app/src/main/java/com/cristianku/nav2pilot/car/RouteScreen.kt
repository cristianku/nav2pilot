package com.cristianku.nav2pilot.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.cristianku.nav2pilot.mapbox.RoutePreview
import com.cristianku.nav2pilot.mapbox.RouteShaping
import com.cristianku.nav2pilot.mapbox.RouteWaypoint
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.LatLng
import com.cristianku.nav2pilot.model.PairedDevice
import com.cristianku.nav2pilot.transport.TransportError
import com.cristianku.nav2pilot.transport.TransportErrorText
import com.cristianku.nav2pilot.transport.TransportResult
import com.cristianku.nav2pilot.ui.screens.RouteFormatting
import kotlinx.coroutines.launch

/**
 * One destination on the app's own map, with the fastest route and up to two alternatives from the phone's
 * position (with the user's Mapbox token, as in the Send tab). The picked route goes to the car as via points.
 */
class RouteScreen(
    carContext: CarContext,
    private val model: CarModel,
    private val destination: Destination,
) : Screen(carContext) {
    private var mapboxToken = ""
    private var tokenLoaded = false
    private var loading = true
    private var routes: List<RoutePreview> = emptyList()
    private var selected = 0
    private var origin: LatLng? = null
    /** Why there is no route preview; the car then picks its own route. */
    private var note = ""
    private var sending = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                showMap()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                model.map.clear()
            }
        })
        lifecycleScope.launch { loadRoutes() }
    }

    override fun onGetTemplate(): Template {
        val setup = model.setupMessage()
        val device = model.devices.selectedDevice()
        if (setup != null || device == null) {
            return MessageTemplate.Builder(setup ?: "")
                .setTitle(DestinationsScreen.APP_TITLE)
                .setHeaderAction(Action.BACK)
                .build()
        }

        val header = Header.Builder()
            .setTitle(destination.displayName())
            .setStartHeaderAction(Action.BACK)
            .build()
        val content = ListTemplate.Builder().setHeader(header)
        if (loading) {
            content.setLoading(true)
        } else {
            content.setSingleList(routeList())
        }

        var sendTitle = "Send"
        if (sending) {
            sendTitle = "Sending…"
        }
        val send = Action.Builder()
            .setTitle(sendTitle)
            .setFlags(Action.FLAG_PRIMARY)
            .setOnClickListener { send(device) }
            .build()

        return MapWithContentTemplate.Builder()
            .setContentTemplate(content.build())
            .setActionStrip(ActionStrip.Builder().addAction(send).build())
            .build()
    }

    private fun routeList(): ItemList {
        val items = ItemList.Builder()
        if (routes.isEmpty()) {
            val row = Row.Builder().setTitle(destination.displayName())
            if (destination.details.isNotBlank()) {
                row.addText(destination.details)
            }
            if (note.isNotEmpty()) {
                row.addText(note)
            }
            items.addItem(row.build())
            return items.build()
        }
        for (index in routes.indices) {
            val route = routes[index]
            var title = RouteFormatting.duration(route.durationSeconds) + " · " + RouteFormatting.distance(route.distanceMeters)
            if (index == 0) {
                title = "Fastest · $title"
            }
            val row = Row.Builder().setTitle(title)
            if (route.summary.isNotBlank()) {
                row.addText(route.summary)
            }
            items.addItem(row.build())
        }
        items.setSelectedIndex(selected)
        items.setOnSelectedListener { index -> selectRoute(index) }
        return items.build()
    }

    private fun selectRoute(index: Int) {
        if (index < 0 || index >= routes.size) {
            return
        }
        selected = index
        showMap()
        invalidate()
    }

    private suspend fun loadRoutes() {
        mapboxToken = model.loadMapboxToken()
        tokenLoaded = true
        val planning = model.routePlanning
        if (mapboxToken.isEmpty() || planning == null) {
            finishLoading("Without a Mapbox token in Nav2Pilot the car picks its own route.")
            return
        }
        if (!planning.location.hasPermission()) {
            finishLoading("Nav2Pilot has no location access, so the car picks its own route.")
            return
        }
        showMap()
        val position = planning.location.currentLocation()
        origin = position
        if (position == null) {
            finishLoading("No position for this phone, so the car picks its own route.")
            return
        }
        val result = planning.directions.routes(position, emptyList(), LatLng(destination.latitude, destination.longitude))
        if (result is TransportResult.Ok) {
            routes = result.value
            var message = ""
            if (routes.isEmpty()) {
                message = "Mapbox found no route."
            }
            finishLoading(message)
            return
        }
        val error = (result as TransportResult.Failed).error
        var message = TransportErrorText.describe(error)
        if (error == TransportError.Unauthorized) {
            message = "Mapbox rejected the token (HTTP 401)."
        } else if (error is TransportError.DeviceRejected) {
            message = "Mapbox: " + error.reason
        }
        finishLoading(message)
    }

    private fun finishLoading(message: String) {
        note = message
        loading = false
        showMap()
        invalidate()
    }

    /** Waits for the token: the base map is Mapbox's with it, OpenFreeMap without. */
    private fun showMap() {
        if (!tokenLoaded) {
            return
        }
        model.map.show(CarMapContent(destination, origin, routes, selected, mapboxToken))
    }

    private fun send(device: PairedDevice) {
        if (sending) {
            return
        }
        sending = true
        invalidate()
        // the fastest route needs nothing more than the destination; an alternative goes as silent via points
        var waypoints = emptyList<RouteWaypoint>()
        if (selected > 0 && selected < routes.size) {
            waypoints = RouteShaping.viaPoints(routes[selected])
        }
        lifecycleScope.launch {
            val outcome = model.sender.send(device, destination, waypoints)
            sending = false
            screenManager.push(SentScreen(carContext, outcome) { send(device) })
        }
    }
}
