package com.payme.sdk.ui.miniapp

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.core.net.toUri
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.utils.WebViewJsDispatcher
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

internal enum class MiniAppBackAction { WEB_HANDLED, WEB_BACK, IGNORE, CLOSE }

/** Back: lùi trong web nếu được; màn web đang chặn back thì bỏ qua; hết lịch sử thì đóng mini-app. */
internal fun resolveMiniAppBack(canGoBack: Boolean, url: String, isBlockedScreen: Boolean): MiniAppBackAction = when {
    url.isNotEmpty() && isBlockedScreen -> MiniAppBackAction.IGNORE
    canGoBack && url.isNotEmpty() -> MiniAppBackAction.WEB_BACK
    canGoBack -> MiniAppBackAction.IGNORE
    else -> MiniAppBackAction.CLOSE
}

/** Hook back của web: chỉ kết quả JS "true" nghĩa là web đã tự xử lý (đóng popup trên cùng / chặn back). */
internal const val WEB_BACK_HOOK_SCRIPT =
    "(function(){try{return !!(window.payMEHandleBack && window.payMEHandleBack())}catch(e){return false}})()"

internal fun isWebBackHandled(jsResult: String?): Boolean = jsResult == "true"

/** Sau hook: web đã xử lý thì xong; không thì theo logic cũ ([resolveMiniAppBack]). */
internal fun resolveMiniAppBackAfterHook(
    jsResult: String?,
    canGoBack: Boolean,
    url: String,
    isBlockedScreen: Boolean
): MiniAppBackAction =
    if (isWebBackHandled(jsResult)) MiniAppBackAction.WEB_HANDLED
    else resolveMiniAppBack(canGoBack, url, isBlockedScreen)

/**
 * Chống gọi lồng: trong lúc chờ kết quả hook, back mới bị bỏ qua. Mỗi lần chờ có token;
 * kết quả muộn (sau timeout hoặc lần khác) không được xử lý lần hai.
 */
internal class WebBackHookGate {
    private var pendingToken = 0
    private var nextToken = 0

    val isPending: Boolean get() = pendingToken != 0

    /** Trả về token mới, hoặc null nếu đang chờ một lần kiểm tra khác. */
    fun begin(): Int? {
        if (isPending) return null
        nextToken = if (nextToken == Int.MAX_VALUE) 1 else nextToken + 1
        pendingToken = nextToken
        return pendingToken
    }

    /** true nếu [token] là lần đang chờ (chỉ một lần, kết quả hay timeout tới trước). */
    fun finish(token: Int): Boolean {
        if (token == 0 || token != pendingToken) return false
        pendingToken = 0
        return true
    }
}

internal class MiniAppBackPressCallback(
    private val webViewProvider: () -> WebView?,
    private val blockedScreensProvider: () -> JSONArray,
    private val onClose: () -> Unit
) : OnBackPressedCallback(true) {
    private val gate = WebBackHookGate()
    private val handler = Handler(Looper.getMainLooper())

    override fun handleOnBackPressed() {
        Log.d(PayMEMiniApp.TAG, "onBackPressed Called")
        val webView = webViewProvider() ?: return
        // Chỉ hỏi web khi đang ở trang mini-app tin cậy; trang ngoài không được chặn back
        if (!WebViewJsDispatcher.isTrustedPage(webView)) {
            applyBack(webView, null)
            return
        }
        val token = gate.begin() ?: return
        // Web không trả lời (renderer treo): không giữ back mãi
        val timeout = Runnable { if (gate.finish(token)) applyBack(webView, null) }
        handler.postDelayed(timeout, WEB_BACK_HOOK_TIMEOUT_MS)
        try {
            webView.evaluateJavascript(WEB_BACK_HOOK_SCRIPT) { result ->
                handler.removeCallbacks(timeout)
                if (gate.finish(token)) applyBack(webView, result)
            }
        } catch (e: Exception) {
            handler.removeCallbacks(timeout)
            if (gate.finish(token)) applyBack(webView, null)
        }
    }

    private fun applyBack(webView: WebView, jsResult: String?) {
        // Fragment/WebView đã huỷ trong lúc chờ: bỏ qua
        if (webViewProvider() !== webView) return
        val url = webView.url.orEmpty()
        val urlPath = url.toUri().path.orEmpty()
        val isBlocked = url.isNotEmpty() && isStringInJsonArray(blockedScreensProvider(), urlPath)
        when (resolveMiniAppBackAfterHook(jsResult, webView.canGoBack(), url, isBlocked)) {
            MiniAppBackAction.WEB_HANDLED -> Log.d(PayMEMiniApp.TAG, "web handled back")
            MiniAppBackAction.WEB_BACK -> {
                Log.d(PayMEMiniApp.TAG, "webview back")
                webView.goBack()
            }
            MiniAppBackAction.IGNORE -> Unit
            MiniAppBackAction.CLOSE -> onClose()
        }
    }

    private companion object {
        const val WEB_BACK_HOOK_TIMEOUT_MS = 1_500L
    }
}
