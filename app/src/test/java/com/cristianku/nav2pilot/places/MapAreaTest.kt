package com.cristianku.nav2pilot.places

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapAreaTest {
    private val searched = MapArea(south = 47.50, west = 8.00, north = 47.52, east = 8.03)

    @Test
    fun aSmallShiftIsStillTheSamePartOfTheMap() {
        val shifted = MapArea(south = 47.502, west = 8.003, north = 47.522, east = 8.033)
        assertFalse(shifted.movedFrom(searched))
        assertFalse(searched.movedFrom(searched))
    }

    @Test
    fun zoomingInShowsNothingNew() {
        val closer = MapArea(south = 47.505, west = 8.010, north = 47.515, east = 8.020)
        assertFalse(closer.movedFrom(searched))
    }

    @Test
    fun aQuarterOfTheHeightOrWidthAwayHasMoved() {
        val north = MapArea(south = 47.506, west = 8.00, north = 47.526, east = 8.03)
        assertTrue(north.movedFrom(searched))
        val east = MapArea(south = 47.50, west = 8.009, north = 47.52, east = 8.039)
        assertTrue(east.movedFrom(searched))
    }

    @Test
    fun zoomingOutByHalfAsMuchAgainHasMoved() {
        val wider = MapArea(south = 47.494, west = 7.99, north = 47.526, east = 8.04)
        assertTrue(wider.movedFrom(searched))
    }

    @Test
    fun anEmptySearchedAreaAlwaysCountsAsMoved() {
        val point = MapArea(south = 47.5, west = 8.0, north = 47.5, east = 8.0)
        assertTrue(searched.movedFrom(point))
    }
}
