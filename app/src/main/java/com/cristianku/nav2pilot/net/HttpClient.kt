package com.cristianku.nav2pilot.net

import java.io.IOException

class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val followRedirects: Boolean = true,
    val readBody: Boolean = true,
) {
    // Headers are left out on purpose: they can contain the Authorization token.
    override fun toString(): String {
        return "HttpRequest($method $url)"
    }
}

class HttpResponse(
    val code: Int,
    val headers: Map<String, List<String>>,
    val body: String,
) {
    fun header(name: String): String? {
        for ((headerName, values) in headers) {
            if (headerName.equals(name, ignoreCase = true) && values.isNotEmpty()) {
                return values[0]
            }
        }
        return null
    }

    fun isSuccessful(): Boolean {
        return code in 200..299
    }

    fun isRedirect(): Boolean {
        return code in 300..399
    }
}

/** Blocking HTTP client. Callers run it on Dispatchers.IO. */
interface HttpClient {
    @Throws(IOException::class)
    fun execute(request: HttpRequest): HttpResponse
}
