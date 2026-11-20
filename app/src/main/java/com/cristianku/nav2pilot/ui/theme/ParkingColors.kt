package com.cristianku.nav2pilot.ui.theme

import com.cristianku.nav2pilot.places.ParkingLevel

/** The same high-contrast ink is used behind white text in the map, favorites and tour. */
internal val ParkingLevel.pinColor: Int get() = when (this) {
    ParkingLevel.AVAILABLE -> 0xFF167348.toInt()
    ParkingLevel.LOW -> 0xFFAA4B08.toInt()
    ParkingLevel.FULL -> 0xFFB72837.toInt()
    ParkingLevel.CLOSED, ParkingLevel.UNKNOWN -> 0xFF576474.toInt()
}
