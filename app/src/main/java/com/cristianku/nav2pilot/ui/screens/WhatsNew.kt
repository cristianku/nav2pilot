package com.cristianku.nav2pilot.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cristianku.nav2pilot.R
import kotlinx.coroutines.launch

// Change this id with the slides, not on every build of the same release.
internal const val NEWS_EDITION = "places-and-parking-1"

private data class Slide(val label: Int, val title: Int, val body: Int)
private val slides = listOf(
    Slide(R.string.news_explore_label, R.string.news_explore_title, R.string.news_explore_body),
    Slide(R.string.news_parking_label, R.string.news_parking_title, R.string.news_parking_body),
    Slide(R.string.news_search_label, R.string.news_search_title, R.string.news_search_body),
)

@Composable
internal fun WhatsNew(onClose: () -> Unit) {
    val pager = rememberPagerState { slides.size }
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onClose)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.safeDrawingPadding().fillMaxSize(), contentAlignment = Alignment.Center) {
            val short = maxHeight < 480.dp
            Column(Modifier.widthIn(max = 540.dp).fillMaxSize().padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.news_name), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onClose) { Text(stringResource(R.string.news_skip)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    slides.indices.forEach { i ->
                        Spacer(Modifier.weight(1f).height(4.dp).background(
                            if (i <= pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape))
                    }
                }
                HorizontalPager(state = pager, modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { index ->
                    val slide = slides[index]
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        NewsArt(index, Modifier.fillMaxWidth().height(if (short) 100.dp else 180.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(slide.label), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.news_counter, index + 1, slides.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(stringResource(slide.title), Modifier.semantics { heading() },
                            style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(slide.body), style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (pager.currentPage > 0) TextButton(onClick = {
                        scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }
                    }) { Text(stringResource(R.string.news_back)) }
                    Spacer(Modifier.weight(1f))
                    val last = pager.currentPage == slides.lastIndex
                    Button(onClick = {
                        if (last) onClose() else scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(slides.lastIndex)) }
                    }, modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 120.dp)) {
                        Text(stringResource(if (last) R.string.news_done else R.string.news_next))
                    }
                }
            }
        }
    }
}

@Composable
private fun NewsArt(index: Int, modifier: Modifier) {
    val ink = Color(0xFF061D35)
    val accent = listOf(Color(0xFF65E7E3), Color(0xFF86EDD0), Color(0xFFA7CFFF))[index]
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(ink, Color(0xFF0D495A))))
        .clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            for (i in -1..6) drawLine(Color.White.copy(alpha = 0.10f), Offset(w * i / 5, 0f),
                Offset(w * (i - 1) / 5, h), 2.dp.toPx())
            for (i in 0..4) drawLine(Color.White.copy(alpha = 0.10f), Offset(0f, h * i / 3),
                Offset(w, h * (i - 0.55f) / 3), 2.dp.toPx())
            val road = Path().apply {
                moveTo(0f, h * 0.8f); lineTo(w * 0.20f, h * 0.72f); lineTo(w * 0.32f, h * 0.37f)
                lineTo(w * 0.70f, h * 0.37f); lineTo(w * 0.82f, h * 0.64f); lineTo(w, h * 0.52f)
            }
            drawPath(road, accent.copy(alpha = 0.56f), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
            drawCircle(accent, 5.dp.toPx(), Offset(w * 0.82f, h * 0.64f))
        }
        Row(Modifier.padding(12.dp).background(ink, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
                if (index == 1) Text("P", color = ink, fontWeight = FontWeight.Bold)
                else Icon(if (index == 0) Icons.Filled.Place else Icons.Filled.Favorite, null, tint = ink, modifier = Modifier.size(20.dp))
            }
            Text(when (index) { 0 -> "Rebgasse"; 1 -> stringResource(R.string.news_free); else -> stringResource(R.string.news_shortcuts) },
                color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        if (index == 1) Text(stringResource(R.string.news_example), Modifier.align(Alignment.BottomEnd).padding(10.dp),
            color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}
