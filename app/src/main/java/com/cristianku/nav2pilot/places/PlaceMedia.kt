package com.cristianku.nav2pilot.places

import com.cristianku.nav2pilot.mapbox.RouteShaping
import com.cristianku.nav2pilot.model.LatLng
import com.cristianku.nav2pilot.net.HttpClient
import com.cristianku.nav2pilot.net.HttpRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

data class PlacePhoto(val title: String, val creditHtml: String, val license: String, val page: String, val thumbnail: String)
data class PlaceReview(val text: String, val author: String, val stars: Double?)

/** Read-only, requested only by the expanded preview. No approximate image or nearest-venue review search. */
class PlaceMedia(private val http: HttpClient, private val io: CoroutineDispatcher) {
    private fun json(url: String): JSONObject {
        val r = http.execute(HttpRequest("GET", url, headers = mapOf("Accept" to "application/json"), timeoutMillis = 5000))
        if (!r.isSuccessful()) throw IOException("HTTP ${r.code}")
        return try { JSONObject(r.body) } catch (e: JSONException) { throw IOException("Invalid response", e) }
    }

    suspend fun photo(info: PlaceInfo): PlacePhoto? = withContext(io) {
        val file = info.commonsFile.ifEmpty {
            if (!info.wikidata.matches(Regex("Q[1-9][0-9]{0,12}"))) return@withContext null
            val claims = json("https://www.wikidata.org/w/api.php?action=wbgetclaims&format=json&property=P18&entity=${info.wikidata}")
                .optJSONObject("claims")?.optJSONArray("P18") ?: return@withContext null
            (0 until claims.length()).mapNotNull { claims.optJSONObject(it) }.filter { it.optString("rank") != "deprecated" }
                .sortedByDescending { it.optString("rank") == "preferred" }.firstNotNullOfOrNull {
                    it.optJSONObject("mainsnak")?.optJSONObject("datavalue")?.optString("value")?.takeIf(String::isNotBlank)
                }.orEmpty()
        }
        if (!file.matches(Regex("[^|\\p{Cntrl}]{1,250}\\.(?i:jpe?g|png|webp)"))) return@withContext null
        val meta = json("https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2&prop=imageinfo" +
            "&iiurlwidth=500&iiprop=url%7Cextmetadata&iiextmetadatafilter=Artist%7CAttribution%7CLicenseShortName&titles=${enc("File:$file")}")
        val image = meta.optJSONObject("query")?.optJSONArray("pages")?.optJSONObject(0)
            ?.optJSONArray("imageinfo")?.optJSONObject(0) ?: return@withContext null
        val ext = image.optJSONObject("extmetadata") ?: return@withContext null
        fun field(key: String) = ext.optJSONObject(key)?.optString("value").orEmpty()
        val credit = field("Attribution").ifBlank { field("Artist") }
        val thumb = image.optString("thumburl")
        val page = image.optString("descriptionurl")
        if (!thumbnailUrl(thumb) || !trusted(page, "commons.wikimedia.org") ||
            credit.isBlank() || field("LicenseShortName").isBlank()) return@withContext null
        PlacePhoto(file, credit, field("LicenseShortName"), page, thumb)
    }

    suspend fun reviews(place: Place): List<PlaceReview> = withContext(io) {
        if (place.name.isBlank() || place.name == place.category.singular ||
            (!PlaceInfoLookup.isOsmId(place.osmId) && place.parkingReading == null)) return@withContext emptyList()
        val sub = "geo:${place.latitude},${place.longitude}?u=35&q=${enc(place.name).replace("+","%20")}"
        parseReviews(json("https://api.mangrove.reviews/reviews?sub=${enc(sub)}&opinionated=true&limit=8"), place)
    }

    companion object {
        private fun enc(text: String) = URLEncoder.encode(text, "UTF-8")
        internal fun thumbnailUrl(url: String) = trusted(url, "thumb.wikimedia.org") || trusted(url, "upload.wikimedia.org")
        internal fun trusted(url: String, host: String): Boolean {
            val uri = try { URI(url) } catch (_: java.net.URISyntaxException) { return false }
            return uri.scheme == "https" && uri.host == host && uri.userInfo == null && uri.port == -1
        }
        internal fun parseReviews(json: JSONObject, place: Place): List<PlaceReview> {
            val rows = json.optJSONArray("reviews") ?: return emptyList()
            val point = LatLng(place.latitude, place.longitude)
            return buildList {
                for (i in 0 until rows.length()) {
                    val row = rows.optJSONObject(i) ?: continue
                    val p = row.optJSONObject("payload") ?: continue
                    val sub = p.optString("sub")
                    if (!sub.startsWith("geo:")) continue
                    val coords = sub.removePrefix("geo:").substringBefore('?').substringBefore(';').split(',')
                    val lat = coords.getOrNull(0)?.toDoubleOrNull() ?: continue
                    val lon = coords.getOrNull(1)?.toDoubleOrNull() ?: continue
                    if (!com.cristianku.nav2pilot.model.CoordinateRules.isValid(lat,lon) ||
                        RouteShaping.distanceMeters(point, LatLng(lat,lon)) > 100) continue
                    val metadata = p.optJSONObject("metadata")
                    val id = metadata?.optString("osm_id").orEmpty().split('/').take(2).joinToString("/")
                    if (id.isNotBlank() && place.osmId.isNotBlank() && id != place.osmId) continue
                    val q = sub.substringAfter('?', "").split('&').firstOrNull { it.startsWith("q=") }?.drop(2).orEmpty()
                    val name = try { URLDecoder.decode(q, "UTF-8") } catch (_: IllegalArgumentException) { continue }
                    if ((id.isBlank() || id != place.osmId) && !name.trim().equals(place.name.trim(), true)) continue
                    val text = (p.opt("opinion") as? String)?.trim()?.take(1500) ?: continue
                    if (text.isEmpty()) continue
                    add(PlaceReview(text, metadata?.optString("nickname").orEmpty().take(80),
                        p.optInt("rating",-1).takeIf { it in 0..100 }?.div(20.0)))
                    if (size == 3) break
                }
            }
        }
    }
}
