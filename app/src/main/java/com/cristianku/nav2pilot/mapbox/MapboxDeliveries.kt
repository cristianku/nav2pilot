package com.cristianku.nav2pilot.mapbox

import com.cristianku.nav2pilot.data.KeyValueStore
import com.cristianku.nav2pilot.security.TokenStore
import java.security.MessageDigest

/**
 * Which device already got which Mapbox token, so a send delivers it only once per device and token.
 *
 * Only a SHA-256 fingerprint of the token is kept, in the plain data store: the token itself stays encrypted.
 */
class MapboxDeliveries(private val store: KeyValueStore) {

    fun isDelivered(dongleId: String, token: String): Boolean {
        val stored = store.getString(KEY_PREFIX + dongleId) ?: return false
        return stored == fingerprint(token)
    }

    fun markDelivered(dongleId: String, token: String) {
        store.putString(KEY_PREFIX + dongleId, fingerprint(token))
    }

    fun forget(dongleId: String) {
        store.remove(KEY_PREFIX + dongleId)
    }

    companion object {
        const val KEY_PREFIX = "mapbox_token_delivered_"

        fun fingerprint(token: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
            val hex = StringBuilder()
            for (byte in digest) {
                hex.append(String.format("%02x", byte))
            }
            return hex.toString()
        }
    }
}

/** What the Mapbox setup needs: the encrypted token, the token check and the record of deliveries to devices. */
class MapboxSetup(
    val tokenStore: TokenStore,
    val verifier: MapboxTokenVerifier,
    val deliveries: MapboxDeliveries,
)
