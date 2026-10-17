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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDismissState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.cristianku.nav2pilot.data.Favorite

// openpilot primary button blue, same as the theme's light primary
private val SendBlue = Color(0xFF465BEA)

@Composable
fun FavoritesScreen(
    favorites: List<Favorite>,
    canSend: Boolean,
    onSelect: (Favorite) -> Unit,
    onSend: (Favorite) -> Unit,
    onRemove: (Favorite) -> Unit,
) {
    if (favorites.isEmpty()) {
        Column(Modifier.padding(16.dp)) {
            Text("No favorites yet.", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap the heart on a destination to keep it here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(favorites, key = { favorite -> favorite.id }) { favorite ->
            FavoriteItem(
                favorite = favorite,
                canSend = canSend,
                onSelect = onSelect,
                onSend = onSend,
                onRemove = onRemove,
            )
        }
    }
}

/** Swipe right-to-left removes the favorite; the button on the right sends it to the comma. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoriteItem(
    favorite: Favorite,
    canSend: Boolean,
    onSelect: (Favorite) -> Unit,
    onSend: (Favorite) -> Unit,
    onRemove: (Favorite) -> Unit,
) {
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

    SwipeToDismiss(
        state = dismissState,
        directions = setOf(DismissDirection.EndToStart),
        background = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer, CardDefaults.shape)
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
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(favorite) },
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Place, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(favorite.destination.displayName(), style = MaterialTheme.typography.bodyLarge)
                        if (favorite.destination.details.isNotEmpty()) {
                            Text(
                                favorite.destination.details,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            favorite.destination.coordinatesText(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = { onSend(favorite) },
                        enabled = canSend,
                        shape = RoundedCornerShape(10.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = SendBlue,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.padding(end = 8.dp).size(48.dp),
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "Send to comma")
                    }
                }
            }
        },
    )
}
