package com.cristianku.nav2pilot.places

import com.cristianku.nav2pilot.testing.FakeHttpClient
import com.cristianku.nav2pilot.transport.TransportError
import com.cristianku.nav2pilot.transport.TransportResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val PHOTON = "https://photon.example/reverse"
private const val OVERPASS = "https://overpass.example/api/interpreter"

private val SAN_FRANCISCO = MapArea(south = 37.72, west = -122.48, north = 37.82, east = -122.36)

private const val PHOTON_ANSWER = """{"features": [
    {"geometry": {"coordinates": [-122.30, 37.77]}, "properties": {"name": "Across the bay"}},
    {"geometry": {"coordinates": [-122.45, 37.75]}, "properties": {"name": "Far"}},
    {"geometry": {"coordinates": [-122.42, 37.77]}, "properties": {"name": "Near", "street": "Market St", "housenumber": "1", "postcode": "94103", "city": "San Francisco"}},
    {"geometry": {"coordinates": [-122.41, 37.76]}, "properties": {}}
]}"""

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PhotonPlacesTest {
    @Test
    fun urlAsksAllTheCategoryTagsInACircleAroundTheArea() {
        assertEquals(
            "$PHOTON?lat=37.77000&lon=-122.42000&radius=8&limit=50&osm_tag=amenity:restaurant&osm_tag=amenity:fast_food",
            PhotonPlaces.url(PHOTON, PlaceCategory.RESTAURANT, SAN_FRANCISCO),
        )
    }

    @Test
    fun parsePlacesKeepsTheAreaAndPutsTheNearestFirst() {
        val places = PhotonPlaces.parsePlaces(JSONObject(PHOTON_ANSWER), PlaceCategory.FUEL, SAN_FRANCISCO)

        assertEquals(listOf("Near", "Fuel station", "Far"), places.map { place -> place.name })
        assertEquals("Market St 1, 94103 San Francisco", places[0].details)
        assertEquals(37.77, places[0].latitude, 0.0001)
        assertEquals(-122.42, places[0].longitude, 0.0001)
    }

    @Test
    fun theReserveAnswersWhenNoOverpassServerDoes() = runTest {
        val http = FakeHttpClient()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val places = OverpassPlaces(http, dispatcher, { listOf(OVERPASS) }, PhotonPlaces(http, dispatcher, PHOTON))

        http.fail("timeout")
        http.respond(200, PHOTON_ANSWER)
        val found = places.search(PlaceCategory.FUEL, SAN_FRANCISCO)

        assertEquals(3, (found as TransportResult.Ok).value.size)
        assertEquals("GET", http.requests[1].method)
        assertTrue(http.requests[1].url.startsWith(PHOTON))
    }

    @Test
    fun aReserveWithNothingLeavesTheOverpassError() = runTest {
        val http = FakeHttpClient()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val places = OverpassPlaces(http, dispatcher, { listOf(OVERPASS) }, PhotonPlaces(http, dispatcher, PHOTON))

        http.respond(504, "<html>Gateway Timeout</html>")
        http.respond(200, """{"features": []}""")
        val empty = places.search(PlaceCategory.FUEL, SAN_FRANCISCO)
        assertEquals(504, ((empty as TransportResult.Failed).error as TransportError.HttpFailure).code)

        http.respond(504, "<html>Gateway Timeout</html>")
        http.fail("timeout")
        val down = places.search(PlaceCategory.FUEL, SAN_FRANCISCO)
        assertEquals(504, ((down as TransportResult.Failed).error as TransportError.HttpFailure).code)
    }

    @Test
    fun overpassAnsweringMeansNoReserve() = runTest {
        val http = FakeHttpClient()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val places = OverpassPlaces(http, dispatcher, { listOf(OVERPASS) }, PhotonPlaces(http, dispatcher, PHOTON))

        http.respond(200, """{"elements": []}""")
        assertTrue((places.search(PlaceCategory.FUEL, SAN_FRANCISCO) as TransportResult.Ok).value.isEmpty())
        assertEquals(1, http.requests.size)
    }
}
