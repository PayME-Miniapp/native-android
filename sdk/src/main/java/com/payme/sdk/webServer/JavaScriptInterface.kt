package com.payme.sdk.webServer

import android.util.Log
import android.webkit.JavascriptInterface
import com.google.gson.JsonParser
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.utils.MixpanelUtil
import com.payme.sdk.utils.Utils
import org.json.JSONArray
import org.json.JSONException
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
    val saveQR: (String) -> Unit,
    val changeEnv: (String) -> Unit,
    val changeLocale: (String) -> Unit,
    val setListScreenBackBlocked: (JSONArray) -> Unit,
    val setModalHeight: (Int) -> Unit
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
        try {
            val json = JSONObject(Utils.formatStringToValidJsonString(data))
            MixpanelUtil.trackEvent("JSLog", json)
            if (data.contains("ewallet/sdk/account/getAccountInfo")) {
                val type = json.optString("type", "")
                if (type != "REQUEST-PAYME") {
                    return
                }
                val jsonData = json.optJSONObject("data")
                val apiData = jsonData?.optJSONObject("data")
                val responseData = apiData?.optJSONObject("data")
                val accountData = responseData?.optJSONObject("accountInfo")
                val phone = accountData?.optString("phone", "")
                val email = accountData?.optString("email", "")
                val fullname = accountData?.optString("fullname", "")
                val accountId = accountData?.opt("accountId") as Number?
                if (phone?.isNotEmpty() == true && email?.isNotEmpty() == true && fullname?.isNotEmpty() == true && accountId!= null) {
                    MixpanelUtil.setPeople(fullname, phone, email, accountId)
                }
            }
        } catch (jsonE: JSONException) {
            val properties = JSONObject()
            properties.put("value", data)
            MixpanelUtil.trackEvent("JSLog", properties)
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "jsLog catch error: ${e}")
        }
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
    public fun jsListScreensSwipeBlocked(data: String) {
        val parseJson = JSONObject(data)
        Log.d(PayMEMiniApp.TAG, " jsListScreensSwipeBlocked: $parseJson")
        val list = parseJson.optJSONArray("list") ?: JSONArray()
        setListScreenBackBlocked(list)
    }

    @JavascriptInterface
    public fun jsPostModalHeight(data: String) {
        val parseJson = JSONObject(data)
        Log.d(PayMEMiniApp.TAG, " jsPostModalHeight: $parseJson")
        val height = parseJson?.optInt("height") ?: 0
        setModalHeight(height)
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

    @JavascriptInterface
    public fun jsSaveQR(data: String) {
        Log.d(PayMEMiniApp.TAG, " jsSaveQR: $data")
        saveQR(data)
    }

    @JavascriptInterface
    public fun jsChangeEnv(data: String) {
        Log.d(PayMEMiniApp.TAG, " jsChangeEnv: $data")
        changeEnv(data)
    }

    @JavascriptInterface
    public fun jsChangeLocale(data: String) {
        Log.d(PayMEMiniApp.TAG, " jsChangeLocale: $data")
        changeLocale(data)
    }
}