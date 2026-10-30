package com.payme.sdk.ui.miniapp

import android.graphics.Bitmap
import android.util.Log
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import com.google.gson.Gson
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.Locale
import com.payme.sdk.models.OpenMiniAppType
import com.payme.sdk.runtime.PayMEConfig
import com.payme.sdk.ui.showSubWebView
import com.payme.sdk.utils.WebViewJsDispatcher
import com.payme.sdk.viewmodels.DeepLinkViewModel
import com.payme.sdk.viewmodels.MiniappViewModel
import com.payme.sdk.viewmodels.NotificationViewModel
import com.payme.sdk.viewmodels.PayMEUpdatePatchViewModel
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

internal class MiniAppWebViewClientFactory(
    private val fragment: Fragment,
    private val viewsProvider: () -> MiniAppViews,
    private val updateViewModelProvider: () -> PayMEUpdatePatchViewModel,
    private val notificationViewModelProvider: () -> NotificationViewModel,
    private val miniappViewModelProvider: () -> MiniappViewModel,
    private val deepLinkViewModelProvider: () -> DeepLinkViewModel,
    private val configProvider: () -> PayMEConfig,
    private val loadUrlProvider: () -> String,
    private val localOriginProvider: () -> String?,
    private val loadLocalPage: (WebView, String) -> Unit,
    private val openTypeProvider: () -> OpenMiniAppType,
    private val onUnauthorizedHttpError: (String) -> Unit,
    private val onReturnError: (String) -> Unit,
    private val onRestartLocalServer: () -> Unit,
    private val onSendNativeDeviceInfo: (onSent: () -> Unit) -> Unit
) {
    fun create(): WebViewClient {
        return object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return handleNavigation(view, url, isMainFrame = true, isRedirect = false)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                if (request == null || view == null) return false
                return handleNavigation(view, request.url.toString(), request.isForMainFrame, request.isRedirect)
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (request == null) return null
                val url = request.url.toString()
                if (isLocalWebSourceUrl(url)) {
                    val file = fragment.context?.let { com.payme.sdk.utils.KycImageStore.resolve(it.filesDir, request.url.path.orEmpty()) }
                    if (file != null) {
                        val input = try { file.inputStream() } catch (_: Exception) { return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0))) }
                        return WebResourceResponse("image/jpeg", null, 200, "OK", mapOf("Cache-Control" to "no-store"), input)
                    }
                }
                return null
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse
            ) {
                val statusCode = errorResponse.statusCode
                val errorUrl = request?.url.toString()
                val errorData = errorResponse.reasonPhrase ?: ""

                if (statusCode == HTTP_STATUS_UNAUTHORIZED && !isLocalWebSourceUrl(errorUrl)) {
                    onUnauthorizedHttpError(errorUrl)
                    return
                }

                Log.d(
                    PayMEMiniApp.TAG,
                    "HTTP error $statusCode for URL: $errorUrl, Reason: $errorData"
                )

                if (statusCode == 404 && isLocalWebSourceUrl(errorUrl)) {
                    if (request?.isForMainFrame == false) {
                        Log.w(PayMEMiniApp.TAG, "Ignore local subresource 404: $errorUrl")
                        return
                    }
                    handleLocalResourceNotFound(errorResponse, errorData, errorUrl)
                }
            }

            override fun onPageStarted(view: WebView?, url: String?, facIcon: Bitmap?) {
                if (url == loadUrlProvider() && isLocalWebSourceUrl(url.orEmpty())) {
                    viewsProvider().loadingView.visibility = View.VISIBLE
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(PayMEMiniApp.TAG, "page finished $url")
                viewsProvider().loadingView.visibility = View.GONE
                if (url == loadUrlProvider() && isLocalWebSourceUrl(url.orEmpty())) {
                    dispatchMainPageFinishedEvents()
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                val url = request?.url?.toString().orEmpty()
                if (isLocalWebSourceUrl(url)) {
                    onRestartLocalServer()
                    reloadCurrentWebSource(view)
                }
                Log.d(PayMEMiniApp.TAG, "error https ${error?.description}")
            }
        }
    }

    private fun handleNavigation(view: WebView, url: String, isMainFrame: Boolean, isRedirect: Boolean): Boolean {
        val action = if (isMainFrame) {
            OriginPolicy.mainFrameNavigation(view.url, url, isRedirect, localOriginProvider())
        } else {
            OriginPolicy.subFrameNavigation(url)
        }
        return when (action) {
            NavigationAction.LOAD -> false
            NavigationAction.SUB_WEB_VIEW -> {
                showSubWebView(fragment, OriginPolicy.subWebViewUrl(url))
                true
            }
            NavigationAction.EXTERNAL -> {
                ExternalUrlLauncher.open(view.context, url)
                true
            }
            NavigationAction.BLOCK -> true
        }
    }

    /** jsRequestDeviceInfo after a reload: same payload and trusted-origin check as onPageFinished. */
    fun redispatchOpenMiniApp(webView: WebView) {
        val activity = fragment.activity ?: return
        if (!isLocalWebSourceUrl(webView.url.orEmpty())) return
        dispatchOpenMiniApp(activity, webView)
    }

    private fun reloadCurrentWebSource(view: WebView?) {
        val fallbackUrl = loadUrlProvider()
        if (fallbackUrl.isNotEmpty()) {
            view?.post {
                loadLocalPage(view, fallbackUrl)
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
                Log.e(PayMEMiniApp.TAG, "404 error content length: ${responseText.length}")

                if (responseText.contains("Error 404, file not found") || errorData.contains("Not Found")) {
                    val errorMessage = when (configProvider().locale) {
                        Locale.en -> "Resource not found error detected from local server - closing mini app"
                        Locale.vi -> "Phát hiện lỗi không tìm thấy tài nguyên từ local server - đóng mini app"
                    }
                    Log.e(PayMEMiniApp.TAG, errorMessage)

                    fragment.activity?.runOnUiThread {
                        sendResourceNotFoundError(errorUrl)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(PayMEMiniApp.TAG, "Error analyzing 404 response: ${e.message}")
        }
    }

    private fun sendResourceNotFoundError(errorUrl: String) {
        val errorDescription = when (configProvider().locale) {
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

    private fun dispatchMainPageFinishedEvents() {
        val activity = fragment.activity ?: return
        val webView = viewsProvider().webView
        val updateViewModel = updateViewModelProvider()
        updateViewModel.setShowUpdatingUI(false)
        updateViewModel.setWebLoaded(true)
        // Device info first (its root check may finish in the background), then the rest in order.
        onSendNativeDeviceInfo {
            // The root check can finish later; re-check the document before sending preferences and openMiniApp.
            if (!isLocalWebSourceUrl(webView.url.orEmpty())) return@onSendNativeDeviceInfo
            dispatchPendingNotification(activity, webView)
            WebViewJsDispatcher.sendNativePreferences(activity, webView)
            dispatchOpenMiniApp(activity, webView)
            dispatchDeepLink(activity, webView)
        }
    }

    private fun dispatchPendingNotification(activity: android.app.Activity, webView: WebView) {
        val notificationViewModel = notificationViewModelProvider()
        val notiValue = notificationViewModel.getNotificationJSON().value
        if (notiValue != null && notiValue.length() > 0) {
            WebViewJsDispatcher.evaluate(
                activity,
                webView,
                "nativeNotificationOpenedApp",
                notiValue.toString()
            )
            notificationViewModel.setNotificationJSON(JSONObject())
        }
    }

    private fun dispatchOpenMiniApp(activity: android.app.Activity, webView: WebView) {
        val openMiniAppData = miniappViewModelProvider().openMiniAppData
        if (openMiniAppData != null) {
            val json = openMiniAppData.toJsonData(configProvider())
            val jsonOpenTypeString = JSONObject.quote(openTypeProvider().toString())
            WebViewJsDispatcher.evaluate(
                activity,
                webView,
                "openMiniApp",
                Gson().toJson(json).toString()
            )
            WebViewJsDispatcher.evaluate(activity, webView, "openType", jsonOpenTypeString)
        }
    }

    private fun dispatchDeepLink(activity: android.app.Activity, webView: WebView) {
        val deepLinkViewModel = deepLinkViewModelProvider()
        val deeplink = deepLinkViewModel.getDeepLinkUrl().value
        if (!deeplink.isNullOrEmpty()) {
            val jsonQuoteString = JSONObject.quote(deeplink)
            WebViewJsDispatcher.evaluate(
                activity,
                webView,
                "nativeLinkingOpenedApp",
                jsonQuoteString
            )
            deepLinkViewModel.setDeepLinkUrl("")
        }
    }

    private fun isLocalWebSourceUrl(url: String): Boolean {
        return OriginPolicy.isLocal(url, localOriginProvider())
    }

    private companion object {
        const val HTTP_STATUS_UNAUTHORIZED = 401
    }
}
