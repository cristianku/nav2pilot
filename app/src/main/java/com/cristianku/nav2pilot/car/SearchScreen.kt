package com.cristianku.nav2pilot.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.resolve.ResolveOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Address search, like the Send tab's: an address, a place, coordinates or a map link. The car shows the keyboard
 * only when it allows it, usually when parked.
 */
class SearchScreen(carContext: CarContext, private val model: CarModel) : Screen(carContext) {
    private var results: List<Destination> = emptyList()
    private var message = ""
    private var searching = false
    private var searchJob: Job? = null

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
        for (destination in results) {
            val row = Row.Builder()
                .setTitle(destination.displayName())
                .setOnClickListener { screenManager.push(RouteScreen(carContext, model, destination)) }
            if (destination.details.isNotBlank()) {
                row.addText(destination.details)
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
            val outcome = model.resolver.resolveTyped(text)
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
        }
    }
}
