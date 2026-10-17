package com.cristianku.nav2pilot.resolve

import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.testing.FakeHttpClient
import com.cristianku.nav2pilot.testing.FakePlaceSearch
import com.cristianku.nav2pilot.testing.Fixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DestinationResolverTest {

    private val http = FakeHttpClient()
    private val search = FakePlaceSearch()
    private val resolver = DestinationResolver(ShortLinkResolver(http), search, UnconfinedTestDispatcher())

    @Test
    fun googleMapsShareResolvesThroughTheShortLink() = runTest {
        http.redirect(Fixtures.SHORT_LINK_TARGET)
        val text = "Copenhagen Coffee Lab\n5 Rue de la Préfecture, 06300 Nice, France\n" + Fixtures.SHORT_LINK

        val outcome = resolver.resolveShared(text, "")

        val resolved = outcome as ResolveOutcome.Resolved
        assertEquals(43.695783, resolved.destination.latitude, 0.0000001)
        assertEquals(7.260654, resolved.destination.longitude, 0.0000001)
        assertEquals("Copenhagen Coffee Lab", resolved.destination.name)
        assertEquals("5 Rue de la Préfecture, 06300 Nice, France", resolved.destination.details)
        assertEquals(DestinationResolver.SOURCE_SHARED_LINK, resolved.source)
        assertTrue(search.queries.isEmpty())
    }

    @Test
    fun linkWithoutCoordinatesFallsBackToTheSharedAddress() = runTest {
        http.redirect("https://www.google.com/maps/place/Some+Cafe/data=!4m2!3m1!1s0x0:0x1")
        http.respond(200)
        search.results["Via Roma 1, Torino"] = listOf(Destination(45.07, 7.68, "Via Roma 1"))

        val outcome = resolver.resolveShared("Some Cafe\nVia Roma 1, Torino\n" + Fixtures.SHORT_LINK, "")

        val resolved = outcome as ResolveOutcome.Resolved
        assertEquals(45.07, resolved.destination.latitude, 0.0000001)
        assertEquals("Some Cafe", resolved.destination.name)
        assertEquals("Via Roma 1, Torino", search.queries[0])
    }

    @Test
    fun typedCoordinatesNeedNoNetwork() = runTest {
        val outcome = resolver.resolveTyped(" 45.4642, 9.19 ")

        val resolved = outcome as ResolveOutcome.Resolved
        assertEquals(45.4642, resolved.destination.latitude, 0.0000001)
        assertEquals(DestinationResolver.SOURCE_COORDINATES, resolved.source)
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun sharedTextWithCoordinateLine() = runTest {
        val outcome = resolver.resolveShared("Parking spot\n45.1, 9.2", "")

        val resolved = outcome as ResolveOutcome.Resolved
        assertEquals(45.1, resolved.destination.latitude, 0.0000001)
        assertEquals("Parking spot", resolved.destination.name)
    }

    @Test
    fun typedAddressWithSeveralResultsOffersChoices() = runTest {
        search.results["Via Roma"] = listOf(
            Destination(45.07, 7.68, "Via Roma", "Torino"),
            Destination(41.9, 12.5, "Via Roma", "Roma"),
        )

        val outcome = resolver.resolveTyped("Via Roma")

        val choices = outcome as ResolveOutcome.Choices
        assertEquals(2, choices.candidates.size)
    }

    @Test
    fun noResults() = runTest {
        val outcome = resolver.resolveTyped("nowhere at all")
        assertTrue(outcome is ResolveOutcome.Failed)
    }

    @Test
    fun searchErrorIsReported() = runTest {
        search.error = IOException("grpc failed")
        val outcome = resolver.resolveTyped("Via Roma")
        assertEquals("Search failed: grpc failed", (outcome as ResolveOutcome.Failed).reason)
    }

    @Test
    fun missingGeocoderIsExplained() = runTest {
        val offlineResolver = DestinationResolver(ShortLinkResolver(http), FakePlaceSearch(available = false), UnconfinedTestDispatcher())
        val outcome = offlineResolver.resolveTyped("Via Roma")
        assertTrue((outcome as ResolveOutcome.Failed).reason.contains("not available"))
    }

    @Test
    fun geoLinkFromViewIntent() = runTest {
        val outcome = resolver.resolveLink("geo:0,0?q=45.1,9.2(Garage)", "", "", DestinationResolver.SOURCE_LINK)
        val resolved = outcome as ResolveOutcome.Resolved
        assertEquals("Garage", resolved.destination.name)
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun unreachableShortLinkWithoutHints() = runTest {
        http.fail("Unable to resolve host")
        val outcome = resolver.resolveTyped(Fixtures.SHORT_LINK)
        assertEquals("Could not open the link: Unable to resolve host", (outcome as ResolveOutcome.Failed).reason)
    }
}
