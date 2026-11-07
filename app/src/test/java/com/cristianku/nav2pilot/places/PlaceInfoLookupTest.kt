package com.cristianku.nav2pilot.places

import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.LatLng
import com.cristianku.nav2pilot.testing.FakeHttpClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

private const val SWISS_SERVER = OverpassEndpoints.SWITZERLAND
private const val MAIN_SERVER = "https://main.example/api/interpreter"

private val PRATTELN = Destination(47.5302, 7.7012, "IKEA", kind = "shop:furniture", osmId = "way/26343678")
private val SPREITENBACH = Destination(47.4189, 8.3741, "IKEA", kind = "shop:furniture", osmId = "way/76278068")
private val FREIBURG = Destination(48.0185, 7.8036, "IKEA", kind = "shop:furniture", osmId = "way/30519110")

// Overpass's answer of 7 November 2026, shortened
private const val SWISS_ANSWER = """{"elements": [
    {"type": "way", "id": 26343678, "tags": {"name": "IKEA", "shop": "furniture",
     "opening_hours": "Mo-We 10:00-20:00; Th-Fr 10:00-21:00; Sa 09:00-20:00", "phone": "+41 848 801100",
     "website": "https://www.ikea.com/ch/de/stores/pratteln/"}},
    {"type": "way", "id": 76278068, "tags": {"name": "IKEA", "opening_hours": "Mo-Sa 09:00-21:00",
     "contact:phone": "+41 848 801 100; +41 56 418 00 00", "contact:website": "www.ikea.com/ch/de/stores/spreitenbach"}}
]}"""

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceInfoLookupTest {
    private fun body(http: FakeHttpClient, index: Int): String {
        return URLDecoder.decode(http.requests[index].body!!.removePrefix("data="), "UTF-8")
    }

    @Test
    fun queryAsksTheElementsByTypeAndId() {
        assertEquals(
            "[out:json][timeout:5];(node(id:5);way(id:26343678,76278068);relation(id:7););out tags;",
            PlaceInfoLookup.query(listOf("way/26343678", "node/5", "way/76278068", "relation/7", "bad/1", "way/x")),
        )
    }

    @Test
    fun infoFromTheTags() {
        val info = PlaceInfoLookup.parse(JSONObject(SWISS_ANSWER))

        assertEquals("Mo-We 10:00-20:00; Th-Fr 10:00-21:00; Sa 09:00-20:00", info["way/26343678"]!!.openingHours)
        assertEquals("+41 848 801100", info["way/26343678"]!!.phone)
        // the first of two numbers, and an address without https
        assertEquals("+41 848 801 100", info["way/76278068"]!!.phone)
        assertEquals("https://www.ikea.com/ch/de/stores/spreitenbach", info["way/76278068"]!!.website)
    }

    @Test
    fun onlyWebPagesAreKept() {
        assertEquals("http://example.com", PlaceInfo.webAddress("http://example.com"))
        assertEquals("", PlaceInfo.webAddress("javascript:alert(1)"))
        assertEquals("", PlaceInfo.webAddress("intent://scan/#Intent;end"))
        assertEquals("", PlaceInfo.webAddress("see the sign"))
    }

    @Test
    fun oneRequestForTheListThenFromMemory() = runTest {
        val http = FakeHttpClient()
        val lookup = PlaceInfoLookup(http, UnconfinedTestDispatcher(testScheduler)) { listOf(SWISS_SERVER, MAIN_SERVER) }
        http.respond(200, SWISS_ANSWER)

        val first = lookup.lookup(listOf(PRATTELN, SPREITENBACH, Destination(47.0, 8.0, "Address")))
        val again = lookup.lookup(listOf(SPREITENBACH))

        assertEquals(1, http.requests.size)
        assertEquals(SWISS_SERVER, http.requests[0].url)
        assertTrue(body(http, 0).contains("way(id:26343678,76278068)"))
        assertEquals(setOf("way/26343678", "way/76278068"), first.keys)
        assertEquals("Mo-Sa 09:00-21:00", again["way/76278068"]!!.openingHours)
    }

    @Test
    fun whatTheSwissServerLacksGoesToTheNext() = runTest {
        val http = FakeHttpClient()
        val lookup = PlaceInfoLookup(http, UnconfinedTestDispatcher(testScheduler)) { listOf(SWISS_SERVER, MAIN_SERVER) }
        http.respond(200, SWISS_ANSWER)
        http.respond(200, """{"elements": [{"type": "way", "id": 30519110, "tags": {"opening_hours": "Mo-Sa 10:00-20:00"}}]}""")

        val found = lookup.lookup(listOf(PRATTELN, FREIBURG))

        assertEquals(2, http.requests.size)
        assertEquals(MAIN_SERVER, http.requests[1].url)
        assertTrue(body(http, 1).contains("way(id:30519110)"))
        assertFalse(body(http, 1).contains("26343678"))
        assertEquals("Mo-Sa 10:00-20:00", found["way/30519110"]!!.openingHours)
        assertEquals(2, found.size)
    }

    @Test
    fun noAnswerIsAskedAgainNextTime() = runTest {
        val http = FakeHttpClient()
        val lookup = PlaceInfoLookup(http, UnconfinedTestDispatcher(testScheduler)) { listOf(MAIN_SERVER) }
        http.fail("timeout")
        http.respond(200, """{"remark": "runtime error: out of memory", "elements": []}""")
        http.respond(200, """{"elements": []}""")

        assertTrue(lookup.lookup(listOf(FREIBURG)).isEmpty())
        assertTrue(lookup.lookup(listOf(FREIBURG)).isEmpty())
        // answered: not in OpenStreetMap any more, and not asked about again
        assertTrue(lookup.lookup(listOf(FREIBURG))["way/30519110"]!!.isEmpty())
        assertTrue(lookup.lookup(listOf(FREIBURG))["way/30519110"]!!.isEmpty())
        assertEquals(3, http.requests.size)
    }

    @Test
    fun swissServerFirstForPlacesAroundSwitzerland() {
        // Stuttgart and Pratteln
        assertEquals(SWISS_SERVER, OverpassEndpoints.forPoints(listOf(LatLng(48.78, 9.18), LatLng(47.53, 7.70)))[0])
        assertFalse(OverpassEndpoints.forPoints(listOf(LatLng(48.78, 9.18))).contains(SWISS_SERVER))
    }
}
