package com.cristianku.nav2pilot.testing

import com.cristianku.nav2pilot.data.KeyValueStore
import com.cristianku.nav2pilot.geocode.PlaceSearch
import com.cristianku.nav2pilot.model.Destination
import com.cristianku.nav2pilot.net.HttpClient
import com.cristianku.nav2pilot.net.HttpRequest
import com.cristianku.nav2pilot.net.HttpResponse
import com.cristianku.nav2pilot.security.TokenCipher
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.Base64

/** Returns scripted responses in order and records every request. */
class FakeHttpClient : HttpClient {
    val requests = mutableListOf<HttpRequest>()
    private val responses = ArrayDeque<Any>()

    fun respond(code: Int, body: String = "", headers: Map<String, List<String>> = emptyMap()) {
        responses.addLast(HttpResponse(code, headers, body))
    }

    fun redirect(location: String, code: Int = 302) {
        responses.addLast(HttpResponse(code, mapOf("Location" to listOf(location)), ""))
    }

    fun fail(message: String) {
        responses.addLast(IOException(message))
    }

    override fun execute(request: HttpRequest): HttpResponse {
        requests.add(request)
        if (responses.isEmpty()) {
            throw AssertionError("Unexpected request: $request")
        }
        val next = responses.removeFirst()
        if (next is IOException) {
            throw next
        }
        return next as HttpResponse
    }
}

class InMemoryKeyValueStore : KeyValueStore {
    val values = mutableMapOf<String, String>()

    override fun getString(key: String): String? {
        return values[key]
    }

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}

/** Reversible stand-in for the Keystore cipher (the Android Keystore does not exist on the JVM). */
class FakeTokenCipher : TokenCipher {
    var failDecrypt = false

    override fun encrypt(plainText: String): String {
        return "fake:" + Base64.getEncoder().encodeToString(plainText.reversed().toByteArray())
    }

    override fun decrypt(encrypted: String): String {
        if (failDecrypt || !encrypted.startsWith("fake:")) {
            throw GeneralSecurityException("cannot decrypt")
        }
        return String(Base64.getDecoder().decode(encrypted.substring(5))).reversed()
    }
}

class FakePlaceSearch(
    private val available: Boolean = true,
) : PlaceSearch {
    val queries = mutableListOf<String>()
    val results = mutableMapOf<String, List<Destination>>()
    var error: IOException? = null

    override fun isAvailable(): Boolean {
        return available
    }

    override suspend fun search(query: String, maxResults: Int): List<Destination> {
        queries.add(query)
        val failure = error
        if (failure != null) {
            throw failure
        }
        return results[query] ?: emptyList()
    }
}

object Fixtures {
    /** Location header returned by https://maps.app.goo.gl/gqhGPJxrF8E7MX479 (public link, fetched 2026-10-17). */
    const val SHORT_LINK = "https://maps.app.goo.gl/gqhGPJxrF8E7MX479"
    const val SHORT_LINK_TARGET =
        "https://www.google.com/maps/place/Copenhagen+Coffee+Lab/@43.6764552,6.9908374,10.65z/data=!4m7!3m6!1s0x12cdd180e7e5b347:0x641651765156f103!8m2!3d43.695783!4d7.260654!15sChVjb3BlbmhhZ2VuIGNvZmZlZSBsYWIiA4gBAVoXIhVjb3BlbmhhZ2VuIGNvZmZlZSBsYWKSAQtjb2ZmZWVfc2hvcKoBaAoNL2cvMTFma3l5NzhqMBABKhkiFWNvcGVuaGFnZW4gY29mZmVlIGxhYigAMh8QASIb36relNgo72m7fjH03ff9bDQMKo5st-5JVOBFMhkQAiIVY29wZW5oYWdlbiBjb2ZmZWUgbGFi4AEA!16s%2Fg%2F11v0gzh078?entry=tts&g_ep=EgoyMDI1MDgxOS4wIPu8ASoASAFQAw%3D%3D&skid=bc0eadd9-6b3f-4bc0-afc7-0d81f9387036"

    /** Location header of the second hop seen from a Swiss IP (2026-10-17): the consent page wrapping the Maps URL. */
    const val CONSENT_REDIRECT =
        "https://consent.google.com/ml?continue=https://www.google.com/maps/place/Copenhagen%2BCoffee%2BLab/@43.6764552,6.9908374,10.65z/data%3D!4m7!3m6!1s0x12cdd180e7e5b347:0x641651765156f103!8m2!3d43.695783!4d7.260654!15sChVjb3BlbmhhZ2VuIGNvZmZlZSBsYWIiA4gBAVoXIhVjb3BlbmhhZ2VuIGNvZmZlZSBsYWKSAQtjb2ZmZWVfc2hvcKoBaAoNL2cvMTFma3l5NzhqMBABKhkiFWNvcGVuaGFnZW4gY29mZmVlIGxhYigAMh8QASIb36relNgo72m7fjH03ff9bDQMKo5st-5JVOBFMhkQAiIVY29wZW5oYWdlbiBjb2ZmZWUgbGFi4AEA!16s%252Fg%252F11v0gzh078?entry%3Dtts%26g_ep%3DEgoyMDI1MDgxOS4wIPu8ASoASAFQAw%253D%253D%26skid%3Dbc0eadd9-6b3f-4bc0-afc7-0d81f9387036&gl=CH&m=0&pc=m&uxe=eomtm&cm=2&hl=de&src=1"

    const val DONGLE_ID = "1a2b3c4d5e6f7a8b"

    /** Unsigned test JWT: header {"alg":"none"}, payload {"identity":"test","exp":1893456000}. */
    const val TEST_JWT = "eyJhbGciOiJub25lIn0.eyJpZGVudGl0eSI6InRlc3QiLCJleHAiOjE4OTM0NTYwMDB9.c2ln"
}
