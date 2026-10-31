package com.example.sdk

import com.payme.sdk.network_requests.generateRequestPassphrase
import com.payme.sdk.network_requests.CryptoAES
import com.payme.sdk.ui.miniapp.OriginPolicy
import com.payme.sdk.ui.miniapp.source.OtaConnection
import com.payme.sdk.utils.ArchiveUtils
import com.payme.sdk.utils.KycImageStore
import com.payme.sdk.utils.WebViewJsDispatcher
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.security.SecureRandom
import java.security.KeyPairGenerator
import java.util.Base64
import java.util.zip.ZipInputStream
import javax.crypto.Cipher

class SecurityPolicyTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun passphraseUsesRandomBytesAndFitsSmallRsaOaep() {
        var calls = 0
        val random = object : SecureRandom() {
            override fun nextBytes(bytes: ByteArray) { calls++; assertEquals(16, bytes.size); super.nextBytes(bytes) }
        }
        val samples = (1..1000).map { generateRequestPassphrase(random) }
        assertEquals(1000, calls)
        assertEquals(1000, samples.toSet().size)
        samples.forEach { assertEquals(22, it.length); assertEquals(16, Base64.getDecoder().decode(it).size) }
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(512) }.generateKeyPair()
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA1AndMGF1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, pair.public)
        val encrypted = cipher.doFinal(samples.first().toByteArray(Charsets.US_ASCII))
        cipher.init(Cipher.DECRYPT_MODE, pair.private)
        assertEquals(samples.first(), String(cipher.doFinal(encrypted), Charsets.US_ASCII))
        val aes = CryptoAES()
        assertEquals("dummy-message", aes.decryptAES(samples.first(), aes.encryptAES(samples.first(), "dummy-message")))
    }

    @Test fun localOriginRequiresExactHostSchemeAndPort() {
        val origin = "http://localhost:4646"
        assertTrue(OriginPolicy.isLocal("$origin/route?q=1", origin))
        listOf("http://localhost.evil.com:4646", "http://localhost:4647", "https://localhost:4646", "http://x@localhost:4646", "http://localhost", "not a url").forEach { assertFalse(OriginPolicy.isLocal(it, origin)) }
        assertFalse(OriginPolicy.isLocal(origin, null))
        assertFalse(OriginPolicy.isLocal(origin, "http://localhost:4647"))
    }

    @Test fun freshServerNeverLoadsAStalePortOrForeignUrlIntoMainWebView() {
        val origin = "http://localhost:4647"
        assertEquals("$origin/", OriginPolicy.mainPageUrl("http://localhost:4646/payment", origin))
        assertEquals("$origin/", OriginPolicy.mainPageUrl("https://foreign.example", origin))
        assertEquals("$origin/payment", OriginPolicy.mainPageUrl("$origin/payment", origin))
    }

    @Test fun bridgeRejectsForeignOriginsAndAllSubframes() {
        val origin = "http://localhost:4646"
        assertTrue(OriginPolicy.acceptsBridge(origin, origin, true))
        assertFalse(OriginPolicy.acceptsBridge(origin, origin, false))
        assertFalse(OriginPolicy.acceptsBridge("https://evil.example", origin, true))
        assertFalse(OriginPolicy.acceptsBridge("http://localhost:4647", origin, true))
    }

    @Test fun otaRequiresExactHttpsHost() {
        assertTrue(OtaConnection.isAllowed("https://static.payme.vn/a.zip"))
        listOf("http://static.payme.vn/a", "https://static.payme.vn.evil.com/a", "https://user@static.payme.vn/a", "https://static.payme.vn:8443/a", "https://evil.com/a").forEach { assertFalse(OtaConnection.isAllowed(it)) }
        var opened = false
        try { OtaConnection.open("http://localhost:1234") { opened = true; error("must not connect") }; fail() } catch (_: java.io.IOException) { }
        assertFalse(opened)
    }

    @Test fun dispatcherPreservesJsonWithoutScriptLiteralInjection() {
        val value = "quote' backslash\\ </script> \u2028\u2029"
        val data = com.google.gson.Gson().toJson(value)
        val script = WebViewJsDispatcher.buildScript("nativeResult", data)!!
        val argument = script.substringAfter("](").substringBeforeLast(");true;")
        assertEquals(value, JsonParser.parseString(argument).asString)
        assertFalse(script.contains("</script>"))
        assertFalse(script.contains('\u2028'))
        assertNull(WebViewJsDispatcher.buildScript("x);attack();//", data))
        assertNull(WebViewJsDispatcher.buildScript("nativeResult", "{});attack();//"))
    }

    @Test fun archiveRejectsTraversalAndLimitsIncludingSkippedEntries() {
        fun rejects(vararg entries: Pair<String, String>, maxBytes: Long = 1024, maxEntries: Int = 10) {
            val destination = temp.newFolder()
            try {
                ZipInputStream(zipBytes(*entries).inputStream()).use { ArchiveUtils.unzipEntries(it, destination, maxBytes, maxEntries) }
                fail("Expected archive rejection")
            } catch (_: IllegalArgumentException) { }
        }
        rejects("../escape.txt" to "x")
        rejects("../sibling/escape.txt" to "x")
        rejects("large.txt" to "12345", maxBytes = 4)
        rejects("__MACOSX/skip" to "12345", maxBytes = 4)
        rejects("a" to "a", "b" to "b", maxEntries = 1)
        ZipInputStream(zipBytes("build/index.html" to "ok").inputStream()).use { assertTrue(ArchiveUtils.unzipEntries(it, temp.newFolder())) }
    }

    @Test fun kycImagesArePrivateAndCanBeReadTwiceBeforeCleanup() {
        val files = temp.newFolder()
        val image = KycImageStore.file(files, "kycFrontIdCard.jpeg")
        image.writeText("dummy-image")
        repeat(2) { assertEquals("dummy-image", KycImageStore.resolve(files, "/images/kycFrontIdCard.jpeg")!!.readText()) }
        assertNull(KycImageStore.resolve(files, "/images/../kycFrontIdCard.jpeg"))
        assertNull(KycImageStore.resolve(files, "/images/unknown.jpeg"))
        assertFalse(image.path.contains("/www/"))
        KycImageStore.clear(files)
        assertFalse(image.exists())
    }
}
