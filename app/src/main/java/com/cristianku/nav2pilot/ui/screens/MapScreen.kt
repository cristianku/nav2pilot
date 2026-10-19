package com.cristianku.nav2pilot.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cristianku.nav2pilot.R
import com.cristianku.nav2pilot.mapbox.RoutePreview
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.PairedDevice
import com.cristianku.nav2pilot.ui.ConnectionState
import com.cristianku.nav2pilot.ui.Formatting
import com.cristianku.nav2pilot.ui.MainUiState
import com.cristianku.nav2pilot.ui.MapIntents
import com.cristianku.nav2pilot.ui.SendFeedback
import com.cristianku.nav2pilot.ui.components.readClipboardText
import com.cristianku.nav2pilot.ui.map.MapColors
import com.cristianku.nav2pilot.ui.map.MapInsets
import com.cristianku.nav2pilot.ui.map.NavMap
import com.cristianku.nav2pilot.ui.map.STOP_COLOR
import com.cristianku.nav2pilot.ui.theme.StatusColors
import kotlinx.coroutines.launch
import java.util.Locale

/** What the map screen does, wired to the view model by the root. */
class MapActions(
    val onSearch: (String) -> Unit,
    val onChooseCandidate: (Destination) -> Unit,
    val onDismissChoices: () -> Unit,
    val onClearSelection: () -> Unit,
    val onToggleFavorite: () -> Unit,
    val onSelectDevice: (String) -> Unit,
    val onCheckConnection: () -> Unit,
    val onSend: () -> Unit,
    val onClearOnDevice: () -> Unit,
    val onPickOnMap: (Double, Double) -> Unit,
    val onAddStop: (Double, Double) -> Unit,
    val onRemoveStop: (Int) -> Unit,
    val onSelectRoute: (Int) -> Unit,
    val onRefreshRoutes: () -> Unit,
    val onRefreshMyLocation: (Boolean) -> Unit,
    val onLocationPermissionResult: (Boolean) -> Unit,
    val onShowMapboxDialog: () -> Unit,
    val onSaveMapboxToken: (String) -> Unit,
    val onHideMapboxDialog: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenFavorites: () -> Unit,
)

/**
 * The only screen: the map fills it. Search, car, settings and favorites float on top; the destination panel
 * comes up from the bottom (phone) or sits in a column on the left (landscape: the driver's side in
 * left-hand-drive cars) once there is a destination.
 */
@Composable
fun MapScreen(
    state: MainUiState,
    devices: List<PairedDevice>,
    selectedDevice: PairedDevice?,
    isFavorite: Boolean,
    wide: Boolean,
    actions: MapActions,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val darkTheme = isSystemInDarkTheme()

    // the search pill: always there without a destination; with one, a tap on the map shows or hides it
    var searchShown by rememberSaveable { mutableStateOf(false) }
    var searchEditing by rememberSaveable { mutableStateOf(false) }
    var panelSizePx by remember { mutableIntStateOf(0) }

    val hasDestination = state.destination != null
    val searchVisible = !hasDestination || searchShown || searchEditing

    // a new destination closes the search
    LaunchedEffect(state.destination) {
        if (state.destination != null) {
            searchEditing = false
            searchShown = false
            focusManager.clearFocus()
        }
    }

    val requestLocation = locationPermission(state = state, actions = actions)

    val statusBarPx = WindowInsets.statusBars.getTop(density)
    val navigationBarPx = WindowInsets.navigationBars.getBottom(density)
    // with the search shown the car sits on a second row
    var topBarHeight = TOP_BAR_HEIGHT
    if (searchVisible && !searchEditing) {
        topBarHeight = TOP_BAR_HEIGHT + MAP_BUTTON_SIZE + 10.dp
    }
    val topBarPx = with(density) { topBarHeight.roundToPx() }
    val insets: MapInsets
    if (wide && hasDestination) {
        insets = MapInsets(left = panelSizePx, top = statusBarPx + topBarPx, right = 0, bottom = navigationBarPx)
    } else if (hasDestination) {
        insets = MapInsets(left = 0, top = statusBarPx + topBarPx, right = 0, bottom = panelSizePx)
    } else {
        insets = MapInsets(left = 0, top = statusBarPx + topBarPx, right = 0, bottom = navigationBarPx)
    }

    val mapColors = MapColors(
        selectedRoute = MaterialTheme.colorScheme.primary.toArgb(),
        otherRoute = UNPICKED_ROUTE_COLOR,
        myLocation = MY_LOCATION_COLOR,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        NavMap(
            state = state,
            darkTheme = darkTheme,
            insets = insets,
            colors = mapColors,
            onTap = {
                if (searchEditing) {
                    searchEditing = false
                    focusManager.clearFocus()
                    actions.onDismissChoices()
                } else if (hasDestination) {
                    searchShown = !searchShown
                }
            },
            onLongPress = { latitude, longitude ->
                if (state.destination == null) {
                    actions.onPickOnMap(latitude, longitude)
                } else {
                    actions.onAddStop(latitude, longitude)
                }
            },
            onSelectRoute = actions.onSelectRoute,
            modifier = Modifier.fillMaxSize(),
        )

        // the destination panel: a column on the left in landscape, a sheet at the bottom on a phone
        val destination = state.destination
        if (destination != null) {
            if (wide) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(WIDE_PANEL_WIDTH)
                        .onSizeChanged { size -> panelSizePx = size.width },
                ) {
                    DestinationPanel(
                        state = state,
                        destination = destination,
                        devices = devices,
                        selectedDevice = selectedDevice,
                        isFavorite = isFavorite,
                        wide = true,
                        actions = actions,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { size -> panelSizePx = size.height },
                ) {
                    DestinationPanel(
                        state = state,
                        destination = destination,
                        devices = devices,
                        selectedDevice = selectedDevice,
                        isFavorite = isFavorite,
                        wide = false,
                        actions = actions,
                    )
                }
            }
        }

        // top: the search and the settings; the car under the search (in its place when the search is hidden)
        var topRowStart = 0.dp
        if (wide && hasDestination) {
            topRowStart = WIDE_PANEL_WIDTH
        }
        val carButton: @Composable () -> Unit = {
            CarButton(
                devices = devices,
                selectedDevice = selectedDevice,
                connection = state.connection,
                hasToken = state.hasToken,
                sending = state.sending,
                actions = actions,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = topRowStart + 12.dp, end = 12.dp, top = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(modifier = Modifier.weight(1f)) {
                    if (searchVisible) {
                        SearchBox(
                            state = state,
                            editing = searchEditing,
                            onStartEditing = { searchEditing = true },
                            onStopEditing = {
                                searchEditing = false
                                focusManager.clearFocus()
                                actions.onDismissChoices()
                            },
                            actions = actions,
                        )
                    } else {
                        carButton()
                    }
                }
                // while typing the search gets the whole row, as in Google Maps
                if (!searchEditing) {
                    Spacer(Modifier.width(8.dp))
                    MapButton(
                        painter = rememberVectorPainter(Icons.Filled.Settings),
                        description = "Settings",
                        onClick = actions.onOpenSettings,
                    )
                }
            }
            if (searchVisible && !searchEditing) {
                Spacer(Modifier.height(10.dp))
                carButton()
            }
        }

        // favorites bottom left, recentre bottom right, just above the panel
        val bottomOffset: Dp
        if (hasDestination && !wide) {
            bottomOffset = with(density) { panelSizePx.toDp() } + 12.dp
        } else {
            bottomOffset = 16.dp
        }
        var favoritesStart = 12.dp
        if (wide && hasDestination) {
            favoritesStart = WIDE_PANEL_WIDTH + 12.dp
        }
        var buttonsModifier = Modifier.padding(bottom = bottomOffset)
        if (!(hasDestination && !wide)) {
            buttonsModifier = buttonsModifier.navigationBarsPadding()
        }
        MapButton(
            painter = rememberVectorPainter(Icons.Filled.Favorite),
            description = "Favorites",
            onClick = actions.onOpenFavorites,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .then(buttonsModifier)
                .padding(start = favoritesStart),
        )
        MapButton(
            painter = painterResource(R.drawable.ic_my_location),
            description = "Show my position",
            onClick = {
                if (hasLocationPermission(context)) {
                    actions.onRefreshMyLocation(true)
                } else {
                    requestLocation()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .then(buttonsModifier)
                .padding(end = 12.dp),
        )
    }

    // the panel and the settings offer the Mapbox setup
    if (state.mapboxDialogVisible) {
        MapboxTokenDialog(
            busy = state.mapboxBusy,
            message = state.mapboxMessage,
            onSave = actions.onSaveMapboxToken,
            onDismiss = actions.onHideMapboxDialog,
        )
    }
}

/**
 * The location permission: asked once at start (the map opens on the phone's position, as in Google Maps) and
 * when the route preview needs it; then the position is read again whenever the app comes back.
 * Returns what the recentre button calls without the permission: the dialog again, or the app settings once
 * Android no longer shows it.
 */
@Composable
private fun locationPermission(state: MainUiState, actions: MapActions): () -> Unit {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var asked by rememberSaveable { mutableStateOf(false) }
    var fromButton by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        var granted = false
        for (value in result.values) {
            if (value) {
                granted = true
            }
        }
        if (granted) {
            actions.onRefreshMyLocation(true)
        } else if (fromButton) {
            // "don't ask again": Android answers at once without a dialog, only the settings can change it
            openAppSettings(context)
        }
        fromButton = false
        actions.onLocationPermissionResult(granted)
    }
    val permissions = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

    LaunchedEffect(Unit) {
        if (hasLocationPermission(context)) {
            actions.onRefreshMyLocation(false)
        } else if (!asked) {
            asked = true
            launcher.launch(permissions)
        }
    }
    LaunchedEffect(state.locationPermissionNeeded) {
        if (state.locationPermissionNeeded && !asked) {
            asked = true
            launcher.launch(permissions)
        }
    }

    // back from the app settings, or from another app: the position may have changed
    val currentActions by rememberUpdatedState(actions)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && hasLocationPermission(context)) {
                currentActions.onRefreshMyLocation(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return {
        fromButton = true
        launcher.launch(permissions)
    }
}

/** Round floating button on the map, at least 48 dp for use in the car. */
@Composable
private fun MapButton(
    painter: Painter,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        shadowElevation = 6.dp,
        modifier = modifier.size(MAP_BUTTON_SIZE),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(painter, contentDescription = description, modifier = Modifier.size(24.dp))
        }
    }
}

/** The pill "Where to?"; tapped, it becomes the search field with the results under it. */
@Composable
private fun SearchBox(
    state: MainUiState,
    editing: Boolean,
    onStartEditing: () -> Unit,
    onStopEditing: () -> Unit,
    actions: MapActions,
) {
    if (!editing) {
        Surface(
            onClick = onStartEditing,
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = SEARCH_MAX_WIDTH)
                .height(MAP_BUTTON_SIZE),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                var text = "Where to?"
                if (state.resolving) {
                    text = "Looking it up…"
                }
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        return
    }

    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = SEARCH_MAX_WIDTH),
    ) {
        Column {
            Row(
                modifier = Modifier.height(MAP_BUTTON_SIZE),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onStopEditing) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                }
                TextField(
                    value = query,
                    onValueChange = { text -> query = text },
                    placeholder = { Text("Search or paste a link", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (query.isNotBlank()) {
                                actions.onSearch(query)
                            }
                        },
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                )
                if (state.resolving) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            }

            if (query.isEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(
                    onClick = {
                        scope.launch {
                            val clip = readClipboardText(clipboard)
                            if (clip != null && clip.isNotBlank()) {
                                query = clip
                                actions.onSearch(clip)
                            }
                        }
                    },
                    enabled = !state.resolving,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Text("Paste a map link or coordinates", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            if (!state.geocoderAvailable) {
                Text(
                    "Address search is unavailable on this phone. Coordinates and map links still work.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (state.resolveError.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(state.resolveError, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }

            if (state.choices.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(
                    modifier = Modifier
                        .heightIn(max = RESULTS_MAX_HEIGHT)
                        .verticalScroll(rememberScrollState()),
                ) {
                    for (choice in state.choices) {
                        ChoiceRow(choice = choice, onChoose = { actions.onChooseCandidate(choice) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(choice: Destination, onChoose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onChoose)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(choice.displayName(), style = MaterialTheme.typography.bodyLarge)
            // the coordinates only when nothing else tells the places apart
            var secondLine = choice.details
            if (secondLine.isEmpty() && choice.displayName() != choice.coordinatesText()) {
                secondLine = choice.coordinatesText()
            }
            if (secondLine.isNotEmpty()) {
                Text(secondLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The selected car with its online dot; a tap opens the car menu. */
@Composable
private fun CarButton(
    devices: List<PairedDevice>,
    selectedDevice: PairedDevice?,
    connection: ConnectionState,
    hasToken: Boolean,
    sending: Boolean,
    actions: MapActions,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = {
                if (!hasToken || devices.isEmpty()) {
                    actions.onOpenSettings()
                } else {
                    menuExpanded = true
                }
            },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            modifier = Modifier.height(MAP_BUTTON_SIZE),
        ) {
            Row(
                modifier = Modifier.padding(start = 14.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    Icon(
                        painterResource(R.drawable.ic_car),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    if (selectedDevice != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(10.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                .padding(2.dp)
                                .background(connectionColor(connection), CircleShape),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                var label = "Add your car"
                if (selectedDevice != null) {
                    label = Formatting.carName(selectedDevice)
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = CAR_LABEL_MAX_WIDTH),
                )
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            for (device in devices) {
                var name = Formatting.carName(device)
                if (selectedDevice != null && device.dongleId == selectedDevice.dongleId) {
                    name = "✓ " + name
                }
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        menuExpanded = false
                        actions.onSelectDevice(device.dongleId)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Check connection")
                        Text(
                            connectionText(connection),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                enabled = selectedDevice != null,
                onClick = {
                    menuExpanded = false
                    actions.onCheckConnection()
                },
            )
            DropdownMenuItem(
                text = { Text("Clear destination on the car") },
                enabled = selectedDevice != null && !sending,
                onClick = {
                    menuExpanded = false
                    actions.onClearOnDevice()
                },
            )
        }
    }
}

/** Destination, routes, stops and the send button. */
@Composable
private fun DestinationPanel(
    state: MainUiState,
    destination: Destination,
    devices: List<PairedDevice>,
    selectedDevice: PairedDevice?,
    isFavorite: Boolean,
    wide: Boolean,
    actions: MapActions,
) {
    val context = LocalContext.current
    var detailsVisible by rememberSaveable(destination.latitude, destination.longitude) { mutableStateOf(false) }

    var shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    var panelModifier = Modifier.fillMaxWidth()
    if (wide) {
        shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
        panelModifier = Modifier.fillMaxSize()
    }

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
        modifier = panelModifier,
    ) {
        var contentModifier = Modifier
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
        if (wide) {
            contentModifier = contentModifier
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
        } else {
            contentModifier = contentModifier
                .heightIn(max = PANEL_MAX_HEIGHT)
                .verticalScroll(rememberScrollState())
        }
        Column(
            modifier = contentModifier.padding(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // name, favorite, close
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 6.dp),
                ) {
                    Text(
                        destination.displayName(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (destination.details.isNotEmpty()) {
                        Text(
                            destination.details,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = actions.onToggleFavorite) {
                    if (isFavorite) {
                        Icon(Icons.Filled.Favorite, contentDescription = "Remove from favorites", tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Filled.FavoriteBorder, contentDescription = "Add to favorites", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = actions.onClearSelection) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear destination", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // needed to decide whether to send it, so never folded away
            if (destination.approximate) {
                NoticeRow(
                    text = "Approximate: the link only had the map centre. Check the pin on the map.",
                    color = StatusColors.warning(),
                )
            }

            RoutesBlock(state = state, actions = actions)

            if (state.stops.isNotEmpty()) {
                StopsRow(stops = state.stops, onRemove = actions.onRemoveStop)
            }

            // without comma prime the car only gets a route through the user's Mapbox token
            val needsMapbox = selectedDevice != null && selectedDevice.needsMapboxForRoutes() && !state.hasMapboxToken
            if (needsMapbox) {
                MapboxNotice(onSetUp = actions.onShowMapboxDialog)
            }

            SendButton(
                state = state,
                devices = devices,
                selectedDevice = selectedDevice,
                destinationOnly = needsMapbox,
                actions = actions,
            )

            val feedback = state.feedback
            if (feedback != null) {
                FeedbackRow(feedback = feedback)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { MapIntents.showOnMap(context, destination) }) {
                    Text("Open in maps")
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
            if (detailsVisible) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(destination.coordinatesText(), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    if (state.destinationSource.isNotEmpty()) {
                        Text(
                            "From: " + state.destinationSource,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "Long-press the map to add a stop. The car follows the route you pick.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The routes as chips ("28 min · 26 km"), or why there are none. */
@Composable
private fun RoutesBlock(state: MainUiState, actions: MapActions) {
    val context = LocalContext.current
    if (state.routesLoading) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Computing routes…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    if (state.routesMessage.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                state.routesMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            if (!hasLocationPermission(context)) {
                TextButton(onClick = { openAppSettings(context) }) {
                    Text("Allow")
                }
            } else {
                TextButton(onClick = actions.onRefreshRoutes) {
                    Text("Retry")
                }
            }
        }
        return
    }
    if (state.routes.isEmpty()) {
        return
    }
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (index in state.routes.indices) {
            RouteChip(
                route = state.routes[index],
                index = index,
                selected = index == state.selectedRouteIndex,
                onSelect = { actions.onSelectRoute(index) },
            )
        }
    }
}

@Composable
private fun RouteChip(route: RoutePreview, index: Int, selected: Boolean, onSelect: () -> Unit) {
    var label = RouteFormatting.duration(route.durationSeconds) + " · " + RouteFormatting.distance(route.distanceMeters)
    if (index == 0) {
        label = "Fastest · $label"
    }
    FilterChip(
        selected = selected,
        onClick = onSelect,
        label = { Text(label, maxLines = 1) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.height(40.dp),
    )
}

@Composable
private fun StopsRow(stops: List<Destination>, onRemove: (Int) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (index in stops.indices) {
            InputChip(
                selected = false,
                onClick = { onRemove(index) },
                label = { Text("Stop ${index + 1}") },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color(STOP_COLOR), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${index + 1}", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                trailingIcon = {
                    Icon(Icons.Filled.Close, contentDescription = "Remove stop ${index + 1}", modifier = Modifier.size(18.dp))
                },
            )
        }
    }
}

/** Shown only when the selected car has no comma prime and there is no Mapbox token. */
@Composable
private fun MapboxNotice(onSetUp: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "No comma prime and no Mapbox token: your car will stay on \"Waiting for route\". " +
                        "A free Mapbox account fixes it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TextButton(onClick = onSetUp, modifier = Modifier.padding(start = 20.dp)) {
                Text("Set up Mapbox")
            }
        }
    }
}

/** The big button; without a car it leads to the settings instead. */
@Composable
private fun SendButton(
    state: MainUiState,
    devices: List<PairedDevice>,
    selectedDevice: PairedDevice?,
    destinationOnly: Boolean,
    actions: MapActions,
) {
    if (!state.hasToken || devices.isEmpty() || selectedDevice == null) {
        var text = "Connect your comma account"
        if (state.hasToken) {
            text = "Choose your car"
        }
        Button(
            onClick = actions.onOpenSettings,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(SEND_BUTTON_HEIGHT),
        ) {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    Button(
        onClick = actions.onSend,
        enabled = !state.sending,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SEND_BUTTON_HEIGHT),
    ) {
        if (state.sending) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "Send to " + Formatting.carName(selectedDevice),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (destinationOnly) {
                Text("destination only, no route", style = MaterialTheme.typography.bodySmall)
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (feedback.success) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        } else {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            feedback.message + " · " + Formatting.time(feedback.timeMillis),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun NoticeRow(text: String, color: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun connectionColor(connection: ConnectionState): Color {
    if (connection is ConnectionState.Online) {
        return StatusColors.success()
    }
    if (connection is ConnectionState.Unreachable) {
        return MaterialTheme.colorScheme.error
    }
    return MaterialTheme.colorScheme.outline
}

private fun connectionText(connection: ConnectionState): String {
    if (connection is ConnectionState.Online) {
        return "Online"
    }
    if (connection is ConnectionState.Checking) {
        return "Checking…"
    }
    if (connection is ConnectionState.Unreachable) {
        return connection.message
    }
    return "Not checked yet"
}

private fun hasLocationPermission(context: Context): Boolean {
    val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    return coarse == PackageManager.PERMISSION_GRANTED || fine == PackageManager.PERMISSION_GRANTED
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

object RouteFormatting {
    fun duration(seconds: Double): String {
        val minutes = Math.round(seconds / 60).toInt()
        if (minutes < 60) {
            return "${maxOf(1, minutes)} min"
        }
        return "${minutes / 60} h " + String.format(Locale.US, "%02d", minutes % 60) + " min"
    }

    fun distance(meters: Double): String {
        if (meters < 1000) {
            return "${Math.round(meters)} m"
        }
        return String.format(Locale.US, "%.1f km", meters / 1000)
    }
}

private val MAP_BUTTON_SIZE = 52.dp
private val TOP_BAR_HEIGHT = 68.dp
private val WIDE_PANEL_WIDTH = 380.dp
private val SEARCH_MAX_WIDTH = 520.dp
private val RESULTS_MAX_HEIGHT = 320.dp
private val CAR_LABEL_MAX_WIDTH = 140.dp
private val PANEL_MAX_HEIGHT = 440.dp
private val SEND_BUTTON_HEIGHT = 56.dp
private const val UNPICKED_ROUTE_COLOR = 0xFF9AA5B1.toInt()
private const val MY_LOCATION_COLOR = 0xFF1A73E8.toInt()
