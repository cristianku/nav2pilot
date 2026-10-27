package com.cristianku.nav2pilot.car

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.view.Surface
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.cristianku.nav2pilot.mapbox.RoutePreview
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.LatLng
import com.cristianku.nav2pilot.ui.map.baseMapStyle
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.snapshotter.MapSnapshotter
import org.maplibre.android.geometry.LatLng as MapLatLng

/** What the route screen shows on the car map. */
data class CarMapContent(
    val destination: Destination,
    val origin: LatLng?,
    val routes: List<RoutePreview>,
    val selectedRoute: Int,
    /** "" draws the OpenFreeMap base map, as on the phone without a Mapbox token. */
    val mapboxToken: String,
)

/** The map drawn on the car screen behind the route screen's panel. */
interface CarMap {
    fun show(content: CarMapContent)

    fun clear()
}

/**
 * Draws the route map on the surface Android Auto gives to map templates. Android Auto has no map view for apps:
 * the base map is a MapLibre snapshot (the phone map's style), the routes and pins are drawn on top of it.
 * A new snapshot is taken only when the plan, the size or the theme change, not when another route is picked.
 */
class CarMapRenderer(private val carContext: CarContext) : SurfaceCallback, CarMap, DefaultLifecycleObserver {
    private var container: SurfaceContainer? = null
    private var visibleArea: Rect? = null
    private var content: CarMapContent? = null

    private var snapshotter: MapSnapshotter? = null
    private var snapshotKey: SnapshotKey? = null
    private var baseMap: Bitmap? = null

    override fun show(content: CarMapContent) {
        this.content = content
        render()
    }

    override fun clear() {
        content = null
        cancelSnapshot()
    }

    /** The day/night theme of the car changed. */
    fun refresh() {
        render()
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        container = surfaceContainer
        render()
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        this.visibleArea = visibleArea
        render()
    }

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) {
        cancelSnapshot()
        container = null
    }

    override fun onDestroy(owner: LifecycleOwner) {
        cancelSnapshot()
        content = null
    }

    private fun render() {
        val surface = container ?: return
        val shown = content ?: return
        val ratio = surface.dpi / 160.0
        // MapLibre works in logical pixels; the surface in physical ones
        val width = surface.width / ratio
        val height = surface.height / ratio
        val area = visibleArea ?: Rect(0, 0, surface.width, surface.height)
        val margin = MARGIN_DP
        val camera = CarMapCamera.fit(
            planPoints(shown),
            width,
            height,
            area.left / ratio + margin,
            area.top / ratio + margin,
            (surface.width - area.right) / ratio + margin,
            (surface.height - area.bottom) / ratio + margin,
        )
        val dark = carContext.isDarkMode
        val key = SnapshotKey(shown.copy(selectedRoute = 0), surface.width, surface.height, area, dark)
        if (key != snapshotKey) {
            cancelSnapshot()
            snapshotKey = key
            baseMap = null
            takeSnapshot(key, camera, width, height, ratio, dark)
        }
        draw(surface, shown, camera, ratio)
    }

    private fun takeSnapshot(key: SnapshotKey, camera: MapCamera, width: Double, height: Double, ratio: Double, dark: Boolean) {
        // must run before the first snapshot, as before the first MapView
        MapLibre.getInstance(carContext.applicationContext)
        val options = MapSnapshotter.Options(Math.round(width).toInt(), Math.round(height).toInt())
            .withPixelRatio(ratio.toFloat())
            .withStyleBuilder(baseMapStyle(key.content.mapboxToken, dark))
            .withCameraPosition(
                CameraPosition.Builder()
                    .target(MapLatLng(camera.center.latitude, camera.center.longitude))
                    .zoom(camera.zoom)
                    .build(),
            )
            .withLogo(false)
        val taken = MapSnapshotter(carContext.applicationContext, options)
        snapshotter = taken
        taken.start(
            { snapshot ->
                if (snapshotter === taken) {
                    snapshotter = null
                    baseMap = snapshot.bitmap
                    render()
                }
            },
            { _ ->
                // no base map (offline, style not loaded): the routes and pins stay on the plain background
                if (snapshotter === taken) {
                    snapshotter = null
                }
            },
        )
    }

    private fun cancelSnapshot() {
        snapshotter?.cancel()
        snapshotter = null
        snapshotKey = null
        baseMap = null
    }

    private fun draw(surfaceContainer: SurfaceContainer, shown: CarMapContent, camera: MapCamera, ratio: Double) {
        val surface = surfaceContainer.surface ?: return
        if (!surface.isValid) {
            return
        }
        val canvas: Canvas
        try {
            canvas = surface.lockCanvas(null)
        } catch (invalid: IllegalArgumentException) {
            return
        } catch (busy: Surface.OutOfResourcesException) {
            return
        }
        try {
            val dark = carContext.isDarkMode
            var background = DAY_BACKGROUND
            if (dark) {
                background = NIGHT_BACKGROUND
            }
            canvas.drawColor(background)
            val bitmap = baseMap
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, null, Rect(0, 0, surfaceContainer.width, surfaceContainer.height), Paint(Paint.FILTER_BITMAP_FLAG))
            }
            val painter = Painter(canvas, camera, surfaceContainer.width / ratio, surfaceContainer.height / ratio, ratio.toFloat())
            drawRoutes(painter, shown, dark)
            val origin = shown.origin
            if (origin != null) {
                painter.dot(origin, MY_LOCATION_COLOR, 8f)
            }
            painter.pin(LatLng(shown.destination.latitude, shown.destination.longitude))
        } finally {
            surface.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawRoutes(painter: Painter, shown: CarMapContent, dark: Boolean) {
        for (index in shown.routes.indices) {
            if (index != shown.selectedRoute) {
                painter.line(shown.routes[index].coordinates, UNPICKED_ROUTE_COLOR, 5f)
            }
        }
        if (shown.selectedRoute < shown.routes.size) {
            var color = DAY_ROUTE_COLOR
            if (dark) {
                color = NIGHT_ROUTE_COLOR
            }
            painter.line(shown.routes[shown.selectedRoute].coordinates, color, 7f)
        }
    }

    private fun planPoints(shown: CarMapContent): List<LatLng> {
        val points = mutableListOf<LatLng>()
        for (route in shown.routes) {
            points.addAll(route.coordinates)
        }
        points.add(LatLng(shown.destination.latitude, shown.destination.longitude))
        val origin = shown.origin
        if (origin != null && shown.routes.isNotEmpty()) {
            points.add(origin)
        }
        return points
    }

    /** The snapshot depends on everything but the picked route. */
    private data class SnapshotKey(
        val content: CarMapContent,
        val width: Int,
        val height: Int,
        val area: Rect,
        val dark: Boolean,
    )

    /** Draws in logical pixels (like MapLibre's dp) on a canvas in physical pixels. */
    private class Painter(
        private val canvas: Canvas,
        private val camera: MapCamera,
        private val width: Double,
        private val height: Double,
        private val density: Float,
    ) {
        fun line(points: List<LatLng>, color: Int, widthDp: Float) {
            if (points.size < 2) {
                return
            }
            val path = Path()
            for (index in points.indices) {
                val screen = CarMapCamera.project(camera, width, height, points[index])
                val x = (screen.x * density).toFloat()
                val y = (screen.y * density).toFloat()
                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.style = Paint.Style.STROKE
            paint.color = color
            paint.strokeWidth = widthDp * density
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(path, paint)
        }

        /** A filled circle with a white ring, like the phone's position on the phone map. */
        fun dot(point: LatLng, color: Int, radiusDp: Float) {
            val screen = CarMapCamera.project(camera, width, height, point)
            val x = (screen.x * density).toFloat()
            val y = (screen.y * density).toFloat()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = WHITE
            canvas.drawCircle(x, y, (radiusDp + 3) * density, paint)
            paint.color = color
            canvas.drawCircle(x, y, radiusDp * density, paint)
        }

        /** The destination pin of the phone map: red circle, white ring, white dot. */
        fun pin(point: LatLng) {
            dot(point, DESTINATION_COLOR, 12f)
            val screen = CarMapCamera.project(camera, width, height, point)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = WHITE
            canvas.drawCircle((screen.x * density).toFloat(), (screen.y * density).toFloat(), 4 * density, paint)
        }
    }

    companion object {
        // room around the plan, in logical pixels
        private const val MARGIN_DP = 32.0

        // the phone map's colours (theme primary, MapScreen and NavMap constants)
        private const val DAY_ROUTE_COLOR = 0xFF007C96.toInt()
        private const val NIGHT_ROUTE_COLOR = 0xFF00D5E8.toInt()
        private const val UNPICKED_ROUTE_COLOR = 0xFF9AA5B1.toInt()
        private const val MY_LOCATION_COLOR = 0xFF1A73E8.toInt()
        private const val DESTINATION_COLOR = 0xFFE03131.toInt()
        private const val DAY_BACKGROUND = 0xFFF3F6FA.toInt()
        private const val NIGHT_BACKGROUND = 0xFF010E30.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()
    }
}
