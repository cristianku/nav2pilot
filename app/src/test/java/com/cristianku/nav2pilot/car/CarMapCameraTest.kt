package com.cristianku.nav2pilot.car

import com.cristianku.nav2pilot.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CarMapCameraTest {

    @Test
    fun worldCoordinatesMatchWebMercator() {
        assertEquals(0.5, CarMapCamera.worldX(0.0), 1e-12)
        assertEquals(0.5, CarMapCamera.worldY(0.0), 1e-12)
        assertEquals(0.0, CarMapCamera.worldX(-180.0), 1e-12)
        assertEquals(1.0, CarMapCamera.worldX(180.0), 1e-12)
        // the Mercator world ends at about 85.05 degrees
        assertEquals(0.0, CarMapCamera.worldY(85.05112878), 1e-9)
        assertEquals(1.0, CarMapCamera.worldY(-89.0), 1e-9)
    }

    @Test
    fun cameraCentreProjectsToTheMiddleOfTheSurface() {
        val camera = MapCamera(LatLng(45.4642, 9.19), 12.0)
        val middle = CarMapCamera.project(camera, 800.0, 480.0, camera.center)
        assertEquals(400.0, middle.x, 1e-6)
        assertEquals(240.0, middle.y, 1e-6)
    }

    @Test
    fun planFitsInsideThePartLeftFreeByThePanel() {
        // Milan to Verona; the content panel covers the left 300 logical pixels of an 800 x 480 surface
        val points = listOf(LatLng(45.4642, 9.19), LatLng(45.4384, 10.9916), LatLng(45.70, 10.0))
        val camera = CarMapCamera.fit(points, 800.0, 480.0, 300.0 + 32, 32.0, 32.0, 32.0)

        var minX = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE
        for (point in points) {
            val screen = CarMapCamera.project(camera, 800.0, 480.0, point)
            assertTrue("x ${screen.x}", screen.x >= 332.0 - 1e-6 && screen.x <= 768.0 + 1e-6)
            assertTrue("y ${screen.y}", screen.y >= 32.0 - 1e-6 && screen.y <= 448.0 + 1e-6)
            minX = minOf(minX, screen.x)
            maxX = maxOf(maxX, screen.x)
            minY = minOf(minY, screen.y)
            maxY = maxOf(maxY, screen.y)
        }
        // centred in the free part, and touching its sides in the tighter direction
        assertEquals(550.0, (minX + maxX) / 2, 1e-6)
        assertEquals(240.0, (minY + maxY) / 2, 1e-6)
        assertEquals(436.0, maxX - minX, 1e-6)
    }

    @Test
    fun singlePointGetsAboutAKilometreAroundIt() {
        val point = LatLng(45.4642, 9.19)
        val camera = CarMapCamera.fit(listOf(point), 800.0, 480.0, 0.0, 0.0, 0.0, 0.0)
        val screen = CarMapCamera.project(camera, 800.0, 480.0, point)
        // the box is even in degrees, not in Mercator: a hundredth of a pixel off
        assertEquals(400.0, screen.x, 1e-6)
        assertEquals(240.0, screen.y, 0.1)
        assertTrue(camera.zoom > 13.0 && camera.zoom < 16.0)
    }

    @Test
    fun zoomStaysWithinMapLibreRange() {
        val point = LatLng(45.0, 9.0)
        val near = LatLng(45.0000001, 9.0000001)
        val camera = CarMapCamera.fit(listOf(point, near), 800.0, 480.0, 0.0, 0.0, 0.0, 0.0)
        assertEquals(CarMapCamera.MAX_ZOOM, camera.zoom, 1e-9)
    }
}
