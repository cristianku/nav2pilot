package com.cristianku.nav2pilot.places

import com.cristianku.nav2pilot.model.CoordinateRules
import com.cristianku.nav2pilot.model.LatLng
import com.cristianku.nav2pilot.net.HttpResponse
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.io.IOException
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import kotlin.math.floor

internal data class ParkingFeed(val key: String, val bounds: MapArea,
    val read: (Instant, (String) -> HttpResponse) -> List<ParkingReading>)

internal object ParkingFeeds {
    const val BASEL = "https://data.bs.ch/api/explore/v2.1/catalog/datasets/100088/records?limit=100"
    const val HK_DIRECTORY = "https://api.data.gov.hk/v1/carpark-info-vacancy?data=info&vehicleTypes=privateCar&lang=en_US"
    private const val HK_LIVE = "https://api.data.gov.hk/v1/carpark-info-vacancy?data=vacancy&vehicleTypes=privateCar"
    private const val BW = "https://api.mobidata-bw.de/park-api/api/public/v3/parking-sites"
    private val italyTime = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss.SSSxx")
    private val localTime = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
    private fun directory(name: String): JSONObject =
        ParkingFeeds::class.java.getResourceAsStream("/parking/$name.json")?.bufferedReader()?.use { JSONObject(it.readText()) }
            ?: throw IOException("Missing $name parking directory")
    private val zurich by lazy { directory("zurich") }
    private val singapore by lazy { directory("singapore") }
    private val verona by lazy { directory("verona") }
    private fun date(f: () -> Instant) = try { f() } catch (_: DateTimeParseException) { null }
    private fun JSONObject.text(k: String) = if (isNull(k)) "" else optString(k).trim()
    private fun JSONObject.number(k: String) = text(k).toIntOrNull()
    private fun JSONObject.instant(k: String) = date { OffsetDateTime.parse(text(k)).toInstant() }
    private fun HttpResponse.rows(key: String): List<JSONObject> {
        val a = JSONObject(body).getJSONArray(key)
        return (0 until a.length()).mapNotNull { a.optJSONObject(it) }
    }
    private fun reading(source: String, zone: String, id: String, title: String, address: String, lat: Double, lon: Double,
                        at: Instant?, status: ParkingStatus, free: Int?, total: Int?, maxAge: Long = 900): ParkingReading? {
        if (id.isBlank() || title.isBlank() || !CoordinateRules.isValid(lat, lon)) return null
        return ParkingReading("$source:$id", title, address, LatLng(lat, lon), at, status, free, total, source, zone, maxAge)
    }
    private fun status(value: String) = when (value.lowercase(java.util.Locale.ROOT)) {
        "offen", "open" -> ParkingStatus.COUNTED
        "geschlossen", "zu", "closed" -> ParkingStatus.CLOSED
        else -> ParkingStatus.UNKNOWN
    }

    private val feeds = listOf(
        ParkingFeed("verona", MapArea(45.38, 10.92, 45.49, 11.06)) { _, get ->
            get("https://api.amt3.it/").rows("Numeri Parcheggi").mapNotNull { r ->
                val id = r.text("id")
                // Only public car parks with verified municipal coordinates (not bus or camper rows).
                val p = verona.optJSONArray(id) ?: return@mapNotNull null
                val at = r.text("ts").toLongOrNull()?.takeIf { it in 1..4102444800L }?.let(Instant::ofEpochSecond)
                val total = r.number("posti_totali")
                val occupied = r.number("parkingNumberOfOccupiedSpaces")
                val free = r.number("posti_liberi")?.takeIf {
                    total != null && total > 0 && it in 0..total &&
                        (r.isNull("parkingNumberOfOccupiedSpaces") || occupied == total - it)
                }
                reading("AMT3 Verona", "Europe/Rome", id, r.text("parcheggio"), p.optString(2),
                    p.optDouble(0), p.optDouble(1), at, ParkingStatus.COUNTED, free, total, 300)
            }
        },
        ParkingFeed("zurich", MapArea(47.30, 8.44, 47.45, 8.64)) { _, get ->
            val rss = get("https://www.pls-zh.ch/plsFeed/rss").body
            // Only RSS data elements are needed. Never process DTDs or external references.
            if (rss.contains("<!DOCTYPE", ignoreCase = true)) throw IOException("Unexpected RSS doctype")
            val items = try {
                DocumentBuilderFactory.newInstance().newDocumentBuilder().apply {
                    setEntityResolver { _, _ -> InputSource(StringReader("")) }
                }.parse(InputSource(StringReader(rss))).getElementsByTagName("item")
            } catch (e: SAXException) { throw IOException("Invalid parking RSS", e) }
            (0 until items.length).mapNotNull { i ->
                val item = items.item(i) as Element
                fun text(tag: String) = item.getElementsByTagName(tag).item(0)?.textContent.orEmpty().trim()
                val id = text("guid").substringAfter("?pid=", "")
                val p = zurich.optJSONArray(id) ?: return@mapNotNull null
                val value = text("description").split('/').map { it.trim() }
                val at = date { ZonedDateTime.parse(text("pubDate"), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }
                reading("Parkleitsystem Stadt Zürich", "Europe/Zurich", id, text("title").substringBefore(" /"), p.optString(2),
                    p.optDouble(0), p.optDouble(1), at, status(value.first()), value.getOrNull(1)?.toIntOrNull(), p.optInt(3).takeIf { it > 0 })
            }
        },
        ParkingFeed("singapore", MapArea(1.20, 103.60, 1.48, 104.10)) { _, get ->
            val snapshot = get("https://api.data.gov.sg/v1/transport/carpark-availability").rows("items").lastOrNull()
            val rows = snapshot?.optJSONArray("carpark_data")
            if (rows == null) emptyList() else (0 until rows.length()).mapNotNull { i ->
                val row = rows.optJSONObject(i) ?: return@mapNotNull null
                val id = row.text("carpark_number")
                val p = singapore.optJSONArray(id) ?: return@mapNotNull null
                val lots = row.optJSONArray("carpark_info") ?: return@mapNotNull null
                val cars = (0 until lots.length()).mapNotNull { lots.optJSONObject(it) }.singleOrNull { it.text("lot_type") == "C" }
                    ?: return@mapNotNull null
                val at = date { LocalDateTime.parse(row.text("update_datetime")).atZone(ZoneId.of("Asia/Singapore")).toInstant() }
                reading("HDB · data.gov.sg", "Asia/Singapore", id, "HDB $id", p.optString(2), p.optDouble(0), p.optDouble(1),
                    at, ParkingStatus.COUNTED, cars.number("lots_available"), cars.number("total_lots"), 300)
            }
        },
        ParkingFeed("basel", MapArea(47.52, 7.55, 47.59, 7.63)) { _, get ->
            get(BASEL).rows("results").mapNotNull { r ->
                val p = r.optJSONObject("geo_point_2d") ?: return@mapNotNull null
                reading("Parkhäuser Basel-Stadt", "Europe/Zurich", r.text("id").ifEmpty { r.text("name") },
                    r.text("title"), r.text("address"), p.optDouble("lat"), p.optDouble("lon"), r.instant("published"),
                    status(r.text("status")), r.number("free"), r.number("total"), 300)
            }
        },
        ParkingFeed("stgallen", MapArea(47.37, 9.27, 47.48, 9.46)) { _, get ->
            get("https://daten.stadt.sg.ch/api/explore/v2.1/catalog/datasets/freie-parkplatze-in-der-stadt-stgallen-pls/records?limit=100")
                .rows("results").mapNotNull { r ->
                    val p = r.optJSONObject("koordinaten") ?: return@mapNotNull null
                    reading("Stadt St.Gallen · PLS", "Europe/Zurich", r.text("ph_id"), r.text("ph_name"), "",
                        p.optDouble("lat"), p.optDouble("lon"), r.instant("letzte_aktualisierung"),
                        status(r.text("ph_status")), r.number("frei"), r.number("anzahl_parkplatze"))
                }
        },
        ParkingFeed("nantes", MapArea(47.10, -1.76, 47.35, -1.40)) { _, get ->
            get("https://data.nantesmetropole.fr/api/explore/v2.1/catalog/datasets/244400404_parkings-publics-nantes-disponibilites/records?limit=100")
                .rows("results").mapNotNull { r ->
                    val p = r.optJSONObject("location") ?: return@mapNotNull null
                    reading("Nantes Métropole", "Europe/Paris", r.text("grp_identifiant"), r.text("grp_nom"), r.text("adresse"),
                        p.optDouble("lat"), p.optDouble("lon"), r.instant("grp_horodatage"),
                        when (r.number("grp_statut")) { 5 -> ParkingStatus.COUNTED; 1, 2 -> ParkingStatus.CLOSED; else -> ParkingStatus.UNKNOWN },
                        r.number("grp_disponible"), r.number("grp_exploitation"))
                }
        },
        ParkingFeed("suedtirol", MapArea(46.19, 10.35, 47.10, 12.52)) { _, get ->
            get("https://mobility.api.opendatahub.com/v2/flat,node/ParkingStation/free/latest?limit=5000&select=scode,sname,sorigin,scoordinate,smetadata.capacity,mvalue,mvalidtime")
                .rows("data").mapNotNull { r ->
                    val p = r.optJSONObject("scoordinate") ?: return@mapNotNull null
                    if (p.optInt("srid") != 4326 || r.text("scode").isEmpty()) return@mapNotNull null
                    reading("Open Data Hub · ${r.text("sorigin")}", "Europe/Rome", r.text("scode"), r.text("sname"), "",
                        p.optDouble("y"), p.optDouble("x"), date { OffsetDateTime.parse(r.text("mvalidtime"), italyTime).toInstant() },
                        ParkingStatus.COUNTED, r.number("mvalue"), r.number("smetadata.capacity"))
                }
        },
        ParkingFeed("asheville", MapArea(35.54, -82.62, 35.65, -82.48)) { _, get ->
            val feed = get("https://s3.amazonaws.com/avl-parking-decks/all-spaces.json")
            // This city's snapshot has no per-garage timestamp. Use publication of the object, never HTTP Date.
            val at = feed.header("Last-Modified")?.let { date { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() } }
            feed.rows("decks").mapNotNull { r ->
                val p = r.optJSONArray("coords") ?: return@mapNotNull null
                reading("City of Asheville", "America/New_York", r.text("slug"), r.text("name"), r.text("address"),
                    p.optDouble(0), p.optDouble(1), at, if (r.text("available") == "closed") ParkingStatus.CLOSED else ParkingStatus.COUNTED,
                    r.number("available"), null, 300)
            }
        },
        ParkingFeed("hongkong", MapArea(22.14, 113.82, 22.57, 114.45)) { _, get ->
            val sites = get(HK_DIRECTORY).rows("results")
            val live = get(HK_LIVE).rows("results").associateBy { it.text("park_Id") }
            sites.mapNotNull { r ->
                // Most catalogue entries have no static privateCar block but do publish vacancies.
                val cars = r.optJSONObject("privateCar")
                val readings = live[r.text("park_Id")]?.optJSONArray("privateCar")
                if (cars == null && readings == null) return@mapNotNull null
                val v = readings?.takeIf { it.length() == 1 }?.optJSONObject(0)
                val at = v?.let { date { LocalDateTime.parse(it.text("lastupdate"), localTime).atZone(ZoneId.of("Asia/Hong_Kong")).toInstant() } }
                reading("Hong Kong Transport Department", "Asia/Hong_Kong", r.text("park_Id"), r.text("name"), r.text("displayAddress"),
                    r.optDouble("latitude"), r.optDouble("longitude"), at, status(r.text("opening_status")),
                    v?.takeIf { it.text("vacancy_type") == "A" }?.number("vacancy"), cars?.number("space"))
            }
        },
    )

    private fun MapArea.intersects(a: MapArea) = south <= a.north && north >= a.south && west <= a.east && east >= a.west
    // Only whole degree tiles reach MobiData, never the viewport or the tapped point.
    // The feed includes other German regions beyond Baden-Württemberg.
    private val germany = MapArea(47.27, 5.86, 55.06, 15.05)
    fun forArea(a: MapArea): List<ParkingFeed> = buildList {
        addAll(feeds.filter { it.bounds.intersects(a) })
        if (!germany.intersects(a)) return@buildList
        for (lat in floor(maxOf(a.south, germany.south)).toInt()..floor(minOf(a.north, germany.north)).toInt()) {
            for (lon in floor(maxOf(a.west, germany.west)).toInt()..floor(minOf(a.east, germany.east)).toInt()) {
                add(ParkingFeed("de:$lat:$lon", MapArea(lat.toDouble(), lon.toDouble(), lat+1.0, lon+1.0)) { now, get ->
                    val url = "$BW?purpose=CAR&ignore_duplicates=true&lat_min=$lat&lon_min=$lon&lat_max=${lat+1}&lon_max=${lon+1}" +
                        "&limit=500&realtime_data_updated_at_since=${Instant.ofEpochSecond(now.epochSecond - 900)}"
                    val response = get(url)
                    fun parse(page: HttpResponse) = page.rows("items").mapNotNull { r ->
                        if (!r.optBoolean("has_realtime_data") || r.text("purpose") != "CAR") return@mapNotNull null
                        val state = r.text("realtime_opening_status")
                        reading("MobiData BW · ${r.text("operator_name").ifBlank { "ParkAPI" }}", "Europe/Berlin", r.text("id"), r.text("name"),
                            r.text("address"), r.optDouble("lat"), r.optDouble("lon"), r.instant("realtime_data_updated_at"),
                            if (state.isEmpty()) ParkingStatus.COUNTED else status(state), r.number("realtime_free_capacity"),
                            r.number("realtime_capacity") ?: r.number("capacity"))
                    }
                    buildList {
                        var page = response
                        var cursor = -1
                        while (true) {
                            addAll(parse(page))
                            val next = JSONObject(page.body).optInt("next_id", -1)
                            if (next <= cursor) break
                            cursor = next
                            page = get("$url&start=$next")
                        }
                    }
                })
            }
        }
    }
}
