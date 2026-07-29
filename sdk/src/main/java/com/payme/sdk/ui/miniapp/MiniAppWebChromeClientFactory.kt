package com.payme.sdk.ui.miniapp

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.payme.sdk.PayMEMiniApp

internal class MiniAppWebChromeClientFactory(
    private val loadUrlProvider: () -> String,
    private val onUrlPartChanged: (String) -> Unit,
    private val fileChooserCallbackProvider: () -> ValueCallback<Array<Uri>>?,
    private val setFileChooserCallback: (ValueCallback<Array<Uri>>?) -> Unit,
    private val launchFileChooser: (Intent) -> Unit
) {
    fun create(): WebChromeClient {
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

                if (fileChooserCallbackProvider() != null) {
                    fileChooserCallbackProvider()?.onReceiveValue(null)
                }

                setFileChooserCallback(filePathCallback)
                val intent = fileChooserParams?.createIntent()
                if (intent == null) {
                    setFileChooserCallback(null)
                    return true
                }
                try {
                    launchFileChooser(intent)
                } catch (e: Exception) {
                    Log.d(PayMEMiniApp.TAG, "chay vo catch ${e.message}")
                }
                return true
            }
        }
    }
}
