package com.payme.sdk.ui.miniapp

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.view.View
import android.webkit.WebView
import com.payme.sdk.utils.Utils
import com.payme.sdk.utils.WebViewJsDispatcher

internal class MiniAppKeyboardHeightDispatcher(
    private val contextProvider: () -> Context?,
    private val activityProvider: () -> Activity?,
    private val rootViewProvider: () -> View?,
    private val webViewProvider: () -> WebView?,
    private val isWebLoadedProvider: () -> Boolean
) {
    fun register() {
        val rootView = rootViewProvider() ?: return
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            if (!isWebLoadedProvider()) {
                return@addOnGlobalLayoutListener
            }
            dispatchKeyboardHeight(rootView)
        }
    }

    private fun dispatchKeyboardHeight(rootView: View) {
        val context = contextProvider() ?: return
        val visibleRect = Rect()
        rootView.getWindowVisibleDisplayFrame(visibleRect)

        val screenHeight = rootView.rootView.height
        val heightDiff = screenHeight - visibleRect.bottom
        val navigationBarHeight = Utils.getSoftNavigationHeight(context)
        val height = if (heightDiff > KEYBOARD_THRESHOLD_PX) {
            Utils.pxToDp(context, heightDiff + navigationBarHeight).toString()
        } else {
            "0"
        }

        val activity = activityProvider() ?: return
        val webView = webViewProvider() ?: return
        WebViewJsDispatcher.evaluate(activity, webView, "nativeKeyboardHeight", height)
    }

    private companion object {
        const val KEYBOARD_THRESHOLD_PX = 140
    }
}
