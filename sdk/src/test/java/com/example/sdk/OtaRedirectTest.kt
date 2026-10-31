package com.example.sdk

import com.payme.sdk.ui.miniapp.source.OtaConnection
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException

class OtaRedirectTest {
    private class Connection(url: URL, private val status: Int, private val location: String? = null) : HttpURLConnection(url) {
        var closed = false
        override fun getResponseCode() = status
        override fun getHeaderField(name: String): String? = if (name == "Location") location else null
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        override fun connect() = Unit
    }

    @Test fun followsRelativeSameHostRedirectWithoutAutomaticRedirects() {
        val connections = mutableListOf<Connection>()
        val result = OtaConnection.open("https://static.payme.vn/manifest") { url ->
            Connection(url, if (connections.isEmpty()) 302 else 200, "/new").also { connections.add(it) }
        }
        assertEquals("/new", result.url.path)
        assertTrue(connections.first().closed)
        assertFalse(result.instanceFollowRedirects)
        result.disconnect()
    }

    @Test fun rejectsForeignAndDowngradeRedirectsBeforeOpeningTheirConnection() {
        for (destination in listOf("https://evil.example/zip", "http://static.payme.vn/zip", "https://static.payme.vn.evil.example/zip")) {
            var calls = 0
            lateinit var connection: Connection
            try {
                OtaConnection.open("https://static.payme.vn/manifest") { url ->
                    calls++
                    Connection(url, 302, destination).also { connection = it }
                }
                fail("Expected rejection")
            } catch (_: IOException) { }
            assertEquals(1, calls)
            assertTrue(connection.closed)
        }
    }

    @Test fun redirectLoopsAreBoundedAndDisconnected() {
        val connections = mutableListOf<Connection>()
        try {
            OtaConnection.open("https://static.payme.vn/loop") { url -> Connection(url, 302, "/loop").also { connections.add(it) } }
            fail("Expected rejection")
        } catch (_: IOException) { }
        assertEquals(6, connections.size)
        assertTrue(connections.all { it.closed })
    }
}
