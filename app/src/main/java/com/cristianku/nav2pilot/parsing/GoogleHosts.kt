package com.cristianku.nav2pilot.parsing

object GoogleHosts {
    // google.com, google.it, google.co.uk, google.com.au, with optional www. or maps. prefix
    private val googleHostPattern = Regex("""^(www\.|maps\.)?google\.(com|[a-z]{2}|co\.[a-z]{2}|com\.[a-z]{2})$""")

    const val CONSENT_HOST = "consent.google.com"

    fun isGoogleHost(host: String): Boolean {
        return googleHostPattern.matches(host)
    }

    fun isShortLink(parts: UrlParts): Boolean {
        if (parts.host == "maps.app.goo.gl") {
            return true
        }
        if (parts.host == "goo.gl" && parts.path.startsWith("/maps")) {
            return true
        }
        return false
    }

    fun isGoogleMapsUrl(parts: UrlParts): Boolean {
        if (parts.scheme != "https" && parts.scheme != "http") {
            return false
        }
        if (!isGoogleHost(parts.host)) {
            return false
        }
        if (parts.host.startsWith("maps.")) {
            return true
        }
        if (parts.path == "/maps" || parts.path.startsWith("/maps/")) {
            return true
        }
        return false
    }
}
