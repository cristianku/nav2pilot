package com.cristianku.nav2pilot.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cristianku.nav2pilot.R
import com.cristianku.nav2pilot.data.Favorite
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.PairedDevice
import com.cristianku.nav2pilot.ui.components.BrandTitle
import com.cristianku.nav2pilot.ui.screens.DevicesScreen
import com.cristianku.nav2pilot.ui.screens.FavoritesScreen
import com.cristianku.nav2pilot.ui.screens.SendScreen

enum class Tab(val label: String) {
    SEND("Send"),
    FAVORITES("Favorites"),
    DEVICES("Devices"),
}

@Composable
private fun iconFor(tab: Tab): Painter {
    if (tab == Tab.FAVORITES) {
        return rememberVectorPainter(Icons.Filled.Favorite)
    }
    if (tab == Tab.DEVICES) {
        return painterResource(R.drawable.ic_car)
    }
    return rememberVectorPainter(Icons.Filled.Send)
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Nav2PilotRoot(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val selectedDongleId by viewModel.selectedDongleId.collectAsStateWithLifecycle()
    var currentTab by rememberSaveable { mutableStateOf(Tab.SEND) }

    LaunchedEffect(state.showSendTabRequest) {
        if (state.showSendTabRequest > 0) {
            currentTab = Tab.SEND
        }
    }

    val selectedDevice = findSelectedDevice(devices, selectedDongleId)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    // the logo on the main screen; the other tabs keep their own title
                    if (currentTab == Tab.SEND) {
                        BrandTitle()
                    } else {
                        Text(currentTab.label, fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                for (tab in Tab.values()) {
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(iconFor(tab), contentDescription = null) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            if (currentTab == Tab.SEND) {
                SendScreen(
                    state = state,
                    devices = devices,
                    selectedDongleId = selectedDongleId,
                    isFavorite = isFavorite(state.destination, favorites),
                    onSearch = { text -> viewModel.search(text) },
                    onChooseCandidate = { destination -> viewModel.chooseCandidate(destination) },
                    onClearSelection = { viewModel.clearSelection() },
                    onToggleFavorite = { viewModel.toggleFavorite() },
                    onSelectDevice = { dongleId -> viewModel.selectDevice(dongleId) },
                    onCheckConnection = { viewModel.checkConnection() },
                    onSend = { viewModel.sendDestination() },
                    onClearOnDevice = { viewModel.clearDestinationOnDevice() },
                    onOpenDevices = { currentTab = Tab.DEVICES },
                )
            } else if (currentTab == Tab.FAVORITES) {
                FavoritesScreen(
                    favorites = favorites,
                    selectedDevice = selectedDevice,
                    sending = state.sending,
                    onSelect = { favorite -> viewModel.selectFavorite(favorite) },
                    onSend = { favorite -> viewModel.sendFavorite(favorite) },
                    onRemove = { favorite -> viewModel.removeFavorite(favorite.id) },
                )
            } else {
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
}
