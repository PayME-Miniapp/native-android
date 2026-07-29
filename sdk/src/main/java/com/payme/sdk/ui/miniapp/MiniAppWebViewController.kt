package com.payme.sdk.ui.miniapp

import android.app.Activity
import android.net.Uri
import android.util.Log
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.OpenMiniAppType
import com.payme.sdk.runtime.PayMEConfig
import com.payme.sdk.viewmodels.DeepLinkViewModel
import com.payme.sdk.viewmodels.MiniappViewModel
import com.payme.sdk.viewmodels.NotificationViewModel
import com.payme.sdk.viewmodels.PayMEUpdatePatchViewModel
import com.payme.sdk.webServer.JavaScriptInterface

internal class MiniAppWebViewController(
    private val fragment: Fragment,
    private val viewsProvider: () -> MiniAppViews,
    private val updateViewModelProvider: () -> PayMEUpdatePatchViewModel,
    private val notificationViewModelProvider: () -> NotificationViewModel,
    private val miniappViewModelProvider: () -> MiniappViewModel,
    private val deepLinkViewModelProvider: () -> DeepLinkViewModel,
    private val configProvider: () -> PayMEConfig,
    private val loadUrlProvider: () -> String,
    private val openTypeProvider: () -> OpenMiniAppType,
    private val onUrlPartChanged: (String) -> Unit,
    private val onUnauthorizedHttpError: (String) -> Unit,
    private val onReturnError: (String) -> Unit,
    private val onRestartLocalServer: () -> Unit,
    private val onSendNativeDeviceInfo: () -> Unit
) {
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher =
        fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_CANCELED -> {
                    Log.d(PayMEMiniApp.TAG, "RESULT_CANCELED fileChooserLauncher")
                    fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = null
                }

                Activity.RESULT_OK -> {
                    Log.d(PayMEMiniApp.TAG, "RESULT_OK fileChooserLauncher")
                    if (fileChooserCallback == null) return@registerForActivityResult
                    fileChooserCallback?.onReceiveValue(
                        WebChromeClient.FileChooserParams.parseResult(
                            result.resultCode, result.data
                        )
                    )
                    fileChooserCallback = null
                }
            }
        }

    fun configure(webView: WebView, javaScriptInterface: JavaScriptInterface) {
        webView.apply {
            setWillNotDraw(false)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            webChromeClient = MiniAppWebChromeClientFactory(
                loadUrlProvider = loadUrlProvider,
                onUrlPartChanged = onUrlPartChanged,
                fileChooserCallbackProvider = { fileChooserCallback },
                setFileChooserCallback = { fileChooserCallback = it },
                launchFileChooser = { fileChooserLauncher.launch(it) }
            ).create()
            webViewClient = MiniAppWebViewClientFactory(
                fragment = fragment,
                viewsProvider = viewsProvider,
                updateViewModelProvider = updateViewModelProvider,
                notificationViewModelProvider = notificationViewModelProvider,
                miniappViewModelProvider = miniappViewModelProvider,
                deepLinkViewModelProvider = deepLinkViewModelProvider,
                configProvider = configProvider,
                loadUrlProvider = loadUrlProvider,
                openTypeProvider = openTypeProvider,
                onUnauthorizedHttpError = onUnauthorizedHttpError,
                onReturnError = onReturnError,
                onRestartLocalServer = onRestartLocalServer,
                onSendNativeDeviceInfo = onSendNativeDeviceInfo
            ).create()

            MiniAppWebSettingsConfigurator.configure(settings)
            requestFocus(View.FOCUS_DOWN)
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(0)
            addJavascriptInterface(javaScriptInterface, "messageHandlers")

            WebStorage.getInstance().deleteAllData()
            loadUrl("javascript:localStorage.clear()")
        }
    }
}
