package com.cristianku.nav2pilot.parsing

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareTextParserTest {

    @Test
    fun splitsNameAddressAndLink() {
        val text = "Copenhagen Coffee Lab\n5 Rue de la Préfecture, 06300 Nice, France\nhttps://maps.app.goo.gl/gqhGPJxrF8E7MX479"
        val shared = ShareTextParser.parse(text, "")
        assertEquals("https://maps.app.goo.gl/gqhGPJxrF8E7MX479", shared.link)
        assertEquals("Copenhagen Coffee Lab", shared.title)
        assertEquals("5 Rue de la Préfecture, 06300 Nice, France", shared.details)
    }

    @Test
    fun linkOnly() {
        val shared = ShareTextParser.parse("https://maps.app.goo.gl/abc", "Duomo")
        assertEquals("https://maps.app.goo.gl/abc", shared.link)
        assertEquals("Duomo", shared.title)
        assertEquals("", shared.details)
    }

    @Test
    fun linkInsideSentenceAndTrailingPunctuation() {
        val shared = ShareTextParser.parse("Meet me here: https://maps.google.com/?q=45.1,9.2.", "")
        assertEquals("https://maps.google.com/?q=45.1,9.2", shared.link)
        assertEquals("Meet me here:", shared.title)
    }

    @Test
    fun geoLinkIsDetected() {
        assertEquals("geo:45.1,9.2", ShareTextParser.parse("Parking\ngeo:45.1,9.2", "").link)
    }

    @Test
    fun noLink() {
        val shared = ShareTextParser.parse("Via Roma 1\nTorino", "")
        assertEquals("", shared.link)
        assertEquals("Via Roma 1", shared.title)
        assertEquals("Via Roma 1, Torino", shared.textWithoutLink)
    }

    @Test
    fun urlPartsDecodesParameters() {
        val parts = UrlParts.parse("https://WWW.Google.com:443/maps/search/?api=1&query=Pike+Place%2C+Seattle#frag")!!
        assertEquals("https", parts.scheme)
        assertEquals("www.google.com", parts.host)
        assertEquals("/maps/search/", parts.path)
        assertEquals("1", parts.parameter("api"))
        assertEquals("Pike Place, Seattle", parts.parameter("query"))
        assertEquals(null, parts.parameter("missing"))
    }
}
