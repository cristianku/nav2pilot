package com.cristianku.nav2pilot.places

import com.cristianku.nav2pilot.data.DeviceRepository
import com.cristianku.nav2pilot.data.FavoritesRepository
import com.cristianku.nav2pilot.resolve.DestinationResolver
import com.cristianku.nav2pilot.resolve.ShortLinkResolver
import com.cristianku.nav2pilot.security.TokenStore
import com.cristianku.nav2pilot.testing.FakeHttpClient
import com.cristianku.nav2pilot.testing.FakePlaceSearch
import com.cristianku.nav2pilot.testing.FakeTokenCipher
import com.cristianku.nav2pilot.testing.InMemoryKeyValueStore
import com.cristianku.nav2pilot.transport.CommaAthenaTransport
import com.cristianku.nav2pilot.transport.SunnylinkTransport
import com.cristianku.nav2pilot.transport.TransportError
import com.cristianku.nav2pilot.transport.TransportRegistry
import com.cristianku.nav2pilot.transport.TransportResult
import com.cristianku.nav2pilot.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.URLDecoder

private const val FIRST_SERVER = "https://one.example/api/interpreter"
private const val SECOND_SERVER = "https://two.example/api/interpreter"

/** Around Frick (CH): a named node with an address, a way with only a brand, an unnamed node, a relation without centre. */
private const val OVERPASS_ANSWER = """{
  "version": 0.6,
  "generator": "Overpass API",
  "elements": [
    {"type": "node", "id": 1, "lat": 47.505, "lon": 8.015,
     "tags": {"amenity": "fuel", "name": "Migrol Frick", "brand": "Migrol", "addr:street": "Hauptstrasse",
              "addr:housenumber": "12", "addr:postcode": "5070", "addr:city": "Frick"}},
    {"type": "way", "id": 2, "center": {"lat": 47.48, "lon": 8.03}, "nodes": [10, 11, 12],
     "tags": {"amenity": "fuel", "brand": "Shell"}},
    {"type": "node", "id": 3, "lat": 47.5, "lon": 8.02, "tags": {"amenity": "fuel"}},
    {"type": "relation", "id": 4, "tags": {"amenity": "fuel", "name": "No position"}}
  ]
}"""

private val FRICK = MapArea(south = 47.45, west = 7.95, north = 47.55, east = 8.10)

@OptIn(ExperimentalCoroutinesApi::class)
class OverpassPlacesTest {

    @Test
    fun queryAsksTheCategoryInTheAreaWithCentres() {
        assertEquals(
            """[out:json][timeout:20];nwr["amenity"="fuel"](47.45000,7.95000,47.55000,8.10000);out center 200;""",
            OverpassPlaces.query(PlaceCategory.FUEL, FRICK),
        )
        assertTrue(OverpassPlaces.query(PlaceCategory.PARKING, FRICK).contains("""["access"!~"^(private|no)$"]"""))
    }

    @Test
    fun parsePlacesNamesThemAndPutsTheNearestFirst() {
        val places = OverpassPlaces.parsePlaces(JSONObject(OVERPASS_ANSWER), PlaceCategory.FUEL, FRICK.center(), 50)

        assertEquals(3, places.size)
        // unnamed: called by its category
        assertEquals("Fuel station", places[0].name)
        assertEquals(47.5, places[0].latitude, 1e-9)
        assertEquals("Migrol Frick", places[1].name)
        assertEquals("Hauptstrasse 12, 5070 Frick", places[1].details)
        // a way: its centre, and the brand when it has no name
        assertEquals("Shell", places[2].name)
        assertEquals("", places[2].details)
        assertEquals(47.48, places[2].latitude, 1e-9)
        assertEquals(8.03, places[2].longitude, 1e-9)

        val nearest = OverpassPlaces.parsePlaces(JSONObject(OVERPASS_ANSWER), PlaceCategory.FUEL, FRICK.center(), 2)
        assertEquals(listOf("Fuel station", "Migrol Frick"), nearest.map { place -> place.name })
    }

    @Test
    fun searchPostsTheQueryAndTriesTheNextServerWhenOneIsBusy() = runTest {
        val http = FakeHttpClient()
        val places = OverpassPlaces(http, UnconfinedTestDispatcher(testScheduler), listOf(FIRST_SERVER, SECOND_SERVER))

        http.respond(429, "")
        http.respond(200, OVERPASS_ANSWER)
        val result = places.search(PlaceCategory.FUEL, FRICK)

        assertTrue(result is TransportResult.Ok)
        assertEquals(3, (result as TransportResult.Ok).value.size)
        assertEquals(listOf(FIRST_SERVER, SECOND_SERVER), http.requests.map { request -> request.url })
        val request = http.requests[1]
        assertEquals("POST", request.method)
        assertEquals("application/x-www-form-urlencoded", request.headers["Content-Type"])
        assertEquals(OverpassPlaces.query(PlaceCategory.FUEL, FRICK), URLDecoder.decode(request.body!!.removePrefix("data="), "UTF-8"))
    }

    @Test
    fun searchReportsWhatWentWrong() = runTest {
        val http = FakeHttpClient()
        val places = OverpassPlaces(http, UnconfinedTestDispatcher(testScheduler), listOf(FIRST_SERVER, SECOND_SERVER))

        // both busy: the last answer
        http.fail("timeout")
        http.respond(504, "<html>Gateway Timeout</html>")
        val busy = places.search(PlaceCategory.FUEL, FRICK)
        assertEquals(504, ((busy as TransportResult.Failed).error as TransportError.HttpFailure).code)

        // a refused query is not sent again elsewhere
        http.requests.clear()
        http.respond(400, "<html>Error: line 1: parse error</html>")
        val refused = places.search(PlaceCategory.FUEL, FRICK)
        assertEquals(400, ((refused as TransportResult.Failed).error as TransportError.HttpFailure).code)
        assertEquals(1, http.requests.size)

        // out of time: 200 with a remark and no elements
        http.respond(200, """{"elements": [], "remark": "runtime error: Query timed out in \"query\" at line 1 after 21 seconds."}""")
        val timedOut = places.search(PlaceCategory.FUEL, FRICK)
        assertTrue((timedOut as TransportResult.Failed).error is TransportError.DeviceRejected)

        http.respond(200, """{"elements": []}""")
        val none = places.search(PlaceCategory.FUEL, FRICK)
        assertTrue((none as TransportResult.Ok).value.isEmpty())
    }

    @Test
    fun areaLimitedAroundItsCentre() {
        val small = FRICK.limitedTo(25_000.0)
        assertEquals(FRICK.south, small.south, 1e-9)
        assertEquals(FRICK.west, small.west, 1e-9)
        assertEquals(FRICK.north, small.north, 1e-9)
        assertEquals(FRICK.east, small.east, 1e-9)

        // a whole country in view: 50 km around its middle
        val large = MapArea(south = 40.0, west = 0.0, north = 50.0, east = 20.0).limitedTo(25_000.0)
        assertEquals(45.0, large.center().latitude, 1e-9)
        assertEquals(10.0, large.center().longitude, 1e-9)
        val height = (large.north - large.south) * 111_320.0
        val width = (large.east - large.west) * 111_320.0 * Math.cos(Math.toRadians(45.0))
        assertEquals(50_000.0, height, 1.0)
        assertEquals(50_000.0, width, 1.0)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlacesViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val http = FakeHttpClient()
    private val storage = InMemoryKeyValueStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MainViewModel {
        val tokenStore = TokenStore(storage, FakeTokenCipher())
        val comma = CommaAthenaTransport(http, { tokenStore.load() }, dispatcher)
        val search = FakePlaceSearch()
        return MainViewModel(
            resolver = DestinationResolver(ShortLinkResolver(http), search, dispatcher),
            placeSearch = search,
            transports = TransportRegistry(comma, SunnylinkTransport()),
            commaTransport = comma,
            tokenStore = tokenStore,
            favoritesRepository = FavoritesRepository(storage),
            deviceRepository = DeviceRepository(storage),
            ioDispatcher = dispatcher,
            places = OverpassPlaces(http, dispatcher, listOf(FIRST_SERVER)),
        )
    }

    @Test
    fun aCategoryShowsItsPlacesAndATappedOneBecomesTheDestination() {
        val viewModel = createViewModel()

        http.respond(200, OVERPASS_ANSWER)
        viewModel.showPlaces(PlaceCategory.FUEL, FRICK)

        val shown = viewModel.uiState.value
        assertEquals(PlaceCategory.FUEL, shown.placesCategory)
        assertEquals(3, shown.places.size)
        assertFalse(shown.placesLoading)

        viewModel.choosePlace(shown.places[1])
        val chosen = viewModel.uiState.value
        assertEquals("Migrol Frick", chosen.destination!!.name)
        assertEquals("Hauptstrasse 12, 5070 Frick", chosen.destination!!.details)
        assertEquals(DestinationResolver.SOURCE_PLACES, chosen.destinationSource)
        // still on the map, to add one as a stop
        assertEquals(3, chosen.places.size)

        viewModel.hidePlaces()
        assertNull(viewModel.uiState.value.placesCategory)
        assertTrue(viewModel.uiState.value.places.isEmpty())
        assertEquals(0, viewModel.uiState.value.placesMessageRequest)
    }

    @Test
    fun nothingFoundOrAFailureLeavesAMessage() {
        val viewModel = createViewModel()

        http.respond(200, """{"elements": []}""")
        viewModel.showPlaces(PlaceCategory.CHARGING, FRICK)
        assertNull(viewModel.uiState.value.placesCategory)
        assertEquals("EV charging: none in this part of the map.", viewModel.uiState.value.placesMessage)
        assertEquals(1, viewModel.uiState.value.placesMessageRequest)

        http.respond(429, "")
        viewModel.showPlaces(PlaceCategory.FUEL, FRICK)
        assertNull(viewModel.uiState.value.placesCategory)
        assertEquals("OpenStreetMap is busy (HTTP 429). Try again in a moment.", viewModel.uiState.value.placesMessage)
        assertEquals(2, viewModel.uiState.value.placesMessageRequest)

        // no area from the map yet: nothing to ask
        val requests = http.requests.size
        viewModel.showPlaces(PlaceCategory.FUEL, null)
        assertEquals(requests, http.requests.size)
        assertEquals(3, viewModel.uiState.value.placesMessageRequest)
    }

    @Test
    fun aZoomedOutMapIsSearchedInItsMiddle() {
        val viewModel = createViewModel()
        val country = MapArea(south = 40.0, west = 0.0, north = 50.0, east = 20.0)

        http.respond(200, """{"elements": []}""")
        viewModel.showPlaces(PlaceCategory.FUEL, country)

        val query = URLDecoder.decode(http.requests.last().body!!.removePrefix("data="), "UTF-8")
        assertEquals(OverpassPlaces.query(PlaceCategory.FUEL, country.limitedTo(25_000.0)), query)
    }
}
