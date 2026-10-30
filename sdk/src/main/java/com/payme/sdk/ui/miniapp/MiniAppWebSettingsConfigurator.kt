package com.payme.sdk.ui.miniapp

import android.annotation.SuppressLint
import android.webkit.WebSettings

internal object MiniAppWebSettingsConfigurator {
    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    fun configure(settings: WebSettings) {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // The bundle and KYC images are served from localhost; nothing loads file:// or content://.
            allowFileAccess = false
            allowContentAccess = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadsImagesAutomatically = true
            // Off-origin https pages (3DS, bank) keep passive http content; active http content is blocked.
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            useWideViewPort = true
        }
    }
}
