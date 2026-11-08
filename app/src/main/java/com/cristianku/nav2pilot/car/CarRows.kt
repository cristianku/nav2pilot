package com.cristianku.nav2pilot.car

import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarLocation
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import com.cristianku.nav2pilot.mapbox.RouteShaping
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.LatLng
import com.cristianku.nav2pilot.places.OpeningHours
import com.cristianku.nav2pilot.places.PlaceInfo
import com.cristianku.nav2pilot.places.PlaceInfoLookup
import com.cristianku.nav2pilot.places.PlaceKinds
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** The rows of the place lists next to Android Auto's map: the favorites and the places nearby. */
object CarRows {
    /**
     * A place with its marker on the map and, when the position is known, its distance; below, open or closed at
     * [now] when [info] has opening hours. Browsable: it opens the route screen; Android Auto asks the others for a
     * distance.
     */
    fun place(destination: Destination, position: LatLng?, info: PlaceInfo?, now: LocalDateTime, onClick: () -> Unit): Row {
        val place = Place.Builder(CarLocation.create(destination.latitude, destination.longitude))
            .setMarker(PlaceMarker.Builder().build())
            .build()
        val row = Row.Builder()
            .setTitle(destination.displayName())
            .setMetadata(Metadata.Builder().setPlace(place).build())
            .setBrowsable(true)
            .setOnClickListener(onClick)
        if (position != null) {
            var text = " "
            if (destination.details.isNotBlank()) {
                text += " · " + destination.details
            }
            val meters = RouteShaping.distanceMeters(position, LatLng(destination.latitude, destination.longitude))
            val withDistance = SpannableString(text)
            withDistance.setSpan(DistanceSpan.create(distance(meters)), 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            row.addText(withDistance)
        } else if (destination.details.isNotBlank()) {
            row.addText(destination.details)
        }
        val status = status(info?.openingHours ?: "", now)
        if (status != null) {
            row.addText(status)
        }
        return row.build()
    }

    /**
     * "Open · Closes 20:00", the open or closed in green, yellow or red as on the phone; null for hours the app
     * cannot read. The car may draw the colours its own way.
     */
    fun status(openingHours: String, now: LocalDateTime): CharSequence? {
        val hours = OpeningHours.parse(openingHours) ?: return null
        val status = hours.statusText(now)
        val color = when (status.tone) {
            OpeningHours.StatusText.Tone.OPEN -> CarColor.GREEN
            OpeningHours.StatusText.Tone.SOON -> CarColor.YELLOW
            OpeningHours.StatusText.Tone.CLOSED -> CarColor.RED
        }
        val text = SpannableString(status.toString())
        text.setSpan(ForegroundCarColorSpan.create(color), 0, status.headline.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return text
    }

    /**
     * Whether to ask OpenStreetMap about a favorite: a place of some kind. Not an address or a building, often
     * someone's home, nor a town or a street, which have no hours; nor one saved before the app kept the kind.
     */
    fun wantsInfo(destination: Destination): Boolean {
        val kind = destination.kind
        return PlaceInfoLookup.isOsmId(destination.osmId) && kind.isNotEmpty() && !PlaceKinds.isAddress(kind) && !PlaceKinds.isArea(kind)
    }

    /**
     * Calls [redraw] when the open or closed of one of the places with [hours] changes: "Closes soon" half an hour
     * before the closing, "Closed" at it. Looks at each new minute, until cancelled.
     */
    suspend fun followClock(hours: () -> List<String>, redraw: () -> Unit) {
        var before = LocalDateTime.now()
        while (true) {
            delay((60 - LocalDateTime.now().second) * 1000L)
            val now = LocalDateTime.now()
            val shown = hours()
            if (shown.map { text -> status(text, before)?.toString() } != shown.map { text -> status(text, now)?.toString() }) {
                redraw()
            }
            before = now
        }
    }

    /** Kilometres with one decimal like the phone's route chips, metres below one kilometre. */
    fun distance(meters: Double): Distance {
        if (meters < 1000) {
            return Distance.create(Math.round(meters).toDouble(), Distance.UNIT_METERS)
        }
        return Distance.create(Math.round(meters / 100) / 10.0, Distance.UNIT_KILOMETERS)
    }

    /** How many places the car shows in a list next to its map (usually 6). */
    fun placeLimit(carContext: CarContext): Int {
        val constraints = carContext.getCarService(ConstraintManager::class.java)
        return constraints.getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST)
    }
}
