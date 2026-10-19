package com.cristianku.nav2pilot.transport

import com.cristianku.nav2pilot.model.BackendKind
import com.cristianku.nav2pilot.model.PairedDevice
import org.json.JSONObject

/**
 * Demo account for the Google Play review: the token "DEMO" gives a fake account with one fake car, so every
 * screen can be tried without a comma device. Nothing is sent to comma's servers while it is in use.
 */
object DemoAccount {
    const val TOKEN = "DEMO"
    const val EMAIL = "demo@nav2pilot.app"
    const val DONGLE_ID = "0000000000000de0"
    const val ALIAS = "Demo car"

    fun isDemoToken(token: String?): Boolean {
        if (token == null) {
            return false
        }
        return token.trim().equals(TOKEN, ignoreCase = true)
    }

    fun device(nowSeconds: Long): PairedDevice {
        return PairedDevice(
            dongleId = DONGLE_ID,
            alias = ALIAS,
            deviceType = "demo",
            lastAthenaPingSeconds = nowSeconds,
            isOwner = true,
            backend = BackendKind.COMMA_ATHENA,
            addedManually = false,
            prime = true,
        )
    }

    /** What athena would answer: getVersion gets a version, every other command succeeds. */
    fun rpcAnswer(method: String): RpcResponse {
        if (method == CommaEndpoints.METHOD_GET_VERSION) {
            val version = JSONObject()
            version.put("version", "demo")
            version.put("branch", "demo")
            version.put("remote", "")
            version.put("commit", "")
            return RpcResponse.Result(version)
        }
        val success = JSONObject()
        success.put("success", 1)
        return RpcResponse.Result(success)
    }
}
