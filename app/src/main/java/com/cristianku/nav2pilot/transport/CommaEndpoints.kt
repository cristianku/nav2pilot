package com.cristianku.nav2pilot.transport

/**
 * comma endpoints used by Nav2Pilot. Every value is documented; sources are listed in README "Verified API".
 */
object CommaEndpoints {
    /** REST API host: https://api.comma.ai docs; openpilot common/api/comma_connect.py API_HOST. */
    const val API_BASE_URL = "https://api.commadotai.com"

    /** Athena HTTP: "POST /:dongle_id" on https://athena.comma.ai (api.comma.ai docs, comma-api openapi.yaml). */
    const val ATHENA_BASE_URL = "https://athena.comma.ai"

    /** Profile of the authenticated user (openpilot tools/lib/auth.py validates tokens with it). */
    const val PROFILE_PATH = "/v1/me"

    /** "List devices owned or readable by authenticated user" (api.comma.ai docs). */
    const val DEVICES_PATH = "/v1/me/devices/"

    /** Where users generate a JWT (api.comma.ai docs, "Authentication"). */
    const val TOKEN_PAGE_URL = "https://jwt.comma.ai"

    const val METHOD_SET_NAV_DESTINATION = "setNavDestination"
    const val METHOD_CLEAR_NAV_DESTINATION = "clearNavDestination"

    /**
     * sunnypilot (cristianku/sunnypilot feature/nav-alpha, sunnypilot/navd/athena.py): the user's Mapbox public
     * token, so the device asks Mapbox for its routes directly instead of comma's route service.
     */
    const val METHOD_SET_MAPBOX_TOKEN = "setMapboxToken"
    const val METHOD_GET_VERSION = "getVersion"
}
