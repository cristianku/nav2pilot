package com.cristianku.nav2pilot.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.cristianku.nav2pilot.R
import com.cristianku.nav2pilot.places.OpeningHours
import com.cristianku.nav2pilot.places.PlaceInfo
import com.cristianku.nav2pilot.places.PlaceKinds
import com.cristianku.nav2pilot.ui.MapIntents
import com.cristianku.nav2pilot.ui.map.placeSymbol
import com.cristianku.nav2pilot.ui.theme.StatusColors
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** The symbol of a place's kind: the places menu's own, a shop, a town, an address, or a plain pin. */
@Composable
fun KindIcon(kind: String, tint: Color = MaterialTheme.colorScheme.primary) {
    val category = PlaceKinds.category(kind)
    if (category != null) {
        Icon(painterResource(placeSymbol(category)), contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        return
    }
    val icon = when {
        PlaceKinds.isShop(kind) -> Icons.Filled.ShoppingCart
        PlaceKinds.isArea(kind) -> Icons.Filled.LocationOn
        PlaceKinds.isAddress(kind) -> Icons.Filled.Home
        else -> Icons.Filled.Place
    }
    Icon(icon, contentDescription = null, tint = tint)
}

/** The time now, again at each new minute: the open or closed of a place follows the clock. */
@Composable
fun currentMinute(): LocalDateTime {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay((60 - LocalDateTime.now().second) * 1000L)
            value = LocalDateTime.now()
        }
    }
    return now
}

/**
 * "Furniture store · Open · Closes 20:00": [label] first when there is one, the open or closed in its colour.
 * Hours the app cannot read give no status: they show as written in the details.
 */
@Composable
fun kindAndStatus(label: String, openingHours: String, now: LocalDateTime): AnnotatedString {
    val hours = remember(openingHours) { OpeningHours.parse(openingHours) }
    val status = hours?.statusText(now)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val toneColor = when (status?.tone) {
        OpeningHours.StatusText.Tone.OPEN -> StatusColors.success()
        OpeningHours.StatusText.Tone.SOON -> StatusColors.warning()
        else -> MaterialTheme.colorScheme.error
    }
    return buildAnnotatedString {
        withStyle(SpanStyle(color = muted)) {
            append(label)
        }
        if (status != null) {
            if (label.isNotEmpty()) {
                withStyle(SpanStyle(color = muted)) {
                    append(" · ")
                }
            }
            withStyle(SpanStyle(color = toneColor, fontWeight = FontWeight.Medium)) {
                append(status.headline)
            }
            if (status.rest.isNotEmpty()) {
                withStyle(SpanStyle(color = muted)) {
                    append(" · " + status.rest)
                }
            }
        }
    }
}

/** The opening hours of the week (today in bold), the phone and the website, each when OpenStreetMap has it. */
@Composable
fun PlaceInfoBlock(info: PlaceInfo, now: LocalDateTime) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (info.openingHours.isNotEmpty()) {
            val hours = remember(info.openingHours) { OpeningHours.parse(info.openingHours) }
            Text("Opening hours", style = MaterialTheme.typography.labelLarge)
            if (hours == null) {
                // more than the app reads (months, holidays by date): as mapped
                Text(info.openingHours, style = MaterialTheme.typography.bodyMedium)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val today = now.dayOfWeek.value - 1
                    for ((index, row) in hours.week().withIndex()) {
                        var weight = FontWeight.Normal
                        if (index == today) {
                            weight = FontWeight.SemiBold
                        }
                        Row {
                            Text(
                                row.first,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = weight,
                                modifier = Modifier.width(DAY_COLUMN_WIDTH),
                            )
                            Text(row.second, style = MaterialTheme.typography.bodyMedium, fontWeight = weight)
                        }
                    }
                }
            }
        }
        if (info.phone.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .clickable { MapIntents.dial(context, info.phone) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Phone, contentDescription = "Call", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(info.phone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (info.website.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .clickable { MapIntents.openWebsite(context, info.website) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painterResource(R.drawable.ic_website),
                    contentDescription = "Website",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    shortWebAddress(info.website),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "ikea.com/ch/de/stores/pratteln" for "https://www.ikea.com/ch/de/stores/pratteln/". */
fun shortWebAddress(address: String): String {
    return address
        .replaceFirst(Regex("^https?://", RegexOption.IGNORE_CASE), "")
        .replaceFirst(Regex("^www\\.", RegexOption.IGNORE_CASE), "")
        .trimEnd('/')
}

private val DAY_COLUMN_WIDTH = 104.dp
