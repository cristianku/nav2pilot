package com.cristianku.nav2pilot.ui

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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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

    private fun createViewModel(): MainViewModel {
        val comma = CommaAthenaTransport(http, { tokenStore.load() }, dispatcher)
        val search = FakePlaceSearch()
        return MainViewModel(
            resolver = DestinationResolver(ShortLinkResolver(http), search, dispatcher),
            placeSearch = search,
            transports = TransportRegistry(comma, SunnylinkTransport()),
            commaTransport = comma,
            tokenStore = tokenStore,
            favoritesRepository = favoritesRepository,
            deviceRepository = deviceRepository,
            ioDispatcher = dispatcher,
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
}
