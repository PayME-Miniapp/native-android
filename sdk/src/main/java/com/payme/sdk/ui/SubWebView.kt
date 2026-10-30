package com.payme.sdk.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.webkit.WebView.WebViewTransport
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentDialog
import androidx.activity.addCallback
import androidx.annotation.RequiresApi
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.R
import com.payme.sdk.ui.miniapp.ExternalUrlLauncher


class SubWebView(
    private val content: String, private val type: String, private val closeInstruction: String
) : DialogFragment() {
    private var rootView: View? = null
    private var myWebView: WebView? = null
    private var subWebView: WebView? = null
    private lateinit var buttonBack: TextView
    private lateinit var buttonReload: ImageView
    private lateinit var buttonBackFooter: ImageView
    private lateinit var loading: View
    private val backGuard = BackRedirectGuard()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
        setStyle(STYLE_NO_FRAME, R.style.DialogStyle)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(
            R.layout.sub_webview, container, false
        )
        rootView = view.findViewById(R.id.root_view)
        // Edge-to-edge (targetSdk 35+) draws the dialog under the system bars; keep the header and footer tappable.
        rootView?.let { root ->
            ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        myWebView = view.findViewById(R.id.webview)
        buttonBackFooter = view.findViewById(R.id.buttonBackFooter)
        buttonReload = view.findViewById(R.id.buttonReload)
        buttonBack = view.findViewById(R.id.buttonBackHeader)
        loading = view.findViewById(R.id.loading)
        loading.setOnClickListener {
            Log.d(PayMEMiniApp.TAG, "loading")
        }
        buttonBack.setOnClickListener {
            dialog?.dismiss()
        }
        buttonBackFooter.setOnClickListener {
            handleBack()
        }
        buttonReload.setOnClickListener {
            if (loading.visibility == View.VISIBLE) {
                return@setOnClickListener
            }

            if (subWebView != null) {
                subWebView!!.reload()
                return@setOnClickListener
            }

            if (myWebView != null) {
                myWebView!!.reload()
            }
        }

        myWebView?.apply {
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                }

                override fun onCreateWindow(
                    view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?
                ): Boolean {
                    Log.d(PayMEMiniApp.TAG, "chay vao oncreate window")
                    if (subWebView != null || view == null || resultMsg == null) {
                        return true
                    }
                    loading.visibility = View.VISIBLE
                    val newWebView = WebView(requireContext())
                    SubWebViewSettingsConfigurator.configure(newWebView, useOverview = true)
                    subWebView = newWebView
                    newWebView.layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    newWebView.setBackgroundColor(Color.TRANSPARENT)
                    view.addView(newWebView)
                    val transport = resultMsg.obj as WebViewTransport
                    transport.webView = newWebView
                    resultMsg.sendToTarget()
                    newWebView.webViewClient = object : WebViewClient() {
                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                            Log.d(PayMEMiniApp.TAG, "shouldOverrideUrlLoading url: $url")
                            return if (url.contains(".pdf")) {
                                loading.visibility = View.VISIBLE
                                val pdfUrl =
                                    "https://docs.google.com/gview?embedded=true&url=${url}"
                                view.loadUrl(pdfUrl)
                                true
                            } else if (url.startsWith("http://") || url.startsWith("https://")) {
                                view.loadUrl(url)
                                false
                            } else {
                                ExternalUrlLauncher.open(view.context, url)
                                true
                            }
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            Log.d(PayMEMiniApp.TAG, "page start $url")
                            if (backGuard.isBounce(url, SystemClock.elapsedRealtime())) {
                                view?.post { closeSubWindow() }
                                return
                            }
                            loading.visibility = View.VISIBLE
                            if (!url.isNullOrEmpty()) {
                                MiniAppFragment.evaluateJs("nativeWebViewNavigation", com.google.gson.Gson().toJson(url))
                                if (closeInstruction.isNotEmpty() && url.contains(closeInstruction)) {
                                    dialog?.dismiss()
                                }
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView,
                            request: WebResourceRequest?,
                            errorResponse: WebResourceResponse
                        ) {
                            Log.d(
                                "PAYME",
                                "HTTP error " + errorResponse.statusCode + errorResponse.data + errorResponse.reasonPhrase
                            )
                            super.onReceivedHttpError(view, request, errorResponse)
                        }

                        override fun onReceivedError(
                            view: WebView?, request: WebResourceRequest?, error: WebResourceError?
                        ) {
                            Log.d(PayMEMiniApp.TAG, "onReceivedError error " + error?.errorCode)
                            super.onReceivedError(view, request, error)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            Log.d(PayMEMiniApp.TAG, "page finish $url ${view?.progress}")
                            if (url?.contains(".pdf") == true) {
                                if (view?.contentHeight == 0) {
                                    Log.d(PayMEMiniApp.TAG, "chay vao reload")
                                    view.reload()
                                    return
                                }
                                if (view?.progress == 100) {
                                    loading.visibility = View.GONE
                                    newWebView.loadUrl("document.body.style.zoom = 1;");
                                }
                            }
                            loading.visibility = View.GONE
                        }
                    }
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    Log.d(PayMEMiniApp.TAG, "shouldOverrideUrlLoading url: $url")
                    return if (url.contains(".pdf")) {
                        val pdfUrl = "https://docs.google.com/gview?embedded=true&url=${url}"
                        view.loadUrl(pdfUrl)
                        false
                    } else if (url.startsWith("http://") || url.startsWith("https://")) {
                        view.loadUrl(url)
                        false
                    } else {
                        ExternalUrlLauncher.open(view.context, url)
                        true
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?, request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url.toString()
                    Log.d(PayMEMiniApp.TAG, "shouldOverrideUrlLoading url: $url")
                    return if (url.contains(".pdf")) {
                        val pdfUrl = "https://docs.google.com/gview?embedded=true&url=${url}"
                        view?.loadUrl(pdfUrl)
                        false
                    } else if (url.startsWith("http://") || url.startsWith("https://")) {
                        false
                    } else {
                        view?.context?.let { ExternalUrlLauncher.open(it, url) }
                        true
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView, request: WebResourceRequest?, errorResponse: WebResourceResponse
                ) {
                    Log.d(
                        PayMEMiniApp.TAG,
                        "HTTP error " + errorResponse.statusCode + errorResponse.data
                    )
                }

                override fun onPageStarted(view: WebView?, url: String?, facIcon: Bitmap?) {
                    Log.d(PayMEMiniApp.TAG, "page started $url")
                    if (backGuard.isBounce(url, SystemClock.elapsedRealtime())) {
                        dialog?.dismiss()
                        return
                    }
                    if (!url.isNullOrEmpty()) {
                        MiniAppFragment.evaluateJs("nativeWebViewNavigation", com.google.gson.Gson().toJson(url))
                        if (closeInstruction.isNotEmpty() && url.contains(closeInstruction)) {
                            dialog?.dismiss()
                        }
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    Log.d(PayMEMiniApp.TAG, "page finished $url")
                    loading.visibility = View.GONE
                }

                override fun onReceivedError(
                    view: WebView?, request: WebResourceRequest?, error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    Log.d(PayMEMiniApp.TAG, "error https ${error?.description}")
                }
            }

            SubWebViewSettingsConfigurator.configure(this, useOverview = false)
            overScrollMode = View.OVER_SCROLL_NEVER
            setBackgroundColor(0)
            if (type == "url") {
                loadUrl(content)
            } else if (type == "html") {
                loadDataWithBaseURL("x-data://base", content, "text/html", "UTF-8", null)
            }
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // White header and footer sit under the system bars: use dark bar icons so they stay visible.
        dialog?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = true
                isAppearanceLightNavigationBars = true
            }
        }
        // The dialog is not cancelable, so system back did nothing; give it the footer back behaviour instead.
        (dialog as? ComponentDialog)?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner) { handleBack() }
    }

    private fun handleBack() {
        Log.d(PayMEMiniApp.TAG, "buttonBackFooter back")
        if (loading.visibility == View.VISIBLE) return
        val web = myWebView ?: return
        val sub = subWebView
        if (sub != null) {
            if (sub.canGoBack()) {
                backGuard.onBack(sub.url, SystemClock.elapsedRealtime())
                sub.goBack()
            } else {
                closeSubWindow()
            }
            return
        }
        if (web.canGoBack()) {
            Log.d(PayMEMiniApp.TAG, "webview back")
            backGuard.onBack(web.url, SystemClock.elapsedRealtime())
            web.goBack()
        } else {
            dialog?.dismiss()
        }
    }

    private fun closeSubWindow() {
        val sub = subWebView ?: return
        myWebView?.removeView(sub)
        sub.destroy()
        subWebView = null
        loading.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        myWebView!!.removeAllViews()
        myWebView!!.destroy()
    }
}

/**
 * Detects a back navigation that a redirect undoes right away (going back lands on a page that bounces straight
 * to the page back started from), so back can close the window instead of looping.
 */
internal class BackRedirectGuard(private val windowMs: Long = 3_000) {
    private var fromUrl: String? = null
    private var startedAt = 0L

    fun onBack(currentUrl: String?, now: Long) {
        fromUrl = currentUrl
        startedAt = now
    }

    fun isBounce(url: String?, now: Long): Boolean {
        val from = fromUrl ?: return false
        if (now - startedAt > windowMs) {
            fromUrl = null
            return false
        }
        if (url != from) return false
        fromUrl = null
        return true
    }
}

/** Shows a SubWebView unless the host can no longer commit fragment transactions. */
internal fun showSubWebView(fragment: Fragment, content: String, type: String = "url", closeInstruction: String = "") {
    if (!fragment.isAdded) return
    val manager = fragment.parentFragmentManager
    if (manager.isStateSaved) return
    SubWebView(content, type, closeInstruction).show(manager, "SUBWEBVIEW")
}
