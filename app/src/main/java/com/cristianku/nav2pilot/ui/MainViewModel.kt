package com.cristianku.nav2pilot.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cristianku.nav2pilot.AppContainer
import com.cristianku.nav2pilot.data.DeviceRepository
import com.cristianku.nav2pilot.data.Favorite
import com.cristianku.nav2pilot.data.FavoritesRepository
import com.cristianku.nav2pilot.geocode.PlaceSearch
import com.cristianku.nav2pilot.mapbox.MapboxSetup
import com.cristianku.nav2pilot.mapbox.MapboxTokenCheck
import com.cristianku.nav2pilot.mapbox.MapboxTokenRules
import com.cristianku.nav2pilot.model.BackendKind
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.DongleIdRules
import com.cristianku.nav2pilot.model.PairedDevice
import com.cristianku.nav2pilot.resolve.DestinationResolver
import com.cristianku.nav2pilot.resolve.ResolveOutcome
import com.cristianku.nav2pilot.security.JwtInfo
import com.cristianku.nav2pilot.security.TokenStore
import com.cristianku.nav2pilot.transport.CommaAthenaTransport
import com.cristianku.nav2pilot.transport.DeviceStatus
import com.cristianku.nav2pilot.transport.TransportError
import com.cristianku.nav2pilot.transport.TransportErrorText
import com.cristianku.nav2pilot.transport.TransportRegistry
import com.cristianku.nav2pilot.transport.TransportResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.GeneralSecurityException

sealed class ConnectionState {
    object Unknown : ConnectionState()
    object Checking : ConnectionState()
    data class Online(val status: DeviceStatus) : ConnectionState()
    data class Unreachable(val message: String) : ConnectionState()
}

data class SendFeedback(
    val success: Boolean,
    val message: String,
    val timeMillis: Long,
)

data class MainUiState(
    val destination: Destination? = null,
    val destinationSource: String = "",
    val resolving: Boolean = false,
    val resolveError: String = "",
    val choices: List<Destination> = emptyList(),
    val connection: ConnectionState = ConnectionState.Unknown,
    val sending: Boolean = false,
    val feedback: SendFeedback? = null,
    val hasToken: Boolean = false,
    val tokenExpirySeconds: Long? = null,
    val accountEmail: String = "",
    val accountBusy: Boolean = false,
    val accountMessage: String = "",
    val tokenDialogVisible: Boolean = false,
    val refreshingDevices: Boolean = false,
    val devicesMessage: String = "",
    val geocoderAvailable: Boolean = true,
    val hasMapboxToken: Boolean = false,
    val mapboxBusy: Boolean = false,
    val mapboxMessage: String = "",
    val mapboxDialogVisible: Boolean = false,
    val mapboxDeviceBusy: Boolean = false,
    val mapboxDeviceMessage: String = "",
    /** Incremented when the Send tab should be brought to front (share received, favorite picked). */
    val showSendTabRequest: Int = 0,
)

class MainViewModel(
    private val resolver: DestinationResolver,
    private val placeSearch: PlaceSearch,
    private val transports: TransportRegistry,
    private val commaTransport: CommaAthenaTransport,
    private val tokenStore: TokenStore,
    private val favoritesRepository: FavoritesRepository,
    private val deviceRepository: DeviceRepository,
    private val ioDispatcher: CoroutineDispatcher,
    private val mapbox: MapboxSetup? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val state = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = state

    val favorites: StateFlow<List<Favorite>> = favoritesRepository.favorites
    val devices: StateFlow<List<PairedDevice>> = deviceRepository.devices
    val selectedDongleId: StateFlow<String> = deviceRepository.selectedDongleId

    private var resolveJob: Job? = null
    private var connectionJob: Job? = null

    init {
        state.value = state.value.copy(geocoderAvailable = placeSearch.isAvailable())
        viewModelScope.launch {
            refreshTokenState()
            checkConnection()
        }
    }

    // ---- destination input ----

    fun handleShare(text: String, subject: String) {
        startResolve { resolver.resolveShared(text, subject) }
        requestSendTab()
    }

    fun handleLink(link: String) {
        startResolve { resolver.resolveLink(link, "", "", DestinationResolver.SOURCE_LINK) }
        requestSendTab()
    }

    fun search(text: String) {
        startResolve { resolver.resolveTyped(text) }
    }

    fun chooseCandidate(destination: Destination) {
        state.value = state.value.copy(
            destination = destination,
            destinationSource = DestinationResolver.SOURCE_SEARCH,
            choices = emptyList(),
            resolveError = "",
            feedback = null,
        )
    }

    fun selectFavorite(favorite: Favorite) {
        resolveJob?.cancel()
        state.value = state.value.copy(
            destination = favorite.destination,
            destinationSource = DestinationResolver.SOURCE_FAVORITE,
            choices = emptyList(),
            resolving = false,
            resolveError = "",
            feedback = null,
        )
        requestSendTab()
    }

    /** Send button on a favorite: select it (brings the Send tab up, where the feedback shows) and send it. */
    fun sendFavorite(favorite: Favorite) {
        if (state.value.sending) {
            return
        }
        selectFavorite(favorite)
        sendDestination()
    }

    fun clearSelection() {
        resolveJob?.cancel()
        state.value = state.value.copy(
            destination = null,
            destinationSource = "",
            choices = emptyList(),
            resolving = false,
            resolveError = "",
            feedback = null,
        )
    }

    fun toggleFavorite() {
        val destination = state.value.destination ?: return
        val existing = favoritesRepository.findByLocation(destination)
        if (existing != null) {
            favoritesRepository.remove(existing.id)
        } else {
            favoritesRepository.add(destination)
        }
    }

    fun removeFavorite(id: String) {
        favoritesRepository.remove(id)
    }

    private fun startResolve(block: suspend () -> ResolveOutcome) {
        resolveJob?.cancel()
        state.value = state.value.copy(resolving = true, resolveError = "", choices = emptyList())
        resolveJob = viewModelScope.launch {
            val outcome = block()
            applyOutcome(outcome)
        }
    }

    private fun applyOutcome(outcome: ResolveOutcome) {
        if (outcome is ResolveOutcome.Resolved) {
            state.value = state.value.copy(
                resolving = false,
                destination = outcome.destination,
                destinationSource = outcome.source,
                choices = emptyList(),
                resolveError = "",
                feedback = null,
            )
            return
        }
        if (outcome is ResolveOutcome.Choices) {
            state.value = state.value.copy(
                resolving = false,
                choices = outcome.candidates,
                resolveError = "",
            )
            return
        }
        if (outcome is ResolveOutcome.Failed) {
            state.value = state.value.copy(
                resolving = false,
                choices = emptyList(),
                resolveError = outcome.reason,
            )
        }
    }

    private fun requestSendTab() {
        state.value = state.value.copy(showSendTabRequest = state.value.showSendTabRequest + 1)
    }

    // ---- device commands ----

    fun selectDevice(dongleId: String) {
        deviceRepository.select(dongleId)
        state.value = state.value.copy(connection = ConnectionState.Unknown, feedback = null, mapboxDeviceMessage = "")
        checkConnection()
    }

    fun checkConnection() {
        val device = deviceRepository.selectedDevice()
        if (device == null) {
            state.value = state.value.copy(connection = ConnectionState.Unknown)
            return
        }
        if (device.backend == BackendKind.COMMA_ATHENA && !state.value.hasToken) {
            state.value = state.value.copy(connection = ConnectionState.Unknown)
            return
        }
        connectionJob?.cancel()
        state.value = state.value.copy(connection = ConnectionState.Checking)
        connectionJob = viewModelScope.launch {
            val result = transports.forDevice(device).checkStatus(device)
            if (result is TransportResult.Ok) {
                state.value = state.value.copy(connection = ConnectionState.Online(result.value))
            } else if (result is TransportResult.Failed) {
                state.value = state.value.copy(connection = ConnectionState.Unreachable(TransportErrorText.describe(result.error)))
            }
        }
    }

    fun sendDestination() {
        val destination = state.value.destination ?: return
        val device = deviceRepository.selectedDevice() ?: return
        if (state.value.sending) {
            return
        }
        state.value = state.value.copy(sending = true, feedback = null)
        viewModelScope.launch {
            // before the destination, so that the first route request of the device already uses the token
            val mapboxNote = deliverMapboxTokenIfNeeded(device)
            val result = transports.forDevice(device).setDestination(device, destination)
            val feedback: SendFeedback
            if (result is TransportResult.Ok) {
                var message = "Destination sent to ${device.displayName()}."
                if (result.value.queued) {
                    message = "Queued by athena: ${result.value.note}"
                }
                if (mapboxNote.isNotEmpty()) {
                    message = message + " " + mapboxNote
                }
                feedback = SendFeedback(true, message, clock())
            } else {
                val error = (result as TransportResult.Failed).error
                feedback = SendFeedback(false, TransportErrorText.describe(error), clock())
            }
            state.value = state.value.copy(sending = false, feedback = feedback)
        }
    }

    fun clearDestinationOnDevice() {
        val device = deviceRepository.selectedDevice() ?: return
        if (state.value.sending) {
            return
        }
        state.value = state.value.copy(sending = true, feedback = null)
        viewModelScope.launch {
            val result = transports.forDevice(device).clearDestination(device)
            val feedback: SendFeedback
            if (result is TransportResult.Ok) {
                feedback = SendFeedback(true, "Destination cleared on ${device.displayName()}.", clock())
            } else {
                val error = (result as TransportResult.Failed).error
                feedback = SendFeedback(false, TransportErrorText.describe(error), clock())
            }
            state.value = state.value.copy(sending = false, feedback = feedback)
        }
    }

    // ---- account and devices ----

    fun showTokenDialog() {
        state.value = state.value.copy(tokenDialogVisible = true, accountMessage = "")
    }

    fun hideTokenDialog() {
        state.value = state.value.copy(tokenDialogVisible = false, accountMessage = "")
    }

    /** Verifies [rawToken] against the comma API and stores it encrypted only when it is accepted. */
    fun saveToken(rawToken: String) {
        val token = TokenStore.normalize(rawToken)
        if (token.isEmpty()) {
            state.value = state.value.copy(accountMessage = "Paste a token first.")
            return
        }
        if (!JwtInfo.looksLikeJwt(token)) {
            state.value = state.value.copy(accountMessage = "This does not look like a JWT (three parts separated by dots).")
            return
        }
        state.value = state.value.copy(accountBusy = true, accountMessage = "")
        viewModelScope.launch {
            val result = commaTransport.verifyToken(token)
            if (result is TransportResult.Ok) {
                withContext(ioDispatcher) { tokenStore.save(token) }
                state.value = state.value.copy(
                    accountBusy = false,
                    tokenDialogVisible = false,
                    accountEmail = result.value.email,
                    accountMessage = "",
                )
                refreshTokenState()
                refreshDevices()
            } else {
                val error = (result as TransportResult.Failed).error
                state.value = state.value.copy(
                    accountBusy = false,
                    accountMessage = "Token not saved. " + TransportErrorText.describe(error),
                )
            }
        }
    }

    fun removeToken() {
        tokenStore.clear()
        state.value = state.value.copy(
            hasToken = false,
            tokenExpirySeconds = null,
            accountEmail = "",
            connection = ConnectionState.Unknown,
        )
    }

    fun refreshDevices() {
        if (state.value.refreshingDevices) {
            return
        }
        state.value = state.value.copy(refreshingDevices = true, devicesMessage = "")
        viewModelScope.launch {
            val result = commaTransport.listDevices()
            if (result is TransportResult.Ok) {
                deviceRepository.replaceAccountDevices(result.value)
                var message = "${result.value.size} device(s) on the comma account."
                if (result.value.isEmpty()) {
                    message = "No devices on this comma account."
                }
                state.value = state.value.copy(refreshingDevices = false, devicesMessage = message)
                checkConnection()
            } else {
                val error = (result as TransportResult.Failed).error
                state.value = state.value.copy(refreshingDevices = false, devicesMessage = TransportErrorText.describe(error))
            }
        }
    }

    /** Returns an error message, or "" when the device was added. */
    fun addManualDevice(dongleIdText: String, alias: String): String {
        val dongleId = DongleIdRules.normalize(dongleIdText)
        if (!DongleIdRules.isValid(dongleId)) {
            return "A dongle ID is 16 hexadecimal characters (0-9, a-f)."
        }
        val device = PairedDevice(
            dongleId = dongleId,
            alias = alias.trim(),
            backend = BackendKind.COMMA_ATHENA,
            addedManually = true,
        )
        if (!deviceRepository.addManual(device)) {
            return "This device is already in the list."
        }
        if (deviceRepository.devices.value.size == 1) {
            checkConnection()
        }
        return ""
    }

    fun removeDevice(dongleId: String) {
        val wasSelected = deviceRepository.selectedDongleId.value == dongleId
        deviceRepository.remove(dongleId)
        if (wasSelected) {
            state.value = state.value.copy(connection = ConnectionState.Unknown)
            checkConnection()
        }
    }

    // ---- Mapbox ----

    fun showMapboxDialog() {
        state.value = state.value.copy(mapboxDialogVisible = true, mapboxMessage = "")
    }

    fun hideMapboxDialog() {
        state.value = state.value.copy(mapboxDialogVisible = false, mapboxMessage = "")
    }

    /** Checks [rawToken] with Mapbox's Tokens API and stores it encrypted only when it is a valid public token. */
    fun saveMapboxToken(rawToken: String) {
        val setup = mapbox ?: return
        val token = rawToken.trim()
        if (token.isEmpty()) {
            state.value = state.value.copy(mapboxMessage = "Paste a token first.")
            return
        }
        if (MapboxTokenRules.isSecretToken(token)) {
            state.value = state.value.copy(mapboxMessage = "This is a secret token (sk.). Paste your Default public token (pk.) instead.")
            return
        }
        if (!MapboxTokenRules.isPublicToken(token)) {
            state.value = state.value.copy(mapboxMessage = "A Mapbox public token starts with pk. (Default public token on the Tokens page).")
            return
        }
        state.value = state.value.copy(mapboxBusy = true, mapboxMessage = "")
        viewModelScope.launch {
            val check = setup.verifier.verify(token)
            if (check is MapboxTokenCheck.Valid) {
                try {
                    withContext(ioDispatcher) { setup.tokenStore.save(token) }
                } catch (error: GeneralSecurityException) {
                    state.value = state.value.copy(mapboxBusy = false, mapboxMessage = "Token not saved. " + (error.message ?: "Keystore error"))
                    return@launch
                }
                state.value = state.value.copy(mapboxBusy = false, mapboxDialogVisible = false, hasMapboxToken = true)
                if (state.value.hasToken && deviceRepository.selectedDevice() != null) {
                    sendMapboxTokenNow(reportUnsupported = false)
                }
            } else if (check is MapboxTokenCheck.Rejected) {
                state.value = state.value.copy(mapboxBusy = false, mapboxMessage = "Token not saved: Mapbox answered ${check.code}.")
            } else if (check is MapboxTokenCheck.Failed) {
                state.value = state.value.copy(mapboxBusy = false, mapboxMessage = "Token not saved. " + TransportErrorText.describe(check.error))
            }
        }
    }

    /** Removes the token from this phone only: the devices keep their copy (see removeMapboxTokenFromDevice). */
    fun removeMapboxToken() {
        mapbox?.tokenStore?.clear()
        state.value = state.value.copy(hasMapboxToken = false, mapboxDeviceMessage = "")
    }

    /** Sends the stored Mapbox token to the selected device, which then asks Mapbox for its routes itself. */
    fun sendMapboxTokenToDevice() {
        if (state.value.mapboxDeviceBusy) {
            return
        }
        viewModelScope.launch {
            sendMapboxTokenNow(reportUnsupported = true)
        }
    }

    /** Removes the Mapbox token from the selected device: it goes back to comma's route service. */
    fun removeMapboxTokenFromDevice() {
        val setup = mapbox ?: return
        val device = deviceRepository.selectedDevice() ?: return
        if (state.value.mapboxDeviceBusy) {
            return
        }
        state.value = state.value.copy(mapboxDeviceBusy = true, mapboxDeviceMessage = "")
        viewModelScope.launch {
            val result = transports.forDevice(device).setMapboxToken(device, "")
            val message: String
            if (result is TransportResult.Ok) {
                setup.deliveries.forget(device.dongleId)
                if (result.value.queued) {
                    message = "Queued by athena: ${result.value.note}"
                } else {
                    message = "Mapbox token removed from ${device.displayName()}: it uses comma's route service again."
                }
            } else {
                val error = (result as TransportResult.Failed).error
                if (error is TransportError.MethodNotAvailable) {
                    // without setMapboxToken the device cannot have a token: nothing to remove
                    setup.deliveries.forget(device.dongleId)
                    message = unsupportedNote(device)
                } else {
                    message = "Mapbox token not removed. " + TransportErrorText.describe(error)
                }
            }
            state.value = state.value.copy(mapboxDeviceBusy = false, mapboxDeviceMessage = message)
        }
    }

    /**
     * [reportUnsupported]: a device without setMapboxToken (unmodified openpilot) gets a note only when the user
     * asked for the token to be sent; it is never an error.
     */
    private suspend fun sendMapboxTokenNow(reportUnsupported: Boolean) {
        val setup = mapbox ?: return
        val device = deviceRepository.selectedDevice() ?: return
        val token = withContext(ioDispatcher) { setup.tokenStore.load() } ?: return
        state.value = state.value.copy(mapboxDeviceBusy = true, mapboxDeviceMessage = "")
        val result = transports.forDevice(device).setMapboxToken(device, token)
        val message: String
        if (result is TransportResult.Ok) {
            if (result.value.queued) {
                message = "Queued by athena: ${result.value.note}"
            } else {
                setup.deliveries.markDelivered(device.dongleId, token)
                message = "Mapbox token sent to ${device.displayName()}: it computes its routes with your Mapbox account."
            }
        } else {
            val error = (result as TransportResult.Failed).error
            if (error is TransportError.MethodNotAvailable) {
                if (reportUnsupported) {
                    message = unsupportedNote(device)
                } else {
                    message = ""
                }
            } else {
                message = mapboxNotSent(error)
            }
        }
        state.value = state.value.copy(mapboxDeviceBusy = false, mapboxDeviceMessage = message)
    }

    /** Delivers the Mapbox token to a device that does not have this token yet. Returns a note for the send feedback. */
    private suspend fun deliverMapboxTokenIfNeeded(device: PairedDevice): String {
        val setup = mapbox ?: return ""
        val token = withContext(ioDispatcher) { setup.tokenStore.load() } ?: return ""
        if (setup.deliveries.isDelivered(device.dongleId, token)) {
            return ""
        }
        val result = transports.forDevice(device).setMapboxToken(device, token)
        if (result is TransportResult.Ok) {
            // a queued token is sent again with the next destination
            if (result.value.queued) {
                return ""
            }
            setup.deliveries.markDelivered(device.dongleId, token)
            return "Your Mapbox token was sent too."
        }
        val error = (result as TransportResult.Failed).error
        // unmodified openpilot has no setMapboxToken: the destination goes out as usual, without a word
        if (error is TransportError.MethodNotAvailable) {
            return ""
        }
        return mapboxNotSent(error)
    }

    private suspend fun refreshTokenState() {
        var mapboxStored = false
        if (mapbox != null) {
            mapboxStored = mapbox.tokenStore.hasToken()
        }
        state.value = state.value.copy(hasMapboxToken = mapboxStored)
        val token = withContext(ioDispatcher) { tokenStore.load() }
        if (token == null) {
            state.value = state.value.copy(hasToken = false, tokenExpirySeconds = null)
            return
        }
        state.value = state.value.copy(hasToken = true, tokenExpirySeconds = JwtInfo.expirySeconds(token))
    }

    companion object {
        fun mapboxNotSent(error: TransportError): String {
            return "Mapbox token not sent. " + TransportErrorText.describe(error)
        }

        fun unsupportedNote(device: PairedDevice): String {
            return "${device.displayName()} takes no Mapbox token (its software has no setMapboxToken): it uses comma's route service."
        }

        fun factory(container: AppContainer): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val viewModel = MainViewModel(
                        resolver = container.destinationResolver,
                        placeSearch = container.placeSearch,
                        transports = container.transports,
                        commaTransport = container.commaTransport,
                        tokenStore = container.tokenStore,
                        favoritesRepository = container.favoritesRepository,
                        deviceRepository = container.deviceRepository,
                        ioDispatcher = container.ioDispatcher,
                        mapbox = container.mapbox,
                    )
                    return viewModel as T
                }
            }
        }
    }
}
