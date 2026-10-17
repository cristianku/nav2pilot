package com.cristianku.nav2pilot.transport

import org.json.JSONException
import org.json.JSONObject

sealed class RpcResponse {
    /** `result` member; [value] is a JSONObject, String, Number, Boolean or null. */
    data class Result(val value: Any?) : RpcResponse()
    data class Error(val code: Int, val message: String) : RpcResponse()
    data class Malformed(val detail: String) : RpcResponse()
}

/**
 * JSON-RPC 2.0 messages as used by athena.
 *
 * Request shape from the comma API schema (JSONRPCPayload: method, jsonrpc "2.0", id 0, optional params).
 * Error shape from openpilot system/athena/rpc.py dumps_error: {"error": {"code": ..., "message": ...}}.
 */
object JsonRpc {
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602

    fun buildRequest(method: String, params: JSONObject?): String {
        val request = JSONObject()
        request.put("method", method)
        if (params != null) {
            request.put("params", params)
        }
        request.put("jsonrpc", "2.0")
        request.put("id", 0)
        return request.toString()
    }

    fun parseResponse(body: String): RpcResponse {
        val json: JSONObject
        try {
            json = JSONObject(body)
        } catch (error: JSONException) {
            return RpcResponse.Malformed(ErrorText.shorten(body))
        }

        if (json.has("error") && !json.isNull("error")) {
            val error = json.get("error")
            if (error is JSONObject) {
                val code = error.optInt("code", 0)
                var message = ""
                if (!error.isNull("message")) {
                    message = error.optString("message", "")
                }
                return RpcResponse.Error(code, message)
            }
            return RpcResponse.Error(0, error.toString())
        }

        if (json.has("result")) {
            if (json.isNull("result")) {
                return RpcResponse.Result(null)
            }
            return RpcResponse.Result(json.get("result"))
        }

        return RpcResponse.Malformed(ErrorText.shorten(body))
    }
}

object ErrorText {
    private const val MAX_LENGTH = 200

    fun shorten(text: String): String {
        val singleLine = text.replace('\n', ' ').replace('\r', ' ').trim()
        if (singleLine.length <= MAX_LENGTH) {
            return singleLine
        }
        return singleLine.substring(0, MAX_LENGTH) + "…"
    }

    /** Best-effort human readable detail from an HTTP error body. */
    fun fromHttpBody(body: String): String {
        try {
            val json = JSONObject(body)
            for (key in listOf("description", "error", "message")) {
                if (json.has(key) && !json.isNull(key)) {
                    val value = json.get(key)
                    if (value is String && value.isNotBlank()) {
                        return shorten(value)
                    }
                }
            }
        } catch (notJson: JSONException) {
            // fall through to the raw body
        }
        return shorten(body)
    }
}
