package com.cristianku.nav2pilot.places

import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.LatLng
import com.cristianku.nav2pilot.net.HttpClient
import com.cristianku.nav2pilot.net.HttpRequest
import com.cristianku.nav2pilot.net.HttpResponse
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.Locale

/** What OpenStreetMap says about a place besides its name and address; "" for what it does not say. */
data class PlaceInfo(
    /** As mapped, "Mo-Fr 08:00-18:00; Sa 08:00-12:00": [OpeningHours.parse] reads it. */
    val openingHours: String = "",
    val phone: String = "",
    /** An http or https address, nothing else. */
    val website: String = "",
) {
    fun isEmpty(): Boolean {
        return openingHours.isEmpty() && phone.isEmpty() && website.isEmpty()
    }

    companion object {
        /** From an element's OpenStreetMap tags. */
        fun fromTags(tags: JSONObject): PlaceInfo {
            return PlaceInfo(
                openingHours = firstTag(tags, "opening_hours"),
                // several numbers are separated by ";": the first is the main one
                phone = firstTag(tags, "phone", "contact:phone").substringBefore(';').trim(),
                website = webAddress(firstTag(tags, "website", "contact:website", "url").substringBefore(';').trim()),
            )
        }

        /** [text] as an address the browser can open; "" for anything that is not a web page ("javascript:", "intent:"). */
        fun webAddress(text: String): String {
            if (text.isEmpty() || text.contains(' ')) {
                return ""
            }
            val lower = text.lowercase(Locale.ROOT)
            if (lower.startsWith("https://") || lower.startsWith("http://")) {
                return text
            }
            // "www.ikea.ch" is often mapped so
            if (!text.contains(':') && text.contains('.')) {
                return "https://$text"
            }
            return ""
        }

        private fun firstTag(tags: JSONObject, vararg keys: String): String {
            for (key in keys) {
                if (tags.isNull(key)) {
                    continue
                }
                val value = tags.optString(key, "").trim()
                if (value.isNotEmpty()) {
                    return value
                }
            }
            return ""
        }
    }
}

/**
 * Opening hours, phone and website of search results, from their OpenStreetMap elements through the Overpass API:
 * the servers get only the elements' ids. One request for a whole list of results, one at a time, and what was
 * found is kept for the rest of the session.
 */
class PlaceInfoLookup(
    private val http: HttpClient,
    private val ioDispatcher: CoroutineDispatcher,
    private val endpoints: (List<LatLng>) -> List<String> = OverpassEndpoints::forPoints,
) {
    private val mutex = Mutex()

    // the oldest out first
    private val known = object : LinkedHashMap<String, PlaceInfo>(MAX_KNOWN, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PlaceInfo>): Boolean {
            return size > MAX_KNOWN
        }
    }

    /**
     * The info of the [destinations] that come from OpenStreetMap, by [Destination.osmId]; empty for those it
     * has none for. Those no server could be asked about are left out, and asked again next time.
     */
    suspend fun lookup(destinations: List<Destination>): Map<String, PlaceInfo> {
        val wanted = destinations.filter { destination -> isOsmId(destination.osmId) }.distinctBy { destination -> destination.osmId }
        if (wanted.isEmpty()) {
            return emptyMap()
        }
        return mutex.withLock {
            val found = mutableMapOf<String, PlaceInfo>()
            var missing = mutableListOf<Destination>()
            for (destination in wanted) {
                val info = known[destination.osmId]
                if (info != null) {
                    found[destination.osmId] = info
                } else {
                    missing.add(destination)
                }
            }
            if (missing.isEmpty()) {
                return@withLock found
            }
            val points = missing.map { destination -> LatLng(destination.latitude, destination.longitude) }
            for (endpoint in endpoints(points)) {
                val asked = missing.map { destination -> destination.osmId }
                // keep only what was asked about, whatever else the answer holds
                val answer = ask(endpoint, asked)?.filterKeys { id -> asked.contains(id) } ?: continue
                for ((id, info) in answer) {
                    known[id] = info
                    found[id] = info
                }
                // the Swiss server has only the places in and around Switzerland: the others go to the next one
                val stillMissing = missing.filter { destination -> !answer.containsKey(destination.osmId) }
                if (stillMissing.isEmpty() || endpoint != OverpassEndpoints.SWITZERLAND) {
                    // asked and not in OpenStreetMap any more: nothing to say about it
                    for (destination in stillMissing) {
                        known[destination.osmId] = PlaceInfo()
                        found[destination.osmId] = PlaceInfo()
                    }
                    break
                }
                missing = stillMissing.toMutableList()
            }
            found
        }
    }

    /** The elements' info by id, or null when the server did not answer. */
    private suspend fun ask(endpoint: String, osmIds: List<String>): Map<String, PlaceInfo>? {
        val request = HttpRequest(
            method = "POST",
            url = endpoint,
            headers = mapOf(
                "Content-Type" to "application/x-www-form-urlencoded",
                "Accept" to "application/json",
            ),
            body = "data=" + URLEncoder.encode(query(osmIds), "UTF-8"),
            timeoutMillis = SERVER_TIMEOUT_MILLIS,
        )
        val response: HttpResponse
        try {
            response = withContext(ioDispatcher) { http.execute(request) }
        } catch (error: IOException) {
            return null
        }
        if (!response.isSuccessful()) {
            return null
        }
        val json: JSONObject
        try {
            json = JSONObject(response.body)
        } catch (notJson: JSONException) {
            return null
        }
        // out of time or memory: 200 with a remark
        if (!json.isNull("remark") && json.optString("remark", "").isNotBlank()) {
            return null
        }
        return parse(json)
    }

    companion object {
        // a list of results and the destinations chosen in a session
        private const val MAX_KNOWN = 200

        // only extra information: better none than a wait
        private const val SERVER_TIMEOUT_MILLIS = 6_000

        private val TYPES = listOf("node", "way", "relation")

        /** "way/26343678": an element of OpenStreetMap, as osm.org writes it. */
        fun isOsmId(text: String): Boolean {
            val type = text.substringBefore('/', "")
            val number = text.substringAfter('/', "")
            return TYPES.contains(type) && number.isNotEmpty() && number.length <= 19 && number.all { char -> char.isDigit() }
        }

        /** Overpass QL: the tags of the elements, by type and id. */
        fun query(osmIds: List<String>): String {
            val query = StringBuilder("[out:json][timeout:5];(")
            for (type in TYPES) {
                val numbers = osmIds.filter { id -> isOsmId(id) && id.startsWith("$type/") }.map { id -> id.substringAfter('/') }
                if (numbers.isNotEmpty()) {
                    query.append(type).append("(id:").append(numbers.joinToString(",")).append(");")
                }
            }
            query.append(");out tags;")
            return query.toString()
        }

        fun parse(json: JSONObject): Map<String, PlaceInfo> {
            val found = mutableMapOf<String, PlaceInfo>()
            val elements = json.optJSONArray("elements") ?: return found
            for (index in 0 until elements.length()) {
                val element = elements.optJSONObject(index) ?: continue
                val type = element.optString("type", "")
                val id = element.optLong("id", -1L)
                if (!TYPES.contains(type) || id < 0) {
                    continue
                }
                var tags = element.optJSONObject("tags")
                if (tags == null) {
                    tags = JSONObject()
                }
                found["$type/$id"] = PlaceInfo.fromTags(tags)
            }
            return found
        }
    }
}
