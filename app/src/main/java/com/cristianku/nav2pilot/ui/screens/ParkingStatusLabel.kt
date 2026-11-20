package com.cristianku.nav2pilot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cristianku.nav2pilot.R
import com.cristianku.nav2pilot.places.ParkingLevel
import com.cristianku.nav2pilot.places.ParkingState
import com.cristianku.nav2pilot.ui.theme.pinColor

@Composable
internal fun ParkingStatusLabel(state: ParkingState) {
    val text = when (state.level) {
        ParkingLevel.AVAILABLE -> stringResource(R.string.parking_free, requireNotNull(state.free))
        ParkingLevel.LOW -> stringResource(R.string.parking_low, requireNotNull(state.free))
        ParkingLevel.FULL -> stringResource(R.string.parking_full)
        ParkingLevel.CLOSED -> stringResource(R.string.parking_closed)
        ParkingLevel.UNKNOWN -> stringResource(R.string.parking_unknown)
    }
    Text(text, Modifier.background(Color(state.level.pinColor), RoundedCornerShape(8.dp))
        .padding(horizontal = 9.dp, vertical = 4.dp), color = Color.White,
        fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
}
