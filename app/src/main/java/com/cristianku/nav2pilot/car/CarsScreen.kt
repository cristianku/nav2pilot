package com.cristianku.nav2pilot.car

import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SectionedItemList
import androidx.car.app.model.Template
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cristianku.nav2pilot.model.PairedDevice
import com.cristianku.nav2pilot.transport.TransportErrorText
import com.cristianku.nav2pilot.transport.TransportResult
import com.cristianku.nav2pilot.ui.Formatting
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The cars of the account with their connection status: tap one to send to it. Also clears the destination on
 * the selected car. Adding or removing cars is setup, so it stays on the phone.
 */
class CarsScreen(carContext: CarContext, private val model: CarModel) : Screen(carContext) {
    /** Status line by dongle ID. */
    private val statuses = mutableMapOf<String, String>()
    private var clearing = false

    init {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                checkAll()
            }
        }
    }

    override fun onGetTemplate(): Template {
        val header = Header.Builder()
            .setTitle("Car")
            .setStartHeaderAction(Action.BACK)
            .build()
        val template = ListTemplate.Builder().setHeader(header)

        val cars = ItemList.Builder()
        cars.setNoItemsMessage("No car in Nav2Pilot yet: add it in Devices on your phone. ${CarModel.SAFE_TO_LOOK}")
        val selectedId = model.devices.selectedDongleId.value
        for (device in model.devices.devices.value) {
            cars.addItem(carRow(device, device.dongleId == selectedId))
        }
        template.addSectionedList(SectionedItemList.create(cars.build(), "Send to"))

        val selected = model.devices.selectedDevice()
        if (selected != null) {
            var title = "Clear the destination"
            if (clearing) {
                title = "Clearing…"
            }
            val clear = Row.Builder()
                .setTitle(title)
                .addText("On " + Formatting.carName(selected))
                .setOnClickListener { clear(selected) }
                .build()
            template.addSectionedList(SectionedItemList.create(ItemList.Builder().addItem(clear).build(), "Destination"))
        }
        return template.build()
    }

    private fun carRow(device: PairedDevice, selected: Boolean): Row {
        val parts = mutableListOf<String>()
        if (selected) {
            parts.add("Selected")
        }
        parts.add(statuses[device.dongleId] ?: CHECKING)
        return Row.Builder()
            .setTitle(Formatting.carName(device))
            .addText(parts.joinToString(" · "))
            .setOnClickListener {
                model.devices.select(device.dongleId)
                invalidate()
            }
            .build()
    }

    private suspend fun checkAll() {
        coroutineScope {
            for (device in model.devices.devices.value) {
                launch {
                    val result = model.transports.forDevice(device).checkStatus(device)
                    if (result is TransportResult.Ok) {
                        var text = "Online"
                        if (result.value.version.isNotEmpty()) {
                            text += " · " + result.value.version
                        }
                        if (result.value.branch.isNotEmpty()) {
                            text += " (" + result.value.branch + ")"
                        }
                        statuses[device.dongleId] = text
                    } else if (result is TransportResult.Failed) {
                        statuses[device.dongleId] = "Not reachable: " + TransportErrorText.describe(result.error)
                    }
                    invalidate()
                }
            }
        }
    }

    private fun clear(device: PairedDevice) {
        if (clearing) {
            return
        }
        clearing = true
        invalidate()
        lifecycleScope.launch {
            val result = model.transports.forDevice(device).clearDestination(device)
            var message = "Destination cleared on ${Formatting.carName(device)}."
            if (result is TransportResult.Failed) {
                message = TransportErrorText.describe(result.error)
            }
            clearing = false
            invalidate()
            CarToast.makeText(carContext, message, CarToast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val CHECKING = "Checking…"
    }
}
