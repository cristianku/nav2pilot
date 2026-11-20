package com.cristianku.nav2pilot.places

import com.cristianku.nav2pilot.model.LatLng
import com.cristianku.nav2pilot.net.HttpResponse
import com.cristianku.nav2pilot.testing.FakeHttpClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ParkingAvailabilityTest {
    private val now = Instant.parse("2026-11-20T12:00:00Z")
    private fun rows(key: String, lat: Double, lon: Double, body: (String) -> String): List<ParkingReading> {
        val feed = ParkingFeeds.forArea(MapArea.around(LatLng(lat, lon), 200.0)).single { it.key == key }
        return feed.read(now) { HttpResponse(200, emptyMap(), body(it)) }
    }
    private fun basel() = rows("basel", 47.56, 7.59) {
        """{"results":[{},{"title":"Parkhaus City","name":"City","geo_point_2d":{"lat":47.56,"lon":7.59},"address":"Uferstrasse 3","published":"$now","status":"offen","free":4,"total":10}]}"""
    }.single()

    @Test fun matchOnIdentityAndProximityAndDoNotStealAmbiguousPins() {
        val r = basel()
        val p = Place(PlaceCategory.PARKING, 47.56, 7.59, "City", "", osmId = "way/42")
        assertTrue(r.matches(p.copy(name = "Basel Parkhaus City")))
        assertFalse(r.matches(p.copy(name = "Parking")))
        assertFalse(r.matches(p.copy(name = "Another garage", details = "Uferstrasse 3")))
        assertFalse(r.matches(p.copy(latitude = 47.575)))
        assertTrue(r.matches(p.copy(name = "Parking", details = "Uferstrasse 3, Basel")))
        val pin = p.copy(name = r.title, osmId = "", parkingReading = r)
        assertEquals("way/42", ParkingAvailability.merge(listOf(pin), listOf(p)).single().osmId)
        assertEquals(3, ParkingAvailability.merge(listOf(pin), listOf(p, p.copy(osmId = "way/43"))).size)
        assertEquals(3, ParkingAvailability.merge(listOf(pin, pin), listOf(p)).size)
    }

    @Test fun staleClosedMissingInvalidAndFutureReadingsNeverSupplyMapNumbers() {
        val r = basel()
        assertEquals(4, r.vacancies(now))
        assertEquals(0, r.copy(free = 0).vacancies(now))
        for (invalid in listOf(r.copy(free = null), r.copy(free = -1), r.copy(free = 11),
            r.copy(status = ParkingStatus.CLOSED), r.copy(status = ParkingStatus.UNKNOWN), r.copy(published = null),
            r.copy(published = now.minusSeconds(301)), r.copy(published = now.plusSeconds(61)))) assertNull(invalid.vacancies(now))
    }

    @Test fun singaporeUsesOnlyCarLotsAndLocalTimestampByStableDirectoryId() {
        val r = rows("singapore", 1.36, 103.84) {
            """{"items":[{"carpark_data":[{"carpark_number":"A1","update_datetime":"2026-11-20T20:00:00","carpark_info":[{"lot_type":"Y","lots_available":"80"},{"lot_type":"C","total_lots":"45","lots_available":"2"}]},{"carpark_number":"missing"}]}]}"""
        }.single()
        assertEquals(2, r.vacancies(now))
        assertEquals(45, r.total)
        assertEquals(1.366714, r.point.latitude, 0.001)
    }

    @Test fun hongKongJoinsByIdAndDoesNotTreatAnIndicatorAsAnExactCount() {
        val r = rows("hongkong", 22.31, 114.19) { url ->
            if (url == ParkingFeeds.HK_DIRECTORY) """{"results":[{"park_Id":"11","name":"Harbour","latitude":22.31,"longitude":114.19,"opening_status":"OPEN","privateCar":{"space":123}}]}"""
            else """{"results":[{"park_Id":"12","privateCar":[{"vacancy_type":"A","vacancy":77}]},{"park_Id":"11","privateCar":[{"vacancy_type":"B","vacancy":1,"lastupdate":"2026-11-20 20:00:00"}]}]}"""
        }.single()
        assertEquals(now, r.published)
        assertEquals(123, r.total)
        assertNull(r.vacancies(now))
    }

    @Test fun zurichJoinsOfficialPidNotListOrder() {
        val r = rows("zurich", 47.40, 8.54) {
            """<rss><channel><item><guid>https://www.pls-zh.ch/parkhaus/x.jsp?pid=accu</guid><title>Parkhaus Accu / Otto-Schütz-Weg</title><description>open / 8</description><pubDate>Fri, 20 Nov 2026 12:00:00 GMT</pubDate></item></channel></rss>"""
        }.single()
        assertEquals(8, r.vacancies(now))
        assertEquals("Parkhaus Accu", r.title)
    }

    @Test fun hongKongCountsDoNotRequireAnOptionalStaticCapacityRecord() {
        val r = rows("hongkong", 22.31, 114.19) { url ->
            if (url == ParkingFeeds.HK_DIRECTORY) """{"results":[{"park_Id":"27","name":"Garage","latitude":22.31,"longitude":114.19,"opening_status":"OPEN"}]}"""
            else """{"results":[{"park_Id":"27","privateCar":[{"vacancy_type":"A","vacancy":5,"lastupdate":"2026-11-20 20:00:00"}]}]}"""
        }.single()
        assertNull(r.total)
        assertEquals(5, r.vacancies(now))
    }

    @Test fun nantesOnlyReportsVacanciesForPubliclyOpenGarages() {
        fun read(code: Int) = rows("nantes", 47.21, -1.56) {
            """{"results":[{"grp_identifiant":6,"grp_nom":"Médiathèque","location":{"lat":47.21,"lon":-1.56},"grp_statut":$code,"grp_horodatage":"$now","grp_disponible":0,"grp_exploitation":30}]}"""
        }.single()
        assertEquals(0, read(5).vacancies(now))
        assertEquals(ParkingStatus.CLOSED, read(2).status)
        assertNull(read(0).vacancies(now))
    }

    @Test fun datesFromItalyGermanyAndAmericaDoNotUseRequestTime() {
        val it = rows("suedtirol", 46.65, 11.14) {
            """{"data":[{"sname":"Stazione","scode":"16","sorigin":"STA","scoordinate":{"x":11.14,"y":46.65,"srid":4326},"mvalidtime":"2026-11-20 12:00:00.000+0000","mvalue":7,"smetadata.capacity":25}]}"""
        }.single()
        assertEquals(7, it.vacancies(now))
        val de = rows("de:47:7", 47.99, 7.85) {
            """{"items":[{"purpose":"CAR","name":"Martinstor","id":42,"has_realtime_data":true,"lat":47.99,"lon":7.85,"realtime_opening_status":"CLOSED","realtime_data_updated_at":"$now","realtime_free_capacity":20,"capacity":120}]}"""
        }.single()
        assertEquals(ParkingStatus.CLOSED, de.status)
        assertNull(de.vacancies(now))
        val us = ParkingFeeds.forArea(MapArea.around(LatLng(35.59,-82.55), 100.0)).single()
        val payload = """{"decks":[{"name":"College Street","slug":"college-street","coords":[35.59,-82.55],"available":"15"}]}"""
        assertNull(us.read(now) { HttpResponse(200, emptyMap(), payload) }.single().vacancies(now))
        assertEquals(15, us.read(now) { HttpResponse(200, mapOf("Last-Modified" to listOf("Fri, 20 Nov 2026 12:00:00 GMT")), payload) }.single().vacancies(now))
    }

    @Test fun noOccupancyRequestInUncoveredAreaAndCityFeedIsSharedAcrossViewportChanges() = runTest {
        val http = FakeHttpClient()
        val service = ParkingAvailability(http, UnconfinedTestDispatcher(testScheduler))
        assertTrue(service.placesIn(MapArea.around(LatLng(-33.86,151.21), 700.0)).isEmpty())
        assertTrue(http.requests.isEmpty())
        http.respond(200, """{"results":[{"grp_identifiant":6,"grp_nom":"Médiathèque","location":{"lat":47.21,"lon":-1.56},"grp_statut":5,"grp_horodatage":"$now","grp_disponible":2}]}""")
        assertEquals("Médiathèque", service.placesIn(MapArea.around(LatLng(47.21,-1.56), 500.0)).single().name)
        assertTrue(service.placesIn(MapArea.around(LatLng(47.26,-1.57), 200.0)).isEmpty())
        assertEquals(1, http.requests.size)
    }

    @Test fun osmCapacityAloneNeverSuppliesAnOccupancyNumber() {
        val osm = JSONObject("""{"elements":[{"type":"node","id":51,"lat":-33.86,"lon":151.21,"tags":{"amenity":"parking","capacity":"120"}}]}""")
        val p = OverpassPlaces.parsePlaces(osm, PlaceCategory.PARKING, LatLng(-33.86,151.21), 50).single()
        assertEquals(120, p.parkingCapacity)
        assertNull(p.parkingReading)
    }
}
