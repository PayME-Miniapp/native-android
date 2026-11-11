package com.example.sdk

import android.content.ContextWrapper
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.ENV
import com.payme.sdk.models.Locale
import com.payme.sdk.network_requests.CryptoRSA
import com.payme.sdk.runtime.EmbeddedAppKey
import com.payme.sdk.runtime.PayMEConfig
import com.payme.sdk.runtime.PayMERuntime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

/** SDK dùng sẵn bộ key "app"; key đối tác truyền vào bị bỏ qua. */
class EmbeddedAppKeyTest {
    private val context = object : ContextWrapper(null) {
        override fun getApplicationContext() = this
    }

    private fun sha256(value: String) =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

    @Test
    fun embeddedKeyRevealsTheAppKeyPair() {
        assertEquals("app", EmbeddedAppKey.APP_ID)
        assertEquals("2422fb15bbb9e6f05203c0b8000599bb66ff84832dc53c10539cbe5c71e709ef", sha256(EmbeddedAppKey.publicKey))
        assertEquals("8331d1650da2abf82051336134fd06829474a92f28eb3474b20a01a73ee0d99e", sha256(EmbeddedAppKey.privateKey))
        assertEquals("RSA", CryptoRSA.stringToPublicKey(EmbeddedAppKey.publicKey).algorithm)
    }

    @Test
    fun newConstructorUsesTheEmbeddedKey() {
        PayMEMiniApp(context, ENV.SANDBOX, Locale.en)
        val config = PayMERuntime.currentConfig()!!
        assertEquals("app", config.appId)
        assertEquals(PayMEConfig.sanitizeKey(EmbeddedAppKey.publicKey), config.publicKey)
        assertEquals(PayMEConfig.sanitizeKey(EmbeddedAppKey.privateKey), config.privateKey)
        assertEquals(ENV.SANDBOX, config.env)
    }

    @Suppress("DEPRECATION")
    @Test
    fun deprecatedConstructorIgnoresThePartnerKey() {
        PayMEMiniApp(context, "partner", "public", "private", ENV.STAGING, Locale.en)
        val config = PayMERuntime.currentConfig()!!
        assertEquals("app", config.appId)
        assertEquals(PayMEConfig.sanitizeKey(EmbeddedAppKey.privateKey), config.privateKey)
        assertEquals(ENV.STAGING, config.env)
        assertEquals(Locale.en, config.locale)
    }
}
