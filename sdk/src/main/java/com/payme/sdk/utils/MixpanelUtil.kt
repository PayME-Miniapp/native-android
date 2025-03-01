package com.payme.sdk.utils

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import com.mixpanel.android.mpmetrics.MixpanelAPI
import com.payme.sdk.PayMEMiniApp
import org.json.JSONObject

object MixpanelUtil {
    @SuppressLint("StaticFieldLeak")
    private var mixpanelAPI: MixpanelAPI? = null

    fun initializeMixpanel(context: Context, token: String) {
        // Sử dụng applicationContext để tránh rò rỉ bộ nhớ
        mixpanelAPI = MixpanelAPI.getInstance(context.applicationContext, token, false)
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