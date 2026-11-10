package com.example.sdk

import com.payme.sdk.models.ENV
import com.payme.sdk.models.Locale
import com.payme.sdk.runtime.PayMEConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class PayMEConfigKeyTest {
    // Dữ liệu giả, không phải khóa thật.
    private val oneLine = "-----BEGIN PUBLIC KEY-----AAAABBBBCCCC/+==-----END PUBLIC KEY-----"

    private fun create(key: String) = PayMEConfig.create("app", key, key, ENV.SANDBOX, Locale.vi)

    @Test
    fun literalBackslashNIsRemovedLikeARealLineBreak() {
        // Khóa viết trong chuỗi raw """...\n...""" của Kotlin: "\n" là hai ký tự "\" và "n".
        val literal = """-----BEGIN PUBLIC KEY-----\nAAAABBBB\nCCCC/+==\n-----END PUBLIC KEY-----"""
        val config = create(literal)
        assertEquals(oneLine, config.publicKey)
        assertEquals(oneLine, config.privateKey)
    }

    @Test
    fun literalCrLfAndRealLineBreaksAreRemoved() {
        assertEquals(oneLine, create("-----BEGIN PUBLIC KEY-----\\r\\nAAAABBBB\\r\\nCCCC/+==\\r\\n-----END PUBLIC KEY-----").publicKey)
        assertEquals(oneLine, create("  -----BEGIN PUBLIC KEY-----\r\nAAAABBBB\nCCCC/+==\n-----END PUBLIC KEY-----\n").publicKey)
    }

    @Test
    fun wellFormedOneLineKeyIsUnchanged() {
        assertEquals(oneLine, create(oneLine).publicKey)
    }
}
