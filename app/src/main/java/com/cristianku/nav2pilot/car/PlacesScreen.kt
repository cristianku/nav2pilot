package com.cristianku.nav2pilot.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.cristianku.nav2pilot.model.LatLng
import com.cristianku.nav2pilot.places.MapArea
import com.cristianku.nav2pilot.places.OverpassPlaces
import com.cristianku.nav2pilot.places.Place
import com.cristianku.nav2pilot.places.PlaceCategory
import com.cristianku.nav2pilot.transport.TransportResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The places of one category around the car, from OpenStreetMap, nearest first on Android Auto's map. Picking one
 * opens its route, like a favorite.
 */
class PlacesScreen(
    carContext: CarContext,
    private val model: CarModel,
    private val category: PlaceCategory,
) : Screen(carContext) {
    private var position: LatLng? = null
    private var places: List<Place> = emptyList()
    private var loading = true
    /** Why no places are shown; "" while loading or when there are some. */
    private var message = ""
    private var failed = false
    private var searchJob: Job? = null

    init {
        search()
    }

    override fun onGetTemplate(): Template {
        val template = PlaceListMapTemplate.Builder()
            .setTitle(category.label)
            .setHeaderAction(Action.BACK)
            .setCurrentLocationEnabled(CarModel.hasFineLocation(carContext))
        if (loading) {
            return template.setLoading(true).build()
        }
        val items = ItemList.Builder()
        if (message.isNotEmpty()) {
            items.setNoItemsMessage(message)
        }
        for (place in places.take(CarRows.placeLimit(carContext))) {
            val destination = place.toDestination()
            items.addItem(CarRows.place(destination, position) { screenManager.push(RouteScreen(carContext, model, destination)) })
        }
        template.setItemList(items.build())
        if (failed) {
            template.setActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setTitle("Try again")
                            .setOnClickListener { search() }
                            .build(),
                    )
                    .build(),
            )
        }
        return template.build()
    }

    private fun search() {
        searchJob?.cancel()
        loading = true
        failed = false
        message = ""
        invalidate()
        searchJob = lifecycleScope.launch {
            val here = model.currentPosition()
            if (here == null) {
                show(emptyList(), noPositionMessage(), failed = true)
                return@launch
            }
            position = here
            val result = model.places.search(category, MapArea.around(here, NEARBY_HALF_METERS))
            if (result is TransportResult.Ok) {
                show(result.value, category.label + ": none within ${NEARBY_HALF_METERS.toInt() / 1000} km.", failed = false)
            } else if (result is TransportResult.Failed) {
                show(emptyList(), OverpassPlaces.errorText(result.error), failed = true)
            }
        }
    }

    private fun show(found: List<Place>, emptyMessage: String, failed: Boolean) {
        places = found
        message = if (found.isEmpty()) emptyMessage else ""
        this.failed = failed
        loading = false
        invalidate()
    }

    private fun noPositionMessage(): String {
        if (!model.hasLocationPermission()) {
            return "Places nearby need this phone's position: allow location access in Nav2Pilot on your phone. " +
                CarModel.SAFE_TO_LOOK
        }
        return "The phone's position is not known yet. Try again in a moment."
    }

    companion object {
        /** The car looks this far to each side of itself. */
        const val NEARBY_HALF_METERS = 10_000.0
    }
}
