package com.cristianku.nav2pilot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.PairedDevice
import com.cristianku.nav2pilot.ui.ConnectionState
import com.cristianku.nav2pilot.ui.Formatting
import com.cristianku.nav2pilot.ui.MainUiState
import com.cristianku.nav2pilot.ui.MapIntents

@Composable
fun SendScreen(
    state: MainUiState,
    devices: List<PairedDevice>,
    selectedDongleId: String,
    isFavorite: Boolean,
    onSearch: (String) -> Unit,
    onChooseCandidate: (Destination) -> Unit,
    onClearSelection: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onCheckConnection: () -> Unit,
    onSend: () -> Unit,
    onClearOnDevice: () -> Unit,
    onOpenDevices: () -> Unit,
) {
    var selectedDevice: PairedDevice? = null
    for (device in devices) {
        if (device.dongleId == selectedDongleId) {
            selectedDevice = device
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SearchSection(state = state, onSearch = onSearch)

        if (state.choices.isNotEmpty()) {
            ChoicesCard(choices = state.choices, onChoose = onChooseCandidate)
        }

        DestinationCard(
            destination = state.destination,
            source = state.destinationSource,
            isFavorite = isFavorite,
            onToggleFavorite = onToggleFavorite,
            onClear = onClearSelection,
        )

        DeviceSection(
            devices = devices,
            selectedDevice = selectedDevice,
            connection = state.connection,
            hasToken = state.hasToken,
            onSelectDevice = onSelectDevice,
            onCheckConnection = onCheckConnection,
            onOpenDevices = onOpenDevices,
        )

        var sendLabel = "Send destination"
        if (selectedDevice != null) {
            sendLabel = "Send to " + selectedDevice.displayName()
        }
        val canSend = state.destination != null && selectedDevice != null && !state.sending
        Button(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.sending) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Send, contentDescription = null)
            }
            Spacer(Modifier.width(8.dp))
            Text(sendLabel)
        }
        OutlinedButton(
            onClick = onClearOnDevice,
            enabled = selectedDevice != null && !state.sending,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Clear destination on device")
        }

        val feedback = state.feedback
        if (feedback != null) {
            val containerColor: Color
            if (feedback.success) {
                containerColor = MaterialTheme.colorScheme.primaryContainer
            } else {
                containerColor = MaterialTheme.colorScheme.errorContainer
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = containerColor),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(feedback.message, style = MaterialTheme.typography.bodyLarge)
                    Text(Formatting.time(feedback.timeMillis), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Text(
            "The device must be online. It keeps the destination until the car goes offroad or the device restarts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchSection(state: MainUiState, onSearch: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { text -> query = text },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Address, lat, lng or map link") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.resolving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus()
                    onSearch(query)
                },
            ),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    focusManager.clearFocus()
                    onSearch(query)
                },
                enabled = query.isNotBlank() && !state.resolving,
            ) {
                Text("Search")
            }
            TextButton(
                onClick = {
                    val clip = clipboard.getText()
                    if (clip != null && clip.text.isNotBlank()) {
                        query = clip.text
                        onSearch(clip.text)
                    }
                },
                enabled = !state.resolving,
            ) {
                Text("Paste and search")
            }
        }
        if (!state.geocoderAvailable) {
            Text(
                "Address search is unavailable on this phone. Coordinates and map links still work.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.resolveError.isNotEmpty()) {
            Text(
                state.resolveError,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ChoicesCard(choices: List<Destination>, onChoose: (Destination) -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                "Choose a result",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
            )
            for (index in choices.indices) {
                val choice = choices[index]
                if (index > 0) {
                    Divider()
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChoose(choice) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Text(choice.displayName(), style = MaterialTheme.typography.bodyLarge)
                    var secondLine = choice.coordinatesText()
                    if (choice.details.isNotEmpty()) {
                        secondLine = choice.details + " · " + secondLine
                    }
                    Text(
                        secondLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DestinationCard(
    destination: Destination?,
    source: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClear: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        if (destination == null) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Place, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(
                    "No destination yet. Share a place from Google Maps, or search above.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            DestinationDetails(
                destination = destination,
                source = source,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onClear = onClear,
            )
        }
    }
}

@Composable
private fun DestinationDetails(
    destination: Destination,
    source: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClear: () -> Unit,
) {
    val context = LocalContext.current
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                destination.displayName(),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onToggleFavorite) {
                if (isFavorite) {
                    Icon(Icons.Filled.Favorite, contentDescription = "Remove from favorites")
                } else {
                    Icon(Icons.Filled.FavoriteBorder, contentDescription = "Add to favorites")
                }
            }
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Clear, contentDescription = "Clear destination")
            }
        }
        if (destination.details.isNotEmpty()) {
            Text(destination.details, style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            destination.coordinatesText(),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
        if (source.isNotEmpty()) {
            Text(
                "From: $source",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (destination.approximate) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Approximate: the link only had the map centre. Check it on the map.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        TextButton(onClick = { MapIntents.showOnMap(context, destination) }) {
            Text("Show on map")
        }
    }
}

@Composable
private fun DeviceSection(
    devices: List<PairedDevice>,
    selectedDevice: PairedDevice?,
    connection: ConnectionState,
    hasToken: Boolean,
    onSelectDevice: (String) -> Unit,
    onCheckConnection: () -> Unit,
    onOpenDevices: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Device", style = MaterialTheme.typography.titleMedium)

        if (!hasToken || devices.isEmpty()) {
            var hint = "No devices yet."
            if (!hasToken) {
                hint = "Add your comma token to list your devices."
            }
            Text(hint, style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onOpenDevices) {
                Text("Open Devices")
            }
        }

        // No early return@Column here: with Compose compiler 1.4.0 it corrupts the composer stack and crashes.
        if (devices.isNotEmpty()) {
            DevicePicker(devices = devices, selectedDevice = selectedDevice, onSelectDevice = onSelectDevice)

            Row(verticalAlignment = Alignment.CenterVertically) {
                ConnectionIndicator(connection = connection, modifier = Modifier.weight(1f))
                IconButton(onClick = onCheckConnection, enabled = connection !is ConnectionState.Checking) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Check connection")
                }
            }
        }
    }
}

@Composable
private fun DevicePicker(
    devices: List<PairedDevice>,
    selectedDevice: PairedDevice?,
    onSelectDevice: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    var title = "Select a device"
                    var subtitle = ""
                    if (selectedDevice != null) {
                        title = selectedDevice.displayName()
                        subtitle = selectedDevice.dongleId
                    }
                    Text(title, style = MaterialTheme.typography.bodyLarge)
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose device")
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (device in devices) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(device.displayName())
                            Text(
                                device.dongleId,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelectDevice(device.dongleId)
                    },
                )
            }
        }
    }
}

@Composable
private fun ConnectionIndicator(connection: ConnectionState, modifier: Modifier = Modifier) {
    val dotColor: Color
    val text: String
    if (connection is ConnectionState.Online) {
        dotColor = Color(0xFF2E7D32)
        var description = "Online"
        val status = connection.status
        if (status.version.isNotEmpty()) {
            description = description + " · " + status.version
        }
        if (status.branch.isNotEmpty()) {
            description = description + " (" + status.branch + ")"
        }
        text = description
    } else if (connection is ConnectionState.Checking) {
        dotColor = MaterialTheme.colorScheme.outline
        text = "Checking connection…"
    } else if (connection is ConnectionState.Unreachable) {
        dotColor = MaterialTheme.colorScheme.error
        text = connection.message
    } else {
        dotColor = MaterialTheme.colorScheme.outline
        text = "Connection not checked"
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(dotColor, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
