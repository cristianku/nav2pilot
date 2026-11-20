package com.cristianku.nav2pilot.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cristianku.nav2pilot.R
import com.cristianku.nav2pilot.places.ParkingLevel
import com.cristianku.nav2pilot.ui.theme.pinColor

private val harborInk = Color(0xFF112D43)

/** An offline illustration, not a MapView: opening the tour makes no requests or permission prompts. */
@Composable
internal fun ParkingCover(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(24.dp)).clearAndSetSemantics {}) {
        Image(painterResource(R.drawable.parking_harbour), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Text(stringResource(R.string.news_example), Modifier.align(Alignment.TopStart).padding(14.dp)
            .background(harborInk, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp),
            color = Color.White, style = MaterialTheme.typography.labelSmall)
        Row(Modifier.align(Alignment.TopEnd).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.shadow(3.dp, CircleShape).background(Color(0xFF007C89), CircleShape)
                .padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(painterResource(R.drawable.ic_place_parking), null, Modifier.size(16.dp), tint = Color.White)
                Text(stringResource(R.string.places_parking), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            Box(Modifier.size(34.dp).shadow(3.dp, CircleShape).background(Color.White, CircleShape),
                contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_places), null, Modifier.size(18.dp), tint = Color(0xFF007C89))
            }
        }
        Box(Modifier.align(BiasAlignment(-0.42f, 0.06f))) { ParkingBadge("P 12", ParkingLevel.AVAILABLE, prominent = true) }
        Box(Modifier.align(BiasAlignment(0.52f, 0.30f))) { ParkingBadge("P 3", ParkingLevel.LOW) }
        Box(Modifier.align(BiasAlignment(0.10f, 0.72f))) { ParkingBadge("P 0", ParkingLevel.FULL) }
        Box(Modifier.align(BiasAlignment(-0.78f, 0.75f))) { ParkingBadge("P", ParkingLevel.UNKNOWN) }
    }
}

@Composable
private fun ParkingBadge(label: String, level: ParkingLevel, prominent: Boolean = false) {
    Box(Modifier.shadow(if (prominent) 6.dp else 2.dp, CircleShape).background(Color(level.pinColor), CircleShape)
        .border(if (prominent) 3.dp else 1.5.dp, Color.White, CircleShape)
        .widthIn(min = if (prominent) 74.dp else 30.dp)
        .padding(horizontal = if (prominent) 15.dp else 7.dp, vertical = if (prominent) 5.dp else 2.dp),
        contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = if (prominent) 24.sp else 14.sp,
            lineHeight = if (prominent) 29.sp else 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun ParkingLegend() {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
        .padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        for ((label, level, description) in listOf(
            Triple("P 12", ParkingLevel.AVAILABLE, R.string.news_free),
            Triple("P 3", ParkingLevel.LOW, R.string.news_low),
            Triple("P 0", ParkingLevel.FULL, R.string.parking_full),
            Triple("P", ParkingLevel.UNKNOWN, R.string.news_no_count))) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.widthIn(min = 56.dp), contentAlignment = Alignment.Center) { ParkingBadge(label, level) }
                Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
