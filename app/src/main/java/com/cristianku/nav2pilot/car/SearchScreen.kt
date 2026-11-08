package com.cristianku.nav2pilot.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.places.PlaceInfo
import com.cristianku.nav2pilot.places.PlaceKinds
import com.cristianku.nav2pilot.resolve.ResolveOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/**
 * Address search, like the Send tab's: an address, a place, coordinates or a map link. The car shows the keyboard
 * only when it allows it, usually when parked. Places found in OpenStreetMap show whether they are open, as on the
 * phone, once their hours arrive.
 */
class SearchScreen(carContext: CarContext, private val model: CarModel) : Screen(carContext) {
    private var results: List<Destination> = emptyList()
    /** By [Destination.osmId], from [CarModel.placeInfo]. */
    private var info: Map<String, PlaceInfo> = emptyMap()
    private var message = ""
    private var searching = false
    private var searchJob: Job? = null

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                CarRows.followClock({ results.map { destination -> info[destination.osmId]?.openingHours ?: "" } }) { invalidate() }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val callback = object : SearchTemplate.SearchCallback {
            override fun onSearchSubmitted(searchText: String) {
                search(searchText)
            }
        }
        val template = SearchTemplate.Builder(callback)
            .setHeaderAction(Action.BACK)
            .setSearchHint("Address, place or coordinates")
            .setShowKeyboardByDefault(true)
        if (searching) {
            return template.setLoading(true).build()
        }
        val items = ItemList.Builder()
        if (message.isNotEmpty()) {
            items.setNoItemsMessage(message)
        }
        val now = LocalDateTime.now()
        for (destination in results) {
            val row = Row.Builder()
                .setTitle(destination.displayName())
                .setOnClickListener { screenManager.push(RouteScreen(carContext, model, destination)) }
            // "Furniture store · Grüssenweg 21, 4133 Pratteln"
            val text = listOf(PlaceKinds.label(destination.kind), destination.details)
                .filter { part -> part.isNotBlank() && !part.equals(destination.name, ignoreCase = true) }
                .joinToString(" · ")
            if (text.isNotEmpty()) {
                row.addText(text)
            }
            val status = CarRows.status(info[destination.osmId]?.openingHours ?: "", now)
            if (status != null) {
                row.addText(status)
            }
            items.addItem(row.build())
        }
        return template.setItemList(items.build()).build()
    }

    private fun search(text: String) {
        if (text.isBlank()) {
            return
        }
        searchJob?.cancel()
        searching = true
        invalidate()
        searchJob = lifecycleScope.launch {
            val outcome = model.resolver.resolveTyped(text, model.lastKnownPosition())
            results = emptyList()
            message = ""
            if (outcome is ResolveOutcome.Resolved) {
                results = listOf(outcome.destination)
            } else if (outcome is ResolveOutcome.Choices) {
                results = outcome.candidates
            } else if (outcome is ResolveOutcome.Failed) {
                message = outcome.reason
            }
            searching = false
            invalidate()
            // the list first, then its hours: one request for all of it, after Enter as on the phone
            val found = model.placeInfo.lookup(results)
            if (found.isNotEmpty()) {
                info = info + found
                invalidate()
            }
        }
    }
}
