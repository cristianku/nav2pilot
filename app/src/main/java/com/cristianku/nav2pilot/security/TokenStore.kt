package com.cristianku.nav2pilot.security

import com.cristianku.nav2pilot.data.KeyValueStore

/**
 * Stores the comma JWT encrypted with [cipher]. The plain token is never logged, never written to
 * disk unencrypted and never shown again in the UI after it is saved.
 */
class TokenStore(
    private val store: KeyValueStore,
    private val cipher: TokenCipher,
) {

    fun save(token: String) {
        val normalized = normalize(token)
        store.putString(KEY_COMMA_TOKEN, cipher.encrypt(normalized))
    }

    /** The token, or null when none is stored or it can no longer be decrypted (e.g. the key was reset). */
    fun load(): String? {
        val encrypted = store.getString(KEY_COMMA_TOKEN) ?: return null
        try {
            return cipher.decrypt(encrypted)
        } catch (error: Exception) {
            // Unreadable ciphertext is useless; drop it so the user is asked for a new token.
            store.remove(KEY_COMMA_TOKEN)
            return null
        }
    }

    fun hasToken(): Boolean {
        return store.getString(KEY_COMMA_TOKEN) != null
    }

    fun clear() {
        store.remove(KEY_COMMA_TOKEN)
    }

    companion object {
        const val KEY_COMMA_TOKEN = "comma_token_encrypted"

        /** Accepts a pasted header value too ("JWT eyJ..."). */
        fun normalize(token: String): String {
            var cleaned = token.trim()
            if (cleaned.startsWith("JWT ", ignoreCase = true)) {
                cleaned = cleaned.substring(4).trim()
            }
            if (cleaned.startsWith("Bearer ", ignoreCase = true)) {
                cleaned = cleaned.substring(7).trim()
            }
            return cleaned
        }
    }
}
