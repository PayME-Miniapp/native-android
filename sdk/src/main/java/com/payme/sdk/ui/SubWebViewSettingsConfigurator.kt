package com.payme.sdk.ui

import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView

internal object SubWebViewSettingsConfigurator {
    fun configure(webView: WebView, useOverview: Boolean) {
        webView.settings.apply {
            setSupportZoom(true)
            textZoom = 100
            if (useOverview) {
                useWideViewPort = true
                loadWithOverviewMode = true
                userAgentString = System.getProperty("http.agent")
            }
            builtInZoomControls = true
            displayZoomControls = false
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            domStorageEnabled = true
            setGeolocationEnabled(true)
            mediaPlaybackRequiresUserGesture = false
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        }
        webView.requestFocus(View.FOCUS_DOWN)
    }
}
