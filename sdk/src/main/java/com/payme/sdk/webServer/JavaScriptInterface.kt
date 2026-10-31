package com.payme.sdk.webServer

import android.util.Log
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.ui.miniapp.MiniAppBridge

internal class JavaScriptInterface(private val bridge: MiniAppBridge) {
    fun dispatch(message: String) {
        val envelope = try { com.google.gson.GsonBuilder().setStrictness(com.google.gson.Strictness.STRICT).create().fromJson(message, com.google.gson.JsonObject::class.java) ?: return } catch (_: Exception) { return }
        val method = envelope.get("type")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString ?: return
        val data = envelope.get("data")?.toString() ?: "null"
        try {
            when (method) {
                "jsPreferences" -> jsPreferences(data)
                "jsRequestPreferences" -> jsRequestPreferences()
                "jsLog" -> jsLog(data)
                "jsBiometricAuthentication" -> jsBiometricAuthentication(data)
                "jsRequestCardKYC" -> jsRequestCardKYC(data)
                "jsRequestKalapaKYC" -> jsRequestKalapaKYC(data)
                "jsRequestKalapaNFC" -> jsRequestKalapaNFC(data)
                "jsRequestFaceAuthen" -> jsRequestFaceAuthen(data)
                "jsRequestFaceKYC" -> jsRequestFaceKYC(data)
                "jsListScreensSwipeBlocked" -> jsListScreensSwipeBlocked(data)
                "jsPostModalHeight" -> jsPostModalHeight(data)
                "jsOpenSetting" -> jsOpenSetting(data)
                "jsShare" -> jsShare(data)
                "jsRequestPermission" -> jsRequestPermission(data)
                "jsRequestDeviceInfo" -> jsRequestDeviceInfo(data)
                "jsRequestContacts" -> jsRequestContacts(data)
                "jsShowKeyboard" -> jsShowKeyboard(data)
                "jsOneSignalSendTags" -> jsOneSignalSendTags(data)
                "jsOneSignalDeleteTags" -> jsOneSignalDeleteTags(data)
                "jsOpenWebView" -> jsOpenWebView(data)
                "jsError" -> jsError(data)
                "jsResponse" -> jsResponse(data)
                "jsClose" -> jsClose(data)
                "jsOpenUrl" -> jsOpenUrl(data)
                "jsSaveQR" -> jsSaveQR(data)
                "jsChangeEnv" -> jsChangeEnv(data)
                "jsChangeLocale" -> jsChangeLocale(data)
                "jsRequestNFCPermission" -> jsRequestNFCPermission(data)
            }
        } catch (_: Exception) {
            // Malformed bridge messages must not crash the host app or expose payloads in logs.
        }
    }

    public fun jsPreferences(data: String?) {
        Log.d(PayMEMiniApp.TAG, "jspref")
        bridge.jsPreferences(data)
    }

    public fun jsRequestPreferences() {
        bridge.jsRequestPreferences()
    }

    public fun jsLog(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsLog")
        bridge.jsLog(data)
    }

    public fun jsBiometricAuthentication(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsBiometricAuthentication")
        bridge.jsBiometricAuthentication(data)
    }

    public fun jsRequestCardKYC(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsRequestCardKyc")
        bridge.jsRequestCardKyc(data)
    }

    public fun jsRequestKalapaKYC(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsRequestKalapaKYC")
        bridge.jsRequestKalapaKyc(data)
    }

    public fun jsRequestKalapaNFC(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsRequestKalapaNFC")
        bridge.jsRequestKalapaNfc(data)
    }

    public fun jsRequestFaceAuthen(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsRequestFaceAuthen")
        bridge.jsRequestFaceAuthen(data)
    }

    public fun jsRequestFaceKYC(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsRequestFaceKyc")
        bridge.jsRequestFaceKyc(data)
    }

    public fun jsListScreensSwipeBlocked(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsListScreensSwipeBlocked")
        bridge.jsListScreensSwipeBlocked(data)
    }

    public fun jsPostModalHeight(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsPostModalHeight")
        bridge.jsPostModalHeight(data)
    }

    public fun jsOpenSetting(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsOpenSetting")
        bridge.jsOpenSetting()
    }

    public fun jsShare(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsShare")
        bridge.jsShare(data)
    }

    public fun jsRequestPermission(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsRequestPermission")
        bridge.jsRequestPermission(data)
    }

    public fun jsRequestDeviceInfo(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsRequestDeviceInfo")
        bridge.jsRequestDeviceInfo()
    }

    public fun jsRequestContacts(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsRequestContacts")
        bridge.jsRequestContacts()
    }

    public fun jsShowKeyboard(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsShowKeyboard")
        bridge.jsShowKeyboard()
    }

    public fun jsOneSignalSendTags(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsOneSignalSendTags")
        bridge.jsOneSignalSendTags(data)
    }

    public fun jsOneSignalDeleteTags(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsOneSignalDeleteTags")
        bridge.jsOneSignalDeleteTags(data)
    }

    public fun jsOpenWebView(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsOpenWebView")
        bridge.jsOpenWebView(data)
    }

    public fun jsError(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsError")
        bridge.jsError(data)
    }

    public fun jsResponse(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsResponse")
        bridge.jsResponse(data)
    }

    public fun jsClose(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsClose")
        bridge.jsClose()
    }

    public fun jsOpenUrl(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsOpenUrl")
        bridge.jsOpenUrl(data)
    }

    public fun jsSaveQR(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsSaveQR")
        bridge.jsSaveQr(data)
    }

    public fun jsChangeEnv(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsChangeEnv")
        bridge.jsChangeEnv(data)
    }

    public fun jsChangeLocale(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsChangeLocale")
        bridge.jsChangeLocale(data)
    }

    public fun jsRequestNFCPermission(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsRequestNFCPermission")
        bridge.jsRequestNfcPermission(data)
    }
}
