package com.cristianku.nav2pilot.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cristianku.nav2pilot.data.Favorite
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.ui.screens.DevicesScreen
import com.cristianku.nav2pilot.ui.screens.FavoritesScreen
import com.cristianku.nav2pilot.ui.screens.SendScreen

enum class Tab(val label: String) {
    SEND("Send"),
    FAVORITES("Favorites"),
    DEVICES("Devices"),
}

private fun iconFor(tab: Tab): ImageVector {
    if (tab == Tab.FAVORITES) {
        return Icons.Filled.Favorite
    }
    if (tab == Tab.DEVICES) {
        return Icons.Filled.Settings
    }
    return Icons.Filled.Place
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

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Nav2Pilot") })
        },
        bottomBar = {
            NavigationBar {
                for (tab in Tab.values()) {
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = { Icon(iconFor(tab), contentDescription = null) },
                        label = { Text(tab.label) },
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
                    onSelect = { favorite -> viewModel.selectFavorite(favorite) },
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
                    onAddManualDevice = { dongleId, alias -> viewModel.addManualDevice(dongleId, alias) },
                    onRemoveDevice = { dongleId -> viewModel.removeDevice(dongleId) },
                )
            }
        }
    }
}
