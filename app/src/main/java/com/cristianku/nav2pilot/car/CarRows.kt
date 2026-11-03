package com.cristianku.nav2pilot.car

import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.CarContext
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.CarLocation
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import com.cristianku.nav2pilot.mapbox.RouteShaping
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.model.LatLng

/** The rows of the place lists next to Android Auto's map: the favorites and the places nearby. */
object CarRows {
    /**
     * A place with its marker on the map and, when the position is known, its distance. Browsable: it opens the
     * route screen; Android Auto asks the others for a distance.
     */
    fun place(destination: Destination, position: LatLng?, onClick: () -> Unit): Row {
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
        return row.build()
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
