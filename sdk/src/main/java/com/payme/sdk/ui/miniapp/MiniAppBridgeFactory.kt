package com.payme.sdk.ui.miniapp

import android.app.Activity
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.payme.sdk.runtime.MiniAppSession
import com.payme.sdk.utils.BiometricGateway
import com.payme.sdk.utils.PermissionCameraUtil
import com.payme.sdk.utils.WebViewJsDispatcher
import com.payme.sdk.webServer.JavaScriptInterface
import org.json.JSONArray

internal class MiniAppBridgeFactory(
    private val activityProvider: () -> Activity?,
    private val webViewProvider: () -> WebView?,
    private val sessionProvider: () -> MiniAppSession,
    private val kycControllerProvider: () -> MiniAppKycController,
    private val permissionControllerProvider: () -> MiniAppPermissionController,
    private val onSendNativeDeviceInfo: () -> Unit,
    private val onSuccess: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onForceClose: () -> Unit,
    private val onChangeEnv: (String) -> Unit,
    private val onChangeLocale: (String) -> Unit,
    private val onSetListScreenBackBlocked: (JSONArray) -> Unit,
    private val onSetModalHeight: (Int) -> Unit
) {
    fun create(webView: WebView): JavaScriptInterface {
        val session = sessionProvider()
        val bridge = MiniAppBridge(
            setNativePreferences = { data -> activityProvider()?.let { WebViewJsDispatcher.setNativePreferences(it, data) } },
            sendNativePreferences = { activityProvider()?.let { WebViewJsDispatcher.sendNativePreferences(it, webView) } },
            biometricAuthen = { data ->
                BiometricGateway.authenticate(activityProvider() as AppCompatActivity, webViewProvider()!!, data)
            },
            startCardKyc = { data -> kycControllerProvider().startCardKyc(data) },
            startFaceKyc = { data -> kycControllerProvider().startFaceKyc(data) },
            startKalapaKyc = { data -> kycControllerProvider().startKalapaKyc(data) },
            startKalapaNFC = { data -> kycControllerProvider().startKalapaNFC(data) },
            startFaceAuthen = { data -> kycControllerProvider().startFaceAuthen(data) },
            openSettings = { activityProvider()?.let { PermissionCameraUtil().openSetting(it) } },
            share = { data -> permissionControllerProvider().share(data) },
            requestPermission = { data -> permissionControllerProvider().requestPermission(data) },
            sendNativeDeviceInfo = { onSendNativeDeviceInfo() },
            getContacts = { permissionControllerProvider().getContacts() },
            nativeOpenKeyboard = {
                activityProvider()?.let { WebViewJsDispatcher.nativeOpenKeyboard(it, webViewProvider()) }
            },
            openWebView = { data -> permissionControllerProvider().openWebView(data) },
            onSuccess = { data -> onSuccess(data) },
            onError = { data -> onError(data) },
            closeMiniApp = { onForceClose() },
            openUrl = { data -> permissionControllerProvider().openUrl(data) },
            saveQR = { data -> permissionControllerProvider().saveQR(data) },
            changeEnv = { data -> onChangeEnv(data) },
            changeLocale = { data -> onChangeLocale(data) },
            setListScreenBackBlocked = { data -> onSetListScreenBackBlocked(data) },
            setModalHeight = { data -> onSetModalHeight(data) },
            requestNFCPermission = { _: String -> permissionControllerProvider().requestNFCPermission() },
            oneSignalSendTags = session.callbacks.onOneSignalSendTags,
            oneSignalDeleteTags = session.callbacks.onOneSignalDeleteTags
        )
        return JavaScriptInterface(bridge)
    }
}
