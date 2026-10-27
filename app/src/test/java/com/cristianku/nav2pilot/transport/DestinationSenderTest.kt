package com.cristianku.nav2pilot.transport

import com.cristianku.nav2pilot.mapbox.RouteWaypoint
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.PairedDevice
import com.cristianku.nav2pilot.testing.FakeHttpClient
import com.cristianku.nav2pilot.testing.Fixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationSenderTest {
    private val http = FakeHttpClient()
    private val device = PairedDevice(Fixtures.DONGLE_ID, "Peugeot 3008")
    private val destination = Destination(45.4642, 9.19, "Duomo")

    private fun sender(mapboxToken: String?): DestinationSender {
        val comma = CommaAthenaTransport(http, { Fixtures.TEST_JWT }, Dispatchers.Unconfined)
        return DestinationSender(TransportRegistry(comma, SunnylinkTransport())) { mapboxToken }
    }

    @Test
    fun mapboxTokenGoesBeforeTheDestination() = runTest {
        http.respond(200, """{"jsonrpc":"2.0","id":0,"result":{"success":1}}""")
        http.respond(200, """{"jsonrpc":"2.0","id":0,"result":{"success":1}}""")

        val outcome = sender("pk.a.b").send(device, destination, emptyList())

        assertTrue(outcome.success)
        assertEquals("Destination sent to Peugeot 3008.", outcome.message)
        assertEquals("setMapboxToken", JSONObject(http.requests[0].body!!).getString("method"))
        assertEquals("setNavDestination", JSONObject(http.requests[1].body!!).getString("method"))
    }

    @Test
    fun waypointsGoAsARoute() = runTest {
        http.respond(200, """{"jsonrpc":"2.0","id":0,"result":{"success":1}}""")

        val outcome = sender(null).send(device, destination, listOf(RouteWaypoint(45.45, 9.5, via = true)))

        assertTrue(outcome.success)
        assertEquals("Route sent to Peugeot 3008.", outcome.message)
        assertEquals(1, http.requests.size)
        assertEquals("setNavRoute", JSONObject(http.requests[0].body!!).getString("method"))
    }

    @Test
    fun failureSaysWhy() = runTest {
        http.respond(401, """{"error":"unauthorized"}""")

        val outcome = sender(null).send(device, destination, emptyList())

        assertFalse(outcome.success)
        assertTrue(outcome.message, outcome.message.contains("HTTP 401"))
    }
}
