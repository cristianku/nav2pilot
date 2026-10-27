package com.cristianku.nav2pilot.car

import com.cristianku.nav2pilot.model.LatLng
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Where the car map looks: the centre of the surface and MapLibre's zoom level. */
data class MapCamera(
    val center: LatLng,
    val zoom: Double,
)

/** A point on the surface, in the same unit as the size the camera was computed for. */
data class ScreenPoint(
    val x: Double,
    val y: Double,
)

/**
 * Web Mercator as MapLibre computes it (512-pixel tiles: the world is 512 × 2^zoom logical pixels wide). The car
 * map is a still MapLibre snapshot taken with this camera, and the routes and pins are drawn on it with [project],
 * so they also show while the snapshot is loading or when it fails.
 */
object CarMapCamera {
    const val TILE_SIZE = 512.0
    const val MAX_ZOOM = 17.0

    // one point alone would zoom in to the maximum: about a kilometre around it, like the phone map
    private const val SINGLE_POINT_SPAN = 0.005

    /**
     * The camera that shows all [points] inside the part of a [width] × [height] surface left free by the
     * paddings (the content panel of the template covers one side).
     */
    fun fit(
        points: List<LatLng>,
        width: Double,
        height: Double,
        paddingLeft: Double,
        paddingTop: Double,
        paddingRight: Double,
        paddingBottom: Double,
    ): MapCamera {
        val all = points.toMutableList()
        if (all.size == 1) {
            val point = all[0]
            all.add(LatLng(point.latitude + SINGLE_POINT_SPAN, point.longitude + SINGLE_POINT_SPAN))
            all.add(LatLng(point.latitude - SINGLE_POINT_SPAN, point.longitude - SINGLE_POINT_SPAN))
        }
        var minX = 1.0
        var maxX = 0.0
        var minY = 1.0
        var maxY = 0.0
        for (point in all) {
            val x = worldX(point.longitude)
            val y = worldY(point.latitude)
            minX = min(minX, x)
            maxX = max(maxX, x)
            minY = min(minY, y)
            maxY = max(maxY, y)
        }
        val freeWidth = max(1.0, width - paddingLeft - paddingRight)
        val freeHeight = max(1.0, height - paddingTop - paddingBottom)
        val spanX = max(maxX - minX, 1e-12)
        val spanY = max(maxY - minY, 1e-12)
        var zoom = log2(min(freeWidth / (spanX * TILE_SIZE), freeHeight / (spanY * TILE_SIZE)))
        zoom = min(MAX_ZOOM, max(0.0, zoom))

        // the middle of the points goes to the middle of the free part, not of the whole surface
        val scale = TILE_SIZE * 2.0.pow(zoom)
        val offsetX = paddingLeft + freeWidth / 2 - width / 2
        val offsetY = paddingTop + freeHeight / 2 - height / 2
        val centerX = (minX + maxX) / 2 - offsetX / scale
        val centerY = (minY + maxY) / 2 - offsetY / scale
        return MapCamera(LatLng(latitude(centerY), longitude(centerX)), zoom)
    }

    /** Where [point] lands on a [width] × [height] surface seen through [camera]. */
    fun project(camera: MapCamera, width: Double, height: Double, point: LatLng): ScreenPoint {
        val scale = TILE_SIZE * 2.0.pow(camera.zoom)
        val x = (worldX(point.longitude) - worldX(camera.center.longitude)) * scale + width / 2
        val y = (worldY(point.latitude) - worldY(camera.center.latitude)) * scale + height / 2
        return ScreenPoint(x, y)
    }

    /** 0 at longitude -180, 1 at +180. */
    fun worldX(longitude: Double): Double {
        return (longitude + 180.0) / 360.0
    }

    /** 0 at the top of the Mercator world (about 85° N), 1 at the bottom. */
    fun worldY(latitude: Double): Double {
        val clamped = min(MAX_LATITUDE, max(-MAX_LATITUDE, latitude))
        val sine = sin(clamped * PI / 180.0)
        return 0.5 - ln((1 + sine) / (1 - sine)) / (4 * PI)
    }

    private fun longitude(worldX: Double): Double {
        return worldX * 360.0 - 180.0
    }

    private fun latitude(worldY: Double): Double {
        val n = PI - 2 * PI * worldY
        return 180.0 / PI * atan(0.5 * (exp(n) - exp(-n)))
    }

    private const val MAX_LATITUDE = 85.05112878
}
