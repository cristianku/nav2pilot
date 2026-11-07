package com.cristianku.nav2pilot.geocode

import com.cristianku.nav2pilot.model.LatLng
import com.cristianku.nav2pilot.testing.FakeHttpClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

private const val PHOTON = "https://photon.example/api/"

private val FRICK = LatLng(47.506, 8.016)

private const val LEFT_OUT = "&osm_tag=!highway:bus_stop&osm_tag=!highway:platform&osm_tag=!railway:platform" +
    "&osm_tag=!railway:stop&osm_tag=!railway:tram_stop&osm_tag=!railway:halt&osm_tag=!public_transport" +
    "&osm_tag=!tourism:information"

// "ikea" near Frick, shortened from Photon's answer of 2026-11-07, plus an address without a name and a town
private const val IKEA_ANSWER = """{"features": [
    {"geometry": {"coordinates": [7.7012, 47.5302]}, "properties": {"osm_type": "W", "osm_id": 26343678, "osm_key": "shop", "osm_value": "furniture", "name": "IKEA", "street": "Grüssenweg", "housenumber": "21", "postcode": "4133", "city": "Pratteln", "state": "Basel-Landschaft", "country": "Switzerland"}},
    {"geometry": {"coordinates": [8.3712, 47.4201]}, "properties": {"osm_type": "N", "osm_id": 5, "osm_key": "amenity", "osm_value": "parking", "name": "IKEA", "street": "Industriestrasse", "postcode": "8957", "city": "Spreitenbach"}},
    {"geometry": {"coordinates": [8.3741, 47.4189]}, "properties": {"osm_type": "W", "osm_id": 76278068, "osm_key": "shop", "osm_value": "furniture", "name": "IKEA", "street": "Müslistrasse", "housenumber": "18", "postcode": "8957", "city": "Spreitenbach"}},
    {"geometry": {"coordinates": [200.0, 47.0]}, "properties": {"name": "Broken"}},
    {"geometry": {"coordinates": [8.0158, 47.5072]}, "properties": {"street": "Hauptstrasse", "housenumber": "12", "postcode": "5070", "city": "Frick"}},
    {"geometry": {"coordinates": [8.5417, 47.3769]}, "properties": {"name": "Zürich", "city": null, "state": "Zürich", "country": "Switzerland"}},
    {"geometry": {"coordinates": [8.0, 47.0]}, "properties": {}}
]}"""

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhotonSearchTest {
    @Test
    fun urlAsksNearThePointInTheLanguageWithoutStops() {
        assertEquals(
            "$PHOTON?q=ikea+pratteln&limit=10&lat=47.51&lon=8.02&lang=de$LEFT_OUT",
            PhotonSearch.url(PHOTON, " ikea pratteln ", FRICK, 5, "de"),
        )
    }

    @Test
    fun urlWithoutPointOrWithALanguagePhotonLacks() {
        // Photon answers 400 to "it": the names come in their local language instead
        assertEquals(
            "$PHOTON?q=caf%C3%A9+%26+bar&limit=11$LEFT_OUT",
            PhotonSearch.url(PHOTON, "café & bar", null, 6, "it"),
        )
    }

    @Test
    fun parseKeepsPhotonsOrderAndDropsTheSamePlaceMappedTwice() {
        val found = PhotonSearch.parse(JSONObject(IKEA_ANSWER), 10)

        assertEquals(listOf("IKEA", "IKEA", "Hauptstrasse 12", "Zürich"), found.map { destination -> destination.name })
        assertEquals("Grüssenweg 21, 4133 Pratteln", found[0].details)
        assertEquals(47.5302, found[0].latitude, 0.00001)
        assertEquals(7.7012, found[0].longitude, 0.00001)
        assertEquals("shop:furniture", found[0].kind)
        assertEquals("way/26343678", found[0].osmId)
        // the car park and the shop in Spreitenbach, 250 m apart: the shop takes the car park's place
        assertEquals("Müslistrasse 18, 8957 Spreitenbach", found[1].details)
        assertEquals("way/76278068", found[1].osmId)
        // without Photon's OpenStreetMap fields
        assertEquals("", found[2].kind)
        assertEquals("", found[2].osmId)
        assertEquals("5070 Frick", found[2].details)
        assertEquals("Switzerland", found[3].details)
    }

    @Test
    fun parseStopsAtTheLimit() {
        val found = PhotonSearch.parse(JSONObject(IKEA_ANSWER), 1)

        assertEquals(1, found.size)
        assertEquals("Grüssenweg 21, 4133 Pratteln", found[0].details)
    }

    @Test
    fun searchSendsTheRequestAndReadsTheAnswer() = runTest {
        val http = FakeHttpClient()
        val search = PhotonSearch(http, UnconfinedTestDispatcher(testScheduler), PHOTON, "en")
        http.respond(200, IKEA_ANSWER)

        val found = search.search("ikea", 2, FRICK)

        assertEquals(2, found.size)
        assertEquals("GET", http.requests[0].method)
        assertEquals("$PHOTON?q=ikea&limit=7&lat=47.51&lon=8.02&lang=en$LEFT_OUT", http.requests[0].url)
    }

    @Test
    fun anErrorAnswerIsAnIOException() = runTest {
        val http = FakeHttpClient()
        val search = PhotonSearch(http, UnconfinedTestDispatcher(testScheduler), PHOTON, "en")
        http.respond(503, "Service Unavailable")

        try {
            search.search("ikea", 5, FRICK)
            fail("no exception")
        } catch (error: IOException) {
            assertTrue(error.message!!.contains("503"))
        }
    }
}
