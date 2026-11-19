package com.cristianku.nav2pilot.places

import com.cristianku.nav2pilot.testing.FakeHttpClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class BaselParkingTest {
    private val pin = Place(PlaceCategory.PARKING, 47.56, 7.59, "City", "")
    private fun row(free: String, status: String = "offen", at: Instant = Instant.now()) =
        """{"name":"City","title":"Parkhaus City","address":"Uferstrasse 3","geo_point_2d":{"lat":47.56,"lon":7.59},"published":"$at","free":$free,"total":10,"status":"$status"}"""

    @Test fun onlyAnIdentifiedGarageCanReportFull() = runTest {
        val http = FakeHttpClient()
        val service = BaselParking(http, UnconfinedTestDispatcher(testScheduler))
        assertNull(service.lookup(pin.copy(latitude = 48.0)))
        assertTrue(http.requests.isEmpty())
        http.respond(200, """{"results":[{},${row("0")}]}""")
        assertEquals("Full", service.lookup(pin)?.availability())
        assertEquals("Full", service.lookup(pin.copy(name = "Basel Parkhaus City"))?.availability())
        assertNull(service.lookup(pin.copy(name = "Parking")))
        assertNull(service.lookup(pin.copy(name = "Different garage", details = "Uferstrasse 3")))
        assertNull(service.lookup(pin.copy(latitude = 47.575)))
        assertEquals("Full", service.lookup(pin.copy(name = "Parking", details = "Uferstrasse 3, Basel"))?.availability())
        assertEquals(listOf(BaselParking.ENDPOINT), http.requests.map { it.url })
    }

    @Test fun staleAmbiguousAndInvalidCountsAreNotAvailability() = runTest {
        suspend fun lookup(rows: String): BaselParking.Garage? {
            val http = FakeHttpClient()
            http.respond(200, """{"results":[$rows]}""")
            return BaselParking(http, UnconfinedTestDispatcher(testScheduler)).lookup(pin)
        }
        assertNull(lookup(row("3", at = Instant.now().minusSeconds(360))))
        assertNull(lookup("${row("3")},${row("2")}"))
        assertEquals("Availability unknown", lookup(row("null"))?.availability())
        assertEquals("Availability unknown", lookup(row("14"))?.availability())
        assertEquals("Closed", lookup(row("4", "zu"))?.availability())
        assertEquals("4 of 10 spaces free", lookup(row("4"))?.availability())
    }
}
