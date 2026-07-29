package com.payme.sdk.ui.miniapp

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.WebView
import com.payme.sdk.PayMEMiniApp

internal class MiniAppWebViewRefreshGuard(
    private val webViewProvider: () -> WebView?
) {
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var isRefreshCheckNeeded = false

    fun scheduleCheck() {
        isRefreshCheckNeeded = true
        refreshHandler.postDelayed({
            val webView = webViewProvider()
            if (isRefreshCheckNeeded && webView?.visibility == View.VISIBLE) {
                if (!isWebViewContentVisible(webView)) {
                    Log.d(PayMEMiniApp.TAG, "WebView phát hiện màn hình trắng, đang làm mới...")
                    val currentUrl = webView.url
                    if (!currentUrl.isNullOrEmpty()) {
                        webView.loadUrl(currentUrl)
                    }
                }
                isRefreshCheckNeeded = false
            }
        }, REFRESH_CHECK_DELAY_MS)
    }

    fun clear() {
        refreshHandler.removeCallbacksAndMessages(null)
        isRefreshCheckNeeded = false
    }

    private fun isWebViewContentVisible(webView: WebView): Boolean {
        return webView.contentHeight > 0 && webView.progress == 100
    }

    private companion object {
        const val REFRESH_CHECK_DELAY_MS = 500L
    }
}
