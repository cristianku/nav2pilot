package com.cristianku.nav2pilot.ui.screens

import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cristianku.nav2pilot.R
import com.cristianku.nav2pilot.mapbox.MapboxEndpoints
import com.cristianku.nav2pilot.model.PairedDevice
import com.cristianku.nav2pilot.transport.CommaEndpoints
import com.cristianku.nav2pilot.transport.SunnylinkTransport
import com.cristianku.nav2pilot.ui.ConnectionState
import com.cristianku.nav2pilot.ui.Formatting
import com.cristianku.nav2pilot.ui.MainUiState
import com.cristianku.nav2pilot.ui.components.BrandCard
import com.cristianku.nav2pilot.ui.components.BrandLogo
import com.cristianku.nav2pilot.ui.components.IconBadge
import com.cristianku.nav2pilot.ui.components.ScreenPadding
import com.cristianku.nav2pilot.ui.components.SectionLabel
import com.cristianku.nav2pilot.ui.theme.StatusColors

@Composable
fun DevicesScreen(
    state: MainUiState,
    devices: List<PairedDevice>,
    selectedDongleId: String,
    onShowTokenDialog: () -> Unit,
    onHideTokenDialog: () -> Unit,
    onSaveToken: (String) -> Unit,
    onRemoveToken: () -> Unit,
    onRefreshDevices: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onRemoveDevice: (String) -> Unit,
    onShowMapboxDialog: () -> Unit,
    onHideMapboxDialog: () -> Unit,
    onSaveMapboxToken: (String) -> Unit,
    onRemoveMapboxToken: () -> Unit,
) {
    var deviceToRemove by remember { mutableStateOf<PairedDevice?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // connected: the car comes first; otherwise the account is the next step
        if (state.hasToken) {
            CarSection(
                state = state,
                devices = devices,
                selectedDongleId = selectedDongleId,
                onRefreshDevices = onRefreshDevices,
                onSelectDevice = onSelectDevice,
                onAskRemove = { device -> deviceToRemove = device },
            )
            AccountSection(state = state, onShowTokenDialog = onShowTokenDialog, onRemoveToken = onRemoveToken)
        } else {
            AccountSection(state = state, onShowTokenDialog = onShowTokenDialog, onRemoveToken = onRemoveToken)
            CarSection(
                state = state,
                devices = devices,
                selectedDongleId = selectedDongleId,
                onRefreshDevices = onRefreshDevices,
                onSelectDevice = onSelectDevice,
                onAskRemove = { device -> deviceToRemove = device },
            )
        }

        Column {
            SectionLabel("Maps and routes")
            MapboxCard(
                state = state,
                onShowMapboxDialog = onShowMapboxDialog,
                onRemoveMapboxToken = onRemoveMapboxToken,
            )
        }

        var selectedDevice: PairedDevice? = null
        for (device in devices) {
            if (device.dongleId == selectedDongleId) {
                selectedDevice = device
            }
        }
        ConnectionCard(selectedDevice = selectedDevice, connection = state.connection)

        AboutCard()
    }

    val removing = deviceToRemove
    if (removing != null) {
        RemoveDeviceDialog(
            device = removing,
            onConfirm = {
                deviceToRemove = null
                onRemoveDevice(removing.dongleId)
            },
            onDismiss = { deviceToRemove = null },
        )
    }
    if (state.tokenDialogVisible) {
        TokenDialog(
            busy = state.accountBusy,
            message = state.accountMessage,
            onSave = onSaveToken,
            onDismiss = onHideTokenDialog,
        )
    }
    if (state.mapboxDialogVisible) {
        MapboxTokenDialog(
            busy = state.mapboxBusy,
            message = state.mapboxMessage,
            onSave = onSaveMapboxToken,
            onDismiss = onHideMapboxDialog,
        )
    }
}

@Composable
private fun CarSection(
    state: MainUiState,
    devices: List<PairedDevice>,
    selectedDongleId: String,
    onRefreshDevices: () -> Unit,
    onSelectDevice: (String) -> Unit,
    onAskRemove: (PairedDevice) -> Unit,
) {
    Column {
        if (devices.size > 1) {
            SectionLabel("Your cars")
        } else {
            SectionLabel("Your car")
        }
        BrandCard {
            if (devices.isEmpty()) {
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
                    var text = "Connect your comma account above to see your car."
                    if (state.hasToken) {
                        text = "No car on this comma account yet."
                    }
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.hasToken) {
                    OutlinedButton(
                        onClick = onRefreshDevices,
                        enabled = !state.refreshingDevices,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(start = 72.dp, bottom = 16.dp),
                    ) {
                        if (state.refreshingDevices) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Refresh from comma account")
                    }
                }
            }
            for (index in devices.indices) {
                val device = devices[index]
                if (index > 0) {
                    Divider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(start = 72.dp),
                    )
                }
                val selected = device.dongleId == selectedDongleId
                DeviceRow(
                    device = device,
                    selected = selected,
                    // nothing to choose with a single car
                    showsSelection = devices.size > 1,
                    canRefresh = state.hasToken && !state.refreshingDevices,
                    refreshing = state.refreshingDevices,
                    onSelect = { onSelectDevice(device.dongleId) },
                    onRefresh = onRefreshDevices,
                    onRemove = { onAskRemove(device) },
                )
            }
        }
        if (state.devicesMessage.isNotEmpty()) {
            Text(
                state.devicesMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 4.dp),
            )
        }
    }
}

@Composable
private fun DeviceRow(
    device: PairedDevice,
    selected: Boolean,
    showsSelection: Boolean,
    canRefresh: Boolean,
    refreshing: Boolean,
    onSelect: () -> Unit,
    onRefresh: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var rowModifier = Modifier.fillMaxWidth()
    if (showsSelection) {
        rowModifier = rowModifier.clickable { onSelect() }
    }
    Row(
        modifier = rowModifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showsSelection) {
            RadioButton(selected = selected, onClick = onSelect, modifier = Modifier.size(40.dp))
        } else {
            IconBadge(
                painter = painterResource(R.drawable.ic_car),
                tint = MaterialTheme.colorScheme.primary,
                background = MaterialTheme.colorScheme.primaryContainer,
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                Formatting.carName(device),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            var details = ""
            if (device.deviceType.isNotEmpty()) {
                details = "comma " + device.deviceType + " · "
            }
            if (device.addedManually) {
                details = details + "added by hand"
            } else {
                details = details + Formatting.lastSeen(device.lastAthenaPingSeconds)
            }
            if (!device.isOwner) {
                details = details + " · read-only access"
            }
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            if (refreshing) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Actions for " + Formatting.carName(device))
                }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Refresh from comma account") },
                    leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null) },
                    enabled = canRefresh,
                    onClick = {
                        menuExpanded = false
                        onRefresh()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Remove car", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = {
                        menuExpanded = false
                        onRemove()
                    },
                )
            }
        }
    }
}

@Composable
private fun RemoveDeviceDialog(
    device: PairedDevice,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var message = "It comes back with Refresh from comma account."
    if (device.addedManually) {
        message = "It was added by hand, so it does not come back from the comma account."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove " + Formatting.carName(device) + "?") },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Remove", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun AccountSection(
    state: MainUiState,
    onShowTokenDialog: () -> Unit,
    onRemoveToken: () -> Unit,
) {
    Column {
        SectionLabel("comma account")
        AccountCard(state = state, onShowTokenDialog = onShowTokenDialog, onRemoveToken = onRemoveToken)
    }
}

@Composable
private fun AccountCard(
    state: MainUiState,
    onShowTokenDialog: () -> Unit,
    onRemoveToken: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    BrandCard {
        if (state.hasToken) {
            ConnectedAccount(state = state, onShowTokenDialog = onShowTokenDialog, onRemoveToken = onRemoveToken)
        } else {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Connect your comma account", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Sign in at jwt.comma.ai with the account your device is paired to, copy the token and paste it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onShowTokenDialog, shape = RoundedCornerShape(12.dp)) {
                        Text("Add token")
                    }
                    TextButton(onClick = { uriHandler.openUri(CommaEndpoints.TOKEN_PAGE_URL) }) {
                        Text("Open jwt.comma.ai")
                    }
                }
            }
        }
    }
}

/** One compact row: "saved", not "connected", because the token is only checked when it is added. */
@Composable
private fun ConnectedAccount(
    state: MainUiState,
    onShowTokenDialog: () -> Unit,
    onRemoveToken: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val expiry = state.tokenExpirySeconds
    var expired = false
    if (expiry != null && expiry * 1000L < System.currentTimeMillis()) {
        expired = true
    }
    var title = "Credentials saved"
    var tint = StatusColors.success()
    if (expired) {
        title = "Access needs renewal"
        tint = StatusColors.warning()
    }
    var expiryText = "Expiry not readable from the token."
    if (expiry != null) {
        expiryText = "Expires " + Formatting.date(expiry)
        if (expired) {
            expiryText = "Expired on " + Formatting.date(expiry) + ". Generate a new token."
        }
    }

    Row(
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (expired) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        } else {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (state.accountEmail.isNotEmpty()) {
                Text(state.accountEmail, style = MaterialTheme.typography.bodySmall)
            }
            Text(expiryText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Stored on this phone only, encrypted with the Android Keystore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            TextButton(onClick = { menuExpanded = true }) {
                Text("Manage")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text("Replace token") },
                    onClick = {
                        menuExpanded = false
                        onShowTokenDialog()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Remove token", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuExpanded = false
                        onRemoveToken()
                    },
                )
            }
        }
    }
}

@Composable
private fun MapboxCard(
    state: MainUiState,
    onShowMapboxDialog: () -> Unit,
    onRemoveMapboxToken: () -> Unit,
) {
    BrandCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.hasMapboxToken) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = StatusColors.success(),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Mapbox token saved", style = MaterialTheme.typography.titleSmall)
                }
                Text(
                    "Your car gets the token automatically with every destination you send. " +
                        "Removing it here also removes it from the selected car.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Stored on this phone, encrypted with the Android Keystore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onShowMapboxDialog, shape = RoundedCornerShape(12.dp)) {
                        Text("Replace token")
                    }
                    TextButton(onClick = onRemoveMapboxToken) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                Text("Mapbox routes (optional)", style = MaterialTheme.typography.titleSmall)
                Text(
                    "With your own free Mapbox account the car computes its routes with Mapbox instead of comma's route service.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onShowMapboxDialog, shape = RoundedCornerShape(12.dp)) {
                    Text("Set up Mapbox")
                }
            }
        }
    }
}

/** How the app reaches the car, folded: not needed day to day. */
@Composable
private fun ConnectionCard(selectedDevice: PairedDevice?, connection: ConnectionState) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BrandCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Connection details", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (expanded) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Hide connection details")
            } else {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Show connection details")
            }
        }
        if (expanded) {
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // the technical identity of the selected car, kept out of the day-to-day view
                if (selectedDevice != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(Formatting.carName(selectedDevice), style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Dongle ID " + selectedDevice.dongleId,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (connection is ConnectionState.Online) {
                            var software = "Software " + connection.status.version
                            if (connection.status.branch.isNotEmpty()) {
                                software = software + " (" + connection.status.branch + ")"
                            }
                            Text(
                                software,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    "comma athena: available. Sends setNavDestination / clearNavDestination, and setMapboxToken to sunnypilot Nav Alpha.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "sunnylink: not available. " + SunnylinkTransport.REASON,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun appVersion(context: Context): String {
    try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val name = info.versionName
        if (name != null) {
            return name
        }
    } catch (notFound: PackageManager.NameNotFoundException) {
        // our own package is always installed; keep the about box without a version anyway
    }
    return ""
}

@Composable
private fun AboutCard() {
    val context = LocalContext.current
    val version = appVersion(context)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BrandLogo(size = 68.dp)
        Spacer(Modifier.size(6.dp))
        Text("Nav2Pilot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (version.isNotEmpty()) {
            Text(
                "Version $version",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "An independent project, not affiliated with comma.ai or sunnypilot.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MapboxTokenDialog(
    busy: Boolean,
    message: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {
            if (!busy) {
                onDismiss()
            }
        },
        title = { Text("Mapbox token") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("1. Create a free Mapbox account and confirm the e-mail.", style = MaterialTheme.typography.bodySmall)
                Text(
                    "2. Add a payment card in the account: without one Mapbox gives only a trial token with low limits. " +
                        "With a card the free tier applies, enough for personal use.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "3. On the Tokens page copy the Default public token (it starts with pk.). Never the secret one (sk.).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "4. Paste it here. If you rotate the token on Mapbox later, paste the new one again.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { uriHandler.openUri(MapboxEndpoints.SIGNUP_URL) }) {
                    Text("Create a free Mapbox account")
                }
                TextButton(onClick = { uriHandler.openUri(MapboxEndpoints.TOKENS_PAGE_URL) }) {
                    Text("Open the Mapbox Tokens page")
                }
                OutlinedTextField(
                    value = token,
                    onValueChange = { text -> token = text },
                    label = { Text("pk.…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        val pasted = clipboard.getText()
                        if (pasted != null) {
                            token = pasted.text
                        }
                    },
                ) {
                    Text("Paste")
                }
                Text(
                    "The token is checked with Mapbox, then stored encrypted on this phone and sent to your comma device, " +
                        "which asks Mapbox for its routes with it.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (message.isNotEmpty()) {
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(token) }, enabled = !busy && token.isNotBlank()) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Verify and save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Cancel")
            }
        },
    )
}


@Composable
private fun TokenDialog(
    busy: Boolean,
    message: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var token by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {
            if (!busy) {
                onDismiss()
            }
        },
        title = { Text("comma token") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The token is checked with the comma API, then stored encrypted on this phone only.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { text -> token = text },
                    label = { Text("JWT") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrect = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (message.isNotEmpty()) {
                    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(token) }, enabled = !busy && token.isNotBlank()) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Verify and save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Cancel")
            }
        },
    )
}
