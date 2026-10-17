package com.cristianku.nav2pilot.net

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class UrlConnectionHttpClient(
    private val userAgent: String,
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 30_000,
) : HttpClient {

    override fun execute(request: HttpRequest): HttpResponse {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.instanceFollowRedirects = request.followRedirects
            connection.connectTimeout = connectTimeoutMillis
            connection.readTimeout = readTimeoutMillis
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", userAgent)
            for ((name, value) in request.headers) {
                connection.setRequestProperty(name, value)
            }

            val body = request.body
            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { output -> output.write(bytes) }
            }

            val code = connection.responseCode
            val headers = mutableMapOf<String, List<String>>()
            for ((name, values) in connection.headerFields) {
                if (name != null) {
                    headers[name] = values
                }
            }

            var responseBody = ""
            if (request.readBody) {
                val stream: InputStream? = if (code >= 400) connection.errorStream else connection.inputStream
                if (stream != null) {
                    responseBody = readLimited(stream)
                }
            }
            return HttpResponse(code, headers, responseBody)
        } finally {
            connection.disconnect()
        }
    }

    @Throws(IOException::class)
    private fun readLimited(stream: InputStream): String {
        stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) {
                    break
                }
                total += count
                if (total > MAX_BODY_BYTES) {
                    throw IOException("Response body larger than $MAX_BODY_BYTES bytes")
                }
                output.write(buffer, 0, count)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }

    companion object {
        private const val MAX_BODY_BYTES = 1_000_000
    }
}
