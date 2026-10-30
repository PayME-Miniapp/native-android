package com.payme.sdk.ui.miniapp

import org.json.JSONArray
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
    private val sendNativeDeviceInfo: (onSent: () -> Unit) -> Unit,
    private val resendOpenMiniApp: () -> Unit,
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
    private val trackLog: (String) -> Unit,
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
    // The web asks for device info on every boot; after a reload it also needs openMiniApp again,
    // sent only once the device-info reply is out (that reply can wait on the root check).
    fun jsRequestDeviceInfo() = sendNativeDeviceInfo { resendOpenMiniApp() }
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

    // Web logs may contain credentials and personal data: trackLog must sanitise (JsLogTelemetry).
    fun jsLog(data: String) = trackLog(data)
}
