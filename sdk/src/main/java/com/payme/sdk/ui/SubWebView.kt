package com.payme.sdk.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.webkit.WebView.WebViewTransport
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.fragment.app.DialogFragment
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.R


class SubWebView(
  private val content: String,
  private val type: String,
  private val closeInstruction: String
) : DialogFragment() {
  private var rootView: View? = null
  private var myWebView: WebView? = null
  private var subWebView: WebView? = null
  private lateinit var buttonBack: TextView
  private lateinit var buttonReload: ImageView
  private lateinit var buttonBackFooter: ImageView
  private lateinit var loading: View


  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    isCancelable = false
    setStyle(STYLE_NO_FRAME, R.style.DialogStyle)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    val view: View = inflater.inflate(
      R.layout.sub_webview,
      container, false
    )
    rootView = view.findViewById(R.id.root_view)
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
          view: WebView?,
          isDialog: Boolean,
          isUserGesture: Boolean,
          resultMsg: Message?
        ): Boolean {
          Log.d(PayMEMiniApp.TAG, "chay vao oncreate window")
          if (subWebView != null || view == null || resultMsg == null) {
            return true
          }
          loading.visibility = View.VISIBLE
          val newWebView = WebView(requireContext())
          newWebView.settings.apply {
            setSupportZoom(true)
            textZoom = 100
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            domStorageEnabled = true
            setGeolocationEnabled(true)
            userAgentString = System.getProperty("http.agent")
            mediaPlaybackRequiresUserGesture = false
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowContentAccess = true
            pluginState = WebSettings.PluginState.ON
            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            requestFocus(View.FOCUS_DOWN)
          }
          subWebView = newWebView
          newWebView.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
          )
          newWebView.setBackgroundColor(Color.TRANSPARENT)
          view!!.addView(newWebView)
          val transport = resultMsg!!.obj as WebViewTransport
          transport.webView = newWebView
          resultMsg.sendToTarget()
          newWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
              Log.d(PayMEMiniApp.TAG, "shouldOverrideUrlLoading url: $url")
              return if (url.contains(".pdf")) {
                loading.visibility = View.VISIBLE
                val pdfUrl = "https://docs.google.com/gview?embedded=true&url=${url}"
                view.loadUrl(pdfUrl)
                true
              } else if (url.startsWith("http://") || url.startsWith("https://")) {
                view.loadUrl(url)
                false
              } else try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                view.context.startActivity(intent)
                true
              } catch (e: Exception) {
                Log.d(PayMEMiniApp.TAG, "shouldOverrideUrlLoading Exception: $e")
                true
              }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
              Log.d(PayMEMiniApp.TAG, "page start $url")
              loading.visibility = View.VISIBLE
              if (!url.isNullOrEmpty()) {
                MiniAppFragment.evaluateJs("nativeWebViewNavigation", "\"$url\"")
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
              view: WebView?,
              request: WebResourceRequest?,
              error: WebResourceError?
            ) {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Log.d(PayMEMiniApp.TAG, "onReceivedError error " + error?.errorCode)
              }
              super.onReceivedError(view, request, error)
            }


            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
              view: WebView?,
              handler: SslErrorHandler,
              error: SslError?
            ) {
              handler.proceed()
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
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
          Log.d(PayMEMiniApp.TAG, "shouldOverrideUrlLoading url: $url")
          return if (url.contains(".pdf")) {
            val pdfUrl = "https://docs.google.com/gview?embedded=true&url=${url}"
            view.loadUrl(pdfUrl)
            false
          } else if (url.startsWith("http://") || url.startsWith("https://")) {
            view.loadUrl(url)
            false
          } else try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
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
            val pdfUrl = "https://docs.google.com/gview?embedded=true&url=${url}"
            view?.loadUrl(pdfUrl)
            false
          } else if (url.startsWith("http://") || url.startsWith("https://")) {
            false
          } else try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
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
          Log.d(PayMEMiniApp.TAG, "HTTP error " + errorResponse.statusCode + errorResponse.data)
        }

        override fun onPageStarted(view: WebView?, url: String?, facIcon: Bitmap?) {
          Log.d(PayMEMiniApp.TAG, "page started $url")
          if (!url.isNullOrEmpty()) {
            MiniAppFragment.evaluateJs("nativeWebViewNavigation", "\"$url\"")
            if (closeInstruction.isNotEmpty() && url.contains(closeInstruction)) {
              dialog?.dismiss()
            }
          }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
          Log.d(PayMEMiniApp.TAG, "page finished $url")
          loading.visibility = View.GONE
        }

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(
          view: WebView?,
          handler: SslErrorHandler?,
          error: SslError?
        ) {
          super.onReceivedSslError(view, handler, error)
          handler?.proceed()
        }

        @RequiresApi(Build.VERSION_CODES.M)
        override fun onReceivedError(
          view: WebView?,
          request: WebResourceRequest?,
          error: WebResourceError?
        ) {
          super.onReceivedError(view, request, error)
          Log.d(PayMEMiniApp.TAG, "error https ${error?.description}")
        }
      }

      settings.apply {
        setSupportZoom(true)
        textZoom = 100
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
        pluginState = WebSettings.PluginState.ON
        cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        requestFocus(View.FOCUS_DOWN)
      }
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

  private fun handleBack() {
    Log.d(PayMEMiniApp.TAG, "buttonBackFooter back")
    if (loading.visibility == View.VISIBLE) return
    if (myWebView != null) {
      if (subWebView != null) {
        if (subWebView!!.canGoBack()) {
          subWebView!!.goBack()
        } else {
          myWebView!!.removeView(subWebView)
          subWebView!!.destroy()
          subWebView = null
        }
        return
      }
      if (myWebView!!.canGoBack()) {
        Log.d(PayMEMiniApp.TAG, "webview back")
        myWebView!!.goBack()
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    myWebView!!.removeAllViews()
    myWebView!!.destroy()
  }
}

