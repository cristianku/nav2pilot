package com.cristianku.nav2pilot.places

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceKindsTest {
    @Test
    fun labels() {
        assertEquals("Furniture store", PlaceKinds.label("shop:furniture"))
        assertEquals("Fuel station", PlaceKinds.label("amenity:fuel"))
        assertEquals("Town", PlaceKinds.label("place:town"))
        // not in the list: from the value
        assertEquals("Second hand shop", PlaceKinds.label("shop:second_hand"))
        assertEquals("Ice rink", PlaceKinds.label("leisure:ice_rink"))
        assertEquals("Shop", PlaceKinds.label("shop:yes"))
        // an address or a building says nothing
        assertEquals("", PlaceKinds.label("building:yes"))
        assertEquals("", PlaceKinds.label("place:house"))
        assertEquals("", PlaceKinds.label("boundary:administrative"))
        assertEquals("", PlaceKinds.label(""))
    }

    @Test
    fun symbols() {
        assertEquals(PlaceCategory.FUEL, PlaceKinds.category("amenity:fuel"))
        assertEquals(PlaceCategory.RESTAURANT, PlaceKinds.category("amenity:fast_food"))
        assertEquals(null, PlaceKinds.category("shop:furniture"))
        assertTrue(PlaceKinds.isShop("shop:furniture"))
        assertTrue(PlaceKinds.isArea("place:city"))
        assertTrue(PlaceKinds.isArea("highway:residential"))
        assertFalse(PlaceKinds.isArea("highway:services"))
        assertTrue(PlaceKinds.isAddress("place:house"))
        assertTrue(PlaceKinds.isAddress("building:residential"))
    }

    @Test
    fun fromPhotonAndOverpass() {
        val photon = JSONObject("""{"osm_type": "W", "osm_id": 26343678, "osm_key": "shop", "osm_value": "furniture"}""")
        assertEquals("shop:furniture", PlaceKinds.fromPhoton(photon))
        assertEquals("way/26343678", PlaceKinds.osmIdFromPhoton(photon))
        assertEquals("", PlaceKinds.osmIdFromPhoton(JSONObject("""{"osm_type": "X", "osm_id": 1}""")))
        assertEquals("", PlaceKinds.fromPhoton(JSONObject("""{"osm_key": null}""")))

        assertEquals("amenity:fast_food", PlaceKinds.fromTags(JSONObject("""{"amenity": "fast_food"}"""), PlaceCategory.RESTAURANT))
        assertEquals("amenity:restaurant", PlaceKinds.fromTags(JSONObject("{}"), PlaceCategory.RESTAURANT))
    }
}
