package com.payme.sdk.ui.miniapp

import android.util.Log
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.utils.MixpanelUtil
import com.payme.sdk.utils.Formatters
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal class MiniAppBridge(
    private val setNativePreferences: (String?) -> Unit,
    private val sendNativePreferences: () -> Unit,
    private val biometricAuthen: (String) -> Unit,
    private val startCardKyc: (String) -> Unit,
    private val startKalapaKyc: (String) -> Unit,
    private val startKalapaNFC: (String) -> Unit,
    private val startFaceKyc: (String) -> Unit,
    private val startFaceAuthen: (String) -> Unit,
    private val openSettings: () -> Unit,
    private val share: (String) -> Unit,
    private val requestPermission: (String) -> Unit,
    private val sendNativeDeviceInfo: () -> Unit,
    private val getContacts: () -> Unit,
    private val nativeOpenKeyboard: () -> Unit,
    private val openWebView: (String) -> Unit,
    private val onSuccess: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val closeMiniApp: () -> Unit,
    private val openUrl: (String) -> Unit,
    private val saveQR: (String) -> Unit,
    private val changeEnv: (String) -> Unit,
    private val changeLocale: (String) -> Unit,
    private val setListScreenBackBlocked: (JSONArray) -> Unit,
    private val setModalHeight: (Int) -> Unit,
    private val requestNFCPermission: (String) -> Unit,
    private val oneSignalSendTags: ((String) -> Unit)?,
    private val oneSignalDeleteTags: ((String) -> Unit)?
) {
    fun jsPreferences(data: String?) = setNativePreferences(data)
    fun jsRequestPreferences() = sendNativePreferences()
    fun jsBiometricAuthentication(data: String) = biometricAuthen(data)
    fun jsRequestCardKyc(data: String) = startCardKyc(data)
    fun jsRequestKalapaKyc(data: String) = startKalapaKyc(data)
    fun jsRequestKalapaNfc(data: String) = startKalapaNFC(data)
    fun jsRequestFaceAuthen(data: String) = startFaceAuthen(data)
    fun jsRequestFaceKyc(data: String) = startFaceKyc(data)
    fun jsOpenSetting() = openSettings()
    fun jsShare(data: String) = share(data)
    fun jsRequestPermission(data: String) = requestPermission(data)
    fun jsRequestDeviceInfo() = sendNativeDeviceInfo()
    fun jsRequestContacts() = getContacts()
    fun jsShowKeyboard() = nativeOpenKeyboard()
    fun jsOpenWebView(data: String) = openWebView(data)
    fun jsError(data: String) = onError(data)
    fun jsResponse(data: String) = onSuccess(data)
    fun jsClose() = closeMiniApp()
    fun jsOpenUrl(data: String) = openUrl(data)
    fun jsSaveQr(data: String) = saveQR(data)
    fun jsChangeEnv(data: String) = changeEnv(data)
    fun jsChangeLocale(data: String) = changeLocale(data)
    fun jsRequestNfcPermission(data: String) = requestNFCPermission(data)
    fun jsOneSignalSendTags(data: String) = oneSignalSendTags?.invoke(data)
    fun jsOneSignalDeleteTags(data: String) = oneSignalDeleteTags?.invoke(data)

    fun jsListScreensSwipeBlocked(data: String) {
        val parseJson = JSONObject(data)
        val list = parseJson.optJSONArray("list") ?: JSONArray()
        setListScreenBackBlocked(list)
    }

    fun jsPostModalHeight(data: String) {
        val parseJson = JSONObject(data)
        setModalHeight(parseJson.optInt("height"))
    }

    fun jsLog(data: String) {
        try {
            val json = JSONObject(Formatters.formatStringToValidJsonString(data))
            MixpanelUtil.trackEvent("JSLog", json)
            trackAccountProfile(json, data)
        } catch (jsonE: JSONException) {
            val properties = JSONObject()
            properties.put("value", "[non-json]")
            MixpanelUtil.trackEvent("JSLog", properties)
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "jsLog catch error: ${e.message}")
        }
    }

    private fun trackAccountProfile(json: JSONObject, rawData: String) {
        if (!rawData.contains("ewallet/sdk/account/getAccountInfo")) {
            return
        }
        val type = json.optString("type", "")
        if (type != "REQUEST-PAYME") {
            return
        }
        val accountData = json.optJSONObject("data")
            ?.optJSONObject("data")
            ?.optJSONObject("data")
            ?.optJSONObject("accountInfo")
        val phone = accountData?.optString("phone", "")
        val email = accountData?.optString("email", "")
        val fullname = accountData?.optString("fullname", "")
        val accountId = accountData?.opt("accountId") as? Number
        if (phone?.isNotEmpty() == true &&
            email?.isNotEmpty() == true &&
            fullname?.isNotEmpty() == true &&
            accountId != null
        ) {
            MixpanelUtil.setPeople(fullname, phone, email, accountId)
        }
    }
}
