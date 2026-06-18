package com.payme.sdk.ui.miniapp

import android.util.Log
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.core.net.toUri
import com.payme.sdk.PayMEMiniApp
import org.json.JSONArray

private fun isStringInJsonArray(jsonArray: JSONArray, targetString: String): Boolean {
    for (i in 0 until jsonArray.length()) {
        val item = jsonArray.getString(i)
        if (item == targetString) {
            return true
        }
    }
    return false
}

internal class MiniAppBackPressCallback(
    private val webViewProvider: () -> WebView?,
    private val blockedScreensProvider: () -> JSONArray
) : OnBackPressedCallback(true) {
    override fun handleOnBackPressed() {
        Log.d(PayMEMiniApp.TAG, "onBackPressed Called")
        val webView = webViewProvider() ?: return
        if (!webView.canGoBack()) {
            return
        }

        val url = webView.url.orEmpty()
        val urlPath = url.toUri().path.orEmpty()
        val shouldBlockBack = url.isEmpty() || isStringInJsonArray(blockedScreensProvider(), urlPath)
        if (!shouldBlockBack) {
            Log.d(PayMEMiniApp.TAG, "webview back")
            webView.goBack()
        }
    }
}
