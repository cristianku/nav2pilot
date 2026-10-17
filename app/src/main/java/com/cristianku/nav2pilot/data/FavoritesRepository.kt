package com.cristianku.nav2pilot.data

import com.cristianku.nav2pilot.model.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class FavoritesRepository(private val store: KeyValueStore) {

    private val favoritesState = MutableStateFlow(load())
    val favorites: StateFlow<List<Favorite>> = favoritesState

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
