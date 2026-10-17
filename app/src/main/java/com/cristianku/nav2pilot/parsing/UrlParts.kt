package com.cristianku.nav2pilot.parsing

import java.net.URLDecoder

data class QueryParameter(
    val name: String,
    val value: String,
)

/**
 * Minimal, lenient URL splitter.
 *
 * java.net.URI rejects many real Google Maps URLs (unencoded spaces, '|' and similar), and
 * android.net.Uri is not available in JVM unit tests, so the URL is split by hand.
 * [path] and [query] stay percent-encoded; [parameters] are decoded.
 */
class UrlParts private constructor(
    val scheme: String,
    val host: String,
    val path: String,
    val query: String,
    val parameters: List<QueryParameter>,
) {
    fun parameter(name: String): String? {
        for (parameter in parameters) {
            if (parameter.name == name) {
                return parameter.value
            }
        }
        return null
    }

    companion object {
        fun parse(url: String): UrlParts? {
            val trimmed = url.trim()
            val schemeEnd = trimmed.indexOf("://")
            if (schemeEnd <= 0) {
                return null
            }
            val scheme = trimmed.substring(0, schemeEnd).lowercase()
            val afterScheme = trimmed.substring(schemeEnd + 3)

            var authorityEnd = afterScheme.length
            for (index in afterScheme.indices) {
                val character = afterScheme[index]
                if (character == '/' || character == '?' || character == '#') {
                    authorityEnd = index
                    break
                }
            }
            val authority = afterScheme.substring(0, authorityEnd)
            var rest = afterScheme.substring(authorityEnd)

            var host = authority
            val userInfoEnd = host.lastIndexOf('@')
            if (userInfoEnd >= 0) {
                host = host.substring(userInfoEnd + 1)
            }
            val portStart = host.indexOf(':')
            if (portStart >= 0) {
                host = host.substring(0, portStart)
            }
            host = host.lowercase()
            if (host.isEmpty()) {
                return null
            }

            val fragmentStart = rest.indexOf('#')
            if (fragmentStart >= 0) {
                rest = rest.substring(0, fragmentStart)
            }

            var path = rest
            var query = ""
            val queryStart = rest.indexOf('?')
            if (queryStart >= 0) {
                path = rest.substring(0, queryStart)
                query = rest.substring(queryStart + 1)
            }

            return UrlParts(scheme, host, path, query, parseQuery(query))
        }

        fun parseQuery(query: String): List<QueryParameter> {
            val parameters = mutableListOf<QueryParameter>()
            if (query.isEmpty()) {
                return parameters
            }
            for (pair in query.split('&')) {
                if (pair.isEmpty()) {
                    continue
                }
                val equalsIndex = pair.indexOf('=')
                if (equalsIndex < 0) {
                    parameters.add(QueryParameter(decode(pair), ""))
                } else {
                    val name = decode(pair.substring(0, equalsIndex))
                    val value = decode(pair.substring(equalsIndex + 1))
                    parameters.add(QueryParameter(name, value))
                }
            }
            return parameters
        }

        /** Percent-decodes [text]; '+' becomes a space, as Google Maps uses it for spaces in paths and queries. */
        fun decode(text: String): String {
            try {
                return URLDecoder.decode(text, "UTF-8")
            } catch (malformed: IllegalArgumentException) {
                return text.replace('+', ' ')
            }
        }
    }
}
