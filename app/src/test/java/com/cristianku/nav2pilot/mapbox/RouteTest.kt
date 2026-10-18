package com.cristianku.nav2pilot.mapbox

import com.cristianku.nav2pilot.data.DeviceRepository
import com.cristianku.nav2pilot.data.FavoritesRepository
import com.cristianku.nav2pilot.location.LocationProvider
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.LatLng
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
import com.cristianku.nav2pilot.transport.TransportResult
import com.cristianku.nav2pilot.ui.MainViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

private const val MAPBOX_TOKEN = "pk.eyJ1IjoiYSJ9.abc"
private const val RPC_SUCCESS = """{"jsonrpc": "2.0", "id": 0, "result": {"success": 1}}"""
private const val RPC_METHOD_NOT_FOUND = """{"jsonrpc": "2.0", "id": 0, "error": {"code": -32601, "message": "Method not found"}}"""

/** Two routes, the second one with two legs: the shape of a Directions API answer. */
private const val DIRECTIONS_ANSWER = """{
  "code": "Ok",
  "routes": [
    {"distance": 12345.6, "duration": 900.0, "legs": [{"summary": "A22"}],
     "geometry": {"type": "LineString", "coordinates": [[11.0, 46.0], [11.1, 46.1]]}},
    {"distance": 15000.0, "duration": 1100.0, "legs": [{"summary": "SS12"}, {"summary": ""}],
     "geometry": {"type": "LineString", "coordinates": [[11.0, 46.0], [11.05, 46.02], [11.1, 46.1]]}}
  ]
}"""

@OptIn(ExperimentalCoroutinesApi::class)
class MapboxDirectionsTest {

    @Test
    fun requestUrlPutsLongitudeFirstAndAsksAlternativesOnlyWithoutStops() {
        val origin = LatLng(46.0, 11.0)
        val destination = LatLng(46.1, 11.1)
        val direct = MapboxDirections.requestUrl(origin, emptyList(), destination, MAPBOX_TOKEN)
        assertEquals(
            "https://api.mapbox.com/directions/v5/mapbox/driving-traffic/11.000000,46.000000;11.100000,46.100000" +
                "?alternatives=true&geometries=geojson&overview=full&steps=false&access_token=" + MAPBOX_TOKEN,
            direct,
        )

        val withStop = MapboxDirections.requestUrl(origin, listOf(LatLng(46.05, 11.05)), destination, MAPBOX_TOKEN)!!
        assertTrue(withStop.contains("11.000000,46.000000;11.050000,46.050000;11.100000,46.100000"))
        assertTrue(withStop.contains("alternatives=false"))

        val tooMany = mutableListOf<LatLng>()
        for (index in 0 until 24) {
            tooMany.add(LatLng(46.0, 11.0))
        }
        assertNull(MapboxDirections.requestUrl(origin, tooMany, destination, MAPBOX_TOKEN))
    }

    @Test
    fun parseRoutesReadsGeometryDistanceDurationAndSummary() {
        val routes = MapboxDirections.parseRoutes(JSONObject(DIRECTIONS_ANSWER))
        assertEquals(2, routes.size)
        assertEquals(listOf(LatLng(46.0, 11.0), LatLng(46.1, 11.1)), routes[0].coordinates)
        assertEquals(12345.6, routes[0].distanceMeters, 0.001)
        assertEquals(900.0, routes[0].durationSeconds, 0.001)
        assertEquals("A22", routes[0].summary)
        assertEquals(3, routes[1].coordinates.size)
        assertEquals("SS12", routes[1].summary)
    }

    @Test
    fun routesReportsMapboxErrors() = runTest {
        val http = FakeHttpClient()
        val directions = MapboxDirections(http, { MAPBOX_TOKEN }, UnconfinedTestDispatcher(testScheduler))
        val origin = LatLng(46.0, 11.0)
        val destination = LatLng(46.1, 11.1)

        http.respond(200, """{"code": "NoRoute", "message": "No route found"}""")
        val noRoute = directions.routes(origin, emptyList(), destination)
        assertTrue(noRoute is TransportResult.Failed)

        http.respond(401, """{"message": "Not Authorized - Invalid Token"}""")
        assertTrue(directions.routes(origin, emptyList(), destination) is TransportResult.Failed)

        val withoutToken = MapboxDirections(http, { null }, UnconfinedTestDispatcher(testScheduler))
        assertTrue(withoutToken.routes(origin, emptyList(), destination) is TransportResult.Failed)
    }

    @Test
    fun viaPointsLieOnTheRouteAtQuarterHalfAndThreeQuarters() {
        val route = RoutePreview(listOf(LatLng(0.0, 0.0), LatLng(0.0, 1.0)), 0.0, 0.0, "")
        val points = RouteShaping.viaPoints(route)
        assertEquals(3, points.size)
        assertEquals(0.25, points[0].longitude, 1e-9)
        assertEquals(0.5, points[1].longitude, 1e-9)
        assertEquals(0.75, points[2].longitude, 1e-9)
        for (point in points) {
            assertEquals(0.0, point.latitude, 1e-9)
            assertTrue(point.via)
        }
        assertTrue(RouteShaping.viaPoints(RoutePreview(listOf(LatLng(0.0, 0.0)), 0.0, 0.0, "")).isEmpty())
    }
}

private class FakeLocationProvider : LocationProvider {
    var permission = true
    var position: LatLng? = LatLng(46.0, 11.0)

    override fun hasPermission(): Boolean {
        return permission
    }

    override suspend fun currentLocation(): LatLng? {
        return position
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class RoutePlanningViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val http = FakeHttpClient()
    private val storage = InMemoryKeyValueStore()
    private val tokenStore = TokenStore(storage, FakeTokenCipher())
    private val mapboxTokenStore = TokenStore(storage, FakeTokenCipher(), TokenStore.KEY_MAPBOX_TOKEN)
    private val deviceRepository = DeviceRepository(storage)
    private val location = FakeLocationProvider()
    private val destination = Destination(latitude = 46.1, longitude = 11.1, name = "Trento")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        tokenStore.save(Fixtures.TEST_JWT)
        mapboxTokenStore.save(MAPBOX_TOKEN)
        deviceRepository.addManual(PairedDevice(Fixtures.DONGLE_ID, "Car"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Starts like the app: reads the tokens and checks the connection (getVersion). */
    private fun createViewModel(ioDispatcher: CoroutineDispatcher = dispatcher): MainViewModel {
        val comma = CommaAthenaTransport(http, { tokenStore.load() }, ioDispatcher)
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
            ioDispatcher = ioDispatcher,
            mapbox = MapboxSetup(mapboxTokenStore, MapboxTokenVerifier(http, ioDispatcher)),
            routePlanning = RoutePlanning(MapboxDirections(http, { mapboxTokenStore.load() }, ioDispatcher), location),
            clock = { 1_000L },
        )
    }

    private fun lastRpc(): JSONObject {
        return JSONObject(http.requests[http.requests.size - 1].body!!)
    }

    @Test
    fun aNewDestinationComputesTheRoutesFromThePhone() {
        val viewModel = createViewModel()
        assertEquals(MAPBOX_TOKEN, viewModel.uiState.value.mapboxMapToken)

        http.respond(200, DIRECTIONS_ANSWER)
        viewModel.chooseCandidate(destination)

        val state = viewModel.uiState.value
        assertEquals(2, state.routes.size)
        assertFalse(state.routesLoading)
        assertEquals(LatLng(46.0, 11.0), state.origin)
        assertTrue(http.requests[http.requests.size - 1].url.startsWith(MapboxEndpoints.DIRECTIONS_URL + "11.000000,46.000000;11.100000,46.100000"))
    }

    /** A geo: link opened from a cold start resolves while the tokens are still being decrypted. */
    @Test
    fun aDestinationArrivingWhileTheMapTokenLoadsIsKept() {
        location.permission = false // no route request: only the token loads run on the IO dispatcher
        val io = StandardTestDispatcher(dispatcher.scheduler)
        val viewModel = createViewModel(io)

        viewModel.chooseCandidate(destination)
        io.scheduler.advanceUntilIdle()

        assertEquals(destination, viewModel.uiState.value.destination)
        assertEquals(MAPBOX_TOKEN, viewModel.uiState.value.mapboxMapToken)
    }

    @Test
    fun withoutLocationPermissionTheScreenIsAskedForIt() {
        location.permission = false
        val viewModel = createViewModel()
        viewModel.chooseCandidate(destination)
        assertTrue(viewModel.uiState.value.locationPermissionNeeded)
        assertTrue(viewModel.uiState.value.routes.isEmpty())

        location.permission = true
        http.respond(200, DIRECTIONS_ANSWER)
        viewModel.onLocationPermissionResult(true)
        assertFalse(viewModel.uiState.value.locationPermissionNeeded)
        assertEquals(2, viewModel.uiState.value.routes.size)
    }

    @Test
    fun theFastestRouteSendsTheDestinationAlone() {
        val viewModel = createViewModel()
        http.respond(200, DIRECTIONS_ANSWER)
        viewModel.chooseCandidate(destination)

        http.respond(200, RPC_SUCCESS) // setMapboxToken
        http.respond(200, RPC_SUCCESS)
        viewModel.sendDestination()
        assertEquals("setNavDestination", lastRpc().getString("method"))
    }

    @Test
    fun anAlternativeSendsItsViaPoints() {
        val viewModel = createViewModel()
        http.respond(200, DIRECTIONS_ANSWER)
        viewModel.chooseCandidate(destination)
        viewModel.selectRoute(1)

        http.respond(200, RPC_SUCCESS) // setMapboxToken
        http.respond(200, RPC_SUCCESS)
        viewModel.sendDestination()
        val rpc = lastRpc()
        assertEquals("setNavRoute", rpc.getString("method"))
        val waypoints = rpc.getJSONObject("params").getJSONArray("waypoints")
        assertEquals(3, waypoints.length())
        assertTrue(waypoints.getJSONObject(0).getBoolean("via"))
        assertEquals("Route sent to Car.", viewModel.uiState.value.feedback!!.message)
    }

    @Test
    fun stopsAreSentInOrderAndRenumberedWhenOneIsRemoved() {
        val viewModel = createViewModel()
        http.respond(200, DIRECTIONS_ANSWER)
        viewModel.chooseCandidate(destination)

        http.respond(200, DIRECTIONS_ANSWER)
        viewModel.addStop(46.02, 11.02)
        http.respond(200, DIRECTIONS_ANSWER)
        viewModel.addStop(46.05, 11.05)
        http.respond(200, DIRECTIONS_ANSWER)
        viewModel.removeStop(0)

        val stops = viewModel.uiState.value.stops
        assertEquals(1, stops.size)
        assertEquals("Stop 1", stops[0].name)
        assertEquals(46.05, stops[0].latitude, 1e-9)

        http.respond(200, RPC_SUCCESS) // setMapboxToken
        http.respond(200, RPC_SUCCESS)
        viewModel.sendDestination()
        val waypoints = lastRpc().getJSONObject("params").getJSONArray("waypoints")
        assertEquals(1, waypoints.length())
        assertFalse(waypoints.getJSONObject(0).getBoolean("via"))
    }

    @Test
    fun aDeviceWithoutSetNavRouteGetsTheDestinationAlone() {
        val viewModel = createViewModel()
        http.respond(200, DIRECTIONS_ANSWER)
        viewModel.chooseCandidate(destination)
        viewModel.selectRoute(1)

        http.respond(200, RPC_SUCCESS) // setMapboxToken
        http.respond(200, RPC_METHOD_NOT_FOUND)
        http.respond(200, RPC_SUCCESS)
        viewModel.sendDestination()
        assertEquals("setNavDestination", lastRpc().getString("method"))
        val feedback = viewModel.uiState.value.feedback!!
        assertTrue(feedback.success)
        assertTrue(feedback.message.contains(CommaAthenaTransport.NO_WAYPOINTS_NOTE))
    }

    @Test
    fun clearingTheDestinationDropsThePlan() {
        val viewModel = createViewModel()
        http.respond(200, DIRECTIONS_ANSWER)
        viewModel.chooseCandidate(destination)
        http.respond(200, DIRECTIONS_ANSWER)
        viewModel.addStop(46.02, 11.02)

        viewModel.clearSelection()
        val state = viewModel.uiState.value
        assertTrue(state.routes.isEmpty())
        assertTrue(state.stops.isEmpty())
        assertEquals(0, state.selectedRouteIndex)
    }
}
