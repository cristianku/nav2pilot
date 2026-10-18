package com.cristianku.nav2pilot.mapbox

import com.cristianku.nav2pilot.data.DeviceRepository
import com.cristianku.nav2pilot.data.FavoritesRepository
import com.cristianku.nav2pilot.model.PairedDevice
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
import com.cristianku.nav2pilot.transport.TransportError
import com.cristianku.nav2pilot.transport.TransportRegistry
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val TOKEN = "pk.eyJ1IjoiYSJ9.abc"
private const val SUCCESS = """{"jsonrpc": "2.0", "id": 0, "result": {"success": 1}}"""
private const val METHOD_NOT_FOUND = """{"jsonrpc": "2.0", "id": 0, "error": {"code": -32601, "message": "Method not found"}}"""

@OptIn(ExperimentalCoroutinesApi::class)
class MapboxTokenTest {

    @Test
    fun tokenKinds() {
        assertTrue(MapboxTokenRules.isPublicToken(TOKEN))
        assertFalse(MapboxTokenRules.isPublicToken("pk.only"))
        assertFalse(MapboxTokenRules.isPublicToken("sk.eyJ1IjoiYSJ9.abc"))
        assertTrue(MapboxTokenRules.isSecretToken("sk.eyJ1IjoiYSJ9.abc"))
    }

    @Test
    fun verifierReadsTheTokensApiCode() = runTest {
        val http = FakeHttpClient()
        val verifier = MapboxTokenVerifier(http, UnconfinedTestDispatcher(testScheduler))

        http.respond(200, """{"code": "TokenValid", "token": {"usage": "pk"}}""")
        assertEquals(MapboxTokenCheck.Valid, verifier.verify(TOKEN))
        assertEquals("https://api.mapbox.com/tokens/v2?access_token=" + TOKEN, http.requests[0].url)

        http.respond(401, """{"code": "TokenInvalid"}""")
        assertEquals(MapboxTokenCheck.Rejected("TokenInvalid"), verifier.verify(TOKEN))

        http.respond(500, "<html>down</html>")
        assertTrue(verifier.verify(TOKEN) is MapboxTokenCheck.Failed)

        http.fail("offline")
        assertEquals(MapboxTokenCheck.Failed(TransportError.Network("offline")), verifier.verify(TOKEN))
    }

    @Test
    fun tokenStoresWithDifferentKeysAreIndependent() {
        val storage = InMemoryKeyValueStore()
        val comma = TokenStore(storage, FakeTokenCipher())
        val mapbox = TokenStore(storage, FakeTokenCipher(), TokenStore.KEY_MAPBOX_TOKEN)
        comma.save(Fixtures.TEST_JWT)
        mapbox.save(TOKEN)

        assertEquals(Fixtures.TEST_JWT, comma.load())
        assertEquals(TOKEN, mapbox.load())
        mapbox.clear()
        assertEquals(Fixtures.TEST_JWT, comma.load())
        assertFalse(mapbox.hasToken())
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MapboxDeviceTokenTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val http = FakeHttpClient()
    private val storage = InMemoryKeyValueStore()
    private val tokenStore = TokenStore(storage, FakeTokenCipher())
    private val mapboxTokenStore = TokenStore(storage, FakeTokenCipher(), TokenStore.KEY_MAPBOX_TOKEN)
    private val deviceRepository = DeviceRepository(storage)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        tokenStore.save(Fixtures.TEST_JWT)
        deviceRepository.addManual(PairedDevice(Fixtures.DONGLE_ID, "Car"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Starts like the app: reads the tokens and checks the connection (getVersion). */
    private fun createViewModel(): MainViewModel {
        val comma = CommaAthenaTransport(http, { tokenStore.load() }, dispatcher)
        val search = FakePlaceSearch()
        http.respond(200, """{"jsonrpc": "2.0", "id": 0, "result": {"version": "1", "branch": "b", "remote": "", "commit": ""}}""")
        return MainViewModel(
            resolver = DestinationResolver(ShortLinkResolver(http), search, dispatcher),
            placeSearch = search,
            transports = TransportRegistry(comma, SunnylinkTransport()),
            commaTransport = comma,
            tokenStore = tokenStore,
            favoritesRepository = FavoritesRepository(storage),
            deviceRepository = deviceRepository,
            ioDispatcher = dispatcher,
            mapbox = MapboxSetup(mapboxTokenStore, MapboxTokenVerifier(http, dispatcher)),
            clock = { 1_000L },
        )
    }

    private fun methods(): List<String> {
        val names = mutableListOf<String>()
        for (request in http.requests) {
            val body = request.body
            if (body == null) {
                names.add(request.url)
            } else {
                names.add(JSONObject(body).getString("method"))
            }
        }
        return names
    }

    private fun sentToken(index: Int): String {
        return JSONObject(http.requests[index].body!!).getJSONObject("params").getString("token")
    }

    @Test
    fun everySendCarriesTheTokenBeforeTheDestination() {
        mapboxTokenStore.save(TOKEN)
        val viewModel = createViewModel()
        viewModel.search("45.0, 9.2")

        http.respond(200, SUCCESS)
        http.respond(200, SUCCESS)
        viewModel.sendDestination()
        http.respond(200, SUCCESS)
        http.respond(200, SUCCESS)
        viewModel.sendDestination()

        assertEquals(listOf("getVersion", "setMapboxToken", "setNavDestination", "setMapboxToken", "setNavDestination"), methods())
        assertEquals(TOKEN, sentToken(1))
        assertEquals(TOKEN, sentToken(3))
        assertEquals("Destination sent to Car.", viewModel.uiState.value.feedback!!.message)
    }

    @Test
    fun unmodifiedOpenpilotGetsTheDestinationWithoutAnyError() {
        mapboxTokenStore.save(TOKEN)
        val viewModel = createViewModel()
        viewModel.search("45.0, 9.2")

        http.respond(200, METHOD_NOT_FOUND)
        http.respond(200, SUCCESS)
        viewModel.sendDestination()

        val feedback = viewModel.uiState.value.feedback!!
        assertTrue(feedback.success)
        assertEquals("Destination sent to Car.", feedback.message)
    }

    @Test
    fun withoutMapboxTokenNothingExtraIsSent() {
        val viewModel = createViewModel()
        viewModel.search("45.0, 9.2")

        http.respond(200, SUCCESS)
        viewModel.sendDestination()

        assertEquals(listOf("getVersion", "setNavDestination"), methods())
    }

    @Test
    fun secretAndMalformedTokensAreRefusedLocally() {
        val viewModel = createViewModel()
        viewModel.saveMapboxToken("sk.eyJ1IjoiYSJ9.abc")
        assertTrue(viewModel.uiState.value.mapboxMessage.contains("secret"))
        viewModel.saveMapboxToken("hello")
        assertTrue(viewModel.uiState.value.mapboxMessage.contains("pk."))
        assertEquals(listOf("getVersion"), methods())
        assertFalse(viewModel.uiState.value.hasMapboxToken)
    }

    @Test
    fun savingStoresTheTokenEncryptedAndSendsItToTheSelectedCarSilently() {
        val viewModel = createViewModel()
        http.respond(200, """{"code": "TokenValid"}""")
        http.respond(200, METHOD_NOT_FOUND)

        viewModel.saveMapboxToken(" $TOKEN ")

        val state = viewModel.uiState.value
        assertTrue(state.hasMapboxToken)
        assertFalse(state.mapboxDialogVisible)
        assertEquals("", state.mapboxMessage)
        assertEquals(TOKEN, mapboxTokenStore.load())
        assertFalse(storage.values[TokenStore.KEY_MAPBOX_TOKEN]!!.contains(TOKEN))
        assertEquals("setMapboxToken", methods().last())
        assertEquals(TOKEN, sentToken(http.requests.size - 1))
    }

    @Test
    fun rejectedTokenIsNotStored() {
        val viewModel = createViewModel()
        http.respond(401, """{"code": "TokenInvalid"}""")

        viewModel.saveMapboxToken(TOKEN)

        assertFalse(viewModel.uiState.value.hasMapboxToken)
        assertTrue(viewModel.uiState.value.mapboxMessage.contains("TokenInvalid"))
        assertFalse(mapboxTokenStore.hasToken())
    }

    @Test
    fun removingTheTokenAlsoRemovesItFromTheSelectedCar() {
        mapboxTokenStore.save(TOKEN)
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.hasMapboxToken)

        http.respond(200, SUCCESS)
        viewModel.removeMapboxToken()

        assertFalse(viewModel.uiState.value.hasMapboxToken)
        assertFalse(mapboxTokenStore.hasToken())
        assertEquals("setMapboxToken", methods().last())
        assertEquals("", sentToken(http.requests.size - 1))
    }
}
