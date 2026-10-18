package com.cristianku.nav2pilot.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.PairedDevice
import java.text.DateFormat
import java.util.Date

object Formatting {
    fun time(timeMillis: Long): String {
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timeMillis))
    }

    fun date(epochSeconds: Long): String {
        return DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochSeconds * 1000L))
    }

    /** The name shown for a car: its alias, otherwise its type. Never the bare dongle ID. */
    fun carName(device: PairedDevice): String {
        if (device.alias.isNotBlank()) {
            return device.alias
        }
        if (device.deviceType.isNotEmpty()) {
            return "comma " + device.deviceType
        }
        return "comma device"
    }

    fun lastSeen(epochSeconds: Long): String {
        if (epochSeconds <= 0L) {
            return "never seen by athena"
        }
        val relative = DateUtils.getRelativeTimeSpanString(
            epochSeconds * 1000L,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        )
        return "athena ping $relative"
    }
}

object MapIntents {
    /** Opens the destination in another map app, so the user can check the pin before sending it. */
    fun showOnMap(context: Context, destination: Destination) {
        val coordinates = destination.latitude.toString() + "," + destination.longitude.toString()
        val label = Uri.encode(destination.displayName())
        val uri = Uri.parse("geo:$coordinates?q=$coordinates($label)")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            context.startActivity(Intent.createChooser(intent, "Show on map"))
        } catch (noMapApp: ActivityNotFoundException) {
            // nothing can show it; the coordinates stay visible in the app
        }
    }
}
