package com.cristianku.nav2pilot.resolve

import com.cristianku.nav2pilot.parsing.LinkParseResult
import com.cristianku.nav2pilot.testing.FakeHttpClient
import com.cristianku.nav2pilot.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortLinkResolverTest {

    @Test
    fun followsOneRedirectAndStopsAtThePlacePin() {
        val http = FakeHttpClient()
        http.redirect(Fixtures.SHORT_LINK_TARGET)

        val result = ShortLinkResolver(http).resolve(Fixtures.SHORT_LINK)

        assertEquals(1, http.requests.size)
        val request = http.requests[0]
        assertEquals("GET", request.method)
        assertEquals(Fixtures.SHORT_LINK, request.url)
        assertFalse("redirects must be followed one hop at a time", request.followRedirects)
        assertFalse(request.readBody)
        assertTrue(request.headers.isEmpty())

        val found = result.result as LinkParseResult.Found
        assertEquals(43.695783, found.destination.latitude, 0.0000001)
        assertEquals(7.260654, found.destination.longitude, 0.0000001)
        assertEquals("", result.networkError)
    }

    @Test
    fun consentInterstitialIsUnwrappedWithoutFetchingIt() {
        val http = FakeHttpClient()
        // short link -> Maps URL without a pin -> consent page whose continue= carries the pin
        http.redirect("https://www.google.com/maps/place/Copenhagen+Coffee+Lab/@43.6764552,6.9908374,10.65z")
        http.redirect(Fixtures.CONSENT_REDIRECT)

        val result = ShortLinkResolver(http).resolve(Fixtures.SHORT_LINK)

        assertEquals(2, http.requests.size)
        for (request in http.requests) {
            assertFalse(request.url.contains("consent.google.com"))
        }
        val found = result.result as LinkParseResult.Found
        assertEquals(43.695783, found.destination.latitude, 0.0000001)
        assertEquals(7.260654, found.destination.longitude, 0.0000001)
        assertFalse(found.destination.approximate)
    }

    @Test
    fun keepsViewportWhenNothingBetterIsAvailable() {
        val http = FakeHttpClient()
        http.redirect("https://www.google.com/maps/place/Somewhere/@45.1,9.2,15z")
        http.respond(200)

        val result = ShortLinkResolver(http).resolve(Fixtures.SHORT_LINK)

        val found = result.result as LinkParseResult.Found
        assertTrue(found.destination.approximate)
        assertEquals("Somewhere", found.destination.name)
    }

    @Test
    fun doesNotFollowRedirectsToOtherHosts() {
        val http = FakeHttpClient()
        http.redirect("https://example.com/somewhere?q=45.1,9.2")

        val result = ShortLinkResolver(http).resolve(Fixtures.SHORT_LINK)

        assertEquals(1, http.requests.size)
        assertTrue(result.result is LinkParseResult.NotRecognized)
        assertEquals("https://example.com/somewhere?q=45.1,9.2", result.finalUrl)
    }

    @Test
    fun relativeLocationIsResolved() {
        val http = FakeHttpClient()
        http.redirect("/maps?q=45.1,9.2")

        val result = ShortLinkResolver(http).resolve("https://maps.google.com/maps?cid=123")

        val found = result.result as LinkParseResult.Found
        assertEquals(45.1, found.destination.latitude, 0.0000001)
        assertEquals("https://maps.google.com/maps?q=45.1,9.2", result.finalUrl)
    }

    @Test
    fun stopsAfterMaxHops() {
        val http = FakeHttpClient()
        for (index in 0 until 10) {
            http.redirect("https://maps.app.goo.gl/loop$index")
        }

        val result = ShortLinkResolver(http, maxHops = 3).resolve(Fixtures.SHORT_LINK)

        assertEquals(3, http.requests.size)
        assertTrue(result.result is LinkParseResult.NotRecognized)
    }

    @Test
    fun networkErrorIsReported() {
        val http = FakeHttpClient()
        http.fail("timeout")

        val result = ShortLinkResolver(http).resolve(Fixtures.SHORT_LINK)

        assertEquals("timeout", result.networkError)
        assertTrue(result.result is LinkParseResult.NotRecognized)
    }

    @Test
    fun httpIsUpgradedToHttps() {
        val http = FakeHttpClient()
        http.redirect(Fixtures.SHORT_LINK_TARGET)

        ShortLinkResolver(http).resolve("http://maps.app.goo.gl/gqhGPJxrF8E7MX479")

        assertEquals(Fixtures.SHORT_LINK, http.requests[0].url)
    }
}
