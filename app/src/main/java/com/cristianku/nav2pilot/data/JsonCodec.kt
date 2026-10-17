package com.cristianku.nav2pilot.data

import com.cristianku.nav2pilot.model.BackendKind
import com.cristianku.nav2pilot.model.CoordinateRules
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.PairedDevice
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class Favorite(
    val id: String,
    val destination: Destination,
)

/** JSON encoding of locally stored, non-secret data. */
object JsonCodec {

    fun destinationToJson(destination: Destination): JSONObject {
        val json = JSONObject()
        json.put("latitude", destination.latitude)
        json.put("longitude", destination.longitude)
        json.put("name", destination.name)
        json.put("details", destination.details)
        json.put("approximate", destination.approximate)
        return json
    }

    fun destinationFromJson(json: JSONObject): Destination? {
        if (!json.has("latitude") || !json.has("longitude")) {
            return null
        }
        val latitude = json.optDouble("latitude", Double.NaN)
        val longitude = json.optDouble("longitude", Double.NaN)
        if (!CoordinateRules.isValid(latitude, longitude)) {
            return null
        }
        return Destination(
            latitude = latitude,
            longitude = longitude,
            name = stringField(json, "name"),
            details = stringField(json, "details"),
            approximate = json.optBoolean("approximate", false),
        )
    }

    fun favoritesToJson(favorites: List<Favorite>): String {
        val array = JSONArray()
        for (favorite in favorites) {
            val json = JSONObject()
            json.put("id", favorite.id)
            json.put("destination", destinationToJson(favorite.destination))
            array.put(json)
        }
        return array.toString()
    }

    fun favoritesFromJson(text: String): List<Favorite> {
        val favorites = mutableListOf<Favorite>()
        val array: JSONArray
        try {
            array = JSONArray(text)
        } catch (error: JSONException) {
            return favorites
        }
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val id = stringField(item, "id")
            val destinationJson = item.optJSONObject("destination") ?: continue
            val destination = destinationFromJson(destinationJson) ?: continue
            if (id.isEmpty()) {
                continue
            }
            favorites.add(Favorite(id, destination))
        }
        return favorites
    }

    fun devicesToJson(devices: List<PairedDevice>): String {
        val array = JSONArray()
        for (device in devices) {
            val json = JSONObject()
            json.put("dongle_id", device.dongleId)
            json.put("alias", device.alias)
            json.put("device_type", device.deviceType)
            json.put("last_athena_ping", device.lastAthenaPingSeconds)
            json.put("is_owner", device.isOwner)
            json.put("backend", device.backend.id)
            json.put("added_manually", device.addedManually)
            array.put(json)
        }
        return array.toString()
    }

    fun devicesFromJson(text: String): List<PairedDevice> {
        val devices = mutableListOf<PairedDevice>()
        val array: JSONArray
        try {
            array = JSONArray(text)
        } catch (error: JSONException) {
            return devices
        }
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val dongleId = stringField(item, "dongle_id")
            if (dongleId.isEmpty()) {
                continue
            }
            devices.add(
                PairedDevice(
                    dongleId = dongleId,
                    alias = stringField(item, "alias"),
                    deviceType = stringField(item, "device_type"),
                    lastAthenaPingSeconds = item.optLong("last_athena_ping", 0L),
                    isOwner = item.optBoolean("is_owner", true),
                    backend = BackendKind.fromId(stringField(item, "backend")),
                    addedManually = item.optBoolean("added_manually", false),
                ),
            )
        }
        return devices
    }

    private fun stringField(json: JSONObject, key: String): String {
        if (json.isNull(key)) {
            return ""
        }
        return json.optString(key, "")
    }
}
