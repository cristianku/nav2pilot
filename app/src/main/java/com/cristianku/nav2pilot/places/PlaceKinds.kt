package com.cristianku.nav2pilot.places

import org.json.JSONObject
import java.util.Locale

/** The kind of a place in OpenStreetMap, its main tag as "key:value" ("shop:furniture"), as people call it. */
object PlaceKinds {

    /** "Furniture store" for "shop:furniture"; "" for addresses and for what is no help to name. */
    fun label(kind: String): String {
        val key = kind.substringBefore(':', "")
        val value = kind.substringAfter(':', "")
        if (key.isEmpty() || value.isEmpty()) {
            return ""
        }
        val known = LABELS[kind]
        if (known != null) {
            return known
        }
        if (UNNAMED_KEYS.contains(key) || value == "yes") {
            if (key == "shop") {
                return "Shop"
            }
            return ""
        }
        val words = value.replace('_', ' ').replaceFirstChar { char -> char.titlecase(Locale.ROOT) }
        if (key == "shop") {
            return "$words shop"
        }
        return words
    }

    /** The category of the map's places menu the kind belongs to, for its symbol; null for the others. */
    fun category(kind: String): PlaceCategory? {
        for (category in PlaceCategory.entries) {
            if (category.tags.contains(kind)) {
                return category
            }
        }
        return null
    }

    fun isShop(kind: String): Boolean {
        return kind.startsWith("shop:")
    }

    /** A town, a region or a street: an area more than a place. */
    fun isArea(kind: String): Boolean {
        val key = kind.substringBefore(':', "")
        if (key == "place") {
            return kind != "place:house"
        }
        return key == "boundary" || (key == "highway" && category(kind) == null)
    }

    /** An address or a building, without a kind of its own. */
    fun isAddress(kind: String): Boolean {
        val key = kind.substringBefore(':', "")
        return key == "building" || kind == "place:house"
    }

    /** A car park found next to the place of the same name: the place is the one to go to. */
    fun isParking(kind: String): Boolean {
        return kind == "amenity:parking" || kind == "amenity:parking_entrance" || kind == "amenity:parking_space"
    }

    /** Photon's osm_key and osm_value as a kind; "" without them. */
    fun fromPhoton(properties: JSONObject): String {
        val key = stringField(properties, "osm_key")
        val value = stringField(properties, "osm_value")
        if (key.isEmpty() || value.isEmpty()) {
            return ""
        }
        return "$key:$value"
    }

    /** Photon's osm_type and osm_id as "way/26343678", as osm.org writes it; "" without them. */
    fun osmIdFromPhoton(properties: JSONObject): String {
        val type = when (stringField(properties, "osm_type")) {
            "N" -> "node"
            "W" -> "way"
            "R" -> "relation"
            else -> return ""
        }
        val id = properties.optLong("osm_id", -1L)
        if (id < 0) {
            return ""
        }
        return "$type/$id"
    }

    /** The first of [category]'s tags the element has, "amenity:fast_food"; the category's first without one. */
    fun fromTags(tags: JSONObject, category: PlaceCategory): String {
        for (tag in category.tags) {
            if (stringField(tags, tag.substringBefore(':')) == tag.substringAfter(':')) {
                return tag
            }
        }
        return category.tags.first()
    }

    // what names a building, an address or an area, not what is there
    private val UNNAMED_KEYS = setOf("building", "boundary", "landuse", "place", "highway", "addr", "entrance")

    private val LABELS = mapOf(
        // shops
        "shop:supermarket" to "Supermarket",
        "shop:furniture" to "Furniture store",
        "shop:convenience" to "Convenience store",
        "shop:bakery" to "Bakery",
        "shop:butcher" to "Butcher",
        "shop:clothes" to "Clothing store",
        "shop:shoes" to "Shoe store",
        "shop:electronics" to "Electronics store",
        "shop:hardware" to "Hardware store",
        "shop:doityourself" to "DIY store",
        "shop:garden_centre" to "Garden centre",
        "shop:department_store" to "Department store",
        "shop:mall" to "Shopping centre",
        "shop:car" to "Car dealer",
        "shop:car_repair" to "Car repair",
        "shop:tyres" to "Tyre shop",
        "shop:bicycle" to "Bicycle shop",
        "shop:books" to "Bookshop",
        "shop:sports" to "Sports shop",
        "shop:toys" to "Toy store",
        "shop:florist" to "Florist",
        "shop:hairdresser" to "Hairdresser",
        "shop:optician" to "Optician",
        "shop:chemist" to "Drugstore",
        "shop:beverages" to "Drinks store",
        "shop:kiosk" to "Kiosk",
        "shop:mobile_phone" to "Phone shop",
        "shop:pet" to "Pet shop",
        "shop:jewelry" to "Jeweller",
        "shop:interior_decoration" to "Home decor store",
        "shop:houseware" to "Houseware store",
        // food and drink
        "amenity:restaurant" to "Restaurant",
        "amenity:fast_food" to "Fast food",
        "amenity:cafe" to "Café",
        "amenity:bar" to "Bar",
        "amenity:pub" to "Pub",
        "amenity:ice_cream" to "Ice cream",
        // on the road
        "amenity:fuel" to "Fuel station",
        "amenity:charging_station" to "Charging station",
        "amenity:parking" to "Parking",
        "amenity:parking_entrance" to "Parking entrance",
        "amenity:car_wash" to "Car wash",
        "amenity:car_rental" to "Car rental",
        "highway:services" to "Service area",
        "highway:rest_area" to "Rest area",
        // services
        "amenity:pharmacy" to "Pharmacy",
        "amenity:hospital" to "Hospital",
        "amenity:clinic" to "Clinic",
        "amenity:doctors" to "Doctor",
        "amenity:dentist" to "Dentist",
        "amenity:veterinary" to "Vet",
        "amenity:bank" to "Bank",
        "amenity:atm" to "ATM",
        "amenity:post_office" to "Post office",
        "amenity:police" to "Police",
        "amenity:fire_station" to "Fire station",
        "amenity:townhall" to "Town hall",
        "amenity:school" to "School",
        "amenity:kindergarten" to "Kindergarten",
        "amenity:university" to "University",
        "amenity:college" to "College",
        "amenity:library" to "Library",
        "amenity:cinema" to "Cinema",
        "amenity:theatre" to "Theatre",
        "amenity:place_of_worship" to "Place of worship",
        "amenity:marketplace" to "Market",
        "amenity:toilets" to "Toilets",
        // staying and visiting
        "tourism:hotel" to "Hotel",
        "tourism:motel" to "Motel",
        "tourism:hostel" to "Hostel",
        "tourism:guest_house" to "Guest house",
        "tourism:apartment" to "Holiday flat",
        "tourism:camp_site" to "Campsite",
        "tourism:caravan_site" to "Caravan site",
        "tourism:museum" to "Museum",
        "tourism:attraction" to "Attraction",
        "tourism:viewpoint" to "Viewpoint",
        "tourism:zoo" to "Zoo",
        "tourism:theme_park" to "Theme park",
        "leisure:park" to "Park",
        "leisure:sports_centre" to "Sports centre",
        "leisure:fitness_centre" to "Gym",
        "leisure:swimming_pool" to "Swimming pool",
        "leisure:water_park" to "Water park",
        "leisure:stadium" to "Stadium",
        "leisure:golf_course" to "Golf course",
        "leisure:playground" to "Playground",
        // travel
        "railway:station" to "Train station",
        "aeroway:aerodrome" to "Airport",
        "amenity:ferry_terminal" to "Ferry terminal",
        // areas
        "place:city" to "City",
        "place:town" to "Town",
        "place:village" to "Village",
        "place:hamlet" to "Hamlet",
        "place:suburb" to "District",
        "place:quarter" to "District",
        "place:neighbourhood" to "Neighbourhood",
        "place:locality" to "Place",
        "highway:motorway" to "Motorway",
        "highway:trunk" to "Road",
        "highway:primary" to "Road",
        "highway:secondary" to "Road",
        "highway:tertiary" to "Road",
        "highway:unclassified" to "Road",
        "highway:residential" to "Street",
        "highway:living_street" to "Street",
        "highway:pedestrian" to "Pedestrian street",
        "natural:peak" to "Mountain",
        "natural:water" to "Lake",
    )

    /** optString returns "null" for JSON null on Android's org.json, so null is checked first. */
    private fun stringField(json: JSONObject, key: String): String {
        if (json.isNull(key)) {
            return ""
        }
        return json.optString(key, "").trim()
    }
}
