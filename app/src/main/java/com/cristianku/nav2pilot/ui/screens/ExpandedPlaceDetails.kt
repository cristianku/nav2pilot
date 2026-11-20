package com.cristianku.nav2pilot.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Html
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cristianku.nav2pilot.BuildConfig
import com.cristianku.nav2pilot.R
import com.cristianku.nav2pilot.places.*
import com.cristianku.nav2pilot.ui.MapIntents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

@Composable
internal fun ExpandedPlaceDetails(place: Place, info: PlaceInfo?, media: PlaceMedia?) {
    val context = LocalContext.current
    Text(stringResource(R.string.place_details), style = MaterialTheme.typography.titleMedium)
    if (!info?.description.isNullOrBlank()) Text(info!!.description)
    if (info != null && (info.phone.isNotEmpty() || info.website.isNotEmpty() || info.openingHours.isNotEmpty())) {
        PlaceInfoBlock(info, currentMinute())
    }

    var photo by remember(info?.commonsFile, info?.wikidata) { mutableStateOf<PlacePhoto?>(null) }
    var bitmap by remember(info?.commonsFile, info?.wikidata) { mutableStateOf<Bitmap?>(null) }
    var fetchingImage by remember { mutableStateOf(false) }
    LaunchedEffect(media, info?.commonsFile, info?.wikidata) {
        fetchingImage = info != null && (info.commonsFile.isNotBlank() || info.wikidata.isNotBlank()) && media != null
        if (fetchingImage && info != null && media != null) {
            try {
                photo = media.photo(info)
                bitmap = photo?.let { fetchThumbnail(it.thumbnail) }
            } catch (_: IOException) { /* The file page can still be opened if only the thumbnail failed. */ }
            fetchingImage = false
        }
    }
    val image = photo
    Text(stringResource(R.string.place_photo), style = MaterialTheme.typography.titleSmall)
    when {
        bitmap != null && image != null -> {
            Image(bitmap!!.asImageBitmap(), image.title, Modifier.fillMaxWidth().heightIn(max = 210.dp),
                contentScale = ContentScale.Fit)
            val author = remember(image) { Html.fromHtml(image.creditHtml, Html.FROM_HTML_MODE_LEGACY).toString().trim() }
            Text("$author · ${image.license}", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { MapIntents.openWebsite(context, image.page) }) { Text("Wikimedia Commons ↗") }
        }
        fetchingImage -> Text(stringResource(R.string.place_media_loading))
        image != null || !info?.imagePage.isNullOrBlank() ->
            TextButton(onClick = { MapIntents.openWebsite(context, image?.page ?: info!!.imagePage) }) {
                Text(stringResource(R.string.place_open_photo))
            }
        else -> Text(stringResource(R.string.place_no_photo), style = MaterialTheme.typography.bodySmall)
    }
    Text(stringResource(R.string.place_reviews), style = MaterialTheme.typography.titleSmall)
    var reviews by remember(place.osmId, place.name) { mutableStateOf<List<PlaceReview>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(media, place.osmId, place.name, attempt) {
        failed = false
        try { reviews = media?.reviews(place).orEmpty() }
        catch (_: IOException) { failed = true }
    }
    when {
        failed -> TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.place_reviews_retry)) }
        reviews == null -> Text(stringResource(R.string.place_media_loading))
        reviews!!.isEmpty() -> Text(stringResource(R.string.place_no_reviews), style = MaterialTheme.typography.bodySmall)
        else -> for (r in reviews.orEmpty()) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(listOfNotNull(r.author.takeIf(String::isNotBlank), r.stars?.let { String.format(Locale.getDefault(), "%.1f/5", it) },
                        "Mangrove").joinToString(" · "), style = MaterialTheme.typography.labelMedium)
                    Text(r.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Only a fixed Wikimedia host and bounded thumbnail; no WebView, redirects or original-sized downloads. */
private suspend fun fetchThumbnail(url: String): Bitmap? = withContext(Dispatchers.IO) {
    if (!PlaceMedia.thumbnailUrl(url)) return@withContext null
    val conn = URL(url).openConnection() as HttpsURLConnection
    try {
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("User-Agent", "Nav2Pilot/${BuildConfig.VERSION_NAME} (https://github.com/cristianku/nav2pilot)")
        if (conn.responseCode != 200) return@withContext null
        val bytes = conn.inputStream.use { stream ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                if (out.size() + n > 2_000_000) return@withContext null
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..2048 || bounds.outHeight !in 1..2048) return@withContext null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } finally { conn.disconnect() }
}
