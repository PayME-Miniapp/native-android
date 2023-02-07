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
) {
    @JavascriptInterface
    public fun jsPreferences(data: String?) {
        Log.d("HIEU", "anh dat goi jspref: $data")
        setNativePreferences(data)
    }

    @JavascriptInterface
    public fun jsRequestPreferences() {
        sendNativePreferences()
    }

    @JavascriptInterface
    public fun jsLog(data: String) {
        Log.d("HIEU", "jsLog: $data")
    }

    @JavascriptInterface
    public fun jsBiometricAuthentication(data: String) {
        Log.d("HIEU", "anh dat goi jsBiometricAuthentication: $data")
        biometricAuthen(data)
    }

    @JavascriptInterface
    public fun jsRequestCardKYC(data: String) {
        Log.d("HIEU", "anh dat goi jsRequestCardKyc: $data")
        startCardKyc(data)
    }

    @JavascriptInterface
    public fun jsRequestFaceKYC(data: String) {
        Log.d("HIEU", "anh dat goi jsRequestFaceKyc: $data")
        startFaceKyc(data)
    }

    @JavascriptInterface
    public fun jsOpenSetting(data: String) {
        Log.d("HIEU", "anh dat goi jsOpenSetting: $data")
        openSettings()
    }

    @JavascriptInterface
    public fun jsShare(data: String) {
        Log.d("HIEU", "anh dat goi jsShare: $data")
        share(data)
    }

    @JavascriptInterface
    public fun jsRequestPermission(data: String) {
        Log.d("HIEU", "anh dat goi jsRequestPermission: $data")
        requestPermission(data)
    }

    @JavascriptInterface
    public fun jsRequestDeviceInfo(data: String) {
        Log.d("HIEU", "anh dat goi jsRequestDeviceInfo: $data")
        sendNativeDeviceInfo()
    }

    @JavascriptInterface
    public fun jsRequestContacts(data: String) {
        Log.d("HIEU", "anh dat goi jsRequestContacts: $data")
        getContacts()
    }

    @JavascriptInterface
    public fun jsShowKeyboard(data: String) {
        Log.d("HIEU", "anh dat goi jsShowKeyboard: $data")
        nativeOpenKeyboard()
    }

    @JavascriptInterface
    public fun jsOneSignalSendTags(data: String) {
        Log.d("HIEU", "anh dat goi jsOneSignalSendTags: $data")
        try {
            val parseJson = JSONObject(data)
//            OneSignal.sendTags(parseJson)
//            OneSignal.sendTags(data)
            if (PayMEMiniApp.onOneSignalSendTags != null) {
                PayMEMiniApp.onOneSignalSendTags!!(data)
            }
        } catch (e: IOException) {
            Log.e("HIEU", "Exception thrown while trying to close Face Detector: $e")
        }
    }

    @JavascriptInterface
    public fun jsOneSignalDeleteTags(data: String) {
        Log.d("HIEU", "anh dat goi jsOneSignalDeleteTags: $data")
        if (PayMEMiniApp.onOneSignalDeleteTags != null) {
            PayMEMiniApp.onOneSignalDeleteTags!!(data)
        }
//        OneSignal.deleteTags(data)
    }

    @JavascriptInterface
    public fun jsOpenWebView(data: String) {
        Log.d("HIEU", "anh dat goi jsOpenWebView: $data")
        openWebView(data)
    }

    @JavascriptInterface
    public fun jsError(data: String) {
        Log.d("HIEU", "anh dat goi jsError: $data")
        onError(data)
    }

    @JavascriptInterface
    public fun jsResponse(data: String) {
        Log.d("HIEU", "anh dat goi jsResponse: $data")
        onSuccess(data)
    }
}