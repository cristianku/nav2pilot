package com.cristianku.nav2pilot.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.cristianku.nav2pilot.mapbox.MapboxEndpoints
import com.cristianku.nav2pilot.model.BackendKind
import com.cristianku.nav2pilot.model.PairedDevice
import com.cristianku.nav2pilot.transport.CommaEndpoints
import com.cristianku.nav2pilot.transport.SunnylinkTransport
import com.cristianku.nav2pilot.ui.Formatting
import com.cristianku.nav2pilot.ui.MainUiState

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
    onAddManualDevice: (String, String) -> String,
    onRemoveDevice: (String) -> Unit,
    onShowMapboxDialog: () -> Unit,
    onHideMapboxDialog: () -> Unit,
    onSaveMapboxToken: (String) -> Unit,
    onRemoveMapboxToken: () -> Unit,
    onSendMapboxTokenToDevice: () -> Unit,
    onRemoveMapboxTokenFromDevice: () -> Unit,
) {
    var addDialogVisible by remember { mutableStateOf(false) }
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
        AccountCard(
            state = state,
            onShowTokenDialog = onShowTokenDialog,
            onRemoveToken = onRemoveToken,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Devices", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (state.refreshingDevices) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onRefreshDevices, enabled = state.hasToken) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh devices from comma")
                }
            }
        }
        if (state.devicesMessage.isNotEmpty()) {
            Text(state.devicesMessage, style = MaterialTheme.typography.bodySmall)
        }
        if (devices.isEmpty()) {
            Text(
                "No devices. Refresh after adding a token, or add one by dongle ID.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        for (device in devices) {
            DeviceRow(
                device = device,
                selected = device.dongleId == selectedDongleId,
                onSelect = { onSelectDevice(device.dongleId) },
                onRemove = { onRemoveDevice(device.dongleId) },
            )
        }
        OutlinedButton(onClick = { addDialogVisible = true }) {
            Text("Add device by dongle ID")
        }

        MapboxCard(
            state = state,
            selectedDevice = selectedDevice,
            onShowMapboxDialog = onShowMapboxDialog,
            onRemoveMapboxToken = onRemoveMapboxToken,
            onSendToDevice = onSendMapboxTokenToDevice,
            onRemoveFromDevice = onRemoveMapboxTokenFromDevice,
        )

        BackendsCard()
    }

    if (state.tokenDialogVisible) {
        TokenDialog(
            busy = state.accountBusy,
            message = state.accountMessage,
            onSave = onSaveToken,
            onDismiss = onHideTokenDialog,
        )
    }
    if (addDialogVisible) {
        AddDeviceDialog(
            onAdd = onAddManualDevice,
            onDismiss = { addDialogVisible = false },
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
private fun MapboxCard(
    state: MainUiState,
    selectedDevice: PairedDevice?,
    onShowMapboxDialog: () -> Unit,
    onRemoveMapboxToken: () -> Unit,
    onSendToDevice: () -> Unit,
    onRemoveFromDevice: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Mapbox", style = MaterialTheme.typography.titleMedium)
            if (state.hasMapboxToken) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        "  Mapbox token stored, encrypted with the Android Keystore",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (selectedDevice != null) {
                    val deviceActionsEnabled = !state.mapboxDeviceBusy && state.hasToken
                    OutlinedButton(onClick = onSendToDevice, enabled = deviceActionsEnabled) {
                        Text("Send token to " + selectedDevice.displayName())
                    }
                    TextButton(onClick = onRemoveFromDevice, enabled = deviceActionsEnabled) {
                        Text("Remove token from " + selectedDevice.displayName())
                    }
                }
                if (state.mapboxDeviceBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                if (state.mapboxDeviceMessage.isNotEmpty()) {
                    Text(state.mapboxDeviceMessage, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "The device gets the token with the next destination you send, or now with the button. " +
                        "Removing it from this phone leaves the copy on the device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onShowMapboxDialog) {
                        Text("Replace token")
                    }
                    TextButton(onClick = onRemoveMapboxToken) {
                        Text("Remove")
                    }
                }
            } else {
                Text(
                    "With your own free Mapbox account the device computes its routes with Mapbox instead of comma's route service.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onShowMapboxDialog) {
                    Text("Set up Mapbox")
                }
            }
        }
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
private fun AccountCard(
    state: MainUiState,
    onShowTokenDialog: () -> Unit,
    onRemoveToken: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("comma account", style = MaterialTheme.typography.titleMedium)
            if (state.hasToken) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(
                        "  Token stored, encrypted with the Android Keystore",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (state.accountEmail.isNotEmpty()) {
                    Text(state.accountEmail, style = MaterialTheme.typography.bodySmall)
                }
                val expiry = state.tokenExpirySeconds
                if (expiry != null) {
                    var expiryText = "Expires " + Formatting.date(expiry)
                    if (expiry * 1000L < System.currentTimeMillis()) {
                        expiryText = "Expired on " + Formatting.date(expiry) + ". Generate a new token."
                    }
                    Text(expiryText, style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Expiry not readable from the token.", style = MaterialTheme.typography.bodySmall)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onShowTokenDialog) {
                        Text("Replace token")
                    }
                    TextButton(onClick = onRemoveToken) {
                        Text("Remove")
                    }
                }
            } else {
                Text(
                    "Sign in at jwt.comma.ai with the account your device is paired to, copy the token and paste it here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onShowTokenDialog) {
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

@Composable
private fun DeviceRow(
    device: PairedDevice,
    selected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() },
    ) {
        Row(
            modifier = Modifier.padding(end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Text(device.displayName(), style = MaterialTheme.typography.bodyLarge)
                Text(
                    device.dongleId,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
            if (device.addedManually) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove device")
                }
            }
        }
    }
}

@Composable
private fun BackendsCard() {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Backends", style = MaterialTheme.typography.titleMedium)
            Text(
                "comma athena: available. Sends setNavDestination / clearNavDestination, and setMapboxToken to sunnypilot Nav Alpha.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "sunnylink: not available. " + SunnylinkTransport.REASON,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
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

@Composable
private fun AddDeviceDialog(
    onAdd: (String, String) -> String,
    onDismiss: () -> Unit,
) {
    var dongleId by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add device") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dongleId,
                    onValueChange = { text -> dongleId = text },
                    label = { Text("Dongle ID (16 hex characters)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(autoCorrect = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = alias,
                    onValueChange = { text -> alias = text },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Backend: " + backendLabel(BackendKind.COMMA_ATHENA) + ". The dongle ID is in the device settings.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (error.isNotEmpty()) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val result = onAdd(dongleId, alias)
                    if (result.isEmpty()) {
                        onDismiss()
                    } else {
                        error = result
                    }
                },
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

private fun backendLabel(kind: BackendKind): String {
    if (kind == BackendKind.SUNNYLINK) {
        return "sunnylink"
    }
    return "comma athena"
}
