package com.cristianku.nav2pilot.places

import com.cristianku.nav2pilot.testing.FakeHttpClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder

@OptIn(ExperimentalCoroutinesApi::class)
class PlaceMediaTest {
    private val place = Place(PlaceCategory.PARKING, 47.552338, 7.592343, "Migros & Co", "", osmId = "way/123")
    @Test fun reviewsCannotComeFromNeighbourEvenWithMatchingNameOrCoordinatesAlone() {
        fun review(id: String, q: String, lat: String = "47.552338") =
            """{"payload":{"sub":"geo:$lat,7.592343?q=$q&u=30","rating":80,"opinion":"A comment","metadata":{"osm_id":"$id","nickname":"Sam"}}}"""
        val data = listOf(review("way/124", "Migros%20%26%20Co"), review("", "Another"),
            review("way/123", "Migros", "47.56"), review("way/123/4", "Migros%20%26%20Co"))
        assertEquals(listOf(PlaceReview("A comment", "Sam", 4.0)),
            PlaceMedia.parseReviews(JSONObject("""{"reviews":[${data.joinToString(",")}]}"""), place))
    }

    @Test fun noLookupOfUnnamedPinAndOnlyEncodeThePublicPlaceOncePerLayer() = runTest {
        val http = FakeHttpClient()
        val media = PlaceMedia(http, UnconfinedTestDispatcher(testScheduler))
        assertTrue(media.reviews(place.copy(name = "Parking")).isEmpty())
        assertNull(media.photo(PlaceInfo(wikidata = "not an item")))
        assertTrue(http.requests.isEmpty())
        http.respond(200, """{"reviews":[]}""")
        media.reviews(place)
        val sub = URLDecoder.decode(http.requests.single().url.substringAfter("?sub=").substringBefore('&'), "UTF-8")
        assertEquals("geo:47.552338,7.592343?u=35&q=Migros%20%26%20Co", sub)
    }

    @Test fun exactWikidataImageHasAttributionAndOnlyWikimediaThumbnailsCanBeLoaded() = runTest {
        val http = FakeHttpClient()
        val media = PlaceMedia(http, UnconfinedTestDispatcher(testScheduler))
        http.respond(200, """{"claims":{"P18":[{"rank":"normal","mainsnak":{"datavalue":{"value":"House.jpg"}}}]}}""")
        http.respond(200, """{"query":{"pages":[{"imageinfo":[{"thumburl":"https://thumb.wikimedia.org/house.jpg","descriptionurl":"https://commons.wikimedia.org/wiki/File:House.jpg","extmetadata":{"Artist":{"value":"A"},"LicenseShortName":{"value":"CC BY 4.0"}}}]}]}}""")
        val photo = media.photo(PlaceInfo(wikidata = "Q123"))!!
        assertEquals("House.jpg", photo.title)
        assertEquals("A", photo.creditHtml)
        assertEquals("CC BY 4.0", photo.license)
        assertFalse(PlaceMedia.trusted("https://upload.wikimedia.org.example.com/image.jpg", "upload.wikimedia.org"))
        assertFalse(PlaceMedia.trusted("http://upload.wikimedia.org/image.jpg", "upload.wikimedia.org"))
    }
}
