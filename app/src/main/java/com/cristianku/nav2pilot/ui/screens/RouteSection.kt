package com.cristianku.nav2pilot.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cristianku.nav2pilot.mapbox.MapboxEndpoints
import com.cristianku.nav2pilot.mapbox.RoutePreview
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.LatLng
import com.cristianku.nav2pilot.ui.MainUiState
import com.cristianku.nav2pilot.ui.components.BrandCard
import com.cristianku.nav2pilot.ui.components.IconBadge
import com.cristianku.nav2pilot.ui.components.SectionLabel
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.util.Locale

/** What the route section does, wired to the view model by the root. */
class RouteActions(
    val onShowMapboxDialog: () -> Unit,
    val onRefreshRoutes: () -> Unit,
    val onSelectRoute: (Int) -> Unit,
    val onAddStop: (Double, Double) -> Unit,
    val onRemoveStop: (Int) -> Unit,
    val onLocationPermissionResult: (Boolean) -> Unit,
)

/**
 * Route preview on a Mapbox map: the fastest route and its alternatives, stops added with a long press.
 * What is picked here is what the device follows (sunnypilot setNavRoute).
 */
@Composable
fun RouteSection(state: MainUiState, actions: RouteActions, mapHeight: Dp = 280.dp) {
    if (!state.routePlanningAvailable || state.destination == null) {
        return
    }
    Column {
        SectionLabel("Route")
        if (state.hasMapboxToken && state.mapboxMapToken.isNotEmpty()) {
            BrandCard {
                PlannedRoute(state = state, mapboxToken = state.mapboxMapToken, actions = actions, mapHeight = mapHeight)
            }
            Text(
                "Long-press the map to add a stop. The device follows the route you pick: through your stops, or through points of the alternative.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 4.dp),
            )
        } else {
            BrandCard {
                MapboxSetupPrompt(onShowMapboxDialog = actions.onShowMapboxDialog)
            }
        }
    }
}

/** Optional: a compact prompt, so it does not stand between the destination and the send button. */
@Composable
private fun MapboxSetupPrompt(onShowMapboxDialog: () -> Unit) {
    Row(
        modifier = Modifier.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(
            painter = rememberVectorPainter(Icons.Filled.LocationOn),
            tint = MaterialTheme.colorScheme.primary,
            background = MaterialTheme.colorScheme.primaryContainer,
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Route preview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Optional, with a free Mapbox account.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onShowMapboxDialog, shape = RoundedCornerShape(12.dp)) {
            Text("Set up Mapbox")
        }
    }
}

@Composable
private fun PlannedRoute(state: MainUiState, mapboxToken: String, actions: RouteActions, mapHeight: Dp) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // asked once by itself; afterwards only from the button, or through the app settings when "don't ask again"
    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    var permissionBlocked by rememberSaveable { mutableStateOf(false) }
    var openedSettings by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        var granted = false
        for (value in result.values) {
            if (value) {
                granted = true
            }
        }
        if (!granted) {
            val activity = findActivity(context)
            if (activity != null && !activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                permissionBlocked = true
            }
        }
        actions.onLocationPermissionResult(granted)
    }
    val locationPermissions = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

    LaunchedEffect(state.locationPermissionNeeded) {
        if (state.locationPermissionNeeded && !permissionAsked) {
            permissionAsked = true
            permissionLauncher.launch(locationPermissions)
        }
    }

    // back from the app settings: try again with whatever was allowed there
    val currentActions by rememberUpdatedState(actions)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && openedSettings) {
                openedSettings = false
                currentActions.onRefreshRoutes()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(mapHeight)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        ) {
            RouteMap(state = state, mapboxToken = mapboxToken, actions = actions)
            MapAttribution(modifier = Modifier.align(Alignment.BottomEnd))
        }

        if (state.routesLoading) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Computing routes…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (state.routesMessage.isNotEmpty() && !state.routesLoading) {
            Row(
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    state.routesMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                if (!hasLocationPermission(context)) {
                    TextButton(
                        onClick = {
                            if (permissionBlocked) {
                                openedSettings = true
                                openAppSettings(context)
                            } else {
                                permissionAsked = true
                                permissionLauncher.launch(locationPermissions)
                            }
                        },
                    ) {
                        Text("Allow")
                    }
                } else {
                    IconButton(onClick = actions.onRefreshRoutes) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Compute the routes again")
                    }
                }
            }
        }

        for (index in state.routes.indices) {
            if (index > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 56.dp))
            }
            RouteRow(
                route = state.routes[index],
                index = index,
                selected = index == state.selectedRouteIndex,
                onSelect = { actions.onSelectRoute(index) },
            )
        }

        if (state.stops.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                "Stops",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp),
            )
        }
        for (index in state.stops.indices) {
            StopRow(stop = state.stops[index], index = index, onRemove = { actions.onRemoveStop(index) })
        }
    }
}

@Composable
private fun RouteRow(route: RoutePreview, index: Int, selected: Boolean, onSelect: () -> Unit) {
    var subtitle = "Fastest"
    if (index > 0) {
        subtitle = "Alternative $index"
    }
    if (route.summary.isNotEmpty()) {
        subtitle += " · " + route.summary
    }
    var titleColor = MaterialTheme.colorScheme.onSurfaceVariant
    var titleWeight = FontWeight.Normal
    if (selected) {
        titleColor = MaterialTheme.colorScheme.onSurface
        titleWeight = FontWeight.SemiBold
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                RouteFormatting.duration(route.durationSeconds) + " · " + RouteFormatting.distance(route.distanceMeters),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = titleWeight,
                color = titleColor,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StopRow(stop: Destination, index: Int, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(STOP_COLOR, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stop.name, style = MaterialTheme.typography.bodyMedium)
            Text(
                stop.coordinatesText(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Remove stop ${index + 1}, ${stop.name}",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Mapbox's terms ask for this text on every map that shows their tiles. */
@Composable
private fun MapAttribution(modifier: Modifier) {
    val context = LocalContext.current
    Text(
        "© Mapbox © OpenStreetMap",
        style = MaterialTheme.typography.labelSmall,
        color = Color(0xFF333333),
        modifier = modifier
            .padding(4.dp)
            .background(Color(0xCCFFFFFF), RoundedCornerShape(4.dp))
            .clickable { openUrl(context, MAPBOX_ATTRIBUTION_URL) }
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

/** The map: routes (the picked one on top, in the accent colour), the destination and the stops. */
@SuppressLint("ClickableViewAccessibility") // the touch listener only keeps the page from scrolling, it handles no click
@Composable
private fun RouteMap(state: MainUiState, mapboxToken: String, actions: RouteActions) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val selectedColor = MaterialTheme.colorScheme.primary.toArgb()
    val currentActions by rememberUpdatedState(actions)

    val mapView = remember {
        configureOsmdroid(context)
        val view = MapView(context)
        view.setMultiTouchControls(true)
        view.zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        view.isTilesScaledToDpi = true
        view.minZoomLevel = 3.0
        view.maxZoomLevel = 19.0
        // a drag on the map moves the map, not the page around it
        view.setOnTouchListener { touched, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                touched.parent?.requestDisallowInterceptTouchEvent(true)
            } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                touched.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
        view
    }
    val holder = remember { MapHolder() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mapView.onResume()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                mapView.onPause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = Modifier.fillMaxSize(),
        update = { view ->
            if (holder.token != mapboxToken) {
                holder.token = mapboxToken
                view.setTileSource(MapboxStreetsTileSource(mapboxToken))
            }
            drawOverlays(
                view = view,
                state = state,
                selectedColor = selectedColor,
                onLongPress = { latitude, longitude -> currentActions.onAddStop(latitude, longitude) },
                onSelectRoute = { index -> currentActions.onSelectRoute(index) },
            )
            val fitKey = FitKey(state.routes, state.destination, state.origin)
            if (holder.fitted != fitKey) {
                holder.fitted = fitKey
                fit(view, state)
            }
        },
    )
}

/** State of the map view that must not trigger a recomposition. */
private class MapHolder {
    var token: String? = null
    var fitted: FitKey? = null
}

/** The camera is moved again only when one of these changes, not when a route is picked. */
private data class FitKey(
    val routes: List<RoutePreview>,
    val destination: Destination?,
    val origin: LatLng?,
)

private fun drawOverlays(
    view: MapView,
    state: MainUiState,
    selectedColor: Int,
    onLongPress: (Double, Double) -> Unit,
    onSelectRoute: (Int) -> Unit,
) {
    val density = view.context.resources.displayMetrics.density
    view.overlays.clear()

    // a long press anywhere on the map adds a stop there; first, so the routes and pins above get their taps
    view.overlays.add(
        MapEventsOverlay(
            object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(point: GeoPoint?): Boolean {
                    return false
                }

                override fun longPressHelper(point: GeoPoint?): Boolean {
                    if (point == null) {
                        return false
                    }
                    onLongPress(point.latitude, point.longitude)
                    return true
                }
            },
        ),
    )

    // unpicked routes first, so the picked one is drawn on top
    val order = mutableListOf<Int>()
    for (index in state.routes.indices) {
        if (index != state.selectedRouteIndex) {
            order.add(index)
        }
    }
    if (state.selectedRouteIndex < state.routes.size) {
        order.add(state.selectedRouteIndex)
    }
    for (index in order) {
        val line = Polyline(view)
        line.setPoints(geoPoints(state.routes[index].coordinates))
        if (index == state.selectedRouteIndex) {
            line.outlinePaint.color = selectedColor
            line.outlinePaint.strokeWidth = 7 * density
        } else {
            line.outlinePaint.color = UNPICKED_ROUTE_COLOR
            line.outlinePaint.strokeWidth = 5 * density
        }
        line.outlinePaint.strokeCap = Paint.Cap.ROUND
        line.outlinePaint.strokeJoin = Paint.Join.ROUND
        line.infoWindow = null
        line.setOnClickListener { _, _, _ ->
            onSelectRoute(index)
            true
        }
        view.overlays.add(line)
    }

    for (index in state.stops.indices) {
        val stop = state.stops[index]
        view.overlays.add(pin(view, GeoPoint(stop.latitude, stop.longitude), circleIcon(view.context, STOP_COLOR.toArgb(), "${index + 1}")))
    }
    val destination = state.destination
    if (destination != null) {
        view.overlays.add(pin(view, GeoPoint(destination.latitude, destination.longitude), circleIcon(view.context, DESTINATION_COLOR, "")))
    }
    view.invalidate()
}

private fun pin(view: MapView, point: GeoPoint, icon: Drawable): Marker {
    val marker = Marker(view)
    marker.position = point
    marker.icon = icon
    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    marker.infoWindow = null
    // no info window and no camera jump on tap
    marker.setOnMarkerClickListener { _, _ -> true }
    return marker
}

/** A filled circle with a white ring and an optional number, like the stop badges in the list. */
private fun circleIcon(context: Context, color: Int, label: String): Drawable {
    val density = context.resources.displayMetrics.density
    val size = (28 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f

    val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    ring.color = android.graphics.Color.WHITE
    canvas.drawCircle(center, center, center, ring)

    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    fill.color = color
    canvas.drawCircle(center, center, center - 3 * density, fill)

    if (label.isEmpty()) {
        canvas.drawCircle(center, center, 4 * density, ring)
    } else {
        val text = Paint(Paint.ANTI_ALIAS_FLAG)
        text.color = android.graphics.Color.WHITE
        text.textSize = 13 * density
        text.typeface = Typeface.DEFAULT_BOLD
        text.textAlign = Paint.Align.CENTER
        val baseline = center - (text.descent() + text.ascent()) / 2
        canvas.drawText(label, center, baseline, text)
    }
    return BitmapDrawable(context.resources, bitmap)
}

private fun fit(view: MapView, state: MainUiState) {
    val points = mutableListOf<GeoPoint>()
    for (route in state.routes) {
        points.addAll(geoPoints(route.coordinates))
    }
    val destination = state.destination
    if (destination != null) {
        points.add(GeoPoint(destination.latitude, destination.longitude))
    }
    val origin = state.origin
    if (origin != null && state.routes.isNotEmpty()) {
        points.add(GeoPoint(origin.latitude, origin.longitude))
    }
    if (points.isEmpty()) {
        return
    }
    // the camera needs the view's size, which it only has after the first layout
    if (view.width == 0 || view.height == 0) {
        view.addOnFirstLayoutListener { _, _, _, _, _ -> fitPoints(view, points) }
        return
    }
    fitPoints(view, points)
}

private fun fitPoints(view: MapView, points: List<GeoPoint>) {
    // only the destination (no routes yet): an overview of one point would zoom in to the maximum
    if (points.size == 1) {
        view.controller.setZoom(13.0)
        view.controller.setCenter(points[0])
        return
    }
    val density = view.context.resources.displayMetrics.density
    view.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(points), false, (36 * density).toInt())
}

private fun geoPoints(points: List<LatLng>): List<GeoPoint> {
    val result = ArrayList<GeoPoint>(points.size)
    for (point in points) {
        result.add(GeoPoint(point.latitude, point.longitude))
    }
    return result
}

/** Mapbox Streets raster tiles, with the user's public token in every tile URL. */
private class MapboxStreetsTileSource(private val token: String) : OnlineTileSourceBase(
    "MapboxStreets",
    0,
    22,
    256,
    "",
    arrayOf(MapboxEndpoints.STREETS_TILES_URL),
    "© Mapbox © OpenStreetMap",
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        return baseUrl +
            MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getX(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) +
            "@2x?access_token=" + Uri.encode(token)
    }
}

/** osmdroid keeps its settings and tile cache in the app's own cache directory: no storage permission. */
private fun configureOsmdroid(context: Context) {
    val configuration = Configuration.getInstance()
    configuration.load(context, context.getSharedPreferences(OSMDROID_PREFERENCES, Context.MODE_PRIVATE))
    configuration.userAgentValue = context.packageName
    val base = File(context.cacheDir, "osmdroid")
    configuration.osmdroidBasePath = base
    configuration.osmdroidTileCache = File(base, "tiles")
}

private fun hasLocationPermission(context: Context): Boolean {
    val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    return coarse == PackageManager.PERMISSION_GRANTED || fine == PackageManager.PERMISSION_GRANTED
}

private fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (noBrowser: android.content.ActivityNotFoundException) {
        // nothing to open it with; the text stays on the map
    }
}

private fun findActivity(context: Context): Activity? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }
        current = current.baseContext
    }
    return null
}

object RouteFormatting {
    fun duration(seconds: Double): String {
        val minutes = Math.round(seconds / 60).toInt()
        if (minutes < 60) {
            return "${maxOf(1, minutes)} min"
        }
        return "${minutes / 60} h " + String.format(Locale.US, "%02d", minutes % 60) + " min"
    }

    fun distance(meters: Double): String {
        if (meters < 1000) {
            return "${Math.round(meters)} m"
        }
        return String.format(Locale.US, "%.1f km", meters / 1000)
    }
}

private val STOP_COLOR = Color(0xFFF08C00)
private const val DESTINATION_COLOR = 0xFFE03131.toInt()
private const val UNPICKED_ROUTE_COLOR = 0xFF9AA5B1.toInt()
private const val OSMDROID_PREFERENCES = "osmdroid"
private const val MAPBOX_ATTRIBUTION_URL = "https://www.mapbox.com/about/maps/"
