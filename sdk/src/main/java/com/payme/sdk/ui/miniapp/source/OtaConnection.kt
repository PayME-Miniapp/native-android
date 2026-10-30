package com.payme.sdk.ui.miniapp.source

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.io.IOException

internal object OtaConnection {
    fun isAllowed(url: String): Boolean = try {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "static.payme.vn" &&
            uri.port in setOf(-1, 443) && uri.rawUserInfo == null && uri.fragment == null
    } catch (_: Exception) { false }

    fun open(url: String, factory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }): HttpURLConnection {
        var current = url
        repeat(6) {
            if (!isAllowed(current)) throw IOException("Untrusted OTA URL")
            val connection = factory(URL(current))
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.requestMethod = "GET"
                val status = connection.responseCode
                if (status !in setOf(301, 302, 303, 307, 308)) return connection
                val location = connection.getHeaderField("Location") ?: throw IOException("Missing OTA redirect")
                current = URL(URL(current), location).toString()
            } catch (e: Exception) {
                connection.disconnect()
                throw e
            }
            connection.disconnect()
        }
        throw IOException("Too many OTA redirects")
    }
}
