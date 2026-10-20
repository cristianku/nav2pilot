package com.cristianku.nav2pilot.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cristianku.nav2pilot.R
import com.cristianku.nav2pilot.mapbox.MapboxEndpoints
import com.cristianku.nav2pilot.mapbox.OpenFreeMapStyles
import com.cristianku.nav2pilot.mapbox.RoutePreview
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.LatLng
import com.cristianku.nav2pilot.places.MapArea
import com.cristianku.nav2pilot.places.PlaceCategory
import com.cristianku.nav2pilot.ui.MainUiState
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.android.geometry.LatLng as MapLatLng

/** What covers the map at each edge (panel, buttons, system bars), in pixels: the camera keeps clear of it. */
data class MapInsets(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/** Colours of what the app draws on the map, from the theme. */
data class MapColors(
    val selectedRoute: Int,
    val otherRoute: Int,
    val myLocation: Int,
)

/**
 * The full-screen map (MapLibre): the phone's position, the destination, the stops, the routes and the points of
 * interest.
 *
 * Base map: Mapbox Streets raster tiles when the user has a Mapbox token (their routes are Mapbox routes, shown on
 * a Mapbox map), otherwise OpenFreeMap, free and without a key. Dark style with the dark theme.
 */
@Composable
fun NavMap(
    state: MainUiState,
    darkTheme: Boolean,
    insets: MapInsets,
    colors: MapColors,
    onTap: () -> Unit,
    onLongPress: (Double, Double) -> Unit,
    onSelectRoute: (Int) -> Unit,
    onTapPlace: (Int) -> Unit,
    onAreaChange: (MapArea) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnSelectRoute by rememberUpdatedState(onSelectRoute)
    val currentOnTapPlace by rememberUpdatedState(onTapPlace)
    val currentOnAreaChange by rememberUpdatedState(onAreaChange)

    val holder = remember { MapHolder() }
    val mapView = remember {
        // must run before the first MapView is created
        MapLibre.getInstance(context)
        val view = MapView(context)
        view.onCreate(null)
        view.getMapAsync { map ->
            holder.map = map
            map.uiSettings.setLogoEnabled(false)
            map.uiSettings.setTiltGesturesEnabled(false)
            map.uiSettings.setAttributionGravity(Gravity.BOTTOM or Gravity.START)
            map.uiSettings.setCompassGravity(Gravity.TOP or Gravity.END)
            map.addOnMapClickListener { point ->
                // the places are drawn over the routes
                val place = indexAt(map, point, context, LAYER_PLACES, PROPERTY_PLACE_INDEX)
                if (place >= 0) {
                    currentOnTapPlace(place)
                } else {
                    val route = indexAt(map, point, context, LAYER_OTHER_ROUTES, PROPERTY_ROUTE_INDEX)
                    if (route >= 0) {
                        currentOnSelectRoute(route)
                    } else {
                        currentOnTap()
                    }
                }
                true
            }
            // where the places menu looks: the part of the map that panels and buttons leave visible
            map.addOnCameraIdleListener {
                val area = visibleArea(map, view, holder.insets)
                if (area != null) {
                    currentOnAreaChange(area)
                }
            }
            map.addOnMapLongClickListener { point ->
                currentOnLongPress(point.latitude, point.longitude)
                true
            }
            render(holder, context)
        }
        view
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                mapView.onStart()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                mapView.onResume()
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                mapView.onPause()
            } else if (event == Lifecycle.Event.ON_STOP) {
                mapView.onStop()
            }
        }
        // the observer first gets the events up to the current state, so a map added while resumed starts too
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                mapView.onPause()
            }
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                mapView.onStop()
            }
            mapView.onDestroy()
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier,
        update = {
            holder.state = state
            holder.styleKey = styleKey(state, darkTheme)
            holder.insets = insets
            holder.colors = colors
            render(holder, context)
        },
    )
}

/** What the map view needs between recompositions; changing it does not recompose. */
private class MapHolder {
    var map: MapLibreMap? = null
    var state: MainUiState? = null
    var styleKey: String = ""
    var loadedStyleKey: String = ""
    var styleLoaded = false
    var insets = MapInsets(0, 0, 0, 0)
    var colors = MapColors(0, 0, 0)
    var fitted: FitKey? = null
    var recenterSeen = 0
    var centeredOnce = false
}

/** The camera moves again only when one of these changes, not when a route is picked. */
private data class FitKey(
    val routes: List<RoutePreview>,
    val destination: Destination?,
    val stops: List<Destination>,
)

private fun styleKey(state: MainUiState, darkTheme: Boolean): String {
    var key = "openfreemap"
    if (state.mapboxMapToken.isNotEmpty()) {
        key = "mapbox:" + state.mapboxMapToken
    }
    if (darkTheme) {
        key += ":dark"
    }
    return key
}

private fun render(holder: MapHolder, context: Context) {
    val map = holder.map ?: return
    val state = holder.state ?: return

    // next to the favorites button in the bottom left corner, not under it
    map.uiSettings.setAttributionMargins(
        holder.insets.left + dp(context, ATTRIBUTION_START_DP),
        0,
        0,
        holder.insets.bottom + dp(context, 20),
    )
    map.uiSettings.setCompassMargins(0, holder.insets.top + dp(context, 8), holder.insets.right + dp(context, 8), 0)

    if (holder.loadedStyleKey != holder.styleKey) {
        holder.loadedStyleKey = holder.styleKey
        holder.styleLoaded = false
        map.setStyle(styleBuilder(holder.styleKey, state.mapboxMapToken)) { style ->
            addOverlayLayers(style, context, holder.colors)
            holder.styleLoaded = true
            render(holder, context)
        }
        return
    }
    if (!holder.styleLoaded) {
        return
    }
    val style = map.style ?: return
    drawData(style, state)
    moveCamera(holder, map, state, context)
}

private fun styleBuilder(key: String, mapboxToken: String): Style.Builder {
    val dark = key.endsWith(":dark")
    if (key.startsWith("mapbox:")) {
        var tiles = MapboxEndpoints.STREETS_TILES_URL
        if (dark) {
            tiles = MapboxEndpoints.DARK_TILES_URL
        }
        val template = tiles + "{z}/{x}/{y}@2x?access_token=" + Uri.encode(mapboxToken)
        val json = "{\"version\":8," +
            "\"sources\":{\"mapbox\":{\"type\":\"raster\",\"tiles\":[\"" + template + "\"],\"tileSize\":256," +
            "\"attribution\":\"© Mapbox © OpenStreetMap\"}}," +
            "\"layers\":[{\"id\":\"mapbox\",\"type\":\"raster\",\"source\":\"mapbox\"}]}"
        return Style.Builder().fromJson(json)
    }
    if (dark) {
        return Style.Builder().fromUri(OpenFreeMapStyles.DARK)
    }
    return Style.Builder().fromUri(OpenFreeMapStyles.LIGHT)
}

/** Our sources and layers, on top of the base map: other routes, picked route, places, position, pins. */
private fun addOverlayLayers(style: Style, context: Context, colors: MapColors) {
    style.addSource(GeoJsonSource(SOURCE_OTHER_ROUTES))
    style.addSource(GeoJsonSource(SOURCE_SELECTED_ROUTE))
    style.addSource(GeoJsonSource(SOURCE_PLACES))
    style.addSource(GeoJsonSource(SOURCE_MY_LOCATION))
    style.addSource(GeoJsonSource(SOURCE_PINS))

    style.addImage(IMAGE_DESTINATION, circleIcon(context, DESTINATION_COLOR, ""))
    for (number in 1..MAX_STOP_IMAGES) {
        style.addImage(IMAGE_STOP_PREFIX + number, circleIcon(context, STOP_COLOR, number.toString()))
    }
    for (category in PlaceCategory.entries) {
        style.addImage(IMAGE_PLACE_PREFIX + category.name, placeIcon(context, PLACE_COLOR, placeSymbol(category)))
    }

    // MapLibre widths and radii are already in dp
    style.addLayer(
        LineLayer(LAYER_OTHER_ROUTES, SOURCE_OTHER_ROUTES).withProperties(
            PropertyFactory.lineColor(colors.otherRoute),
            PropertyFactory.lineWidth(5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        LineLayer(LAYER_SELECTED_ROUTE, SOURCE_SELECTED_ROUTE).withProperties(
            PropertyFactory.lineColor(colors.selectedRoute),
            PropertyFactory.lineWidth(7f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        SymbolLayer(LAYER_PLACES, SOURCE_PLACES).withProperties(
            PropertyFactory.iconImage(Expression.get(PROPERTY_ICON)),
            // all of them, even when they touch: a hidden place could not be tapped
            PropertyFactory.iconAllowOverlap(true),
        ),
    )
    style.addLayer(
        CircleLayer(LAYER_MY_LOCATION, SOURCE_MY_LOCATION).withProperties(
            PropertyFactory.circleColor(colors.myLocation),
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
    style.addLayer(
        SymbolLayer(LAYER_PINS, SOURCE_PINS).withProperties(
            PropertyFactory.iconImage(Expression.get(PROPERTY_ICON)),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ),
    )
}

private fun drawData(style: Style, state: MainUiState) {
    val others = mutableListOf<Feature>()
    val selected = mutableListOf<Feature>()
    for (index in state.routes.indices) {
        val feature = Feature.fromGeometry(LineString.fromLngLats(points(state.routes[index].coordinates)))
        feature.addNumberProperty(PROPERTY_ROUTE_INDEX, index)
        if (index == state.selectedRouteIndex) {
            selected.add(feature)
        } else {
            others.add(feature)
        }
    }
    setFeatures(style, SOURCE_OTHER_ROUTES, others)
    setFeatures(style, SOURCE_SELECTED_ROUTE, selected)

    val places = mutableListOf<Feature>()
    for (index in state.places.indices) {
        val place = state.places[index]
        val feature = Feature.fromGeometry(Point.fromLngLat(place.longitude, place.latitude))
        feature.addStringProperty(PROPERTY_ICON, IMAGE_PLACE_PREFIX + place.category.name)
        feature.addNumberProperty(PROPERTY_PLACE_INDEX, index)
        places.add(feature)
    }
    setFeatures(style, SOURCE_PLACES, places)

    val position = mutableListOf<Feature>()
    val origin = state.origin
    if (origin != null) {
        position.add(Feature.fromGeometry(Point.fromLngLat(origin.longitude, origin.latitude)))
    }
    setFeatures(style, SOURCE_MY_LOCATION, position)

    val pins = mutableListOf<Feature>()
    for (index in state.stops.indices) {
        val stop = state.stops[index]
        val pin = Feature.fromGeometry(Point.fromLngLat(stop.longitude, stop.latitude))
        var number = index + 1
        if (number > MAX_STOP_IMAGES) {
            number = MAX_STOP_IMAGES
        }
        pin.addStringProperty(PROPERTY_ICON, IMAGE_STOP_PREFIX + number)
        pins.add(pin)
    }
    val destination = state.destination
    if (destination != null) {
        val pin = Feature.fromGeometry(Point.fromLngLat(destination.longitude, destination.latitude))
        pin.addStringProperty(PROPERTY_ICON, IMAGE_DESTINATION)
        pins.add(pin)
    }
    setFeatures(style, SOURCE_PINS, pins)
}

private fun setFeatures(style: Style, sourceId: String, features: List<Feature>) {
    val source = style.getSourceAs<GeoJsonSource>(sourceId) ?: return
    source.setGeoJson(FeatureCollection.fromFeatures(features))
}

/**
 * Camera: the whole plan when the destination, the stops or the routes change; the phone's position on the
 * recentre button, and once at start when there is nothing else to show.
 */
private fun moveCamera(holder: MapHolder, map: MapLibreMap, state: MainUiState, context: Context) {
    val origin = state.origin

    if (state.recenterRequest != holder.recenterSeen) {
        holder.recenterSeen = state.recenterRequest
        if (origin != null) {
            holder.centeredOnce = true
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(MapLatLng(origin.latitude, origin.longitude), MY_LOCATION_ZOOM))
            return
        }
    }

    val fitKey = FitKey(state.routes, state.destination, state.stops)
    if (holder.fitted != fitKey) {
        holder.fitted = fitKey
        val destination = state.destination
        if (destination != null) {
            fitPlan(holder, map, state, context)
            return
        }
    }

    if (!holder.centeredOnce && origin != null && state.destination == null) {
        holder.centeredOnce = true
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(MapLatLng(origin.latitude, origin.longitude), MY_LOCATION_ZOOM))
    }
}

private fun fitPlan(holder: MapHolder, map: MapLibreMap, state: MainUiState, context: Context) {
    val points = mutableListOf<MapLatLng>()
    for (route in state.routes) {
        for (point in route.coordinates) {
            points.add(MapLatLng(point.latitude, point.longitude))
        }
    }
    for (stop in state.stops) {
        points.add(MapLatLng(stop.latitude, stop.longitude))
    }
    val destination = state.destination ?: return
    points.add(MapLatLng(destination.latitude, destination.longitude))
    val origin = state.origin
    if (origin != null && state.routes.isNotEmpty()) {
        points.add(MapLatLng(origin.latitude, origin.longitude))
    }
    // one point alone would zoom in to the maximum: a box of about a kilometre around it
    if (points.size == 1) {
        points.add(MapLatLng(destination.latitude + SINGLE_POINT_SPAN, destination.longitude + SINGLE_POINT_SPAN))
        points.add(MapLatLng(destination.latitude - SINGLE_POINT_SPAN, destination.longitude - SINGLE_POINT_SPAN))
    }
    val bounds = LatLngBounds.Builder().includes(points).build()
    val margin = dp(context, 40)
    map.animateCamera(
        CameraUpdateFactory.newLatLngBounds(
            bounds,
            holder.insets.left + margin,
            holder.insets.top + margin,
            holder.insets.right + margin,
            holder.insets.bottom + margin,
        ),
    )
}

/**
 * The index ([property]) of what [layer] draws under a tap, or -1: unpicked routes can be picked on the map, places
 * tapped.
 */
private fun indexAt(map: MapLibreMap, point: MapLatLng, context: Context, layer: String, property: String): Int {
    val screen = map.projection.toScreenLocation(point)
    val reach = dp(context, 14).toFloat()
    val area = RectF(screen.x - reach, screen.y - reach, screen.x + reach, screen.y + reach)
    val features = map.queryRenderedFeatures(area, layer)
    for (feature in features) {
        if (feature.hasProperty(property)) {
            return feature.getNumberProperty(property).toInt()
        }
    }
    return -1
}

/** The latitudes and longitudes under the part of [view] that [insets] leave uncovered; null before layout. */
private fun visibleArea(map: MapLibreMap, view: MapView, insets: MapInsets): MapArea? {
    val left = insets.left.toFloat()
    val top = insets.top.toFloat()
    val right = (view.width - insets.right).toFloat()
    val bottom = (view.height - insets.bottom).toFloat()
    if (right <= left || bottom <= top) {
        return null
    }
    // all four corners: the map may be rotated
    val corners = listOf(PointF(left, top), PointF(right, top), PointF(left, bottom), PointF(right, bottom))
    var south = 90.0
    var west = 180.0
    var north = -90.0
    var east = -180.0
    for (corner in corners) {
        val point = map.projection.fromScreenLocation(corner)
        south = minOf(south, point.latitude)
        west = minOf(west, point.longitude)
        north = maxOf(north, point.latitude)
        east = maxOf(east, point.longitude)
    }
    return MapArea(south = south, west = west, north = north, east = east)
}

private fun points(coordinates: List<LatLng>): List<Point> {
    val result = ArrayList<Point>(coordinates.size)
    for (coordinate in coordinates) {
        result.add(Point.fromLngLat(coordinate.longitude, coordinate.latitude))
    }
    return result
}

/** A filled circle with a white ring and an optional number, like the stop badges in the panel. */
private fun circleIcon(context: Context, color: Int, label: String): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (30 * density).toInt()
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
    return bitmap
}

/** The white symbol of a place's category on a small coloured disc, like the points of interest of map apps. */
private fun placeIcon(context: Context, color: Int, symbol: Int): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (26 * density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f

    val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    ring.color = android.graphics.Color.WHITE
    canvas.drawCircle(center, center, center, ring)

    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    fill.color = color
    canvas.drawCircle(center, center, center - 2 * density, fill)

    // mutate(): the white tint stays on this copy of the icon
    val drawable = ContextCompat.getDrawable(context, symbol)?.mutate()
    if (drawable != null) {
        val inset = (6 * density).toInt()
        drawable.setBounds(inset, inset, size - inset, size - inset)
        drawable.setTint(android.graphics.Color.WHITE)
        drawable.draw(canvas)
    }
    return bitmap
}

/** The symbol of a category, on the map and in the places menu. */
fun placeSymbol(category: PlaceCategory): Int {
    return when (category) {
        PlaceCategory.FUEL -> R.drawable.ic_place_fuel
        PlaceCategory.CHARGING -> R.drawable.ic_place_charging
        PlaceCategory.PARKING -> R.drawable.ic_place_parking
        PlaceCategory.REST_AREA -> R.drawable.ic_place_rest_area
        PlaceCategory.RESTAURANT -> R.drawable.ic_place_restaurant
        PlaceCategory.CAFE -> R.drawable.ic_place_cafe
        PlaceCategory.SUPERMARKET -> R.drawable.ic_place_supermarket
        PlaceCategory.HOTEL -> R.drawable.ic_place_hotel
        PlaceCategory.PHARMACY -> R.drawable.ic_place_pharmacy
    }
}

private fun dp(context: Context, value: Int): Int {
    return (value * context.resources.displayMetrics.density).toInt()
}

const val STOP_COLOR = 0xFFF08C00.toInt()
private const val DESTINATION_COLOR = 0xFFE03131.toInt()
// apart from the route, the destination, the stops and the position
private const val PLACE_COLOR = 0xFF5F3DC4.toInt()
private const val MY_LOCATION_ZOOM = 14.0
private const val SINGLE_POINT_SPAN = 0.005
// favorites button: 12 dp margin + 52 dp + 12 dp
private const val ATTRIBUTION_START_DP = 76
// MainViewModel.MAX_STOPS
private const val MAX_STOP_IMAGES = 10

private const val SOURCE_OTHER_ROUTES = "nav2pilot-other-routes"
private const val SOURCE_SELECTED_ROUTE = "nav2pilot-selected-route"
private const val SOURCE_MY_LOCATION = "nav2pilot-my-location"
private const val SOURCE_PINS = "nav2pilot-pins"
private const val SOURCE_PLACES = "nav2pilot-places"
private const val LAYER_OTHER_ROUTES = "nav2pilot-other-routes"
private const val LAYER_SELECTED_ROUTE = "nav2pilot-selected-route"
private const val LAYER_MY_LOCATION = "nav2pilot-my-location"
private const val LAYER_PINS = "nav2pilot-pins"
private const val LAYER_PLACES = "nav2pilot-places"
private const val IMAGE_DESTINATION = "nav2pilot-destination"
private const val IMAGE_STOP_PREFIX = "nav2pilot-stop-"
private const val IMAGE_PLACE_PREFIX = "nav2pilot-place-"
private const val PROPERTY_ICON = "icon"
private const val PROPERTY_ROUTE_INDEX = "route"
private const val PROPERTY_PLACE_INDEX = "place"
