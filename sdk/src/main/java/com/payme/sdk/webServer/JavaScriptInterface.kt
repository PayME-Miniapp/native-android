package com.payme.sdk.webServer

import android.util.Log
import android.webkit.JavascriptInterface
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.ui.miniapp.MiniAppBridge

internal class JavaScriptInterface(private val bridge: MiniAppBridge) {
    @JavascriptInterface
    public fun jsPreferences(data: String?) {
        Log.d(PayMEMiniApp.TAG, "jspref")
        bridge.jsPreferences(data)
    }

    @JavascriptInterface
    public fun jsRequestPreferences() {
        bridge.jsRequestPreferences()
    }

    @JavascriptInterface
    public fun jsLog(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsLog")
        bridge.jsLog(data)
    }

    @JavascriptInterface
    public fun jsBiometricAuthentication(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsBiometricAuthentication")
        bridge.jsBiometricAuthentication(data)
    }

    @JavascriptInterface
    public fun jsRequestCardKYC(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsRequestCardKyc")
        bridge.jsRequestCardKyc(data)
    }

    @JavascriptInterface
    public fun jsRequestKalapaKYC(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsRequestKalapaKYC")
        bridge.jsRequestKalapaKyc(data)
    }

    @JavascriptInterface
    public fun jsRequestKalapaNFC(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsRequestKalapaNFC")
        bridge.jsRequestKalapaNfc(data)
    }

    @JavascriptInterface
    public fun jsRequestFaceAuthen(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsRequestFaceAuthen")
        bridge.jsRequestFaceAuthen(data)
    }

    @JavascriptInterface
    public fun jsRequestFaceKYC(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsRequestFaceKyc")
        bridge.jsRequestFaceKyc(data)
    }

    @JavascriptInterface
    public fun jsListScreensSwipeBlocked(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsListScreensSwipeBlocked")
        bridge.jsListScreensSwipeBlocked(data)
    }

    @JavascriptInterface
    public fun jsPostModalHeight(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsPostModalHeight")
        bridge.jsPostModalHeight(data)
    }

    @JavascriptInterface
    public fun jsOpenSetting(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsOpenSetting")
        bridge.jsOpenSetting()
    }

    @JavascriptInterface
    public fun jsShare(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsShare")
        bridge.jsShare(data)
    }

    @JavascriptInterface
    public fun jsRequestPermission(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsRequestPermission")
        bridge.jsRequestPermission(data)
    }

    @JavascriptInterface
    public fun jsRequestDeviceInfo(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsRequestDeviceInfo")
        bridge.jsRequestDeviceInfo()
    }

    @JavascriptInterface
    public fun jsRequestContacts(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsRequestContacts")
        bridge.jsRequestContacts()
    }

    @JavascriptInterface
    public fun jsShowKeyboard(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsShowKeyboard")
        bridge.jsShowKeyboard()
    }

    @JavascriptInterface
    public fun jsOneSignalSendTags(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsOneSignalSendTags")
        bridge.jsOneSignalSendTags(data)
    }

    @JavascriptInterface
    public fun jsOneSignalDeleteTags(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsOneSignalDeleteTags")
        bridge.jsOneSignalDeleteTags(data)
    }

    @JavascriptInterface
    public fun jsOpenWebView(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsOpenWebView")
        bridge.jsOpenWebView(data)
    }

    @JavascriptInterface
    public fun jsError(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsError")
        bridge.jsError(data)
    }

    @JavascriptInterface
    public fun jsResponse(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsResponse")
        bridge.jsResponse(data)
    }

    @JavascriptInterface
    public fun jsClose(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsClose")
        bridge.jsClose()
    }

    @JavascriptInterface
    public fun jsOpenUrl(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsOpenUrl")
        bridge.jsOpenUrl(data)
    }

    @JavascriptInterface
    public fun jsSaveQR(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsSaveQR")
        bridge.jsSaveQr(data)
    }

    @JavascriptInterface
    public fun jsChangeEnv(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsChangeEnv")
        bridge.jsChangeEnv(data)
    }

    @JavascriptInterface
    public fun jsChangeLocale(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsChangeLocale")
        bridge.jsChangeLocale(data)
    }

    @JavascriptInterface
    public fun jsRequestNFCPermission(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsRequestNFCPermission")
        bridge.jsRequestNfcPermission(data)
    }
}
