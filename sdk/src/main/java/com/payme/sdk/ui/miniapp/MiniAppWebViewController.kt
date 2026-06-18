package com.payme.sdk.ui.miniapp

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.Locale
import com.payme.sdk.models.OpenMiniAppType
import com.payme.sdk.utils.Utils
import com.payme.sdk.viewmodels.DeepLinkViewModel
import com.payme.sdk.viewmodels.MiniappViewModel
import com.payme.sdk.viewmodels.NotificationViewModel
import com.payme.sdk.viewmodels.PayMEUpdatePatchViewModel
import com.payme.sdk.webServer.JavaScriptInterface
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

internal class MiniAppWebViewController(
    private val fragment: Fragment,
    private val viewsProvider: () -> MiniAppViews,
    private val updateViewModelProvider: () -> PayMEUpdatePatchViewModel,
    private val notificationViewModelProvider: () -> NotificationViewModel,
    private val miniappViewModelProvider: () -> MiniappViewModel,
    private val deepLinkViewModelProvider: () -> DeepLinkViewModel,
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

            webChromeClient = createWebChromeClient()
            webViewClient = createWebViewClient()

            configureWebSettings(settings)
            requestFocus(View.FOCUS_DOWN)
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(0)
            addJavascriptInterface(javaScriptInterface, "messageHandlers")

            WebStorage.getInstance().deleteAllData()
            loadUrl("javascript:localStorage.clear()")
        }
    }

    private fun createWebChromeClient(): WebChromeClient {
        return object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                val url = view.url.orEmpty().removePrefix(loadUrlProvider())
                onUrlPartChanged(url)
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                Log.d(PayMEMiniApp.TAG, "chay vo on file chooser $filePathCallback")

                if (fileChooserCallback != null) {
                    fileChooserCallback?.onReceiveValue(null)
                }

                fileChooserCallback = filePathCallback
                val intent = fileChooserParams?.createIntent()
                if (intent == null) {
                    fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = null
                    return true
                }
                try {
                    fileChooserLauncher.launch(intent)
                } catch (e: Exception) {
                    Log.d(PayMEMiniApp.TAG, "chay vo catch ${e.message}")
                    return true
                }
                return true
            }
        }
    }

    private fun createWebViewClient(): WebViewClient {
        return object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                Log.d(PayMEMiniApp.TAG, "shouldOverrideUrlLoading url: $url")
                return if (url.contains(".pdf")) {
                    val pdfUrl = "https://docs.google.com/gview?embedded=true&url=$url"
                    view.loadUrl(pdfUrl)
                    false
                } else if (url.startsWith("http://") || url.startsWith("https://")) {
                    view.loadUrl(url)
                    false
                } else try {
                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                    view.context.startActivity(intent)
                    true
                } catch (e: Exception) {
                    Log.d(PayMEMiniApp.TAG, "shouldOverrideUrlLoading Exception: $e")
                    true
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url.toString()
                Log.d(PayMEMiniApp.TAG, "shouldOverrideUrlLoading url: $url")
                return if (url.contains(".pdf")) {
                    val pdfUrl = "https://docs.google.com/gview?embedded=true&url=$url"
                    view?.loadUrl(pdfUrl)
                    false
                } else if (url.startsWith("http://") || url.startsWith("https://")) {
                    false
                } else try {
                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                    view?.context?.startActivity(intent)
                    true
                } catch (e: Exception) {
                    Log.d(PayMEMiniApp.TAG, "shouldOverrideUrlLoading Exception: $e")
                    true
                }
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse
            ) {
                val statusCode = errorResponse.statusCode
                val errorUrl = request?.url.toString()
                val errorData = errorResponse.reasonPhrase ?: ""

                if (statusCode == HTTP_STATUS_UNAUTHORIZED && !isLocalhostUrl(errorUrl)) {
                    onUnauthorizedHttpError(errorUrl)
                    return
                }

                Log.d(
                    PayMEMiniApp.TAG,
                    "HTTP error $statusCode for URL: $errorUrl, Reason: $errorData"
                )

                if (statusCode == 404 && errorUrl.startsWith("http://localhost")) {
                    handleLocalResourceNotFound(errorResponse, errorData, errorUrl)
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, facIcon: Bitmap?) {
                Log.d(PayMEMiniApp.TAG, "page started $url")
                if (url == loadUrlProvider()) {
                    viewsProvider().loadingView.visibility = View.VISIBLE
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(PayMEMiniApp.TAG, "page finished $url")
                viewsProvider().loadingView.visibility = View.GONE
                if (url == loadUrlProvider()) {
                    dispatchMainPageFinishedEvents()
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                onRestartLocalServer()
                Log.d(PayMEMiniApp.TAG, "error https ${error?.description}")
            }
        }
    }

    private fun handleLocalResourceNotFound(
        errorResponse: WebResourceResponse,
        errorData: String,
        errorUrl: String
    ) {
        try {
            val inputStream = errorResponse.data
            if (inputStream != null) {
                val reader = BufferedReader(InputStreamReader(inputStream))
                val responseText = reader.readText()
                Log.e(PayMEMiniApp.TAG, "404 error content: $responseText")

                if (responseText.contains("Error 404, file not found") || errorData.contains("Not Found")) {
                    val errorMessage = when (PayMEMiniApp.locale) {
                        Locale.en -> "Resource not found error detected from local server - closing mini app"
                        Locale.vi -> "Phát hiện lỗi không tìm thấy tài nguyên từ local server - đóng mini app"
                    }
                    Log.e(PayMEMiniApp.TAG, errorMessage)

                    fragment.activity?.runOnUiThread {
                        val errorDescription = when (PayMEMiniApp.locale) {
                            Locale.en -> "Error 404: Resource not found at path: $errorUrl"
                            Locale.vi -> "Lỗi 404: Không tìm thấy tài nguyên tại: $errorUrl"
                        }

                        try {
                            val errorJson = JSONObject().apply {
                                put("code", "RESOURCE_NOT_FOUND")
                                put("description", errorDescription)
                                put("isCloseMiniApp", true)
                            }.toString()
                            onReturnError(errorJson)
                        } catch (e: Exception) {
                            Log.e(PayMEMiniApp.TAG, "Error sending error to parent: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(PayMEMiniApp.TAG, "Error analyzing 404 response: ${e.message}")
        }
    }

    private fun dispatchMainPageFinishedEvents() {
        val activity = fragment.activity ?: return
        val webView = viewsProvider().webView
        val updateViewModel = updateViewModelProvider()
        updateViewModel.setShowUpdatingUI(false)
        updateViewModel.setWebLoaded(true)
        onSendNativeDeviceInfo()

        val notificationViewModel = notificationViewModelProvider()
        val notiValue = notificationViewModel.getNotificationJSON().value
        if (notiValue != null && notiValue.length() > 0) {
            Utils.evaluateJSWebView(
                activity,
                webView,
                "nativeNotificationOpenedApp",
                notiValue.toString(),
                null
            )
            notificationViewModel.setNotificationJSON(JSONObject())
        }
        Utils.sendNativePref(activity, webView)

        val openMiniAppData = miniappViewModelProvider().openMiniAppData
        if (openMiniAppData != null) {
            val json = openMiniAppData.toJsonData()
            val jsonOpenTypeString = JSONObject.quote(openTypeProvider().toString())
            Utils.evaluateJSWebView(
                activity,
                webView,
                "openMiniApp",
                Gson().toJson(json).toString(),
                null
            )
            Utils.evaluateJSWebView(activity, webView, "openType", jsonOpenTypeString, null)
        }

        val deepLinkViewModel = deepLinkViewModelProvider()
        val deeplink = deepLinkViewModel.getDeepLinkUrl().value
        if (!deeplink.isNullOrEmpty()) {
            val jsonQuoteString = JSONObject.quote(deeplink)
            Utils.evaluateJSWebView(
                activity,
                webView,
                "nativeLinkingOpenedApp",
                jsonQuoteString,
                null
            )
            deepLinkViewModel.setDeepLinkUrl("")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun configureWebSettings(settings: WebSettings) {
        settings.apply {
            // The miniapp bridge is injected through messageHandlers and requires JavaScript.
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            useWideViewPort = true
        }
    }

    private fun isLocalhostUrl(url: String): Boolean {
        return url.startsWith("http://localhost") || url.startsWith("https://localhost")
    }

    private companion object {
        const val HTTP_STATUS_UNAUTHORIZED = 401
    }
}
