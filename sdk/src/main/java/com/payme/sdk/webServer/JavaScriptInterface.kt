package com.payme.sdk.webServer

import android.util.Log
import android.webkit.JavascriptInterface
import com.payme.sdk.PayMEMiniApp
import org.json.JSONObject
import java.io.IOException

class JavaScriptInterface(
    val setNativePreferences: ((String?) -> Unit),
    val sendNativePreferences: () -> Unit,
    val biometricAuthen: ((String) -> Unit),
    val startCardKyc: ((String) -> Unit),
    val startFaceKyc: ((String) -> Unit),
    val openSettings: () -> Unit,
    val share: ((String) -> Unit),
    val requestPermission: ((String) -> Unit),
    val sendNativeDeviceInfo: () -> Unit,
    val getContacts: () -> Unit,
    val nativeOpenKeyboard: () -> Unit,
    val openWebView: (String) -> Unit,
    val onSuccess: (String) -> Unit,
    val onError: (String) -> Unit,
    val closeMiniApp: () -> Unit,
    val openUrl: (String) -> Unit,
) {
    @JavascriptInterface
    public fun jsPreferences(data: String?) {
        Log.d(PayMEMiniApp.TAG, " jspref: $data")
        setNativePreferences(data)
    }

    @JavascriptInterface
    public fun jsRequestPreferences() {
        sendNativePreferences()
    }

    @JavascriptInterface
    public fun jsLog(data: String) {
        Log.d(PayMEMiniApp.TAG, "jsLog: $data")
    }

    @JavascriptInterface
    public fun jsBiometricAuthentication(data: String) {
        Log.d(PayMEMiniApp.TAG, " jsBiometricAuthentication: $data")
        biometricAuthen(data)
    }

    @JavascriptInterface
    public fun jsRequestCardKYC(data: String) {
        Log.d(PayMEMiniApp.TAG, " jsRequestCardKyc: $data")
        startCardKyc(data)
    }

    @JavascriptInterface
    public fun jsRequestFaceKYC(data: String) {
        Log.d(PayMEMiniApp.TAG, " jsRequestFaceKyc: $data")
        startFaceKyc(data)
    }

    @JavascriptInterface
    public fun jsOpenSetting(data: String) {
        Log.d(PayMEMiniApp.TAG, " jsOpenSetting: $data")
        openSettings()
    }

    @JavascriptInterface
    public fun jsShare(data: String) {
        Log.d(PayMEMiniApp.TAG, " jsShare: $data")
        share(data)
    }

    @JavascriptInterface
    public fun jsRequestPermission(data: String) {
        Log.d(PayMEMiniApp.TAG, " jsRequestPermission: $data")
        requestPermission(data)
    }

    @JavascriptInterface
    public fun jsRequestDeviceInfo(data: String) {
        Log.d(PayMEMiniApp.TAG, " jsRequestDeviceInfo: $data")
        sendNativeDeviceInfo()
    }

    @JavascriptInterface
    public fun jsRequestContacts(data: String) {
        Log.d(PayMEMiniApp.TAG, " jsRequestContacts: $data")
        getContacts()
    }

    @JavascriptInterface
    public fun jsShowKeyboard(data: String) {
        Log.d(PayMEMiniApp.TAG, " jsShowKeyboard: $data")
        nativeOpenKeyboard()
    }

    @JavascriptInterface
    public fun jsOneSignalSendTags(data: String) {
        Log.d(PayMEMiniApp.TAG, " jsOneSignalSendTags: $data")
        try {
            val parseJson = JSONObject(data)
//            OneSignal.sendTags(parseJson)
//            OneSignal.sendTags(data)
            if (PayMEMiniApp.onOneSignalSendTags != null) {
                PayMEMiniApp.onOneSignalSendTags!!(data)
            }
        } catch (e: IOException) {
            Log.e(PayMEMiniApp.TAG, "Exception thrown while trying to close Face Detector: $e")
        }
    }

    @JavascriptInterface
    public fun jsOneSignalDeleteTags(data: String) {
        Log.d(PayMEMiniApp.TAG, " jsOneSignalDeleteTags: $data")
        if (PayMEMiniApp.onOneSignalDeleteTags != null) {
            PayMEMiniApp.onOneSignalDeleteTags!!(data)
        }
//        OneSignal.deleteTags(data)
    }

    @JavascriptInterface
    public fun jsOpenWebView(data: String) {
        Log.d(PayMEMiniApp.TAG, " jsOpenWebView: $data")
        openWebView(data)
    }

    @JavascriptInterface
    public fun jsError(data: String) {
        Log.d(PayMEMiniApp.TAG, " jsError: $data")
        onError(data)
    }

    @JavascriptInterface
    public fun jsResponse(data: String) {
        Log.d(PayMEMiniApp.TAG, " jsResponse: $data")
        onSuccess(data)
    }

    @JavascriptInterface
    public fun jsClose(data: String) {
        Log.d(PayMEMiniApp.TAG, " jsClose: $data")
        closeMiniApp()
    }

    @JavascriptInterface
    public fun jsOpenUrl(data: String) {
        Log.d(PayMEMiniApp.TAG, " jsOpenUrl: $data")
        openUrl(data)
    }
}