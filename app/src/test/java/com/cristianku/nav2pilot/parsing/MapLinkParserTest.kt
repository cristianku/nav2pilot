package com.cristianku.nav2pilot.parsing

import com.cristianku.nav2pilot.testing.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLinkParserTest {

    private fun found(link: String): LinkParseResult.Found {
        val result = MapLinkParser.parse(link)
        assertTrue("expected coordinates for $link but got $result", result is LinkParseResult.Found)
        return result as LinkParseResult.Found
    }

    private fun query(link: String): String {
        val result = MapLinkParser.parse(link)
        assertTrue("expected a query for $link but got $result", result is LinkParseResult.Query)
        return (result as LinkParseResult.Query).text
    }

    private fun assertCoordinates(result: LinkParseResult.Found, latitude: Double, longitude: Double) {
        assertEquals(latitude, result.destination.latitude, 0.0000001)
        assertEquals(longitude, result.destination.longitude, 0.0000001)
    }

    @Test
    fun placePinWinsOverViewport() {
        // Real redirect target of a maps.app.goo.gl link: the viewport (@43.676,6.990) is far from the pin.
        val result = found(Fixtures.SHORT_LINK_TARGET)
        assertCoordinates(result, 43.695783, 7.260654)
        assertEquals("Copenhagen Coffee Lab", result.destination.name)
        assertFalse(result.destination.approximate)
    }

    @Test
    fun mapsUrlsApiSearchAndDirections() {
        // https://developers.google.com/maps/documentation/urls/get-started
        assertCoordinates(found("https://www.google.com/maps/search/?api=1&query=47.5951518%2C-122.3316393"), 47.5951518, -122.3316393)
        assertCoordinates(found("https://www.google.com/maps/search/?api=1&query=47.5951518+,-122.3316393"), 47.5951518, -122.3316393)
        assertCoordinates(found("https://www.google.com/maps/dir/?api=1&destination=47.5951518,+-122.3316393"), 47.5951518, -122.3316393)
        assertEquals("Pike Place Market, Seattle", query("https://www.google.com/maps/search/?api=1&query=Pike+Place+Market%2C+Seattle"))
    }

    @Test
    fun legacyQueryParameters() {
        assertCoordinates(found("https://maps.google.com/?q=45.4642,9.19"), 45.4642, 9.19)
        assertCoordinates(found("http://maps.google.co.uk/maps?q=loc:+47.5951518,-122.3316393&z=15"), 47.5951518, -122.3316393)
        assertCoordinates(found("https://maps.google.de/maps?q=47.5951518,-122.3316393&z=17&t=k"), 47.5951518, -122.3316393)
        assertCoordinates(found("http://maps.google.com/maps?saddr=Here&daddr=45.1,9.2"), 45.1, 9.2)
    }

    @Test
    fun legacyLlIsApproximate() {
        val result = found("http://maps.google.com/maps?q=%3Cname%3E&ie=UTF8&hl=en&hq=&ll=47.5951518,-122.3316393&spn=0,359.967062")
        assertCoordinates(result, 47.5951518, -122.3316393)
        assertTrue(result.destination.approximate)
    }

    @Test
    fun pathForms() {
        assertCoordinates(found("https://www.google.com/maps/search/47.5951518,-122.3316393"), 47.5951518, -122.3316393)
        assertCoordinates(found("https://www.google.com/maps/dir/Current+Location/47.5951518+,-122.3316393"), 47.5951518, -122.3316393)
        assertCoordinates(found("https://www.google.com/maps/place/45.4642,9.19/@45.4642,9.19,17z"), 45.4642, 9.19)
        assertEquals("1970 Naglee Ave San Jose, CA 95126", query("https://www.google.com/maps/dir//1970%20Naglee%20Ave%20San%20Jose,%20CA%2095126"))
        assertEquals("1970 Naglee Ave San Jose, CA 95126", query("https://www.google.com/maps/place/1970%20Naglee%20Ave%20San%20Jose,%20CA%2095126"))
    }

    @Test
    fun viewportOnlyIsApproximateAndKeepsPlaceName() {
        val result = found("https://www.google.com/maps/place/Duomo+di+Milano/@45.4640976,9.1897378,17z")
        assertCoordinates(result, 45.4640976, 9.1897378)
        assertTrue(result.destination.approximate)
        assertEquals("Duomo di Milano", result.destination.name)
    }

    @Test
    fun geoUris() {
        assertCoordinates(found("geo:37.330593,-121.859425"), 37.330593, -121.859425)
        assertCoordinates(found("geo:37.330593,+-121.859425+;u=35"), 37.330593, -121.859425)
        assertCoordinates(found("geo:0,0?q=37.330593+,-121.859425"), 37.330593, -121.859425)

        val labelled = found("geo:0,0?q=37.330593,-121.859425%28Main+Street%29")
        assertCoordinates(labelled, 37.330593, -121.859425)
        assertEquals("Main Street", labelled.destination.name)

        assertEquals("Main Street", found("geo:0,0?q=37.330593,-121.859425 (Main Street)").destination.name)
        assertEquals("1970 Naglee Ave San Jose, CA 95126", query("geo:0,0?q=1970+Naglee+Ave+San+Jose,+CA+95126"))
    }

    @Test
    fun geoUriWithoutLocationIsNotRecognized() {
        assertTrue(MapLinkParser.parse("geo:0,0") is LinkParseResult.NotRecognized)
        assertTrue(MapLinkParser.parse("geo:abc") is LinkParseResult.NotRecognized)
        assertTrue(MapLinkParser.parse("geo:0,0?q=") is LinkParseResult.NotRecognized)
    }

    @Test
    fun googleNavigationIntentUri() {
        assertCoordinates(found("google.navigation:q=47.5951518,-122.3316393"), 47.5951518, -122.3316393)
        assertEquals("1970 Naglee Ave San Jose, CA 95126", query("google.navigation:q=1970+Naglee+Ave+San+Jose,+CA+95126&mode=d"))
    }

    @Test
    fun nonMapsLinksAreIgnored() {
        assertTrue(MapLinkParser.parse("https://example.com/maps?q=45.1,9.2") is LinkParseResult.NotRecognized)
        assertTrue(MapLinkParser.parse("https://www.google.com/search?q=45.1,9.2") is LinkParseResult.NotRecognized)
        assertTrue(MapLinkParser.parse("https://google.evil.com/maps?q=45.1,9.2") is LinkParseResult.NotRecognized)
        // a short link has no coordinates until it is expanded
        assertTrue(MapLinkParser.parse(Fixtures.SHORT_LINK) is LinkParseResult.NotRecognized)
    }

    @Test
    fun hostRules() {
        assertTrue(GoogleHosts.isGoogleHost("www.google.com"))
        assertTrue(GoogleHosts.isGoogleHost("google.it"))
        assertTrue(GoogleHosts.isGoogleHost("maps.google.co.uk"))
        assertTrue(GoogleHosts.isGoogleHost("www.google.com.au"))
        assertFalse(GoogleHosts.isGoogleHost("google.com.evil.org"))
        assertFalse(GoogleHosts.isGoogleHost("notgoogle.com"))
        assertTrue(GoogleHosts.isShortLink(UrlParts.parse("https://maps.app.goo.gl/abc")!!))
        assertTrue(GoogleHosts.isShortLink(UrlParts.parse("https://goo.gl/maps/abc")!!))
        assertFalse(GoogleHosts.isShortLink(UrlParts.parse("https://goo.gl/other")!!))
    }
}
