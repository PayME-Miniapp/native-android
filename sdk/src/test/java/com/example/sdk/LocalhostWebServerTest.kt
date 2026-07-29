package com.example.sdk

import com.payme.sdk.webServer.MySimpleWebServer
import fi.iki.elonen.NanoHTTPD
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files

class LocalhostWebServerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun rootServesIndexHtml() {
        val root = webRoot()
        val response = serve(root, "/")

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals(NanoHTTPD.MIME_HTML, response.mimeType)
        assertTrue(response.data.readText().contains("miniapp"))
    }

    @Test
    fun extensionlessRouteFallsBackToRootIndexHtml() {
        val root = webRoot()
        val response = serve(root, "/payment/success")

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertEquals(NanoHTTPD.MIME_HTML, response.mimeType)
        assertTrue(response.data.readText().contains("miniapp"))
    }

    @Test
    fun existingStaticAssetIsServed() {
        val root = webRoot()
        val response = serve(root, "/static/app.js")

        assertEquals(NanoHTTPD.Response.Status.OK, response.status)
        assertTrue(response.data.readText().contains("window.loaded"))
    }

    @Test
    fun missingStaticAssetReturnsNotFoundInsteadOfIndexHtml() {
        val root = webRoot()
        val jsResponse = serve(root, "/static/missing.js")
        val cssResponse = serve(root, "/static/missing.css")

        assertEquals(NanoHTTPD.Response.Status.NOT_FOUND, jsResponse.status)
        assertEquals(NanoHTTPD.Response.Status.NOT_FOUND, cssResponse.status)
    }

    @Test
    fun pathTraversalReturnsForbidden() {
        val root = webRoot()
        val response = serve(root, "/../secret.txt")

        assertEquals(NanoHTTPD.Response.Status.FORBIDDEN, response.status)
    }

    @Test
    fun encodedPathTraversalReturnsForbidden() {
        val root = webRoot()
        val encodedSlashResponse = serve(root, "/..%2Fsecret.txt")
        val encodedDotsResponse = serve(root, "/%2e%2e/secret.txt")

        assertEquals(NanoHTTPD.Response.Status.FORBIDDEN, encodedSlashResponse.status)
        assertEquals(NanoHTTPD.Response.Status.FORBIDDEN, encodedDotsResponse.status)
    }

    @Test
    fun symlinkOutsideWebRootReturnsForbidden() {
        val root = webRoot()
        val secret = temporaryFolder.newFile("secret.txt").also { it.writeText("secret") }
        val link = File(root, "secret-link.txt")
        try {
            Files.createSymbolicLink(link.toPath(), secret.toPath())
        } catch (_: UnsupportedOperationException) {
            return
        } catch (_: SecurityException) {
            return
        }

        val response = serve(root, "/secret-link.txt")

        assertEquals(NanoHTTPD.Response.Status.FORBIDDEN, response.status)
    }

    private fun webRoot() = temporaryFolder.newFolder("www").also { root ->
        root.resolve("index.html").writeText("<html><body>miniapp</body></html>")
        root.resolve("static").mkdirs()
        root.resolve("static/app.js").writeText("window.loaded = true")
    }

    private fun server(root: java.io.File): MySimpleWebServer {
        return MySimpleWebServer("localhost", 0, root, true, "*")
    }

    private fun serve(root: java.io.File, uri: String): NanoHTTPD.Response {
        val server = server(root)
        return server.serve(TestSession(uri, server))
    }

    private fun InputStream.readText(): String = bufferedReader().use { it.readText() }

    private class TestSession(
        private val uri: String,
        private val server: NanoHTTPD
    ) : NanoHTTPD.IHTTPSession {
        override fun execute() = Unit
        override fun getCookies() = server.CookieHandler(emptyMap<String, String>())
        override fun getHeaders(): MutableMap<String, String> = mutableMapOf()
        override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun getMethod(): NanoHTTPD.Method = NanoHTTPD.Method.GET
        override fun getParms(): MutableMap<String, String> = mutableMapOf()
        override fun getParameters(): MutableMap<String, MutableList<String>> = mutableMapOf()
        override fun getQueryParameterString(): String? = null
        override fun getUri(): String = uri
        override fun parseBody(files: MutableMap<String, String>?) = Unit
        override fun getRemoteIpAddress(): String = "127.0.0.1"
        override fun getRemoteHostName(): String = "localhost"
    }
}
