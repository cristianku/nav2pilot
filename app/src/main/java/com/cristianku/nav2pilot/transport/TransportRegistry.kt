package com.cristianku.nav2pilot.transport

import com.cristianku.nav2pilot.model.BackendKind
import com.cristianku.nav2pilot.model.PairedDevice

class TransportRegistry(
    val comma: CommaAthenaTransport,
    val sunnylink: SunnylinkTransport,
) {
    fun forBackend(kind: BackendKind): NavigationTransport {
        if (kind == BackendKind.SUNNYLINK) {
            return sunnylink
        }
        return comma
    }

    fun forDevice(device: PairedDevice): NavigationTransport {
        return forBackend(device.backend)
    }
}

object TransportErrorText {
    fun describe(error: TransportError): String {
        if (error is TransportError.NoToken) {
            return "No comma token stored. Add one in Devices."
        }
        if (error is TransportError.Unauthorized) {
            return "The comma token was rejected (HTTP 401). It may be expired: generate a new one."
        }
        if (error is TransportError.Forbidden) {
            return "This account has no access to the device (HTTP 403)."
        }
        if (error is TransportError.RateLimited) {
            return "Too many requests (HTTP 429). Try again in a moment."
        }
        if (error is TransportError.MethodNotAvailable) {
            return "The device does not support ${error.method}. It needs openpilot or sunnypilot with Nav Alpha."
        }
        if (error is TransportError.DeviceRejected) {
            return "Rejected by the device: ${error.reason}"
        }
        if (error is TransportError.InvalidRequest) {
            return "Invalid request: ${error.reason}"
        }
        if (error is TransportError.DeviceError) {
            return "Device error: ${error.reason}"
        }
        if (error is TransportError.HttpFailure) {
            return "Device unreachable or request refused (HTTP ${error.code}): ${error.detail}"
        }
        if (error is TransportError.Network) {
            return "Network error: ${error.detail}"
        }
        if (error is TransportError.Malformed) {
            return "Unexpected response: ${error.detail}"
        }
        if (error is TransportError.NotImplemented) {
            return error.reason
        }
        return "Unknown error"
    }
}
