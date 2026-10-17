package com.cristianku.nav2pilot.resolve

import com.cristianku.nav2pilot.net.HttpClient
import com.cristianku.nav2pilot.net.HttpRequest
import com.cristianku.nav2pilot.parsing.GoogleHosts
import com.cristianku.nav2pilot.parsing.LinkParseResult
import com.cristianku.nav2pilot.parsing.MapLinkParser
import com.cristianku.nav2pilot.parsing.UrlParts
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL

data class ShortLinkResult(
    val result: LinkParseResult,
    val finalUrl: String,
    val networkError: String,
)

/**
 * Expands Google Maps short links (maps.app.goo.gl) by following HTTP redirects one hop at a time,
 * reading only the Location header, and stops as soon as a hop contains a place pin.
 *
 * - Only Google Maps hosts are contacted; any other redirect target ends the walk.
 * - consent.google.com (EU cookie consent interstitial) is not fetched: its `continue` parameter
 *   already carries the original Maps URL.
 */
class ShortLinkResolver(
    private val http: HttpClient,
    private val maxHops: Int = 6,
) {

    fun resolve(startUrl: String): ShortLinkResult {
        var currentUrl = startUrl.trim()
        var best: LinkParseResult = LinkParseResult.NotRecognized
        var networkError = ""
        var hop = 0

        while (hop <= maxHops) {
            val parsed = MapLinkParser.parse(currentUrl)
            if (MapLinkParser.rank(parsed) > MapLinkParser.rank(best)) {
                best = parsed
            }
            if (MapLinkParser.rank(best) == EXACT_RANK) {
                break
            }

            val parts = UrlParts.parse(currentUrl) ?: break

            if (parts.host == GoogleHosts.CONSENT_HOST) {
                val continueUrl = parts.parameter("continue")
                if (continueUrl == null || continueUrl.isBlank()) {
                    break
                }
                currentUrl = continueUrl
                hop += 1
                continue
            }

            if (!isAllowedToFetch(parts)) {
                break
            }
            if (hop == maxHops) {
                break
            }

            val request = HttpRequest(
                method = "GET",
                url = upgradeToHttps(currentUrl),
                followRedirects = false,
                readBody = false,
            )
            val response = try {
                http.execute(request)
            } catch (error: IOException) {
                networkError = error.message ?: error.javaClass.simpleName
                break
            }
            if (!response.isRedirect()) {
                break
            }
            val location = response.header("Location") ?: break
            currentUrl = absoluteUrl(currentUrl, location)
            hop += 1
        }

        return ShortLinkResult(best, currentUrl, networkError)
    }

    private fun isAllowedToFetch(parts: UrlParts): Boolean {
        if (parts.scheme != "https" && parts.scheme != "http") {
            return false
        }
        if (GoogleHosts.isShortLink(parts)) {
            return true
        }
        if (GoogleHosts.isGoogleMapsUrl(parts)) {
            return true
        }
        return false
    }

    private fun upgradeToHttps(url: String): String {
        if (url.startsWith("http://", ignoreCase = true)) {
            return "https://" + url.substring("http://".length)
        }
        return url
    }

    private fun absoluteUrl(baseUrl: String, location: String): String {
        if (location.startsWith("https://", ignoreCase = true) || location.startsWith("http://", ignoreCase = true)) {
            return location
        }
        try {
            return URL(URL(baseUrl), location).toString()
        } catch (malformed: MalformedURLException) {
            return location
        }
    }

    companion object {
        private const val EXACT_RANK = 3
    }
}
