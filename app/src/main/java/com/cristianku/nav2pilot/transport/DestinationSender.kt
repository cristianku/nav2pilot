package com.cristianku.nav2pilot.transport

import com.cristianku.nav2pilot.mapbox.RouteWaypoint
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.PairedDevice

/** How a send ended, with the text shown to the user. */
data class SendOutcome(
    val success: Boolean,
    val message: String,
)

/**
 * Sends a destination the same way from the phone and from the car screen: the user's Mapbox token first, so the
 * car's first route request already uses it, then the destination alone or with its waypoints.
 */
class DestinationSender(
    private val transports: TransportRegistry,
    private val mapboxToken: suspend () -> String?,
) {
    suspend fun send(device: PairedDevice, destination: Destination, waypoints: List<RouteWaypoint>): SendOutcome {
        val transport = transports.forDevice(device)
        val token = mapboxToken()
        if (token != null) {
            // silent on purpose: a car that is off, or runs software without setMapboxToken, keeps routing as before
            transport.setMapboxToken(device, token)
        }
        val result: TransportResult<Delivery>
        if (waypoints.isEmpty()) {
            result = transport.setDestination(device, destination)
        } else {
            result = transport.setRoute(device, destination, waypoints)
        }
        if (result is TransportResult.Failed) {
            return SendOutcome(false, TransportErrorText.describe(result.error))
        }
        val delivery = (result as TransportResult.Ok).value
        var message = "Destination sent to ${device.displayName()}."
        if (waypoints.isNotEmpty()) {
            message = "Route sent to ${device.displayName()}."
        }
        if (delivery.queued) {
            message = "Queued by athena: ${delivery.note}"
        } else if (delivery.note.isNotEmpty()) {
            message = "Destination sent to ${device.displayName()}. " + delivery.note
        }
        return SendOutcome(true, message)
    }
}
