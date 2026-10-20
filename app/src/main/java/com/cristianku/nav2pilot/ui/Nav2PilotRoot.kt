package com.cristianku.nav2pilot.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cristianku.nav2pilot.data.Favorite
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.PairedDevice
import com.cristianku.nav2pilot.ui.screens.DevicesScreen
import com.cristianku.nav2pilot.ui.screens.FavoritesScreen
import com.cristianku.nav2pilot.ui.screens.MapActions
import com.cristianku.nav2pilot.ui.screens.MapScreen

// narrower than this, or taller than wide, stays the phone layout
private const val WIDE_MIN_WIDTH_DP = 600
private const val SETTINGS_MAX_WIDTH_DP = 640

private fun isFavorite(destination: Destination?, favorites: List<Favorite>): Boolean {
    if (destination == null) {
        return false
    }
    for (favorite in favorites) {
        if (favorite.destination.sameLocationAs(destination)) {
            return true
        }
    }
    return false
}

private fun findSelectedDevice(devices: List<PairedDevice>, selectedDongleId: String): PairedDevice? {
    for (device in devices) {
        if (device.dongleId == selectedDongleId) {
            return device
        }
    }
    return null
}

/** The map is the app; favorites come up in a sheet over it, the settings open as a page with a back arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Nav2PilotRoot(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val selectedDongleId by viewModel.selectedDongleId.collectAsStateWithLifecycle()
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var favoritesOpen by rememberSaveable { mutableStateOf(false) }

    // a shared place or a picked favorite: back to the map
    LaunchedEffect(state.showSendTabRequest) {
        if (state.showSendTabRequest > 0) {
            settingsOpen = false
            favoritesOpen = false
        }
    }

    val selectedDevice = findSelectedDevice(devices, selectedDongleId)

    // landscape and large screens (car head units, tablets, a phone on its side)
    val configuration = LocalConfiguration.current
    val wide = configuration.screenWidthDp >= WIDE_MIN_WIDTH_DP && configuration.screenWidthDp > configuration.screenHeightDp

    Box(
        modifier = Modifier
            .fillMaxSize()
            // edge to edge the window no longer shrinks for the keyboard
            .imePadding(),
    ) {
        MapScreen(
            state = state,
            devices = devices,
            selectedDevice = selectedDevice,
            isFavorite = isFavorite(state.destination, favorites),
            wide = wide,
            actions = MapActions(
                onSearch = { text -> viewModel.search(text) },
                onChooseCandidate = { destination -> viewModel.chooseCandidate(destination) },
                onDismissChoices = { viewModel.dismissChoices() },
                onClearSelection = { viewModel.clearSelection() },
                onToggleFavorite = { viewModel.toggleFavorite() },
                onSelectDevice = { dongleId -> viewModel.selectDevice(dongleId) },
                onCheckConnection = { viewModel.checkConnection() },
                onSend = { viewModel.sendDestination() },
                onClearOnDevice = { viewModel.clearDestinationOnDevice() },
                onPickOnMap = { latitude, longitude -> viewModel.pickOnMap(latitude, longitude) },
                onAddStop = { latitude, longitude -> viewModel.addStop(latitude, longitude) },
                onRemoveStop = { index -> viewModel.removeStop(index) },
                onSelectRoute = { index -> viewModel.selectRoute(index) },
                onRefreshRoutes = { viewModel.refreshRoutes() },
                onRefreshMyLocation = { recenter -> viewModel.refreshMyLocation(recenter) },
                onLocationPermissionResult = { granted -> viewModel.onLocationPermissionResult(granted) },
                onShowMapboxDialog = { viewModel.showMapboxDialog() },
                onSaveMapboxToken = { token -> viewModel.saveMapboxToken(token) },
                onHideMapboxDialog = { viewModel.hideMapboxDialog() },
                onOpenSettings = { settingsOpen = true },
                onOpenFavorites = { favoritesOpen = true },
                onShowPlaces = { category, area -> viewModel.showPlaces(category, area) },
                onHidePlaces = { viewModel.hidePlaces() },
                onChoosePlace = { place -> viewModel.choosePlace(place) },
            ),
        )
    }

    if (favoritesOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { favoritesOpen = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            Text(
                "Favorites",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 560.dp),
            ) {
                FavoritesScreen(
                    favorites = favorites,
                    selectedDevice = selectedDevice,
                    sending = state.sending,
                    onSelect = { favorite -> viewModel.selectFavorite(favorite) },
                    onSend = { favorite -> viewModel.sendFavorite(favorite) },
                    onRemove = { favorite -> viewModel.removeFavorite(favorite.id) },
                )
            }
        }
    }
    // over the map, which stays alive underneath (no reload of the tiles when coming back)
    if (settingsOpen) {
        BackHandler { settingsOpen = false }
        SettingsPage(
            onBack = { settingsOpen = false },
        ) {
            DevicesScreen(
                state = state,
                devices = devices,
                selectedDongleId = selectedDongleId,
                onShowTokenDialog = { viewModel.showTokenDialog() },
                onHideTokenDialog = { viewModel.hideTokenDialog() },
                onSaveToken = { token -> viewModel.saveToken(token) },
                onRemoveToken = { viewModel.removeToken() },
                onRefreshDevices = { viewModel.refreshDevices() },
                onSelectDevice = { dongleId -> viewModel.selectDevice(dongleId) },
                onRemoveDevice = { dongleId -> viewModel.removeDevice(dongleId) },
                onShowMapboxDialog = { viewModel.showMapboxDialog() },
                onHideMapboxDialog = { viewModel.hideMapboxDialog() },
                onSaveMapboxToken = { token -> viewModel.saveMapboxToken(token) },
                onRemoveMapboxToken = { viewModel.removeMapboxToken() },
            )
        }
    }

}

/** A page over the map with a title and a back arrow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPage(onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to the map")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            // lists and settings read badly across a whole wide screen
            Box(modifier = Modifier.widthIn(max = SETTINGS_MAX_WIDTH_DP.dp)) {
                content()
            }
        }
    }
}
