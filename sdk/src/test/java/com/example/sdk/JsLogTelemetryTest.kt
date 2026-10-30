package com.example.sdk

import com.google.gson.Gson
import com.payme.sdk.ui.miniapp.JsLogTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class JsLogTelemetryTest {
    private val gson = Gson()

    /** The web sends jsLog data as JSON.stringify(log), wrapped as a JSON string by the envelope. */
    private fun asBridgeData(log: Any): String = gson.toJson(gson.toJson(log))

    @Test fun keepsOnlyAllowlistedPrimitiveFields() {
        val log = mapOf(
            "type" to "REQUEST-PAYME", "action" to "PAY", "env" to "PRODUCTION", "appId" to "dummy-app",
            "sdkVersion" to "1.0.23", "platform" to "android", "screen" to "payment", "step" to "confirm",
            "status" to "FAILED", "result" to true, "code" to 105002, "errorCode" to "E1",
            "api" to "https://api.example/be/ewallet/sdk/account/getAccountInfo",
            "accessToken" to "dummy-token", "privateKey" to "dummy-private", "publicKey" to "dummy-public",
            "biometricKey" to "dummy-bio", "phone" to "0900000000", "email" to "a@example.com",
            "fullname" to "Dummy Name", "identifyNumber" to "000000000", "address" to "Somewhere",
            "data" to mapOf("accessToken" to "dummy-token")
        )
        val properties = JsLogTelemetry.sanitize(asBridgeData(log))
        assertEquals(
            mapOf(
                "type" to "REQUEST-PAYME", "action" to "PAY", "env" to "PRODUCTION", "appId" to "dummy-app",
                "sdkVersion" to "1.0.23", "platform" to "android", "screen" to "payment", "step" to "confirm",
                "status" to "FAILED", "result" to true, "code" to 105002L, "errorCode" to "E1"
            ),
            properties
        )
    }

    @Test fun nestedOrLongValuesInAllowedFieldsAreDropped() {
        val log = mapOf(
            "type" to "RESPONSE-UPLOAD",
            "result" to mapOf("accessToken" to "dummy-token"),
            "status" to listOf("dummy"),
            "step" to "x".repeat(65)
        )
        assertEquals(mapOf("type" to "RESPONSE-UPLOAD"), JsLogTelemetry.sanitize(asBridgeData(log)))
        assertEquals(mapOf("code" to 1.5), JsLogTelemetry.sanitize(gson.toJson(mapOf("code" to 1.5))))
    }

    @Test fun freeTextAndMalformedLogsCarryNoContent() {
        val nonJson = mapOf("value" to "[non-json]")
        listOf(
            asBridgeData("OPEN-MINI-APP - {\"phone\":\"0900000000\",\"accessToken\":\"dummy-token\"}"),
            asBridgeData("Payment click submit"),
            "not json", "", null, gson.toJson(listOf("dummy-token"))
        ).forEach { assertEquals(it.toString(), nonJson, JsLogTelemetry.sanitize(it)) }
    }

    @Test fun identifiesOnlyByAccountIdFromAccountInfoLog() {
        val accountInfo = mapOf(
            "type" to "REQUEST-PAYME",
            "api" to "https://api.example/be/ewallet/sdk/account/getAccountInfo",
            "data" to mapOf("data" to mapOf("data" to mapOf("accountInfo" to mapOf(
                "accountId" to 123456789, "phone" to "0900000000", "email" to "a@example.com", "fullname" to "Dummy"
            ))))
        )
        assertEquals("123456789", JsLogTelemetry.accountId(asBridgeData(accountInfo)))
        assertFalse(JsLogTelemetry.sanitize(asBridgeData(accountInfo)).containsKey("data"))
        assertNull(JsLogTelemetry.accountId(asBridgeData(accountInfo + ("type" to "PAYLOAD-PAYME"))))
        assertNull(JsLogTelemetry.accountId(asBridgeData(accountInfo + ("api" to "https://api.example/be/other"))))
        assertNull(JsLogTelemetry.accountId(asBridgeData(mapOf("type" to "REQUEST-PAYME", "api" to "x/ewallet/sdk/account/getAccountInfo", "code" to 1))))
        val phoneAsId = mapOf("type" to "REQUEST-PAYME", "api" to "ewallet/sdk/account/getAccountInfo",
            "data" to mapOf("data" to mapOf("data" to mapOf("accountInfo" to mapOf("accountId" to "0900000000")))))
        assertNull(JsLogTelemetry.accountId(asBridgeData(phoneAsId)))
    }
}
