package com.cristianku.nav2pilot.resolve

import com.cristianku.nav2pilot.geocode.PlaceSearch
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.parsing.CoordinateParser
import com.cristianku.nav2pilot.parsing.GoogleHosts
import com.cristianku.nav2pilot.parsing.LinkParseResult
import com.cristianku.nav2pilot.parsing.MapLinkParser
import com.cristianku.nav2pilot.parsing.ShareTextParser
import com.cristianku.nav2pilot.parsing.UrlParts
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class ResolveOutcome {
    data class Resolved(val destination: Destination, val source: String) : ResolveOutcome()
    data class Choices(val query: String, val candidates: List<Destination>) : ResolveOutcome()
    data class Failed(val reason: String) : ResolveOutcome()
}

/** Turns shared text, typed text or a map link into a destination. */
class DestinationResolver(
    private val shortLinkResolver: ShortLinkResolver,
    private val placeSearch: PlaceSearch,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /** Text received through a share intent (Intent.EXTRA_TEXT and Intent.EXTRA_SUBJECT). */
    suspend fun resolveShared(text: String, subject: String): ResolveOutcome {
        val shared = ShareTextParser.parse(text, subject)
        if (shared.link.isNotEmpty()) {
            return resolveLink(shared.link, shared.title, shared.details, SOURCE_SHARED_LINK)
        }

        for (line in text.lines()) {
            val coordinates = CoordinateParser.parse(line.trim())
            if (coordinates != null) {
                val name = firstOtherLine(text, line)
                return ResolveOutcome.Resolved(
                    Destination(coordinates.latitude, coordinates.longitude, name = name),
                    SOURCE_COORDINATES,
                )
            }
        }

        if (shared.textWithoutLink.isEmpty()) {
            return ResolveOutcome.Failed("The shared content is empty.")
        }
        return search(listOf(shared.textWithoutLink), shared.title, SOURCE_SEARCH)
    }

    /** Text typed or pasted in the search field: coordinates, a map link or an address. */
    suspend fun resolveTyped(text: String): ResolveOutcome {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return ResolveOutcome.Failed("Enter an address, coordinates or a map link.")
        }

        val coordinates = CoordinateParser.parse(trimmed)
        if (coordinates != null) {
            return ResolveOutcome.Resolved(Destination(coordinates.latitude, coordinates.longitude), SOURCE_COORDINATES)
        }

        val shared = ShareTextParser.parse(trimmed, "")
        if (shared.link.isNotEmpty()) {
            return resolveLink(shared.link, shared.title, shared.details, SOURCE_LINK)
        }

        return search(listOf(trimmed), "", SOURCE_SEARCH)
    }

    suspend fun resolveLink(link: String, title: String, details: String, source: String): ResolveOutcome {
        var result = MapLinkParser.parse(link)
        var networkError = ""

        if (MapLinkParser.rank(result) < EXACT_RANK && needsExpansion(link)) {
            val expanded = withContext(ioDispatcher) { shortLinkResolver.resolve(link) }
            if (MapLinkParser.rank(expanded.result) > MapLinkParser.rank(result)) {
                result = expanded.result
            }
            networkError = expanded.networkError
        }

        if (result is LinkParseResult.Found) {
            val destination = applyHints(result.destination, title, details)
            return ResolveOutcome.Resolved(destination, source)
        }

        if (result is LinkParseResult.Query) {
            return search(searchTexts(title, details, result.text), title, source)
        }

        if (title.isNotEmpty()) {
            return search(searchTexts(title, details, ""), title, source)
        }
        if (networkError.isNotEmpty()) {
            return ResolveOutcome.Failed("Could not open the link: $networkError")
        }
        return ResolveOutcome.Failed("This link does not contain a location.")
    }

    private suspend fun search(texts: List<String>, nameHint: String, source: String): ResolveOutcome {
        if (!placeSearch.isAvailable()) {
            return ResolveOutcome.Failed(
                "Address search is not available on this phone (no Geocoder service). Enter coordinates instead.",
            )
        }

        var lastError = ""
        var lastText = ""
        for (text in texts) {
            lastText = text
            val results: List<Destination>
            try {
                results = placeSearch.search(text, MAX_RESULTS)
            } catch (error: IOException) {
                lastError = error.message ?: error.javaClass.simpleName
                continue
            }
            if (results.size == 1) {
                var destination = results[0]
                if (nameHint.isNotEmpty()) {
                    destination = destination.copy(name = nameHint)
                }
                return ResolveOutcome.Resolved(destination, source)
            }
            if (results.size > 1) {
                return ResolveOutcome.Choices(text, results)
            }
        }

        if (lastError.isNotEmpty()) {
            return ResolveOutcome.Failed("Search failed: $lastError")
        }
        return ResolveOutcome.Failed("No results for \"$lastText\".")
    }

    private fun needsExpansion(link: String): Boolean {
        val parts = UrlParts.parse(link) ?: return false
        if (GoogleHosts.isShortLink(parts)) {
            return true
        }
        if (GoogleHosts.isGoogleMapsUrl(parts)) {
            return true
        }
        return false
    }

    private fun applyHints(destination: Destination, title: String, details: String): Destination {
        var name = destination.name
        var detailText = destination.details
        if (name.isEmpty()) {
            name = title
        }
        if (detailText.isEmpty()) {
            detailText = details
        }
        return destination.copy(name = name, details = detailText)
    }

    /** Address lines are tried first: they geocode more reliably than a business name. */
    private fun searchTexts(title: String, details: String, query: String): List<String> {
        val candidates = mutableListOf<String>()
        if (details.isNotEmpty()) {
            candidates.add(details)
            if (title.isNotEmpty()) {
                candidates.add("$title, $details")
            }
        }
        if (query.isNotEmpty()) {
            candidates.add(query)
        }
        if (title.isNotEmpty()) {
            candidates.add(title)
        }

        val distinct = mutableListOf<String>()
        for (candidate in candidates) {
            if (!distinct.contains(candidate)) {
                distinct.add(candidate)
            }
        }
        return distinct
    }

    private fun firstOtherLine(text: String, excludedLine: String): String {
        for (line in text.lines()) {
            val cleaned = line.trim()
            if (cleaned.isNotEmpty() && line != excludedLine) {
                return cleaned
            }
        }
        return ""
    }

    companion object {
        const val SOURCE_SHARED_LINK = "Shared link"
        const val SOURCE_LINK = "Map link"
        const val SOURCE_COORDINATES = "Coordinates"
        const val SOURCE_SEARCH = "Search"
        const val SOURCE_MAP = "Map"
        const val SOURCE_FAVORITE = "Favorite"
        const val SOURCE_PLACES = "OpenStreetMap"

        private const val EXACT_RANK = 3
        private const val MAX_RESULTS = 5
    }
}
