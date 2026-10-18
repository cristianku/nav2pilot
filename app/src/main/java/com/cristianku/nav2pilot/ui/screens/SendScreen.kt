package com.cristianku.nav2pilot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cristianku.nav2pilot.R
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.PairedDevice
import com.cristianku.nav2pilot.ui.ConnectionState
import com.cristianku.nav2pilot.ui.Formatting
import com.cristianku.nav2pilot.ui.MainUiState
import com.cristianku.nav2pilot.ui.MapIntents
import com.cristianku.nav2pilot.ui.SendFeedback
import com.cristianku.nav2pilot.ui.components.BrandCard
import com.cristianku.nav2pilot.ui.components.EmptyState
import com.cristianku.nav2pilot.ui.components.IconBadge
import com.cristianku.nav2pilot.ui.components.ScreenPadding
import com.cristianku.nav2pilot.ui.components.SectionLabel
import com.cristianku.nav2pilot.ui.theme.StatusColors

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
    routeActions: RouteActions,
    onSaveMapboxToken: (String) -> Unit,
    onHideMapboxDialog: () -> Unit,
    wide: Boolean = false,
) {
    var selectedDevice: PairedDevice? = null
    for (device in devices) {
        if (device.dongleId == selectedDongleId) {
            selectedDevice = device
        }
    }

    val destinationBlock: @Composable () -> Unit = {
        Column {
            SectionLabel("Destination")
            DestinationCard(
                destination = state.destination,
                source = state.destinationSource,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                onClear = onClearSelection,
            )
        }
    }
    val deviceBlock: @Composable () -> Unit = {
        Column {
            SectionLabel("Device")
            DeviceCard(
                devices = devices,
                selectedDevice = selectedDevice,
                connection = state.connection,
                hasToken = state.hasToken,
                sending = state.sending,
                onSelectDevice = onSelectDevice,
                onCheckConnection = onCheckConnection,
                onClearOnDevice = onClearOnDevice,
                onOpenDevices = onOpenDevices,
            )
            Text(
                "The device must be online. It keeps the destination until the car goes offroad or the device restarts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
            )
        }
    }

    if (wide) {
        // landscape (car screens, tablets, a phone on its side): the form on the left, the map on the right
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = ScreenPadding, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    SearchSection(state = state, onSearch = onSearch)
                    if (state.choices.isNotEmpty()) {
                        ChoicesCard(choices = state.choices, onChoose = onChooseCandidate)
                    }
                    destinationBlock()
                    deviceBlock()
                }
                SendBar(
                    state = state,
                    devices = devices,
                    selectedDevice = selectedDevice,
                    onSend = onSend,
                )
            }
            BoxWithConstraints(
                modifier = Modifier
                    .weight(WIDE_ROUTE_PANE_WEIGHT)
                    .fillMaxHeight(),
            ) {
                // the map fills the visible pane; the route list scrolls below it
                var mapHeight = maxHeight - 56.dp
                if (mapHeight < 200.dp) {
                    mapHeight = 200.dp
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(end = ScreenPadding, top = 8.dp, bottom = 8.dp),
                ) {
                    if (state.destination == null || !state.routePlanningAvailable) {
                        SectionLabel("Route")
                        BrandCard {
                            EmptyState(
                                painter = rememberVectorPainter(Icons.Filled.Place),
                                title = "No route yet",
                                message = "Search or share a place: its routes show up here.",
                                modifier = Modifier.padding(vertical = 32.dp, horizontal = 16.dp),
                            )
                        }
                    } else {
                        RouteSection(state = state, actions = routeActions, mapHeight = mapHeight)
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ScreenPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                SearchSection(state = state, onSearch = onSearch)
                if (state.choices.isNotEmpty()) {
                    ChoicesCard(choices = state.choices, onChoose = onChooseCandidate)
                }
                destinationBlock()
                RouteSection(state = state, actions = routeActions)
                deviceBlock()
            }

            SendBar(
                state = state,
                devices = devices,
                selectedDevice = selectedDevice,
                onSend = onSend,
            )
        }
    }

    // the route section offers the Mapbox setup too
    if (state.mapboxDialogVisible) {
        MapboxTokenDialog(
            busy = state.mapboxBusy,
            message = state.mapboxMessage,
            onSave = onSaveMapboxToken,
            onDismiss = onHideMapboxDialog,
        )
    }
}

// the route pane is a bit wider than the form: the map is what a landscape screen is for
private const val WIDE_ROUTE_PANE_WEIGHT = 1.3f

/** The send button and its result, kept above the tab bar while the content scrolls. */
@Composable
private fun SendBar(
    state: MainUiState,
    devices: List<PairedDevice>,
    selectedDevice: PairedDevice?,
    onSend: () -> Unit,
) {
    val sendLabel = "Send to comma"
    val canSend = state.destination != null && selectedDevice != null && !state.sending

    // what is missing, next to the button that needs it
    var missing = ""
    if (!canSend && !state.sending) {
        if (!state.hasToken && devices.isEmpty()) {
            missing = "Connect your comma account in Devices to send."
        } else if (selectedDevice == null) {
            missing = "Select a device to send to."
        } else if (state.destination == null) {
            missing = "Search or share a place to send it."
        }
    }

    Surface(color = MaterialTheme.colorScheme.background) {
        Column {
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onSend,
                    enabled = canSend,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    if (state.sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        sendLabel,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val feedback = state.feedback
                if (feedback != null) {
                    FeedbackRow(feedback = feedback)
                } else if (missing.isNotEmpty()) {
                    Text(
                        missing,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackRow(feedback: SendFeedback) {
    val color: Color
    if (feedback.success) {
        color = StatusColors.success()
    } else {
        color = MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (feedback.success) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        } else {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            feedback.message + " · " + Formatting.time(feedback.timeMillis),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun SearchSection(state: MainUiState, onSearch: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val clipboard = LocalClipboardManager.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Where to?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { text -> query = text },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search a place or paste a link") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.resolving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else if (query.isNotEmpty()) {
                    Row {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                onSearch(query)
                            },
                            enabled = query.isNotBlank(),
                        ) {
                            Icon(
                                Icons.Filled.ArrowForward,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Address, coordinates (lat, lng) or map link",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )
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
                Text("Paste link")
            }
        }
        if (!state.geocoderAvailable) {
            Text(
                "Address search is unavailable on this phone. Coordinates and map links still work.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        if (state.resolveError.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    state.resolveError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ChoicesCard(choices: List<Destination>, onChoose: (Destination) -> Unit) {
    Column {
        SectionLabel("Choose a result")
        BrandCard {
            for (index in choices.indices) {
                val choice = choices[index]
                if (index > 0) {
                    Divider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 68.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChoose(choice) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(
                        painter = rememberVectorPainter(Icons.Filled.Place),
                        tint = MaterialTheme.colorScheme.secondary,
                        background = MaterialTheme.colorScheme.secondaryContainer,
                        size = 36.dp,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(choice.displayName(), style = MaterialTheme.typography.bodyLarge)
                        // the coordinates only when nothing else tells the places apart
                        var secondLine = choice.details
                        if (secondLine.isEmpty() && choice.displayName() != choice.coordinatesText()) {
                            secondLine = choice.coordinatesText()
                        }
                        if (secondLine.isNotEmpty()) {
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
    BrandCard {
        if (destination == null) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(
                    painter = rememberVectorPainter(Icons.Filled.Place),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    background = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("No destination yet", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Search above, or share a place from Google Maps.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
    // a new destination starts with the details folded
    var detailsVisible by rememberSaveable(destination.latitude, destination.longitude) { mutableStateOf(false) }
    val hasName = destination.displayName() != destination.coordinatesText()

    Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 4.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            IconBadge(
                painter = rememberVectorPainter(Icons.Filled.Place),
                tint = MaterialTheme.colorScheme.secondary,
                background = MaterialTheme.colorScheme.secondaryContainer,
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    destination.displayName(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (destination.details.isNotEmpty()) {
                    Text(
                        destination.details,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onToggleFavorite) {
                if (isFavorite) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = "Remove from favorites",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Icon(
                        Icons.Filled.FavoriteBorder,
                        contentDescription = "Add to favorites",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Filled.Clear,
                    contentDescription = "Clear destination",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // needed to decide whether to send it, so never folded away
        if (destination.approximate) {
            Row(
                modifier = Modifier.padding(start = 56.dp, top = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = StatusColors.warning(),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Approximate: the link only had the map centre. Check it on the map.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusColors.warning(),
                )
            }
        }

        if (detailsVisible) {
            Column(
                modifier = Modifier.padding(start = 56.dp, top = 10.dp, end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (hasName) {
                    Text(
                        destination.coordinatesText(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (source.isNotEmpty()) {
                    Text(
                        "From: $source",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.padding(start = 44.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { MapIntents.showOnMap(context, destination) }) {
                Text("Show on map")
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { detailsVisible = !detailsVisible }) {
                if (detailsVisible) {
                    Text("Hide details")
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(18.dp))
                } else {
                    Text("Details")
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    devices: List<PairedDevice>,
    selectedDevice: PairedDevice?,
    connection: ConnectionState,
    hasToken: Boolean,
    sending: Boolean,
    onSelectDevice: (String) -> Unit,
    onCheckConnection: () -> Unit,
    onClearOnDevice: () -> Unit,
    onOpenDevices: () -> Unit,
) {
    BrandCard {
        if (!hasToken || devices.isEmpty()) {
            var title = "No devices yet"
            var hint = "Refresh the list in Devices."
            if (!hasToken) {
                title = "Connect your comma account"
                hint = "Add your comma token to list your devices."
            }
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(
                    painter = painterResource(R.drawable.ic_car),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    background = MaterialTheme.colorScheme.surfaceVariant,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FilledTonalButton(
                onClick = onOpenDevices,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(start = 72.dp, bottom = 16.dp),
            ) {
                Text("Open Devices")
            }
        }

        // No early return@Column here: with Compose compiler 1.4.0 it corrupts the composer stack and crashes.
        if (devices.isNotEmpty()) {
            DevicePicker(
                devices = devices,
                selectedDevice = selectedDevice,
                connection = connection,
                sending = sending,
                onSelectDevice = onSelectDevice,
                onCheckConnection = onCheckConnection,
                onClearOnDevice = onClearOnDevice,
            )
        }
    }
}

@Composable
private fun DevicePicker(
    devices: List<PairedDevice>,
    selectedDevice: PairedDevice?,
    connection: ConnectionState,
    sending: Boolean,
    onSelectDevice: (String) -> Unit,
    onCheckConnection: () -> Unit,
    onClearOnDevice: () -> Unit,
) {
    var pickerExpanded by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { pickerExpanded = true }
                    .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(
                    painter = painterResource(R.drawable.ic_car),
                    tint = MaterialTheme.colorScheme.primary,
                    background = MaterialTheme.colorScheme.primaryContainer,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    var title = "Select a device"
                    if (selectedDevice != null) {
                        title = Formatting.carName(selectedDevice)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose device")
                    }
                    if (selectedDevice != null) {
                        ConnectionLine(connection = connection)
                    }
                }
            }
            DropdownMenu(expanded = pickerExpanded, onDismissRequest = { pickerExpanded = false }) {
                for (device in devices) {
                    DropdownMenuItem(
                        text = {
                            Text(Formatting.carName(device))
                        },
                        onClick = {
                            pickerExpanded = false
                            onSelectDevice(device.dongleId)
                        },
                    )
                }
            }
        }
        if (connection is ConnectionState.Checking) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        } else {
            IconButton(onClick = onCheckConnection, enabled = selectedDevice != null) {
                Icon(Icons.Filled.Refresh, contentDescription = "Check connection")
            }
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "More device actions")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Clear destination on device") },
                    enabled = selectedDevice != null && !sending,
                    onClick = {
                        menuExpanded = false
                        onClearOnDevice()
                    },
                )
            }
        }
    }
}

/** The state of the last check only: an old ping is no proof the device is reachable now. */
@Composable
private fun ConnectionLine(connection: ConnectionState) {
    val dotColor: Color
    val text: String
    if (connection is ConnectionState.Online) {
        dotColor = StatusColors.success()
        text = "Online"
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

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(dotColor, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
