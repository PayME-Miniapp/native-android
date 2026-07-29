package com.example.sdk

import com.payme.sdk.models.ENV
import com.payme.sdk.models.Locale
import com.payme.sdk.models.OpenMiniAppPaymentData
import com.payme.sdk.models.PaymentData
import com.payme.sdk.runtime.PayMEConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMiniAppDataTest {
    @Test
    fun paymentDataUsesProvidedSessionConfig() {
        val data = OpenMiniAppPaymentData(
            phone = "0900000000",
            paymentData = PaymentData(
                transactionId = "txn-1",
                amount = 120000,
                note = "note",
                ipnUrl = "https://example.com/ipn",
                extraData = mapOf("nested" to mapOf("flag" to true)),
                isShowResult = true
            )
        )

        val json = data.toJsonData(
            PayMEConfig(
                appId = "app-id",
                publicKey = "public-key",
                privateKey = "private-key",
                env = ENV.SANDBOX,
                locale = Locale.en
            )
        )

        assertEquals("PAY", json.get("action").asString)
        assertEquals("app-id", json.get("appId").asString)
        assertEquals("public-key", json.get("publicKey").asString)
        assertEquals("private-key", json.get("privateKey").asString)
        assertEquals("SANDBOX", json.get("env").asString)
        assertEquals("en", json.get("locale").asString)
        assertEquals("0900000000", json.get("phone").asString)
        assertEquals("txn-1", json.get("transactionId").asString)
        assertTrue(json.getAsJsonObject("extraData").getAsJsonObject("nested").get("flag").asBoolean)
    }
}
