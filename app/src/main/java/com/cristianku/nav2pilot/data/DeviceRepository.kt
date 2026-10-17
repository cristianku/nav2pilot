package com.cristianku.nav2pilot.data

import com.cristianku.nav2pilot.model.PairedDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Paired devices (from the comma account and added by hand) and the currently selected one. */
class DeviceRepository(private val store: KeyValueStore) {

    private val devicesState = MutableStateFlow(loadDevices())
    val devices: StateFlow<List<PairedDevice>> = devicesState

    private val selectedState = MutableStateFlow(store.getString(KEY_SELECTED) ?: "")
    val selectedDongleId: StateFlow<String> = selectedState

    init {
        ensureValidSelection()
    }

    /**
     * Replaces the devices that came from the account with [accountDevices].
     * Manually added devices stay, unless the account now lists the same dongle ID.
     */
    fun replaceAccountDevices(accountDevices: List<PairedDevice>) {
        val updated = mutableListOf<PairedDevice>()
        for (device in accountDevices) {
            updated.add(device.copy(addedManually = false))
        }
        for (device in devicesState.value) {
            if (device.addedManually && !containsId(accountDevices, device.dongleId)) {
                updated.add(device)
            }
        }
        saveDevices(updated)
        ensureValidSelection()
    }

    /** False when a device with the same dongle ID already exists. */
    fun addManual(device: PairedDevice): Boolean {
        if (containsId(devicesState.value, device.dongleId)) {
            return false
        }
        val updated = devicesState.value.toMutableList()
        updated.add(device.copy(addedManually = true))
        saveDevices(updated)
        ensureValidSelection()
        return true
    }

    fun remove(dongleId: String) {
        val updated = mutableListOf<PairedDevice>()
        for (device in devicesState.value) {
            if (device.dongleId != dongleId) {
                updated.add(device)
            }
        }
        saveDevices(updated)
        ensureValidSelection()
    }

    fun select(dongleId: String) {
        if (!containsId(devicesState.value, dongleId)) {
            return
        }
        saveSelection(dongleId)
    }

    fun selectedDevice(): PairedDevice? {
        val selectedId = selectedState.value
        for (device in devicesState.value) {
            if (device.dongleId == selectedId) {
                return device
            }
        }
        return null
    }

    private fun ensureValidSelection() {
        val devices = devicesState.value
        if (containsId(devices, selectedState.value)) {
            return
        }
        if (devices.isEmpty()) {
            saveSelection("")
        } else {
            saveSelection(devices[0].dongleId)
        }
    }

    private fun containsId(devices: List<PairedDevice>, dongleId: String): Boolean {
        for (device in devices) {
            if (device.dongleId == dongleId) {
                return true
            }
        }
        return false
    }

    private fun loadDevices(): List<PairedDevice> {
        val text = store.getString(KEY_DEVICES) ?: return emptyList()
        return JsonCodec.devicesFromJson(text)
    }

    private fun saveDevices(devices: List<PairedDevice>) {
        store.putString(KEY_DEVICES, JsonCodec.devicesToJson(devices))
        devicesState.value = devices
    }

    private fun saveSelection(dongleId: String) {
        if (dongleId.isEmpty()) {
            store.remove(KEY_SELECTED)
        } else {
            store.putString(KEY_SELECTED, dongleId)
        }
        selectedState.value = dongleId
    }

    companion object {
        const val KEY_DEVICES = "devices"
        const val KEY_SELECTED = "selected_dongle_id"
    }
}
