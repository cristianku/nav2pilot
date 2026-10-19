package com.cristianku.nav2pilot.mapbox

import com.cristianku.nav2pilot.net.HttpClient
import com.cristianku.nav2pilot.net.HttpRequest
import com.cristianku.nav2pilot.net.HttpResponse
import com.cristianku.nav2pilot.security.TokenStore
import com.cristianku.nav2pilot.transport.ErrorText
import com.cristianku.nav2pilot.transport.TransportError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * Mapbox endpoints. Each user signs up for their own free Mapbox account and pastes its default public token:
 * Mapbox has no API for a third-party app to create an account or a token.
 */
object MapboxEndpoints {
    const val SIGNUP_URL = "https://account.mapbox.com/auth/signup/"
    const val TOKENS_PAGE_URL = "https://console.mapbox.com/account/access-tokens/"

    /** Tokens API, "Retrieve a token": answers {"code": "TokenValid" | "TokenInvalid" | "TokenExpired" | ...}. */
    const val TOKEN_CHECK_URL = "https://api.mapbox.com/tokens/v2"

    /** Directions API, same profile as the device's navd; coordinates "lng,lat;lng,lat" follow the slash. */
    const val DIRECTIONS_URL = "https://api.mapbox.com/directions/v5/mapbox/driving-traffic/"

    /** Static Tiles API: raster tiles of the Mapbox Streets style, the map of users with a Mapbox token. */
    const val STREETS_TILES_URL = "https://api.mapbox.com/styles/v1/mapbox/streets-v12/tiles/256/"

    /** The same for the dark theme. */
    const val DARK_TILES_URL = "https://api.mapbox.com/styles/v1/mapbox/dark-v11/tiles/256/"
}

/** Free base map for everybody else: OpenFreeMap styles, no key and no account. */
object OpenFreeMapStyles {
    const val LIGHT = "https://tiles.openfreemap.org/styles/liberty"
    const val DARK = "https://tiles.openfreemap.org/styles/dark"
}

object MapboxTokenRules {
    /** Public tokens start with "pk."; secret ("sk.") and temporary ("tk.") tokens must not be stored in an app. */
    fun isPublicToken(token: String): Boolean {
        if (!token.startsWith("pk.")) {
            return false
        }
        return token.split('.').size == 3
    }

    fun isSecretToken(token: String): Boolean {
        return token.startsWith("sk.")
    }
}

sealed class MapboxTokenCheck {
    object Valid : MapboxTokenCheck()
    data class Rejected(val code: String) : MapboxTokenCheck()
    data class Failed(val error: TransportError) : MapboxTokenCheck()
}

/** Checks a Mapbox token with the Tokens API before it is stored. */
class MapboxTokenVerifier(
    private val http: HttpClient,
    private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun verify(token: String): MapboxTokenCheck {
        val url = MapboxEndpoints.TOKEN_CHECK_URL + "?access_token=" + URLEncoder.encode(token, "UTF-8")
        val request = HttpRequest(method = "GET", url = url, headers = mapOf("Accept" to "application/json"))
        val response: HttpResponse
        try {
            response = withContext(ioDispatcher) { http.execute(request) }
        } catch (error: IOException) {
            return MapboxTokenCheck.Failed(TransportError.Network(error.message ?: error.javaClass.simpleName))
        }

        var code = ""
        try {
            val json = JSONObject(response.body)
            if (!json.isNull("code")) {
                code = json.optString("code", "")
            }
        } catch (notJson: JSONException) {
            return MapboxTokenCheck.Failed(TransportError.HttpFailure(response.code, ErrorText.fromHttpBody(response.body)))
        }
        if (code == "TokenValid") {
            return MapboxTokenCheck.Valid
        }
        if (code.isEmpty()) {
            return MapboxTokenCheck.Failed(TransportError.HttpFailure(response.code, ErrorText.fromHttpBody(response.body)))
        }
        return MapboxTokenCheck.Rejected(code)
    }
}

/** What the Mapbox setup needs: the encrypted token and the token check. */
class MapboxSetup(
    val tokenStore: TokenStore,
    val verifier: MapboxTokenVerifier,
)
