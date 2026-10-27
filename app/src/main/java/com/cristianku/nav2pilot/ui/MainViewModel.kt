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
import com.cristianku.nav2pilot.mapbox.RoutePlanning
import com.cristianku.nav2pilot.mapbox.RoutePreview
import com.cristianku.nav2pilot.mapbox.RouteShaping
import com.cristianku.nav2pilot.mapbox.RouteWaypoint
import com.cristianku.nav2pilot.model.BackendKind
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.CoordinateRules
import com.cristianku.nav2pilot.model.DongleIdRules
import com.cristianku.nav2pilot.model.LatLng
import com.cristianku.nav2pilot.model.PairedDevice
import com.cristianku.nav2pilot.resolve.DestinationResolver
import com.cristianku.nav2pilot.resolve.ResolveOutcome
import com.cristianku.nav2pilot.security.JwtInfo
import com.cristianku.nav2pilot.security.TokenStore
import com.cristianku.nav2pilot.transport.CommaAthenaTransport
import com.cristianku.nav2pilot.transport.DemoAccount
import com.cristianku.nav2pilot.transport.DestinationSender
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
    // route preview on the Mapbox map
    val routePlanningAvailable: Boolean = false,
    /** The Mapbox public token for the map tiles; "" without one. */
    val mapboxMapToken: String = "",
    val routes: List<RoutePreview> = emptyList(),
    val selectedRouteIndex: Int = 0,
    val stops: List<Destination> = emptyList(),
    val routesLoading: Boolean = false,
    val routesMessage: String = "",
    val origin: LatLng? = null,
    /** True while the preview waits for the location permission, which only the screen can ask for. */
    val locationPermissionNeeded: Boolean = false,
    /** Incremented when the map should be brought to front (share received, favorite picked). */
    val showSendTabRequest: Int = 0,
    /** Incremented when the map should move to the phone's position (the recentre button). */
    val recenterRequest: Int = 0,
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
    private val routePlanning: RoutePlanning? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {

    private val state = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = state

    val favorites: StateFlow<List<Favorite>> = favoritesRepository.favorites
    val devices: StateFlow<List<PairedDevice>> = deviceRepository.devices
    val selectedDongleId: StateFlow<String> = deviceRepository.selectedDongleId

    // the Mapbox token goes with every destination, before it, so the first route request of the car already uses it
    private val sender = DestinationSender(transports) { loadMapboxToken() }

    private var resolveJob: Job? = null
    private var connectionJob: Job? = null
    private var routesJob: Job? = null

    init {
        state.value = state.value.copy(
            geocoderAvailable = placeSearch.isAvailable(),
            routePlanningAvailable = routePlanning != null && mapbox != null,
        )
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
        destinationChanged()
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
        destinationChanged()
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

    /** Long press on the map with no destination yet: that point becomes the destination. */
    fun pickOnMap(latitude: Double, longitude: Double) {
        if (!CoordinateRules.isValid(latitude, longitude)) {
            return
        }
        resolveJob?.cancel()
        state.value = state.value.copy(
            destination = Destination(latitude = latitude, longitude = longitude, name = "Dropped pin"),
            destinationSource = DestinationResolver.SOURCE_MAP,
            choices = emptyList(),
            resolving = false,
            resolveError = "",
            feedback = null,
        )
        destinationChanged()
    }

    /** Closes the search results without choosing one. */
    fun dismissChoices() {
        state.value = state.value.copy(choices = emptyList(), resolveError = "")
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
        destinationChanged()
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
            destinationChanged()
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
        state.value = state.value.copy(connection = ConnectionState.Unknown, feedback = null)
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
        val waypoints = plannedWaypoints()
        viewModelScope.launch {
            val outcome = sender.send(device, destination, waypoints)
            state.value = state.value.copy(sending = false, feedback = SendFeedback(outcome.success, outcome.message, clock()))
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
        if (!JwtInfo.looksLikeJwt(token) && !DemoAccount.isDemoToken(token)) {
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
                state.value = state.value.copy(mapboxBusy = false, mapboxDialogVisible = false, hasMapboxToken = true, mapboxMapToken = token)
                // the selected car gets it right away; if it is off, with the next destination
                val device = deviceRepository.selectedDevice()
                if (device != null) {
                    sendMapboxToken(token, device)
                }
                refreshRoutes()
            } else if (check is MapboxTokenCheck.Rejected) {
                state.value = state.value.copy(mapboxBusy = false, mapboxMessage = "Token not saved: Mapbox answered ${check.code}.")
            } else if (check is MapboxTokenCheck.Failed) {
                state.value = state.value.copy(mapboxBusy = false, mapboxMessage = "Token not saved. " + TransportErrorText.describe(check.error))
            }
        }
    }

    /** Removes the token from this phone and from the selected car, which goes back to comma's route service. */
    fun removeMapboxToken() {
        mapbox?.tokenStore?.clear()
        routesJob?.cancel()
        state.value = state.value.copy(
            hasMapboxToken = false,
            mapboxMapToken = "",
            routes = emptyList(),
            routesLoading = false,
            routesMessage = "",
            locationPermissionNeeded = false,
        )
        val device = deviceRepository.selectedDevice() ?: return
        viewModelScope.launch {
            sendMapboxToken("", device)
        }
    }

    private suspend fun loadMapboxToken(): String? {
        val setup = mapbox ?: return null
        return withContext(ioDispatcher) { setup.tokenStore.load() }
    }

    /**
     * The car asks Mapbox for its routes with this token ("" removes it). Silent on purpose: a car that is off,
     * or runs software without setMapboxToken (unmodified openpilot), keeps routing as before, and the token goes
     * again with the next destination.
     */
    private suspend fun sendMapboxToken(token: String, device: PairedDevice) {
        transports.forDevice(device).setMapboxToken(device, token)
    }

    // ---- route preview ----

    /** Route previews from the phone's position to the destination through the stops. */
    fun refreshRoutes() {
        routesJob?.cancel()
        val planning = routePlanning
        val target = state.value.destination
        if (planning == null || target == null || !state.value.hasMapboxToken) {
            state.value = state.value.copy(routes = emptyList(), routesLoading = false, routesMessage = "", locationPermissionNeeded = false)
            return
        }
        if (!planning.location.hasPermission()) {
            state.value = state.value.copy(
                routes = emptyList(),
                routesLoading = false,
                routesMessage = LOCATION_NEEDED_MESSAGE,
                locationPermissionNeeded = true,
            )
            return
        }
        state.value = state.value.copy(routesLoading = true, routesMessage = "", locationPermissionNeeded = false)
        routesJob = viewModelScope.launch {
            val position = planning.location.currentLocation()
            state.value = state.value.copy(origin = position)
            if (position == null) {
                state.value = state.value.copy(
                    routes = emptyList(),
                    routesLoading = false,
                    routesMessage = "No position for this phone. Turn location on and try again.",
                )
                return@launch
            }

            val stopPoints = mutableListOf<LatLng>()
            for (stop in state.value.stops) {
                stopPoints.add(LatLng(stop.latitude, stop.longitude))
            }
            val result = planning.directions.routes(position, stopPoints, LatLng(target.latitude, target.longitude))
            if (result is TransportResult.Ok) {
                val found = result.value
                var selected = state.value.selectedRouteIndex
                if (selected >= found.size) {
                    selected = 0
                }
                var message = ""
                if (found.isEmpty()) {
                    message = "Mapbox found no route."
                }
                state.value = state.value.copy(routes = found, selectedRouteIndex = selected, routesLoading = false, routesMessage = message)
            } else {
                val error = (result as TransportResult.Failed).error
                var message = TransportErrorText.describe(error)
                if (error == TransportError.Unauthorized) {
                    message = "Mapbox rejected the token (HTTP 401). Check it in Devices."
                } else if (error is TransportError.DeviceRejected) {
                    message = "Mapbox: " + error.reason
                }
                state.value = state.value.copy(routes = emptyList(), selectedRouteIndex = 0, routesLoading = false, routesMessage = message)
            }
        }
    }

    /**
     * The phone's position for the map ("you are here"); [recenter] also moves the map there. Nothing happens
     * without the location permission, which only the screen can ask for.
     */
    fun refreshMyLocation(recenter: Boolean) {
        val planning = routePlanning ?: return
        if (!planning.location.hasPermission()) {
            return
        }
        viewModelScope.launch {
            val position = planning.location.currentLocation() ?: return@launch
            var request = state.value.recenterRequest
            if (recenter) {
                request = request + 1
            }
            state.value = state.value.copy(origin = position, recenterRequest = request)
        }
    }

    /** The answer of the permission dialog the screen showed. */
    fun onLocationPermissionResult(granted: Boolean) {
        if (granted) {
            refreshRoutes()
            return
        }
        state.value = state.value.copy(locationPermissionNeeded = false, routesMessage = LOCATION_DENIED_MESSAGE)
    }

    fun selectRoute(index: Int) {
        if (index < 0 || index >= state.value.routes.size) {
            return
        }
        state.value = state.value.copy(selectedRouteIndex = index, feedback = null)
    }

    /** Adds a stop before the destination, in the order they are added. */
    fun addStop(latitude: Double, longitude: Double) {
        if (!CoordinateRules.isValid(latitude, longitude)) {
            return
        }
        if (state.value.destination == null || state.value.stops.size >= MAX_STOPS) {
            return
        }
        val stops = state.value.stops.toMutableList()
        stops.add(Destination(latitude = latitude, longitude = longitude, name = "Stop ${stops.size + 1}"))
        state.value = state.value.copy(stops = stops, selectedRouteIndex = 0, feedback = null)
        refreshRoutes()
    }

    fun removeStop(index: Int) {
        val current = state.value.stops
        if (index < 0 || index >= current.size) {
            return
        }
        val renamed = mutableListOf<Destination>()
        for (position in current.indices) {
            if (position == index) {
                continue
            }
            renamed.add(current[position].copy(name = "Stop ${renamed.size + 1}"))
        }
        state.value = state.value.copy(stops = renamed, selectedRouteIndex = 0, feedback = null)
        refreshRoutes()
    }

    /** What the device gets besides the destination: the stops, or via points of the alternative picked on the map. */
    fun plannedWaypoints(): List<RouteWaypoint> {
        val current = state.value
        val waypoints = mutableListOf<RouteWaypoint>()
        for (stop in current.stops) {
            waypoints.add(RouteWaypoint(stop.latitude, stop.longitude, via = false))
        }
        if (waypoints.isEmpty() && current.selectedRouteIndex > 0 && current.selectedRouteIndex < current.routes.size) {
            return RouteShaping.viaPoints(current.routes[current.selectedRouteIndex])
        }
        return waypoints
    }

    /** A new destination starts a new plan. */
    private fun destinationChanged() {
        routesJob?.cancel()
        state.value = state.value.copy(
            stops = emptyList(),
            selectedRouteIndex = 0,
            routes = emptyList(),
            routesLoading = false,
            routesMessage = "",
            locationPermissionNeeded = false,
        )
        if (state.value.destination != null) {
            refreshRoutes()
        }
    }

    private suspend fun refreshTokenState() {
        var mapboxStored = false
        if (mapbox != null) {
            mapboxStored = mapbox.tokenStore.hasToken()
        }
        state.value = state.value.copy(hasMapboxToken = mapboxStored)
        if (mapboxStored && routePlanning != null) {
            // loaded first: state.value read before the suspension would overwrite what changed meanwhile
            val mapToken = loadMapboxToken() ?: ""
            state.value = state.value.copy(mapboxMapToken = mapToken)
        }
        val token = withContext(ioDispatcher) { tokenStore.load() }
        if (token == null) {
            state.value = state.value.copy(hasToken = false, tokenExpirySeconds = null)
            return
        }
        state.value = state.value.copy(hasToken = true, tokenExpirySeconds = JwtInfo.expirySeconds(token))
    }

    companion object {
        // sunnypilot setNavRoute accepts 20 waypoints; three are left for the via points of an alternative
        const val MAX_STOPS = 10

        private const val LOCATION_NEEDED_MESSAGE = "The route preview starts from this phone's position: allow location access."
        private const val LOCATION_DENIED_MESSAGE =
            "No location access: the route preview starts from this phone's position. Allow it in the app settings."

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
                        routePlanning = container.routePlanning,
                    )
                    return viewModel as T
                }
            }
        }
    }
}
