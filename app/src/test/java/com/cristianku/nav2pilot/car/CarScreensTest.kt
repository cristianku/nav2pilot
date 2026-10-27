package com.cristianku.nav2pilot.car

import android.app.Application
import android.os.Looper
import android.text.Spanned
import androidx.car.app.AppManager
import androidx.car.app.ScreenManager
import androidx.car.app.OnDoneCallback
import androidx.car.app.model.CarText
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SearchTemplate
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.car.app.testing.ScreenController
import androidx.car.app.testing.TestAppManager
import androidx.car.app.testing.TestCarContext
import androidx.car.app.testing.TestScreenManager
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.cristianku.nav2pilot.data.DeviceRepository
import com.cristianku.nav2pilot.data.FavoritesRepository
import com.cristianku.nav2pilot.location.LocationProvider
import com.cristianku.nav2pilot.mapbox.MapboxDirections
import com.cristianku.nav2pilot.mapbox.RoutePlanning
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
import com.cristianku.nav2pilot.transport.DestinationSender
import com.cristianku.nav2pilot.transport.SendOutcome
import com.cristianku.nav2pilot.transport.SunnylinkTransport
import com.cristianku.nav2pilot.transport.TransportRegistry
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The car screens with the Car App Library's test host: every template is built, so the library's own checks
 * (rows, actions, which template may hold what) run on each of them.
 */
@RunWith(RobolectricTestRunner::class)
// Robolectric needs Java 21 for SDK 36; the build toolchain is JDK 17
@Config(application = Application::class, sdk = [35])
class CarScreensTest {
    private val carContext = TestCarContext.createCarContext(ApplicationProvider.getApplicationContext())
    private val http = FakeHttpClient()
    private val storage = InMemoryKeyValueStore()
    private val commaToken = TokenStore(storage, FakeTokenCipher())
    private val mapboxToken = TokenStore(storage, FakeTokenCipher(), TokenStore.KEY_MAPBOX_TOKEN)
    private val devices = DeviceRepository(storage)
    private val favorites = FavoritesRepository(storage)
    private val search = FakePlaceSearch()
    private val location = FakeLocation()
    private val map = RecordingMap()

    private fun model(): CarModel {
        val unconfined = Dispatchers.Unconfined
        val comma = CommaAthenaTransport(http, { commaToken.load() }, unconfined)
        val transports = TransportRegistry(comma, SunnylinkTransport())
        return CarModel(
            devices = devices,
            favorites = favorites,
            commaToken = commaToken,
            mapboxToken = mapboxToken,
            transports = transports,
            sender = DestinationSender(transports) { mapboxToken.load() },
            resolver = DestinationResolver(ShortLinkResolver(http), search, unconfined),
            searchAvailable = true,
            routePlanning = RoutePlanning(MapboxDirections(http, { mapboxToken.load() }, unconfined), location),
            map = map,
            ioDispatcher = unconfined,
        )
    }

    private fun setUpCar() {
        commaToken.save(Fixtures.TEST_JWT)
        devices.addManual(PairedDevice(Fixtures.DONGLE_ID, "Peugeot 3008"))
    }

    private val done = object : OnDoneCallback {}

    private fun text(carText: CarText?): String {
        return carText!!.toCharSequence().toString()
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun pushed(): List<androidx.car.app.Screen> {
        return (carContext.getCarService(ScreenManager::class.java) as TestScreenManager).screensPushed
    }

    @Test
    fun withoutTokenTheCarAsksForSetupOnThePhone() {
        val screen = DestinationsScreen(carContext, model())
        ScreenController(screen).moveToState(Lifecycle.State.RESUMED)

        val template = screen.onGetTemplate() as MessageTemplate
        assertTrue(text(template.message).contains("comma token"))
        assertTrue(text(template.message).contains(CarModel.SAFE_TO_LOOK))
    }

    @Test
    fun favoritesAreOnTheMapNearestFirst() {
        setUpCar()
        favorites.add(Destination(45.4384, 10.9916, "Arena", "Verona"))
        favorites.add(Destination(45.4642, 9.19, "Duomo", "Milano"))
        location.position = LatLng(45.47, 9.18)

        val screen = DestinationsScreen(carContext, model())
        ScreenController(screen).moveToState(Lifecycle.State.RESUMED)
        idle()

        val template = screen.onGetTemplate() as PlaceListMapTemplate
        assertEquals("Peugeot 3008", text(template.title))
        val rows = template.itemList!!.items
        assertEquals(2, rows.size)
        assertEquals("Duomo", text((rows[0] as Row).title))
        val line = (rows[0] as Row).texts[0].toCharSequence() as Spanned
        assertEquals("  · Milano", line.toString())
        val distance = line.getSpans(0, line.length, DistanceSpan::class.java)[0].distance
        assertEquals(1.0, distance.displayDistance, 1e-9)
        assertEquals(Distance.UNIT_KILOMETERS, distance.displayUnit)
        assertTrue((rows[0] as Row).isBrowsable)
        assertNotNull((rows[0] as Row).metadata!!.place)
        val actions = template.actionStrip!!.actions
        assertEquals(listOf("Search", "Car"), actions.map { text(it.title) })

        (rows[1] as Row).onClickDelegate!!.sendClick(done)
        assertTrue(pushed().last() is RouteScreen)
    }

    @Test
    fun withoutMapboxTheDestinationGoesAlone() {
        setUpCar()
        val destination = Destination(45.4642, 9.19, "Duomo", "Milano")
        val screen = RouteScreen(carContext, model(), destination)
        ScreenController(screen).moveToState(Lifecycle.State.RESUMED)
        idle()

        val template = screen.onGetTemplate() as MapWithContentTemplate
        val content = template.contentTemplate as ListTemplate
        val row = content.singleList!!.items[0] as Row
        assertEquals("Duomo", text(row.title))
        assertTrue(text(row.texts[1]).contains("Mapbox token"))
        assertEquals("", map.shown.last().mapboxToken)
        assertTrue(map.shown.last().routes.isEmpty())

        http.respond(200, """{"jsonrpc":"2.0","id":0,"result":{"success":1}}""")
        template.actionStrip!!.actions[0].onClickDelegate!!.sendClick(done)
        idle()

        assertEquals("setNavDestination", JSONObject(http.requests.last().body!!).getString("method"))
        val sent = pushed().last() as SentScreen
        val message = sent.onGetTemplate() as MessageTemplate
        assertEquals("Destination sent to Peugeot 3008.", text(message.message))
        assertEquals(listOf("Done"), message.actions.map { text(it.title) })
    }

    @Test
    fun alternativePickedInTheCarGoesAsViaPoints() {
        setUpCar()
        mapboxToken.save("pk.a.b")
        location.position = LatLng(45.47, 9.18)
        http.respond(200, DIRECTIONS)
        val destination = Destination(45.4384, 10.9916, "Arena")
        val screen = RouteScreen(carContext, model(), destination)
        ScreenController(screen).moveToState(Lifecycle.State.RESUMED)
        idle()

        val template = screen.onGetTemplate() as MapWithContentTemplate
        val list = (template.contentTemplate as ListTemplate).singleList!!
        assertEquals(2, list.items.size)
        assertEquals("Fastest · 1 h 25 min · 157.0 km", text((list.items[0] as Row).title))
        assertEquals("A4", text((list.items[0] as Row).texts[0]))
        assertEquals("pk.a.b", map.shown.last().mapboxToken)
        assertEquals(LatLng(45.47, 9.18), map.shown.last().origin)

        list.onSelectedDelegate!!.sendSelected(1, done)
        assertEquals(1, map.shown.last().selectedRoute)

        // the token first, then the route
        http.respond(200, """{"jsonrpc":"2.0","id":0,"result":{"success":1}}""")
        http.respond(200, """{"jsonrpc":"2.0","id":0,"result":{"success":1}}""")
        (screen.onGetTemplate() as MapWithContentTemplate).actionStrip!!.actions[0].onClickDelegate!!.sendClick(done)
        idle()

        assertEquals("setMapboxToken", JSONObject(http.requests[1].body!!).getString("method"))
        val route = JSONObject(http.requests[2].body!!)
        assertEquals("setNavRoute", route.getString("method"))
        assertEquals(3, route.getJSONObject("params").getJSONArray("waypoints").length())
        assertTrue(pushed().last() is SentScreen)
    }

    @Test
    fun failedSendOffersARetry() {
        var retried = false
        val screen = SentScreen(carContext, SendOutcome(false, "Device unreachable")) { retried = true }
        ScreenController(screen).moveToState(Lifecycle.State.RESUMED)

        val template = screen.onGetTemplate() as MessageTemplate
        assertEquals("Not sent", text(template.title))
        assertEquals(listOf("Try again", "Done"), template.actions.map { text(it.title) })
        template.actions[0].onClickDelegate!!.sendClick(done)
        assertTrue(retried)
    }

    @Test
    fun carsShowTheirStatusAndCanClearTheDestination() {
        setUpCar()
        devices.addManual(PairedDevice("0a0b0c0d0e0f0a0b", "Second car"))
        http.respond(200, """{"jsonrpc":"2.0","id":0,"result":{"version":"0.10.1","branch":"nav-alpha","remote":"","commit":""}}""")
        http.respond(200, """{"jsonrpc":"2.0","id":0,"error":{"code":-32000,"message":"Device offline"}}""")

        val screen = CarsScreen(carContext, model())
        ScreenController(screen).moveToState(Lifecycle.State.RESUMED)
        idle()

        val template = screen.onGetTemplate() as ListTemplate
        val cars = template.sectionedLists[0].itemList.items
        assertEquals("Selected · Online · 0.10.1 (nav-alpha)", text((cars[0] as Row).texts[0]))
        assertTrue(text((cars[1] as Row).texts[0]).startsWith("Not reachable"))

        (cars[1] as Row).onClickDelegate!!.sendClick(done)
        assertEquals("0a0b0c0d0e0f0a0b", devices.selectedDongleId.value)

        http.respond(200, """{"jsonrpc":"2.0","id":0,"result":{"success":1}}""")
        val clear = screen.onGetTemplate() as ListTemplate
        (clear.sectionedLists[1].itemList.items[0] as Row).onClickDelegate!!.sendClick(done)
        idle()
        assertEquals("clearNavDestination", JSONObject(http.requests.last().body!!).getString("method"))
        val toasts = (carContext.getCarService(AppManager::class.java) as TestAppManager).toastsShown
        assertEquals("Destination cleared on Second car.", toasts.last().toString())
    }

    @Test
    fun searchShowsTheCandidates() {
        setUpCar()
        search.results["arena verona"] = listOf(
            Destination(45.4384, 10.9916, "Arena di Verona", "Piazza Bra"),
            Destination(45.43, 10.99, "Arena Hotel", "Verona"),
        )
        val screen = SearchScreen(carContext, model())
        ScreenController(screen).moveToState(Lifecycle.State.RESUMED)

        val empty = screen.onGetTemplate() as SearchTemplate
        empty.searchCallbackDelegate.sendSearchSubmitted("arena verona", done)
        idle()

        val template = screen.onGetTemplate() as SearchTemplate
        val rows = template.itemList!!.items
        assertEquals(2, rows.size)
        assertEquals("Arena di Verona", text((rows[0] as Row).title))
        (rows[0] as Row).onClickDelegate!!.sendClick(done)
        assertTrue(pushed().last() is RouteScreen)
    }

    @Test
    fun withoutCarsTheListSaysWhereToAddThem() {
        commaToken.save(Fixtures.TEST_JWT)
        val screen = DestinationsScreen(carContext, model())
        ScreenController(screen).moveToState(Lifecycle.State.RESUMED)

        val template = screen.onGetTemplate() as MessageTemplate
        assertFalse(text(template.message).contains("comma token"))
        assertTrue(text(template.message).contains("Devices"))
    }

    private class FakeLocation : LocationProvider {
        var position: LatLng? = null

        override fun hasPermission(): Boolean {
            return position != null
        }

        override suspend fun currentLocation(): LatLng? {
            return position
        }
    }

    private class RecordingMap : CarMap {
        val shown = mutableListOf<CarMapContent>()

        override fun show(content: CarMapContent) {
            shown.add(content)
        }

        override fun clear() {
        }
    }

    companion object {
        // two routes Milan -> Verona, coordinates shortened
        private const val DIRECTIONS = """{"code":"Ok","routes":[
            {"distance":157000,"duration":5100,"legs":[{"summary":"A4"}],
             "geometry":{"type":"LineString","coordinates":[[9.18,45.47],[9.9,45.5],[10.9916,45.4384]]}},
            {"distance":170000,"duration":6000,"legs":[{"summary":"SS11"}],
             "geometry":{"type":"LineString","coordinates":[[9.18,45.47],[10.0,45.3],[10.9916,45.4384]]}}
        ]}"""
    }
}
