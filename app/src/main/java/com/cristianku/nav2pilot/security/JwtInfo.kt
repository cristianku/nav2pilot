package com.cristianku.nav2pilot.security

import org.json.JSONException
import org.json.JSONObject
import java.util.Base64

/**
 * Reads public claims of a JWT for display only (RFC 7519). The signature is not verified here:
 * the comma API verifies it on every request.
 */
object JwtInfo {

    fun looksLikeJwt(token: String): Boolean {
        val parts = token.split('.')
        if (parts.size != 3) {
            return false
        }
        for (part in parts) {
            if (part.isEmpty()) {
                return false
            }
        }
        for (character in token) {
            if (character.isWhitespace()) {
                return false
            }
        }
        return true
    }

    /** The "exp" claim (seconds since epoch), or null when absent or unreadable. */
    fun expirySeconds(token: String): Long? {
        val payload = payload(token) ?: return null
        if (!payload.has("exp") || payload.isNull("exp")) {
            return null
        }
        val expiry = payload.optLong("exp", 0L)
        if (expiry <= 0L) {
            return null
        }
        return expiry
    }

    private fun payload(token: String): JSONObject? {
        if (!looksLikeJwt(token)) {
            return null
        }
        val encodedPayload = token.split('.')[1]
        try {
            val bytes = Base64.getUrlDecoder().decode(encodedPayload)
            return JSONObject(String(bytes, Charsets.UTF_8))
        } catch (badBase64: IllegalArgumentException) {
            return null
        } catch (badJson: JSONException) {
            return null
        }
    }
}
