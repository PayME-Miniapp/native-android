package com.payme.sdk.ui.miniapp

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.Strictness
import com.payme.sdk.utils.MixpanelUtil
import org.json.JSONObject

/**
 * jsLog -> Mixpanel through an explicit allowlist. Web logs can carry tokens, keys, PII and raw
 * API payloads, so only short primitive values of the fields below are kept. No people properties.
 */
internal object JsLogTelemetry {
    const val EVENT_NAME = "JSLog"

    private val ALLOWED_FIELDS = setOf(
        "type", "action", "env", "appId", "sdkVersion", "platform",
        "screen", "step", "status", "result", "code", "errorCode"
    )
    private const val MAX_VALUE_LENGTH = 64
    private const val ACCOUNT_INFO_API = "ewallet/sdk/account/getAccountInfo"
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()

    fun track(data: String?) {
        MixpanelUtil.trackEvent(EVENT_NAME, JSONObject(sanitize(data)))
        accountId(data)?.let { MixpanelUtil.identify(it) }
    }

    fun sanitize(data: String?): Map<String, Any> {
        val json = parseObject(data) ?: return mapOf("value" to "[non-json]")
        val properties = linkedMapOf<String, Any>()
        for ((key, value) in json.entrySet()) {
            if (key !in ALLOWED_FIELDS) continue
            primitiveValue(value)?.let { properties[key] = it }
        }
        return properties
    }

    /** Baseline identified users by accountId from the getAccountInfo log. Keep only that id. */
    fun accountId(data: String?): String? {
        if (data == null || !data.contains(ACCOUNT_INFO_API)) return null
        val json = parseObject(data) ?: return null
        if ((json.get("type") as? JsonPrimitive)?.takeIf { it.isString }?.asString != "REQUEST-PAYME") return null
        var node: JsonObject = json
        for (key in listOf("data", "data", "data", "accountInfo")) {
            node = node.get(key) as? JsonObject ?: return null
        }
        val id = node.get("accountId") as? JsonPrimitive ?: return null
        return if (id.isNumber) id.asString.toLongOrNull()?.toString() else null
    }

    private fun primitiveValue(value: JsonElement): Any? {
        val primitive = value as? JsonPrimitive ?: return null
        return when {
            primitive.isBoolean -> primitive.asBoolean
            primitive.isNumber -> primitive.asString.let { it.toLongOrNull() ?: it.toDoubleOrNull() }
            else -> primitive.asString.takeIf { it.length <= MAX_VALUE_LENGTH }
        }
    }

    /** The web double-encodes: the envelope data is a JSON string holding the JSON log. */
    private fun parseObject(data: String?): JsonObject? {
        var element = parse(data)
        repeat(2) {
            val primitive = element as? JsonPrimitive
            if (primitive != null && primitive.isString) element = parse(primitive.asString)
        }
        return element as? JsonObject
    }

    private fun parse(text: String?): JsonElement? = try {
        text?.let { gson.fromJson(it, JsonElement::class.java) }
    } catch (_: Exception) {
        null
    }
}
