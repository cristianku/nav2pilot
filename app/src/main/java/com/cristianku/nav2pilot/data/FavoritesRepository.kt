package com.cristianku.nav2pilot.data

import com.cristianku.nav2pilot.model.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class FavoritesRepository(private val store: KeyValueStore) {

    private val favoritesState = MutableStateFlow(load())
    val favorites: StateFlow<List<Favorite>> = favoritesState
    private fun readPlaces(key: String) = JsonCodec.favoritesFromJson(store.getString(key) ?: "[]")
    private val savedSlots = MutableStateFlow(readPlaces("shortcuts"))
    val shortcuts: StateFlow<List<Favorite>> = savedSlots
    private val sent = MutableStateFlow(readPlaces("recentSent"))
    val recentSent: StateFlow<List<Favorite>> = sent

    fun setShortcut(slot: String, destination: Destination?) {
        require(slot == "Home" || slot == "Work")
        val updated = savedSlots.value.filterNot { it.id == slot } + listOfNotNull(destination?.let { Favorite(slot, it) })
        store.putString("shortcuts", JsonCodec.favoritesToJson(updated))
        savedSlots.value = updated
    }

    fun recordSent(destination: Destination) {
        val updated = (listOf(Favorite(UUID.randomUUID().toString(), destination)) +
            sent.value.filterNot { it.destination.sameLocationAs(destination) }).take(5)
        store.putString("recentSent", JsonCodec.favoritesToJson(updated))
        sent.value = updated
    }

    fun clearRecent() {
        store.remove("recentSent")
        sent.value = emptyList()
    }

    /** Adds [destination]; a favorite at the same location is returned instead of duplicated. */
    fun add(destination: Destination): Favorite {
        val existing = findByLocation(destination)
        if (existing != null) {
            return existing
        }
        val favorite = Favorite(UUID.randomUUID().toString(), destination)
        val updated = favoritesState.value.toMutableList()
        updated.add(favorite)
        save(updated)
        return favorite
    }

    fun remove(id: String) {
        val updated = mutableListOf<Favorite>()
        for (favorite in favoritesState.value) {
            if (favorite.id != id) {
                updated.add(favorite)
            }
        }
        save(updated)
    }

    fun findByLocation(destination: Destination): Favorite? {
        for (favorite in favoritesState.value) {
            if (favorite.destination.sameLocationAs(destination)) {
                return favorite
            }
        }
        return null
    }

    private fun load(): List<Favorite> {
        val text = store.getString(KEY_FAVORITES) ?: return emptyList()
        return JsonCodec.favoritesFromJson(text)
    }

    private fun save(favorites: List<Favorite>) {
        store.putString(KEY_FAVORITES, JsonCodec.favoritesToJson(favorites))
        favoritesState.value = favorites
    }

    companion object {
        const val KEY_FAVORITES = "favorites"
    }
}
