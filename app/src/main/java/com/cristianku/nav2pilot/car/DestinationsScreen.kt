package com.cristianku.nav2pilot.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cristianku.nav2pilot.model.LatLng
import com.cristianku.nav2pilot.ui.Formatting
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The first car screen: the favorites on Android Auto's own map, nearest first. Picking one opens its route.
 * The title is the car the destination goes to; the Car button changes it, Nearby finds places around the car.
 */
class DestinationsScreen(carContext: CarContext, private val model: CarModel) : Screen(carContext) {
    private var position: LatLng? = null

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // the phone app runs in the same process: what changes there shows up here
                    combine(model.favorites.favorites, model.devices.devices, model.devices.selectedDongleId) { _, _, _ -> Unit }
                        .collect { invalidate() }
                }
                val found = model.currentPosition()
                if (found != null) {
                    position = found
                    invalidate()
                }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val setup = model.setupMessage()
        if (setup != null) {
            return MessageTemplate.Builder(setup)
                .setTitle(APP_TITLE)
                .setHeaderAction(Action.APP_ICON)
                .build()
        }
        val device = model.devices.selectedDevice() ?: return MessageTemplate.Builder(APP_TITLE).build()

        val items = ItemList.Builder()
        items.setNoItemsMessage("No favorites yet. Star places in Nav2Pilot on your phone, or search for one.")
        for (favorite in CarModel.nearestFirst(model.favorites.favorites.value, position, CarRows.placeLimit(carContext))) {
            val destination = favorite.destination
            items.addItem(CarRows.place(destination, position) { screenManager.push(RouteScreen(carContext, model, destination)) })
        }

        val strip = ActionStrip.Builder()
        strip.addAction(
            Action.Builder()
                .setTitle("Nearby")
                .setOnClickListener { screenManager.push(NearbyScreen(carContext, model)) }
                .build(),
        )
        if (model.searchAvailable) {
            strip.addAction(
                Action.Builder()
                    .setTitle("Search")
                    .setOnClickListener { screenManager.push(SearchScreen(carContext, model)) }
                    .build(),
            )
        }
        strip.addAction(
            Action.Builder()
                .setTitle("Car")
                .setOnClickListener { screenManager.push(CarsScreen(carContext, model)) }
                .build(),
        )

        return PlaceListMapTemplate.Builder()
            .setTitle(Formatting.carName(device))
            .setHeaderAction(Action.APP_ICON)
            .setItemList(items.build())
            .setActionStrip(strip.build())
            .setCurrentLocationEnabled(CarModel.hasFineLocation(carContext))
            .build()
    }

    companion object {
        const val APP_TITLE = "Nav2Pilot"
    }
}
