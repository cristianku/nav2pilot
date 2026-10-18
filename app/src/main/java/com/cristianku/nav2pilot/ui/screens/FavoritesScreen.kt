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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cristianku.nav2pilot.data.Favorite
import com.cristianku.nav2pilot.model.PairedDevice
import com.cristianku.nav2pilot.ui.components.BrandCard
import com.cristianku.nav2pilot.ui.components.EmptyState
import com.cristianku.nav2pilot.ui.components.IconBadge
import com.cristianku.nav2pilot.ui.components.ScreenPadding

@Composable
fun FavoritesScreen(
    favorites: List<Favorite>,
    selectedDevice: PairedDevice?,
    sending: Boolean,
    onSelect: (Favorite) -> Unit,
    onSend: (Favorite) -> Unit,
    onRemove: (Favorite) -> Unit,
) {
    if (favorites.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                painter = rememberVectorPainter(Icons.Filled.Favorite),
                title = "Your places, one tap away",
                message = "Tap the heart on a destination to keep it here.",
                modifier = Modifier.padding(bottom = 48.dp),
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
    selectedDevice: PairedDevice?,
    sending: Boolean,
    onSelect: (Favorite) -> Unit,
    onSend: (Favorite) -> Unit,
    onRemove: (Favorite) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dismissState = rememberDismissState(
        confirmValueChange = { value ->
            if (value == DismissValue.DismissedToStart) {
                onRemove(favorite)
                true
            } else {
                false
            }
        },
    )

    val sendDescription = "Send " + favorite.destination.displayName() + " to comma"

    SwipeToDismiss(
        state = dismissState,
        directions = setOf(DismissDirection.EndToStart),
        background = {
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
        dismissContent = {
            BrandCard(modifier = Modifier.clickable { onSelect(favorite) }) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(
                        painter = rememberVectorPainter(Icons.Filled.Place),
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
                        Icon(Icons.Filled.Send, contentDescription = sendDescription, modifier = Modifier.size(20.dp))
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
