package com.payme.sdk.utils

import android.content.Context
import com.mixpanel.android.mpmetrics.MixpanelAPI
import org.json.JSONObject

object MixpanelUtil {
    private var mixpanelAPI: MixpanelAPI? = null

    fun initializeMixpanel(context: Context, token: String) {
        mixpanelAPI = MixpanelAPI.getInstance(context, token, false)
        mixpanelAPI?.flushBatchSize = 20
    }

    fun trackEvent(eventName: String, properties: JSONObject?) {
        mixpanelAPI?.track(eventName, properties)
    }

    fun flushEvents() {
        mixpanelAPI?.flush()

    }

    fun setPeople(name: String, phone: String, email: String, accountId: Number) {
        val json = JSONObject()
        json.put("\$name", name)
        json.put("\$phone", phone)
        json.put("\$email", email)
        mixpanelAPI?.identify(accountId.toString())
        mixpanelAPI?.people?.set(json)
    }
}