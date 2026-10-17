package com.cristianku.nav2pilot.transport

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonRpcTest {

    @Test
    fun requestWithoutParams() {
        val json = JSONObject(JsonRpc.buildRequest("getVersion", null))
        assertEquals("getVersion", json.getString("method"))
        assertEquals("2.0", json.getString("jsonrpc"))
        assertEquals(0, json.getInt("id"))
        assertEquals(false, json.has("params"))
    }

    @Test
    fun parsesResultErrorAndGarbage() {
        val result = JsonRpc.parseResponse("""{"jsonrpc":"2.0","id":0,"result":{"success":1}}""")
        assertTrue((result as RpcResponse.Result).value is JSONObject)

        assertEquals(RpcResponse.Result(null), JsonRpc.parseResponse("""{"jsonrpc":"2.0","id":0,"result":null}"""))
        assertEquals(
            RpcResponse.Error(-32601, "method not found: x"),
            JsonRpc.parseResponse("""{"jsonrpc":"2.0","id":0,"error":{"code":-32601,"message":"method not found: x"}}"""),
        )
        assertEquals(RpcResponse.Error(0, "plain"), JsonRpc.parseResponse("""{"error":"plain"}"""))
        assertTrue(JsonRpc.parseResponse("<html>bad gateway</html>") is RpcResponse.Malformed)
        assertTrue(JsonRpc.parseResponse("""{"jsonrpc":"2.0","id":0}""") is RpcResponse.Malformed)
    }

    @Test
    fun errorTextIsShortAndSingleLine() {
        val long = "x".repeat(500)
        assertTrue(ErrorText.shorten(long).length <= 201)
        assertEquals("a b", ErrorText.shorten("a\nb"))
        assertEquals("Device not found", ErrorText.fromHttpBody("""{"error":"Device not found"}"""))
        assertEquals("not json", ErrorText.fromHttpBody("not json"))
    }
}
