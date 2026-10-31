package com.payme.sdk.ui.miniapp

import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.payme.sdk.utils.WebViewJsDispatcher
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
import com.payme.sdk.ui.showSubWebView
import com.payme.sdk.viewmodels.DeepLinkViewModel
import com.payme.sdk.viewmodels.MiniappViewModel
import com.payme.sdk.viewmodels.NotificationViewModel
import com.payme.sdk.viewmodels.PayMEUpdatePatchViewModel
import com.payme.sdk.webServer.JavaScriptInterface
import org.json.JSONObject

internal class MiniAppWebViewController(
    private val fragment: Fragment,
    private val viewsProvider: () -> MiniAppViews,
    private val updateViewModelProvider: () -> PayMEUpdatePatchViewModel,
    private val notificationViewModelProvider: () -> NotificationViewModel,
    private val miniappViewModelProvider: () -> MiniappViewModel,
    private val deepLinkViewModelProvider: () -> DeepLinkViewModel,
    private val configProvider: () -> PayMEConfig,
    private val loadUrlProvider: () -> String,
    private val localOriginProvider: () -> String?,
    private val onLoadUrlChanged: (String) -> Unit,
    private val openTypeProvider: () -> OpenMiniAppType,
    private val onUrlPartChanged: (String) -> Unit,
    private val onUnauthorizedHttpError: (String) -> Unit,
    private val onReturnError: (String) -> Unit,
    private val onRestartLocalServer: () -> Unit,
    private val onSendNativeDeviceInfo: (onSent: () -> Unit) -> Unit
) {
    private var bridge: JavaScriptInterface? = null
    private var bridgeOrigin: String? = null
    private var clientFactory: MiniAppWebViewClientFactory? = null

    fun load(webView: WebView, url: String) {
        if (bridge == null) return
        val origin = localOriginProvider() ?: return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            onReturnError(JSONObject().apply {
                put("code", "UNSUPPORTED_WEBVIEW")
                put("description", "Please update Android System WebView")
                put("isCloseMiniApp", true)
            }.toString())
            return
        }
        if (bridgeOrigin != origin) {
            if (bridgeOrigin != null) WebViewCompat.removeWebMessageListener(webView, BRIDGE_NAME)
            WebViewCompat.addWebMessageListener(webView, BRIDGE_NAME, setOf(origin)) { _, message, sourceOrigin, isMainFrame, _ ->
                if (message.type == androidx.webkit.WebMessageCompat.TYPE_STRING && OriginPolicy.acceptsBridge(sourceOrigin.toString(), localOriginProvider(), isMainFrame)) {
                    message.data?.let { bridge?.dispatch(it) }
                }
            }
            bridgeOrigin = origin
        }
        WebViewJsDispatcher.trust(webView, origin)
        val mainUrl = OriginPolicy.mainPageUrl(url, origin)
        onLoadUrlChanged(mainUrl)
        webView.loadUrl(mainUrl)
        if (!OriginPolicy.isLocal(url, origin) &&
            (url.startsWith("https://") || url.startsWith("http://")) &&
            android.net.Uri.parse(url).host != "localhost"
        ) {
            showSubWebView(fragment, OriginPolicy.subWebViewUrl(url))
        }
    }

    /** Teardown and terminal errors: the page keeps no bridge until configure() runs again. */
    fun removeBridge(webView: WebView) {
        bridge = null
        if (bridgeOrigin != null) {
            try {
                WebViewCompat.removeWebMessageListener(webView, BRIDGE_NAME)
            } catch (e: Exception) {
                Log.d(PayMEMiniApp.TAG, "removeWebMessageListener failed: ${e.javaClass.simpleName}")
            }
            bridgeOrigin = null
        }
    }

    fun redispatchOpenMiniApp(webView: WebView) {
        clientFactory?.redispatchOpenMiniApp(webView)
    }

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
        bridge = javaScriptInterface
        bridgeOrigin = null
        WebViewJsDispatcher.trust(webView, "")
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
            val factory = MiniAppWebViewClientFactory(
                fragment = fragment,
                viewsProvider = viewsProvider,
                updateViewModelProvider = updateViewModelProvider,
                notificationViewModelProvider = notificationViewModelProvider,
                miniappViewModelProvider = miniappViewModelProvider,
                deepLinkViewModelProvider = deepLinkViewModelProvider,
                configProvider = configProvider,
                loadUrlProvider = loadUrlProvider,
                localOriginProvider = localOriginProvider,
                loadLocalPage = { view, url -> load(view, url) },
                openTypeProvider = openTypeProvider,
                onUnauthorizedHttpError = onUnauthorizedHttpError,
                onReturnError = onReturnError,
                onRestartLocalServer = onRestartLocalServer,
                onSendNativeDeviceInfo = onSendNativeDeviceInfo
            )
            clientFactory = factory
            webViewClient = factory.create()

            MiniAppWebSettingsConfigurator.configure(settings)
            requestFocus(View.FOCUS_DOWN)
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(0)

            WebStorage.getInstance().deleteAllData()
        }
    }

    private companion object {
        const val BRIDGE_NAME = "messageHandlers"
    }
}
