package com.cristianku.nav2pilot.transport

import com.cristianku.nav2pilot.model.BackendKind
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.PairedDevice

sealed class TransportResult<out T> {
    data class Ok<T>(val value: T) : TransportResult<T>()
    data class Failed(val error: TransportError) : TransportResult<Nothing>()
}

sealed class TransportError {
    object NoToken : TransportError()
    object Unauthorized : TransportError()
    object Forbidden : TransportError()
    object RateLimited : TransportError()
    data class MethodNotAvailable(val method: String) : TransportError()
    data class DeviceRejected(val reason: String) : TransportError()
    data class InvalidRequest(val reason: String) : TransportError()
    data class DeviceError(val reason: String) : TransportError()
    data class HttpFailure(val code: Int, val detail: String) : TransportError()
    data class Network(val detail: String) : TransportError()
    data class Malformed(val detail: String) : TransportError()
    data class NotImplemented(val reason: String) : TransportError()
}

/** Software running on the device, from athenad's getVersion. */
data class DeviceStatus(
    val version: String,
    val branch: String,
    val remote: String,
    val commit: String,
)

/** How the backend acknowledged a command. */
data class Delivery(
    /** True when the backend stored the command for later delivery instead of reaching the device. */
    val queued: Boolean,
    val note: String,
)

data class AccountInfo(
    val email: String,
)

/**
 * A backend able to deliver navigation commands to a device.
 *
 * Implementations: CommaAthenaTransport (comma athena JSON-RPC, verified) and SunnylinkTransport (stub).
 */
interface NavigationTransport {
    val kind: BackendKind
    val displayName: String
    val implemented: Boolean

    suspend fun listDevices(): TransportResult<List<PairedDevice>>

    suspend fun checkStatus(device: PairedDevice): TransportResult<DeviceStatus>

    suspend fun setDestination(device: PairedDevice, destination: Destination): TransportResult<Delivery>

    suspend fun clearDestination(device: PairedDevice): TransportResult<Delivery>

    /** The user's Mapbox public token, for the route requests of the device itself; an empty token removes it. */
    suspend fun setMapboxToken(device: PairedDevice, token: String): TransportResult<Delivery>
}
