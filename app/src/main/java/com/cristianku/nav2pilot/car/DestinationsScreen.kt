package com.cristianku.nav2pilot.car

import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarLocation
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cristianku.nav2pilot.data.Favorite
import com.cristianku.nav2pilot.model.LatLng
import com.cristianku.nav2pilot.ui.Formatting
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The first car screen: the favorites on Android Auto's own map, nearest first. Picking one opens its route.
 * The title is the car the destination goes to; the Car button changes it.
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
        for (favorite in CarModel.nearestFirst(model.favorites.favorites.value, position, placeLimit())) {
            items.addItem(placeRow(favorite))
        }

        val strip = ActionStrip.Builder()
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

    private fun placeRow(favorite: Favorite): Row {
        val destination = favorite.destination
        val place = Place.Builder(CarLocation.create(destination.latitude, destination.longitude))
            .setMarker(PlaceMarker.Builder().build())
            .build()
        // browsable: it opens the route screen; Android Auto asks the others for a distance
        val row = Row.Builder()
            .setTitle(destination.displayName())
            .setMetadata(Metadata.Builder().setPlace(place).build())
            .setBrowsable(true)
            .setOnClickListener { screenManager.push(RouteScreen(carContext, model, destination)) }
        val here = position
        if (here != null) {
            var text = " "
            if (destination.details.isNotBlank()) {
                text += " · " + destination.details
            }
            val withDistance = SpannableString(text)
            withDistance.setSpan(DistanceSpan.create(distance(CarModel.distanceMeters(favorite, here))), 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            row.addText(withDistance)
        } else if (destination.details.isNotBlank()) {
            row.addText(destination.details)
        }
        return row.build()
    }

    /** Kilometres with one decimal like the phone's route chips, metres below one kilometre. */
    private fun distance(meters: Double): Distance {
        if (meters < 1000) {
            return Distance.create(Math.round(meters).toDouble(), Distance.UNIT_METERS)
        }
        return Distance.create(Math.round(meters / 100) / 10.0, Distance.UNIT_KILOMETERS)
    }

    /** How many places the car shows in a list next to its map (usually 6). */
    private fun placeLimit(): Int {
        val constraints = carContext.getCarService(ConstraintManager::class.java)
        return constraints.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST)
    }

    companion object {
        const val APP_TITLE = "Nav2Pilot"
    }
}
