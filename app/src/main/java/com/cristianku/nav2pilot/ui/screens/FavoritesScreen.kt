package com.cristianku.nav2pilot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.cristianku.nav2pilot.R
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.places.ParkingAvailability
import com.cristianku.nav2pilot.places.ParkingReading
import com.cristianku.nav2pilot.places.stateAt
import kotlinx.coroutines.delay
import java.time.Instant
import com.cristianku.nav2pilot.data.Favorite
import com.cristianku.nav2pilot.model.PairedDevice
import com.cristianku.nav2pilot.ui.components.BrandCard
import com.cristianku.nav2pilot.ui.components.EmptyState
import com.cristianku.nav2pilot.ui.components.IconBadge
import com.cristianku.nav2pilot.ui.components.ScreenPadding

@Composable
fun FavoritesScreen(
    favorites: List<Favorite>,
    mapReadings: List<ParkingReading>,
    lookupParking: suspend (Destination) -> ParkingReading?,
    selectedDevice: PairedDevice?,
    sending: Boolean,
    onSelect: (Favorite) -> Unit,
    onSend: (Favorite) -> Unit,
    onRemove: (Favorite) -> Unit,
) {
    if (favorites.isEmpty()) {
        // as tall as the message: an empty sheet over the whole map says nothing more
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, top = 16.dp, end = 32.dp, bottom = 40.dp),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                painter = rememberVectorPainter(Icons.Filled.Favorite),
                title = "Your places, one tap away",
                message = "Tap the heart on a destination to keep it here.",
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = ScreenPadding, top = 8.dp, end = ScreenPadding, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(favorites, key = { favorite -> favorite.id }) { favorite ->
            FavoriteItem(
                favorite = favorite,
                mapReading = remember(favorite.destination, mapReadings) {
                    ParkingAvailability.matchFavorite(favorite.destination, mapReadings)
                },
                lookupParking = lookupParking,
                selectedDevice = selectedDevice,
                sending = sending,
                onSelect = onSelect,
                onSend = onSend,
                onRemove = onRemove,
            )
        }
    }
}

/** Swipe right-to-left (or the menu) removes the favorite; the button on the right sends it to the comma. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoriteItem(
    favorite: Favorite,
    mapReading: ParkingReading?,
    lookupParking: suspend (Destination) -> ParkingReading?,
    selectedDevice: PairedDevice?,
    sending: Boolean,
    onSelect: (Favorite) -> Unit,
    onSend: (Favorite) -> Unit,
    onRemove: (Favorite) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState()

    val sendDescription = "Send " + favorite.destination.displayName() + " to comma"

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove(favorite)
            }
        },
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.large)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remove favorite",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        content = {
            BrandCard(modifier = Modifier.clickable { onSelect(favorite) }) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(
                        painter = if (favorite.destination.isParking) painterResource(R.drawable.ic_place_parking)
                            else rememberVectorPainter(Icons.Filled.Place),
                        tint = MaterialTheme.colorScheme.secondary,
                        background = MaterialTheme.colorScheme.secondaryContainer,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            favorite.destination.displayName(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (favorite.destination.isParking) {
                            FavoriteAvailability(favorite.destination, mapReading, lookupParking)
                        }
                        if (favorite.destination.details.isNotEmpty()) {
                            Text(
                                favorite.destination.details,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { onSend(favorite) },
                        enabled = selectedDevice != null && !sending,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = sendDescription, modifier = Modifier.size(20.dp))
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "More actions",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Remove from favorites") },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onRemove(favorite)
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

/** Only visible parking favorites refresh. A reading already on the map is usable on the very first frame. */
@Composable
private fun FavoriteAvailability(place: Destination, initial: ParkingReading?, lookup: suspend (Destination) -> ParkingReading?) {
    var reading by remember(place, initial) { mutableStateOf(initial) }
    var now by remember(place) { mutableStateOf(Instant.now()) }
    var loading by remember(place, initial) { mutableStateOf(initial?.isFresh(now) != true) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(place, initial, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = Instant.now()
                reading = lookup(place)
                now = Instant.now()
                loading = false
                delay(60_000)
            }
        }
    }
    if (loading) Text(stringResource(R.string.parking_checking), style = MaterialTheme.typography.bodySmall)
    else ParkingStatusLabel(reading.stateAt(now))
}
