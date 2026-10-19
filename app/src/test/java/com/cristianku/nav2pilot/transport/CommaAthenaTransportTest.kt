package com.cristianku.nav2pilot.transport

import com.cristianku.nav2pilot.model.BackendKind
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.PairedDevice
import com.cristianku.nav2pilot.testing.FakeHttpClient
import com.cristianku.nav2pilot.testing.Fixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommaAthenaTransportTest {

    private val http = FakeHttpClient()
    private var token: String? = "secret.jwt.token"
    private val transport = CommaAthenaTransport(http, { token }, UnconfinedTestDispatcher())
    private val device = PairedDevice(Fixtures.DONGLE_ID, "Peugeot 3008")
    private val destination = Destination(45.4642, 9.19, name = "Duomo", details = "Milano")

    private fun failedError(result: TransportResult<*>): TransportError {
        assertTrue("expected failure but got $result", result is TransportResult.Failed)
        return (result as TransportResult.Failed).error
    }

    @Test
    fun demoTokenNeverTouchesTheNetwork() = runTest {
        token = "demo"

        val account = transport.verifyToken(" DEMO ")
        val devices = transport.listDevices()
        val demoCar = (devices as TransportResult.Ok).value.single()
        val status = transport.checkStatus(demoCar)
        val sent = transport.setDestination(demoCar, destination)
        val cleared = transport.clearDestination(demoCar)

        assertEquals(TransportResult.Ok(AccountInfo(DemoAccount.EMAIL)), account)
        assertEquals(DemoAccount.DONGLE_ID, demoCar.dongleId)
        assertEquals("demo", (status as TransportResult.Ok).value.version)
        assertEquals(TransportResult.Ok(Delivery(queued = false, note = "")), sent)
        assertEquals(TransportResult.Ok(Delivery(queued = false, note = "")), cleared)
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun setDestinationPostsJsonRpcToAthena() = runTest {
        http.respond(200, """{"jsonrpc": "2.0", "id": 0, "result": {"success": 1}}""")

        val result = transport.setDestination(device, destination)

        assertEquals(TransportResult.Ok(Delivery(queued = false, note = "")), result)
        val request = http.requests.single()
        assertEquals("POST", request.method)
        assertEquals("https://athena.comma.ai/" + Fixtures.DONGLE_ID, request.url)
        assertEquals("JWT secret.jwt.token", request.headers["Authorization"])
        assertEquals("application/json", request.headers["Content-Type"])

        val body = JSONObject(request.body!!)
        assertEquals("setNavDestination", body.getString("method"))
        assertEquals("2.0", body.getString("jsonrpc"))
        assertEquals(0, body.getInt("id"))
        val params = body.getJSONObject("params")
        assertEquals(45.4642, params.getDouble("latitude"), 0.0)
        assertEquals(9.19, params.getDouble("longitude"), 0.0)
        // athenad's setNavDestination(latitude, longitude) has no other parameters; extra keys would be -32602
        assertEquals(2, params.length())
    }

    @Test
    fun clearDestinationSendsNoParams() = runTest {
        http.respond(200, """{"jsonrpc": "2.0", "id": 0, "result": {"success": 1}}""")

        val result = transport.clearDestination(device)

        assertTrue(result is TransportResult.Ok)
        val body = JSONObject(http.requests.single().body!!)
        assertEquals("clearNavDestination", body.getString("method"))
        assertFalse(body.has("params"))
    }

    @Test
    fun deviceRejectionIsReported() = runTest {
        http.respond(200, """{"jsonrpc": "2.0", "id": 0, "result": {"success": 0, "error": "Invalid destination coordinates"}}""")

        val error = failedError(transport.setDestination(device, destination))

        assertEquals(TransportError.DeviceRejected("Invalid destination coordinates"), error)
    }

    @Test
    fun missingMethodMeansNoNavAlpha() = runTest {
        // shape of openpilot system/athena/rpc.py dumps_error
        http.respond(200, """{"jsonrpc": "2.0", "id": 0, "error": {"code": -32601, "message": "method not found: setNavDestination"}}""")

        val error = failedError(transport.setDestination(device, destination))

        assertEquals(TransportError.MethodNotAvailable("setNavDestination"), error)
        assertTrue(TransportErrorText.describe(error).contains("Nav Alpha"))
    }

    @Test
    fun invalidParamsAndServerErrors() = runTest {
        http.respond(200, """{"jsonrpc": "2.0", "id": 0, "error": {"code": -32602, "message": "unexpected keyword argument"}}""")
        assertEquals(TransportError.InvalidRequest("unexpected keyword argument"), failedError(transport.setDestination(device, destination)))

        http.respond(200, """{"jsonrpc": "2.0", "id": 0, "error": {"code": -32000, "message": "boom"}}""")
        assertEquals(TransportError.DeviceError("boom"), failedError(transport.setDestination(device, destination)))
    }

    @Test
    fun queuedResultString() = runTest {
        // api.comma.ai: "For queued messages, result is string containing `Device offline, message queued`"
        http.respond(200, """{"jsonrpc": "2.0", "id": 0, "result": "Device offline, message queued"}""")

        val result = transport.setDestination(device, destination)

        assertEquals(TransportResult.Ok(Delivery(queued = true, note = "Device offline, message queued")), result)
    }

    @Test
    fun httpStatusMapping() = runTest {
        http.respond(401, """{"error": "unauthorized"}""")
        assertEquals(TransportError.Unauthorized, failedError(transport.setDestination(device, destination)))
        http.respond(403, "")
        assertEquals(TransportError.Forbidden, failedError(transport.setDestination(device, destination)))
        http.respond(429, "")
        assertEquals(TransportError.RateLimited, failedError(transport.setDestination(device, destination)))
        // illustrative body: athena's real error body for an unknown or offline device is not documented
        http.respond(404, """{"error": "Device not found"}""")
        assertEquals(TransportError.HttpFailure(404, "Device not found"), failedError(transport.setDestination(device, destination)))
    }

    @Test
    fun networkErrorDoesNotLeakTheToken() = runTest {
        http.fail("connect timed out")

        val error = failedError(transport.setDestination(device, destination))

        assertEquals(TransportError.Network("connect timed out"), error)
        assertFalse(TransportErrorText.describe(error).contains("secret"))
        assertFalse(http.requests.single().toString().contains("secret"))
    }

    @Test
    fun noTokenNoRequest() = runTest {
        token = null

        assertEquals(TransportError.NoToken, failedError(transport.setDestination(device, destination)))
        assertEquals(TransportError.NoToken, failedError(transport.listDevices()))
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun invalidInputIsRejectedLocally() = runTest {
        val badDevice = PairedDevice("not-a-dongle", "x")
        assertTrue(failedError(transport.setDestination(badDevice, destination)) is TransportError.InvalidRequest)
        assertTrue(failedError(transport.setDestination(device, Destination(95.0, 0.0))) is TransportError.InvalidRequest)
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun checkStatusUsesGetVersion() = runTest {
        http.respond(
            200,
            """{"jsonrpc": "2.0", "id": 0, "result": {"version": "0.10.1", "remote": "github.com/sunnypilot/sunnypilot", "branch": "feature/nav-alpha", "commit": "689f3a1", "commit_date": "2026-10-17"}}""",
        )

        val result = transport.checkStatus(device)

        val status = (result as TransportResult.Ok).value
        assertEquals("0.10.1", status.version)
        assertEquals("feature/nav-alpha", status.branch)
        assertEquals("getVersion", JSONObject(http.requests.single().body!!).getString("method"))
    }

    @Test
    fun listDevicesParsesTheDocumentedFields() = runTest {
        http.respond(
            200,
            """[
              {"dongle_id": "1a2b3c4d5e6f7a8b", "alias": "Peugeot 3008", "device_type": "three", "last_athena_ping": 1760700000, "is_owner": true, "prime": false},
              {"dongle_id": "ffffffffffffffff", "alias": null, "device_type": "mici", "last_athena_ping": null, "is_owner": false},
              {"dongle_id": "bad", "alias": "ignored"}
            ]""",
        )

        val devices = (transport.listDevices() as TransportResult.Ok).value

        val request = http.requests.single()
        assertEquals("GET", request.method)
        assertEquals("https://api.commadotai.com/v1/me/devices/", request.url)
        assertEquals("JWT secret.jwt.token", request.headers["Authorization"])

        assertEquals(2, devices.size)
        assertEquals(PairedDevice("1a2b3c4d5e6f7a8b", "Peugeot 3008", "three", 1760700000L, true, BackendKind.COMMA_ATHENA, false, prime = false), devices[0])
        assertTrue(devices[0].needsMapboxForRoutes())
        // no "prime" field: unknown, which also counts as no prime
        assertEquals(null, devices[1].prime)
        assertTrue(devices[1].needsMapboxForRoutes())
        assertEquals("", devices[1].alias)
        assertEquals(0L, devices[1].lastAthenaPingSeconds)
        assertFalse(devices[1].isOwner)
        assertEquals("ffffffffffffffff", devices[1].displayName())
    }

    @Test
    fun verifyTokenUsesTheGivenTokenNotTheStoredOne() = runTest {
        http.respond(200, """{"email": "driver@example.com", "id": "abcdef0123456789", "username": null}""")

        val result = transport.verifyToken("candidate.jwt.value")

        assertEquals(TransportResult.Ok(AccountInfo("driver@example.com")), result)
        val request = http.requests.single()
        assertEquals("https://api.commadotai.com/v1/me", request.url)
        assertEquals("JWT candidate.jwt.value", request.headers["Authorization"])
    }

    @Test
    fun sunnylinkIsAnExplicitStub() = runTest {
        val sunnylink = SunnylinkTransport()
        assertFalse(sunnylink.implemented)
        val error = failedError(sunnylink.setDestination(device.copy(backend = BackendKind.SUNNYLINK), destination))
        assertTrue(error is TransportError.NotImplemented)

        val registry = TransportRegistry(transport, sunnylink)
        assertTrue(registry.forDevice(device) === transport)
        assertTrue(registry.forDevice(device.copy(backend = BackendKind.SUNNYLINK)) === sunnylink)
    }
}
