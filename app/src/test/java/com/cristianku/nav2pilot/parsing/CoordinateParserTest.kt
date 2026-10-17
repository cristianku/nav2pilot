package com.cristianku.nav2pilot.parsing

import com.cristianku.nav2pilot.model.CoordinateRules
import com.cristianku.nav2pilot.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateParserTest {

    @Test
    fun acceptsCommaSpaceAndSemicolonSeparators() {
        val expected = LatLng(45.4642, 9.19)
        assertEquals(expected, CoordinateParser.parse("45.4642, 9.19"))
        assertEquals(expected, CoordinateParser.parse("45.4642,9.19"))
        assertEquals(expected, CoordinateParser.parse("45.4642 9.19"))
        assertEquals(expected, CoordinateParser.parse("  45.4642;9.19  "))
    }

    @Test
    fun acceptsSignsAndLocPrefix() {
        assertEquals(LatLng(-33.8688, 151.2093), CoordinateParser.parse("-33.8688, +151.2093"))
        assertEquals(LatLng(47.5951518, -122.3316393), CoordinateParser.parse("loc: 47.5951518,-122.3316393"))
        assertEquals(LatLng(47.5951518, -122.3316393), CoordinateParser.parse("47.5951518 ,-122.3316393"))
    }

    @Test
    fun rejectsOutOfRangeValuesLikeAthena() {
        assertNull(CoordinateParser.parse("91, 0"))
        assertNull(CoordinateParser.parse("0, -181"))
        assertNotNullAndValid(CoordinateParser.parse("90, 180"))
        assertNotNullAndValid(CoordinateParser.parse("-90, -180"))
    }

    @Test
    fun rejectsTextThatIsNotACoordinatePair() {
        assertNull(CoordinateParser.parse(""))
        assertNull(CoordinateParser.parse("Piazza del Duomo, Milano"))
        assertNull(CoordinateParser.parse("45.4642"))
        assertNull(CoordinateParser.parse("45.4642, 9.19, 12"))
        assertNull(CoordinateParser.parse("1970 Naglee Ave San Jose"))
    }

    @Test
    fun coordinateRulesRejectNanAndInfinity() {
        assertFalse(CoordinateRules.isValid(Double.NaN, 0.0))
        assertFalse(CoordinateRules.isValid(0.0, Double.POSITIVE_INFINITY))
        assertFalse(CoordinateRules.isValid(Double.NEGATIVE_INFINITY, 0.0))
        assertTrue(CoordinateRules.isValid(0.0, 0.0))
    }

    private fun assertNotNullAndValid(value: LatLng?) {
        assertTrue(value != null)
        assertTrue(CoordinateRules.isValid(value!!.latitude, value.longitude))
    }
}
