package com.cristianku.nav2pilot.ui

import com.cristianku.nav2pilot.data.DeviceRepository
import com.cristianku.nav2pilot.data.FavoritesRepository
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.PairedDevice
import com.cristianku.nav2pilot.places.MapArea
import com.cristianku.nav2pilot.places.PlaceInfoLookup
import com.cristianku.nav2pilot.resolve.DestinationResolver
import com.cristianku.nav2pilot.resolve.ShortLinkResolver
import com.cristianku.nav2pilot.security.TokenStore
import com.cristianku.nav2pilot.testing.FakeHttpClient
import com.cristianku.nav2pilot.testing.FakePlaceSearch
import com.cristianku.nav2pilot.testing.FakeTokenCipher
import com.cristianku.nav2pilot.testing.Fixtures
import com.cristianku.nav2pilot.testing.InMemoryKeyValueStore
import com.cristianku.nav2pilot.transport.CommaAthenaTransport
import com.cristianku.nav2pilot.transport.SunnylinkTransport
import com.cristianku.nav2pilot.transport.TransportRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val http = FakeHttpClient()
    private val storage = InMemoryKeyValueStore()
    private val tokenStore = TokenStore(storage, FakeTokenCipher())
    private val deviceRepository = DeviceRepository(storage)
    private val favoritesRepository = FavoritesRepository(storage)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val suggestions = FakePlaceSearch()
    private val search = FakePlaceSearch()

    private fun createViewModel(placeInfoLookup: PlaceInfoLookup? = null): MainViewModel {
        val comma = CommaAthenaTransport(http, { tokenStore.load() }, dispatcher)
        return MainViewModel(
            resolver = DestinationResolver(ShortLinkResolver(http), search, dispatcher),
            placeSearch = search,
            transports = TransportRegistry(comma, SunnylinkTransport()),
            commaTransport = comma,
            tokenStore = tokenStore,
            favoritesRepository = favoritesRepository,
            deviceRepository = deviceRepository,
            ioDispatcher = dispatcher,
            suggestionSearch = suggestions,
            placeInfoLookup = placeInfoLookup,
            clock = { 1_000L },
        )
    }

    @Test
    fun shareThenSendReportsConfirmation() {
        tokenStore.save(Fixtures.TEST_JWT)
        deviceRepository.addManual(PairedDevice(Fixtures.DONGLE_ID, "Peugeot 3008"))
        http.respond(200, """{"jsonrpc":"2.0","id":0,"result":{"version":"0.10.1","branch":"feature/nav-alpha","remote":"","commit":""}}""")

        val viewModel = createViewModel()

        val connection = viewModel.uiState.value.connection
        assertTrue(connection is ConnectionState.Online)
        assertEquals("feature/nav-alpha", (connection as ConnectionState.Online).status.branch)
        assertEquals(1893456000L, viewModel.uiState.value.tokenExpirySeconds)

        viewModel.handleShare("Duomo\n45.4642, 9.19", "")
        val destination = viewModel.uiState.value.destination
        assertNotNull(destination)
        assertEquals("Duomo", destination!!.name)
        assertEquals(1, viewModel.uiState.value.showSendTabRequest)

        http.respond(200, """{"jsonrpc":"2.0","id":0,"result":{"success":1}}""")
        viewModel.sendDestination()

        val feedback = viewModel.uiState.value.feedback!!
        assertTrue(feedback.success)
        assertEquals("Destination sent to Peugeot 3008.", feedback.message)
        assertFalse(viewModel.uiState.value.sending)
        val body = JSONObject(http.requests.last().body!!)
        assertEquals("setNavDestination", body.getString("method"))
    }

    @Test
    fun withoutTokenNothingIsSent() {
        deviceRepository.addManual(PairedDevice(Fixtures.DONGLE_ID, "Car"))
        val viewModel = createViewModel()
        assertEquals(ConnectionState.Unknown, viewModel.uiState.value.connection)

        viewModel.handleShare("45.1, 9.2", "")
        viewModel.sendDestination()

        assertFalse(viewModel.uiState.value.feedback!!.success)
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun rejectedTokenIsNotStored() {
        val viewModel = createViewModel()
        http.respond(401, """{"error":"unauthorized"}""")

        viewModel.saveToken(Fixtures.TEST_JWT)

        assertFalse(tokenStore.hasToken())
        assertTrue(viewModel.uiState.value.accountMessage.startsWith("Token not saved."))
    }

    @Test
    fun acceptedTokenIsStoredAndDevicesAreLoaded() {
        val viewModel = createViewModel()
        http.respond(200, """{"email":"driver@example.com"}""")
        http.respond(200, """[{"dongle_id":"1a2b3c4d5e6f7a8b","alias":"Peugeot 3008","device_type":"three","last_athena_ping":1760700000,"is_owner":true}]""")
        http.respond(200, """{"jsonrpc":"2.0","id":0,"result":{"version":"0.10.1","branch":"b","remote":"","commit":""}}""")

        viewModel.saveToken("JWT " + Fixtures.TEST_JWT)

        assertEquals(Fixtures.TEST_JWT, tokenStore.load())
        assertTrue(viewModel.uiState.value.hasToken)
        assertEquals("driver@example.com", viewModel.uiState.value.accountEmail)
        assertEquals(1, deviceRepository.devices.value.size)
        assertEquals(Fixtures.DONGLE_ID, deviceRepository.selectedDongleId.value)
        assertTrue(viewModel.uiState.value.connection is ConnectionState.Online)
    }

    @Test
    fun manualDeviceValidation() {
        val viewModel = createViewModel()
        assertTrue(viewModel.addManualDevice("xyz", "").isNotEmpty())
        assertEquals("", viewModel.addManualDevice(" 1A2B3C4D5E6F7A8B ", "Car"))
        assertEquals("1a2b3c4d5e6f7a8b", deviceRepository.devices.value[0].dongleId)
        assertTrue(viewModel.addManualDevice("1a2b3c4d5e6f7a8b", "Again").isNotEmpty())
    }

    @Test
    fun favoriteToggle() {
        val viewModel = createViewModel()
        viewModel.handleShare("45.1, 9.2", "")

        viewModel.toggleFavorite()
        assertEquals(1, favoritesRepository.favorites.value.size)

        viewModel.toggleFavorite()
        assertTrue(favoritesRepository.favorites.value.isEmpty())
    }

    @Test
    fun suggestionsComeAfterAPauseInTheTyping() {
        val viewModel = createViewModel()
        val ikea = Destination(47.53, 7.70, "IKEA", "Grüssenweg 21, 4133 Pratteln")
        suggestions.results["ike"] = listOf(ikea)
        suggestions.results["ikea"] = listOf(ikea)

        viewModel.typeSearch("ik")
        viewModel.typeSearch("ike")
        dispatcher.scheduler.advanceTimeBy(100)
        viewModel.typeSearch("ikea")
        dispatcher.scheduler.advanceTimeBy(299)
        assertTrue(viewModel.uiState.value.suggestions.isEmpty())

        dispatcher.scheduler.advanceTimeBy(2)
        assertEquals(listOf(ikea), viewModel.uiState.value.suggestions)
        // only the last text was looked up
        assertEquals(listOf("ikea"), suggestions.queries)
    }

    @Test
    fun noSuggestionsForOneLetterCoordinatesOrALink() {
        val viewModel = createViewModel()

        viewModel.typeSearch("i")
        viewModel.typeSearch("45.46, 9.19")
        viewModel.typeSearch("https://maps.app.goo.gl/abc")
        dispatcher.scheduler.advanceTimeBy(1_000)

        assertTrue(suggestions.queries.isEmpty())
    }

    @Test
    fun aChosenSuggestionBecomesTheDestinationAndTheListCloses() {
        val viewModel = createViewModel()
        val ikea = Destination(47.53, 7.70, "IKEA", "Grüssenweg 21, 4133 Pratteln")
        suggestions.results["ikea"] = listOf(ikea)
        viewModel.typeSearch("ikea")
        dispatcher.scheduler.advanceTimeBy(1_000)

        viewModel.chooseCandidate(ikea)

        assertEquals(ikea, viewModel.uiState.value.destination)
        assertTrue(viewModel.uiState.value.suggestions.isEmpty())
    }

    @Test
    fun withoutThePhonesPositionTheMapsMiddleIsWhereTheSuggestionsLook() {
        val viewModel = createViewModel()
        suggestions.results["ikea"] = listOf(Destination(47.53, 7.70, "IKEA"))

        // the whole world: no hint
        viewModel.mapAreaChanged(MapArea(south = -60.0, west = -170.0, north = 70.0, east = 170.0))
        viewModel.typeSearch("ikea")
        dispatcher.scheduler.advanceTimeBy(1_000)
        assertNull(suggestions.nearPoints[0])
        assertNull(viewModel.uiState.value.searchCenter)

        viewModel.mapAreaChanged(MapArea(south = 47.49, west = 7.99, north = 47.52, east = 8.04))
        viewModel.typeSearch("ikea ")
        dispatcher.scheduler.advanceTimeBy(1_000)
        val near = suggestions.nearPoints[1]!!
        assertEquals(47.505, near.latitude, 0.0001)
        assertEquals(8.015, near.longitude, 0.0001)
        assertEquals(near, viewModel.uiState.value.searchCenter)
    }

    private val pratteln = Destination(47.53, 7.70, "IKEA", "Grüssenweg 21, 4133 Pratteln", kind = "shop:furniture", osmId = "way/26343678")
    private val spreitenbach = Destination(47.42, 8.37, "IKEA", "Müslistrasse 18, 8957 Spreitenbach", kind = "shop:furniture", osmId = "way/76278068")

    private fun lookup(): PlaceInfoLookup {
        return PlaceInfoLookup(http, dispatcher) { listOf("https://overpass.example/api/interpreter") }
    }

    @Test
    fun theResultsOfASearchGetTheirOpeningHoursInOneRequest() {
        val viewModel = createViewModel(lookup())
        search.results["ikea"] = listOf(pratteln, spreitenbach)
        http.respond(
            200,
            """{"elements": [{"type": "way", "id": 26343678, "tags": {"opening_hours": "Mo-Sa 10:00-20:00"}},
                {"type": "way", "id": 76278068, "tags": {"shop": "furniture"}}]}""",
        )

        viewModel.search("ikea")

        val state = viewModel.uiState.value
        assertEquals(listOf(pratteln, spreitenbach), state.choices)
        assertEquals("Mo-Sa 10:00-20:00", state.placeInfo["way/26343678"]!!.openingHours)
        assertTrue(state.placeInfo["way/76278068"]!!.isEmpty())
        assertTrue(http.requests[0].body!!.contains("26343678%2C76278068"))

        // known already: chosen, no new request
        viewModel.chooseCandidate(pratteln)
        assertEquals(1, http.requests.size)
        assertEquals("Mo-Sa 10:00-20:00", viewModel.uiState.value.placeInfo[pratteln.osmId]!!.openingHours)
    }

    @Test
    fun suggestionsAskNothingTheChosenOneGetsItsHours() {
        val viewModel = createViewModel(lookup())
        suggestions.results["ikea"] = listOf(pratteln, Destination(47.5, 8.0, "Ikea street"))
        viewModel.typeSearch("ikea")
        dispatcher.scheduler.advanceTimeBy(1_000)
        assertEquals(2, viewModel.uiState.value.suggestions.size)
        assertTrue(http.requests.isEmpty())

        http.respond(200, """{"elements": [{"type": "way", "id": 26343678, "tags": {"opening_hours": "24/7", "phone": "+41 848 801100"}}]}""")
        viewModel.chooseCandidate(pratteln)

        assertEquals(1, http.requests.size)
        assertEquals("+41 848 801100", viewModel.uiState.value.placeInfo[pratteln.osmId]!!.phone)
    }

    @Test
    fun aFailedSuggestionShowsNothing() {
        val viewModel = createViewModel()
        suggestions.error = IOException("timeout")

        viewModel.typeSearch("ikea")
        dispatcher.scheduler.advanceTimeBy(1_000)

        assertTrue(viewModel.uiState.value.suggestions.isEmpty())
        assertEquals("", viewModel.uiState.value.resolveError)
    }
}
