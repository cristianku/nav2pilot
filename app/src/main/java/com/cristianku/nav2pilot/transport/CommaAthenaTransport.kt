package com.cristianku.nav2pilot.transport

import com.cristianku.nav2pilot.model.BackendKind
import com.cristianku.nav2pilot.model.CoordinateRules
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.DongleIdRules
import com.cristianku.nav2pilot.model.PairedDevice
import com.cristianku.nav2pilot.net.HttpClient
import com.cristianku.nav2pilot.net.HttpRequest
import com.cristianku.nav2pilot.net.HttpResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * comma athena backend.
 *
 * - Device list: GET {api}/v1/me/devices/ with "Authorization: JWT <token>".
 * - Commands: JSON-RPC 2.0 POSTed to https://athena.comma.ai/<dongle_id>, relayed by athena to the
 *   device's athenad websocket.
 * - setNavDestination(latitude, longitude) and clearNavDestination() are the athenad methods of
 *   openpilot / sunnypilot Nav Alpha. Only latitude and longitude are sent: the current athenad
 *   signature has no other parameters and rejects unknown ones with JSON-RPC error -32602.
 */
class CommaAthenaTransport(
    private val http: HttpClient,
    private val tokenProvider: () -> String?,
    private val ioDispatcher: CoroutineDispatcher,
    private val apiBaseUrl: String = CommaEndpoints.API_BASE_URL,
    private val athenaBaseUrl: String = CommaEndpoints.ATHENA_BASE_URL,
) : NavigationTransport {

    override val kind: BackendKind = BackendKind.COMMA_ATHENA
    override val displayName: String = "comma athena"
    override val implemented: Boolean = true

    /** Checks a token before it is stored. The token is only used for this request. */
    suspend fun verifyToken(token: String): TransportResult<AccountInfo> {
        val responseResult = get(apiBaseUrl + CommaEndpoints.PROFILE_PATH, token)
        if (responseResult is TransportResult.Failed) {
            return responseResult
        }
        val response = (responseResult as TransportResult.Ok).value
        try {
            val profile = JSONObject(response.body)
            var email = ""
            if (!profile.isNull("email")) {
                email = profile.optString("email", "")
            }
            return TransportResult.Ok(AccountInfo(email))
        } catch (error: JSONException) {
            return TransportResult.Failed(TransportError.Malformed(ErrorText.shorten(response.body)))
        }
    }

    override suspend fun listDevices(): TransportResult<List<PairedDevice>> {
        val token = loadToken() ?: return TransportResult.Failed(TransportError.NoToken)
        val responseResult = get(apiBaseUrl + CommaEndpoints.DEVICES_PATH, token)
        if (responseResult is TransportResult.Failed) {
            return responseResult
        }
        val response = (responseResult as TransportResult.Ok).value
        try {
            return TransportResult.Ok(parseDevices(response.body))
        } catch (error: JSONException) {
            return TransportResult.Failed(TransportError.Malformed(ErrorText.shorten(response.body)))
        }
    }

    override suspend fun checkStatus(device: PairedDevice): TransportResult<DeviceStatus> {
        val rpcResult = callAthena(device, CommaEndpoints.METHOD_GET_VERSION, null)
        if (rpcResult is TransportResult.Failed) {
            return rpcResult
        }
        val rpc = (rpcResult as TransportResult.Ok).value
        if (rpc is RpcResponse.Error) {
            return TransportResult.Failed(TransportError.DeviceError(rpc.message))
        }
        if (rpc is RpcResponse.Malformed) {
            return TransportResult.Failed(TransportError.Malformed(rpc.detail))
        }
        val value = (rpc as RpcResponse.Result).value
        if (value !is JSONObject) {
            return TransportResult.Failed(TransportError.Malformed(value.toString()))
        }
        return TransportResult.Ok(
            DeviceStatus(
                version = stringField(value, "version"),
                branch = stringField(value, "branch"),
                remote = stringField(value, "remote"),
                commit = stringField(value, "commit"),
            ),
        )
    }

    override suspend fun setDestination(device: PairedDevice, destination: Destination): TransportResult<Delivery> {
        if (!CoordinateRules.isValid(destination.latitude, destination.longitude)) {
            return TransportResult.Failed(TransportError.InvalidRequest("Coordinates out of range"))
        }
        val params = JSONObject()
        params.put("latitude", destination.latitude)
        params.put("longitude", destination.longitude)
        val rpcResult = callAthena(device, CommaEndpoints.METHOD_SET_NAV_DESTINATION, params)
        return interpretCommandResult(rpcResult, CommaEndpoints.METHOD_SET_NAV_DESTINATION)
    }

    override suspend fun clearDestination(device: PairedDevice): TransportResult<Delivery> {
        val rpcResult = callAthena(device, CommaEndpoints.METHOD_CLEAR_NAV_DESTINATION, null)
        return interpretCommandResult(rpcResult, CommaEndpoints.METHOD_CLEAR_NAV_DESTINATION)
    }

    override suspend fun setMapboxToken(device: PairedDevice, token: String): TransportResult<Delivery> {
        val params = JSONObject()
        params.put("token", token)
        val rpcResult = callAthena(device, CommaEndpoints.METHOD_SET_MAPBOX_TOKEN, params)
        return interpretCommandResult(rpcResult, CommaEndpoints.METHOD_SET_MAPBOX_TOKEN)
    }

    private suspend fun callAthena(device: PairedDevice, method: String, params: JSONObject?): TransportResult<RpcResponse> {
        val token = loadToken() ?: return TransportResult.Failed(TransportError.NoToken)
        if (!DongleIdRules.isValid(device.dongleId)) {
            return TransportResult.Failed(TransportError.InvalidRequest("Invalid dongle ID"))
        }
        val request = HttpRequest(
            method = "POST",
            url = athenaBaseUrl + "/" + device.dongleId,
            headers = mapOf(
                "Authorization" to "JWT $token",
                "Content-Type" to "application/json",
                "Accept" to "application/json",
            ),
            body = JsonRpc.buildRequest(method, params),
        )
        val responseResult = execute(request)
        if (responseResult is TransportResult.Failed) {
            return responseResult
        }
        val response = (responseResult as TransportResult.Ok).value
        return TransportResult.Ok(JsonRpc.parseResponse(response.body))
    }

    private fun interpretCommandResult(rpcResult: TransportResult<RpcResponse>, method: String): TransportResult<Delivery> {
        if (rpcResult is TransportResult.Failed) {
            return rpcResult
        }
        val rpc = (rpcResult as TransportResult.Ok).value

        if (rpc is RpcResponse.Error) {
            if (rpc.code == JsonRpc.METHOD_NOT_FOUND) {
                return TransportResult.Failed(TransportError.MethodNotAvailable(method))
            }
            if (rpc.code == JsonRpc.INVALID_PARAMS) {
                return TransportResult.Failed(TransportError.InvalidRequest(rpc.message))
            }
            return TransportResult.Failed(TransportError.DeviceError(rpc.message))
        }
        if (rpc is RpcResponse.Malformed) {
            return TransportResult.Failed(TransportError.Malformed(rpc.detail))
        }

        val value = (rpc as RpcResponse.Result).value
        if (value is JSONObject) {
            if (isSuccess(value.opt("success"))) {
                return TransportResult.Ok(Delivery(queued = false, note = ""))
            }
            var reason = "The device did not accept the command"
            if (!value.isNull("error")) {
                reason = value.optString("error", reason)
            }
            return TransportResult.Failed(TransportError.DeviceRejected(reason))
        }
        // api.comma.ai docs: for queued messages "result is string containing `Device offline, message queued`"
        if (value is String) {
            if (value.contains("queued", ignoreCase = true)) {
                return TransportResult.Ok(Delivery(queued = true, note = value))
            }
            return TransportResult.Failed(TransportError.Malformed(ErrorText.shorten(value)))
        }
        return TransportResult.Failed(TransportError.Malformed(value.toString()))
    }

    private fun isSuccess(successValue: Any?): Boolean {
        if (successValue is Number) {
            return successValue.toInt() == 1
        }
        if (successValue is Boolean) {
            return successValue
        }
        return false
    }

    /** The token provider decrypts with the Android Keystore, so it runs off the main thread. */
    private suspend fun loadToken(): String? {
        return withContext(ioDispatcher) { tokenProvider() }
    }

    private suspend fun get(url: String, token: String): TransportResult<HttpResponse> {
        val request = HttpRequest(
            method = "GET",
            url = url,
            headers = mapOf(
                "Authorization" to "JWT $token",
                "Accept" to "application/json",
            ),
        )
        return execute(request)
    }

    private suspend fun execute(request: HttpRequest): TransportResult<HttpResponse> {
        val response: HttpResponse
        try {
            response = withContext(ioDispatcher) { http.execute(request) }
        } catch (error: IOException) {
            return TransportResult.Failed(TransportError.Network(error.message ?: error.javaClass.simpleName))
        }

        if (response.code == 401) {
            return TransportResult.Failed(TransportError.Unauthorized)
        }
        if (response.code == 403) {
            return TransportResult.Failed(TransportError.Forbidden)
        }
        if (response.code == 429) {
            return TransportResult.Failed(TransportError.RateLimited)
        }
        if (!response.isSuccessful()) {
            return TransportResult.Failed(TransportError.HttpFailure(response.code, ErrorText.fromHttpBody(response.body)))
        }
        return TransportResult.Ok(response)
    }

    companion object {
        fun parseDevices(body: String): List<PairedDevice> {
            val array = JSONArray(body)
            val devices = mutableListOf<PairedDevice>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val dongleId = DongleIdRules.normalize(stringField(item, "dongle_id"))
                if (!DongleIdRules.isValid(dongleId)) {
                    continue
                }
                var lastPing = 0L
                if (!item.isNull("last_athena_ping")) {
                    lastPing = item.optLong("last_athena_ping", 0L)
                }
                var isOwner = true
                if (!item.isNull("is_owner")) {
                    isOwner = item.optBoolean("is_owner", true)
                }
                devices.add(
                    PairedDevice(
                        dongleId = dongleId,
                        alias = stringField(item, "alias"),
                        deviceType = stringField(item, "device_type"),
                        lastAthenaPingSeconds = lastPing,
                        isOwner = isOwner,
                        backend = BackendKind.COMMA_ATHENA,
                        addedManually = false,
                    ),
                )
            }
            return devices
        }

        /** optString returns "null" for JSON null on Android's org.json, so null is checked first. */
        fun stringField(json: JSONObject, key: String): String {
            if (json.isNull(key)) {
                return ""
            }
            return json.optString(key, "")
        }
    }
}
