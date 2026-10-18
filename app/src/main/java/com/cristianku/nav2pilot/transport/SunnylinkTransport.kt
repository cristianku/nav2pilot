package com.cristianku.nav2pilot.transport

import com.cristianku.nav2pilot.mapbox.RouteWaypoint
import com.cristianku.nav2pilot.model.BackendKind
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.PairedDevice

/**
 * sunnylink backend: NOT IMPLEMENTED.
 *
 * What is verified (README "sunnylink"):
 * - On the device, sunnylinkd registers the same JSON-RPC dispatcher as athenad, so setNavDestination
 *   and clearNavDestination are reachable over the sunnylink websocket.
 * - The public sunnylink web frontend ships generated types for
 *   POST https://athena.sunnylink.ai/navigation/{deviceId}/set_destination, but no documentation,
 *   and its users authenticate through the frontend's own Logto OIDC client.
 *
 * Not verified: an API contract and a supported way for a third-party app to obtain a sunnylink token.
 * Until both exist this backend reports NotImplemented instead of guessing.
 */
class SunnylinkTransport : NavigationTransport {
    override val kind: BackendKind = BackendKind.SUNNYLINK
    override val displayName: String = "sunnylink"
    override val implemented: Boolean = false

    override suspend fun listDevices(): TransportResult<List<PairedDevice>> {
        return notImplemented()
    }

    override suspend fun checkStatus(device: PairedDevice): TransportResult<DeviceStatus> {
        return notImplemented()
    }

    override suspend fun setDestination(device: PairedDevice, destination: Destination): TransportResult<Delivery> {
        return notImplemented()
    }

    override suspend fun setRoute(device: PairedDevice, destination: Destination, waypoints: List<RouteWaypoint>): TransportResult<Delivery> {
        return notImplemented()
    }

    override suspend fun clearDestination(device: PairedDevice): TransportResult<Delivery> {
        return notImplemented()
    }

    override suspend fun setMapboxToken(device: PairedDevice, token: String): TransportResult<Delivery> {
        return notImplemented()
    }

    private fun notImplemented(): TransportResult.Failed {
        return TransportResult.Failed(TransportError.NotImplemented(REASON))
    }

    companion object {
        const val REASON = "sunnylink has no documented public API for third-party apps yet. Use the comma backend."
    }
}
