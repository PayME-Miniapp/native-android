package com.payme.sdk.ui

//import vn.kalapa.ekyc.KalapaSDK.Companion.configure
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.airbnb.lottie.LottieAnimationView
import com.google.gson.Gson
import com.payme.sdk.BuildConfig
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.R
import com.payme.sdk.models.ActionOpenMiniApp
import com.payme.sdk.models.OpenMiniAppDataInterface
import com.payme.sdk.models.OpenMiniAppKYCData
import com.payme.sdk.models.OpenMiniAppType
import com.payme.sdk.models.PayMEError
import com.payme.sdk.models.PayMEErrorType
import com.payme.sdk.models.PayMEVersion
import com.payme.sdk.models.getPhoneFromOpenMiniAppData
import com.payme.sdk.utils.DeviceTypeResolver
import com.payme.sdk.utils.MixpanelUtil
import com.payme.sdk.utils.PermissionCameraUtil
import com.payme.sdk.utils.Utils
import com.payme.sdk.viewmodels.DeepLinkViewModel
import com.payme.sdk.viewmodels.MiniappViewModel
import com.payme.sdk.viewmodels.NotificationViewModel
import com.payme.sdk.viewmodels.PayMEUpdatePatchViewModel
import com.payme.sdk.viewmodels.SubWebViewViewModel
import com.payme.sdk.webServer.JavaScriptInterface
import com.payme.sdk.webServer.WebServer
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import vn.kalapa.ekyc.KalapaFlowType
import vn.kalapa.ekyc.KalapaHandler
import vn.kalapa.ekyc.KalapaSDK
import vn.kalapa.ekyc.KalapaSDK.Companion.isBackBitmapInitialized
import vn.kalapa.ekyc.KalapaSDK.Companion.isFaceBitmapInitialized
import vn.kalapa.ekyc.KalapaSDK.Companion.isFrontBitmapInitialized
import vn.kalapa.ekyc.KalapaSDK.Companion.startFullEKYC
import vn.kalapa.ekyc.KalapaSDKConfig
import vn.kalapa.ekyc.KalapaSDKResultCode
import vn.kalapa.ekyc.models.CreateSessionResult
import vn.kalapa.ekyc.models.KalapaError
import vn.kalapa.ekyc.models.KalapaResult
import vn.kalapa.ekyc.models.PreferencesConfig
import vn.kalapa.ekyc.networks.KalapaAPI.Companion.doRequestGetSession
import vn.kalapa.ekyc.utils.Common.Companion.isOnline
import vn.kalapa.ekyc.views.ProgressView.Companion.hideProgress
import vn.kalapa.ekyc.views.ProgressView.Companion.showProgress
import vn.kalapa.ekyc.views.ProgressView.ProgressViewType
import java.io.File
import java.net.URL
import javax.net.ssl.SSLException


fun isStringInJsonArray(jsonArray: JSONArray, targetString: String): Boolean {
    for (i in 0 until jsonArray.length()) {
        val item = jsonArray.getString(i)
        if (item == targetString) {
            return true
        }
    }
    return false
}

class BackPressCallback(private val fragment: MiniAppFragment) : OnBackPressedCallback(true) {
    override fun handleOnBackPressed() {
        Log.d(PayMEMiniApp.TAG, "onBackPressed Called")
        val myWebView = fragment.view?.findViewById<WebView>(R.id.webview)
        if (myWebView != null) {
            if (myWebView.canGoBack()) {
                val url = myWebView.url
                val parts = URL(url)
                val listPath = fragment.getListScreenBackBlocked()
                val check = (url.isNullOrEmpty() || isStringInJsonArray(listPath, parts.path))

                if (!check) {
                    Log.d(PayMEMiniApp.TAG, "webview back")
                    myWebView.goBack()
                }
            }
        }
    }
}

class MiniAppFragment : Fragment() {
    private var rootView: View? = null
    private var myWebView: WebView? = null
    private var www_root: File? = null
    private var server: com.payme.sdk.webServer.MySimpleWebServer? = null

    private var port = 4646
    private var permissionType = ""
    private var nativeAppState = "active"
    private var listScreenBackBlocked = JSONArray()

    private var paramsKyc: JSONObject? = null
    private var paramsSaveQr: String? = null
    private var backgroundDownload = false
    private var versionCheckingTask: Thread? = null

    private lateinit var payMEUpdatePatchViewModel: PayMEUpdatePatchViewModel
    private lateinit var miniappViewModel: MiniappViewModel

    private lateinit var updatingView: CardView
    private lateinit var progressBar: ProgressBar
    private lateinit var textProgress: TextView
    private lateinit var textUpdateLabel: TextView
    private lateinit var lottieView: LottieAnimationView
    private lateinit var lottieContainerView: LinearLayout
    private lateinit var loadingView: View

    private var preferencesConfig: PreferencesConfig? = null
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var faceAuthenData: JSONObject? = null

    private fun unzip() {
        val filesDir = requireContext().filesDir
        val sourceWeb = File("${filesDir.path}/update", "sdkWebapp3-main.zip")
        if (sourceWeb.exists() && !backgroundDownload && sourceWeb.length() > 0) {
            Log.d(PayMEMiniApp.TAG, "chay vo copy update")
            val wwwDirectory = File(filesDir.path, "www")
            wwwDirectory.delete()
            if (!wwwDirectory.exists()) {
                wwwDirectory.mkdir()
            }
            Utils.unzipFile("${filesDir.path}/update/sdkWebapp3-main.zip", "${filesDir.path}/www")
            sourceWeb.delete()
            return
        }

        Log.d(PayMEMiniApp.TAG, "chay vo unzip source down san")

        val wwwDirectory = File(filesDir.path, "www")
        if (!wwwDirectory.exists()) {
            wwwDirectory.mkdir()
        }
        val unzipped = File("${filesDir.path}/www", "sdkWebapp3-main")
        val content = unzipped.listFiles()
        if (content == null || content.isEmpty()) {
            Utils.copyDir(requireContext(), path = "www")
            Utils.unzipFile("${filesDir.path}/www/sdkWebapp3-main.zip", "${filesDir.path}/www")
        }
    }

    private fun unzipDefaultSource() {
        val filesDir = requireContext().filesDir
        Log.d(PayMEMiniApp.TAG, "chay vo unzipDefaultSource")
        val wwwDirectory = File(filesDir.path, "www")
        if (!wwwDirectory.exists()) {
            wwwDirectory.mkdir()
        }
        Utils.copyDir(requireContext(), path = "www")
        Utils.unzipFile("${filesDir.path}/www/sdkWebapp3-main.zip", "${filesDir.path}/www")
    }

    private fun startServer() {
        if (server != null) {
            return
        }
        port = Utils.findRandomOpenPort() ?: 4646
        www_root = File("${requireContext().filesDir.path}/www", "sdkWebapp3-main")
        if (loadUrl.contains("http://localhost") || loadUrl.isEmpty()) {
            loadUrl = "http://localhost:$port/"
        }

//        loadUrl = "http://10.8.20.39:3000/"
        try {
            server = WebServer("localhost", port, www_root)
            (server as WebServer).start()
            Log.d(PayMEMiniApp.TAG, "start server with port $port")
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "error start server ${e.message}")
        }
    }

    private fun stopServer() {
        if (server != null) {
            Log.d(PayMEMiniApp.TAG, "Stopped Server")
            server!!.stop()
            server = null
        }
    }

    @SuppressLint("HardwareIds")
    private fun sendNativeDeviceInfo() {
        val insets = JSONObject()
        val statusHeight = activity?.let {
            Utils.getStatusBarHeight(it)
        }
        if (openType == OpenMiniAppType.screen) {
            insets.put("top", statusHeight?.let { Utils.pxToDp(requireContext(), it) })
        }
        val bottom = Utils.getRootWindowInsetsCompat(rootView!!) ?: 0
        insets.put("bottom", Utils.pxToDp(requireContext(), bottom.toInt()))
        val deviceInfo = JSONObject()
        deviceInfo.put("platform", "android")
        val deviceId =
            Settings.Secure.getString(requireContext().contentResolver, Settings.Secure.ANDROID_ID)
        deviceInfo.put("deviceId", deviceId)
        deviceInfo.put("userAgent", Utils.getUserAgent(requireContext()))
        deviceInfo.put(
            "version",
            requireContext().packageManager.getPackageInfo(
                requireContext().packageName,
                0
            ).versionName
        )
        deviceInfo.put(
            "buildNumber",
            requireContext().packageManager.getPackageInfo(
                requireContext().packageName,
                0
            ).versionCode
        )
        deviceInfo.put("isEmulator", Utils.isEmulator())
        deviceInfo.put("brand", Build.BRAND)
        deviceInfo.put("model", Build.MODEL)
        deviceInfo.put("bundleId", requireContext().packageName)
        deviceInfo.put("systemName", "Android")
        deviceInfo.put("systemVersion", Build.VERSION.RELEASE)
        deviceInfo.put("deviceType", DeviceTypeResolver(requireContext()).deviceType.value)
        deviceInfo.put("insets", insets)
        deviceInfo.put("miniAppVersion", BuildConfig.SDK_VERSION)

        val biometric = JSONObject()
        biometric.put("isSupport", Utils.isBiometricReady(requireContext()))
        biometric.put("type", "UNKNOWN")
        deviceInfo.put("biometric", biometric)

        activity?.let {
            Utils.evaluateJSWebView(
                it,
                myWebView!!,
                "nativeDeviceInfo",
                deviceInfo.toString(),
                null
            )
        }
    }

    private fun downloadSourceWeb(url: String?, editor: SharedPreferences.Editor, patch: Int) {
        val filesDir = requireContext().filesDir
        val updateDirectory = File(filesDir.path, "update")
        if (!updateDirectory.exists()) {
            updateDirectory.mkdir()
        }
        val sourceWeb = File("${filesDir.path}/update", "sdkWebapp3-main.zip")
        if (sourceWeb.exists()) {
            sourceWeb.delete()
        }
        sourceWeb.createNewFile()
        url?.let {
            Utils.download(
                requireContext(),
                it,
                sourceWeb.absolutePath
            ) { totalBytesCopied, length ->
                val progressValuePercent = (totalBytesCopied * 100 / length).toInt()
                activity?.runOnUiThread {
                    progressBar.progress = progressValuePercent
                    textProgress.text = "${progressValuePercent}%"

                    val layoutParams = lottieView.layoutParams as LinearLayout.LayoutParams
                    layoutParams.leftMargin =
                        progressValuePercent * (lottieContainerView.width - lottieView.width) / 100
                    layoutParams.topMargin = 0
                    layoutParams.rightMargin = 0
                    layoutParams.bottomMargin = 0
                    lottieView.requestLayout()
                }
            }
            Log.d(PayMEMiniApp.TAG, "source web length ${sourceWeb.length()}")
            if (sourceWeb.length() > 0) {
                editor.putInt("PAYME_PATCH", patch)
                editor.apply()
            } else {
                if (!backgroundDownload) {
                    sourceWeb.delete()
                    payMEUpdatePatchViewModel.setDoneUpdate(true)
                }
            }
            backgroundDownload = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, BackPressCallback(this))
        miniappViewModel = ViewModelProvider(requireActivity())[MiniappViewModel::class.java]

        if (isOpenMiniAppInit()) {
            miniappViewModel.openMiniAppData = openMiniAppData
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.fragment_mini_app, container, false)
        payMEUpdatePatchViewModel = PayMEUpdatePatchViewModel()
        rootView = view.findViewById(R.id.root_view)
        myWebView = view.findViewById(R.id.webview)
        updatingView = view.findViewById(R.id.updating_view)
        progressBar = view.findViewById(R.id.progress)
        textProgress = view.findViewById(R.id.progress_text)
        textUpdateLabel = view.findViewById(R.id.update_label_text)
        lottieView = view.findViewById(R.id.lottieView)
        lottieContainerView = view.findViewById(R.id.lottie_container_view)
        loadingView = view.findViewById(R.id.loading)
        versionCheckingTask = Thread {
            try {
                loadingView.visibility = View.VISIBLE
                val versionFile = File(requireContext().filesDir.path, "version.json")
                if (versionFile.exists()) {
                    versionFile.delete()
                }
                versionFile.createNewFile()
                Utils.downloadWithoutTemp(
                    "https://static.payme.vn/frontend/miniapp-store/PayMEMiniAppVersion.json",
                    versionFile.absolutePath
                )
//        val jsonString: String =
//          applicationContext.assets.open("PayMEMiniAppVersion.json").bufferedReader()
//            .use { it.readText() }
                val jsonString: String = File(versionFile.absolutePath).readText(Charsets.UTF_8)
                val jsonArray = JSONArray(jsonString)
                var version: String = ""
                var found: JSONObject? = null
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    if (item.getString("version") == BuildConfig.SDK_VERSION) {
                        found = item
                        version = item.getString("version")
                    }
                }
                if (found == null) {
                    payMEUpdatePatchViewModel.setDoneUpdate(true)
                    return@Thread
                }
                Log.d("PAYMELOG", "payme miniapp mode ${PayMEMiniApp.mode}")
                val sharedPreference =
                    requireContext().getSharedPreferences(
                        "PAYME_NATIVE_UPDATE",
                        Context.MODE_PRIVATE
                    )
                val editor = sharedPreference.edit()
                val mode = found.optJSONObject(PayMEMiniApp.mode)
                if (mode == null) {
                    payMEUpdatePatchViewModel.setDoneUpdate(true)
                    return@Thread
                }
                val localMode = sharedPreference.getString("PAYME_MODE", "")
                if (PayMEMiniApp.mode != localMode) {
                    editor.putString("PAYME_MODE", PayMEMiniApp.mode)
                    editor.putInt("PAYME_PATCH", if (localMode == "") 0 else -1)
                    editor.apply()
                }
                val patch = mode.optInt("patch", 0)
                val latestMandatory = mode.optInt("latestMandatoryPatch", 0)
                val url = mode.optString("url")
                val localPatch = sharedPreference.getInt("PAYME_PATCH", 0)

                if (patch == 0 && latestMandatory == 0) {
                    Log.d(PayMEMiniApp.TAG, "default")
                    payMEUpdatePatchViewModel.setLoadDefaultSource(true)
                    payMEUpdatePatchViewModel.setDoneUpdate(true)
                    return@Thread
                }
                val localMandatory = localPatch < latestMandatory
                val payMEVersion = PayMEVersion(patch, version, localMandatory, url)
                if (payMEVersion.patch <= localPatch) {
                    Log.d(PayMEMiniApp.TAG, "do not update")
                    payMEUpdatePatchViewModel.setDoneUpdate(true)
                    return@Thread
                }
                if (!payMEVersion.mandatory) {
                    Log.d(PayMEMiniApp.TAG, "download ngầm")
                    backgroundDownload = true
                    payMEUpdatePatchViewModel.setDoneUpdate(true)
                    downloadSourceWeb(payMEVersion.url, editor, payMEVersion.patch)
                    return@Thread
                }
                Log.d(PayMEMiniApp.TAG, "force update")
                activity?.runOnUiThread {
                    textUpdateLabel.text =
                        "Đang tải dữ liệu (${BuildConfig.SDK_VERSION}.${patch})..."
                }
                payMEUpdatePatchViewModel.setShowUpdatingUI(true)
                payMEUpdatePatchViewModel.setIsForceUpdating(true)
                downloadSourceWeb(payMEVersion.url, editor, payMEVersion.patch)
                Log.d(PayMEMiniApp.TAG, "downloaded source moi")
                payMEUpdatePatchViewModel.setIsForceUpdating(false)
                payMEUpdatePatchViewModel.setDoneUpdate(true)
            } catch (e: SSLException) {
                Log.d(PayMEMiniApp.TAG, "SSLException ex ${e}")
            } catch (e: Exception) {
                payMEUpdatePatchViewModel.setDoneUpdate(true)
                Log.d(PayMEMiniApp.TAG, "thread ex ${e}")
            }
        }
        versionCheckingTask?.start()
        val networkCallback: ConnectivityManager.NetworkCallback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    payMEUpdatePatchViewModel.setIsLostConnection(false)
                }

                override fun onLost(network: Network) {
                    payMEUpdatePatchViewModel.setIsLostConnection(true)
                }
            }

        val connectivityManager =
            requireContext().getSystemService(AppCompatActivity.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } else {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }

        rootView!!.viewTreeObserver.addOnGlobalLayoutListener {
            if (payMEUpdatePatchViewModel.getWebLoaded().value == false) {
                return@addOnGlobalLayoutListener
            }
            val r = Rect()
            rootView!!.getWindowVisibleDisplayFrame(r)

            val screenHeight: Int = rootView!!.rootView.height
            val heightDiff: Int = screenHeight - r.bottom
            val navigationBarHeight = Utils.getSoftNavigationHeight(requireContext())

            if (heightDiff > 140) {
                val height = Utils.pxToDp(requireContext(), heightDiff + navigationBarHeight)
                activity?.let {
                    Utils.evaluateJSWebView(
                        it,
                        myWebView!!,
                        "nativeKeyboardHeight",
                        height.toString(),
                        null
                    )
                }
            } else {
                activity?.let {
                    Utils.evaluateJSWebView(
                        it,
                        myWebView!!,
                        "nativeKeyboardHeight",
                        "0",
                        null
                    )
                }
            }
        }

        myWebView?.apply {
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    val url = URL(view?.url).toString().removePrefix(loadUrl)
                    onSetWebViewUrlPart(url)
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

                    if (fileChooserCallback != null) {
                        fileChooserCallback?.onReceiveValue(null)
                    }

                    fileChooserCallback = filePathCallback;
                    val intent = fileChooserParams?.createIntent()
                    try {
                        fileChooserLauncher.launch(intent)
                    } catch (e: Exception) {
                        Log.d(PayMEMiniApp.TAG, "chay vo catch ${e.message}")
                        return true
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
                    Log.d(
                        PayMEMiniApp.TAG,
                        "HTTP error " + errorResponse.statusCode + errorResponse.data
                    )
                }

                override fun onPageStarted(view: WebView?, url: String?, facIcon: Bitmap?) {
                    Log.d(PayMEMiniApp.TAG, "page started $url")
                    if (url == loadUrl) {
                        loadingView.visibility = View.VISIBLE
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    Log.d(PayMEMiniApp.TAG, "page finished $url")
                    loadingView.visibility = View.GONE
                    if (url == loadUrl) {
                        payMEUpdatePatchViewModel.setShowUpdatingUI(false)
                        payMEUpdatePatchViewModel.setWebLoaded(true)
                        sendNativeDeviceInfo()
                        val notiValue = notificationViewModel.getNotificationJSON().value
                        if (notiValue != null && notiValue.length() > 0) {
                            activity?.let {
                                Utils.evaluateJSWebView(
                                    it,
                                    myWebView!!,
                                    "nativeNotificationOpenedApp",
                                    notiValue.toString(),
                                    null
                                )
                            }
                            notificationViewModel.setNotificationJSON(JSONObject())
                        }
                        activity?.let { Utils.sendNativePref(it, myWebView!!) }

                        val openMiniAppData = miniappViewModel.openMiniAppData
                        if (openMiniAppData != null) {
                            val json = openMiniAppData.toJsonData()
                            val jsonOpenTypeString = JSONObject.quote(openType.toString())
                            activity?.let {
                                Utils.evaluateJSWebView(
                                    it,
                                    myWebView!!,
                                    "openMiniApp",
                                    Gson().toJson(json).toString(),
                                    null
                                )
                                Utils.evaluateJSWebView(
                                    it,
                                    myWebView!!,
                                    "openType",
                                    jsonOpenTypeString,
                                    null
                                )
                            }
                        }

                        val deeplink = deepLinkViewModel.getDeepLinkUrl().value
                        if (!deeplink.isNullOrEmpty()) {
                            val jsonQuoteString = JSONObject.quote(deeplink)
                            activity?.let {
                                Utils.evaluateJSWebView(
                                    it,
                                    myWebView!!,
                                    "nativeLinkingOpenedApp",
                                    jsonQuoteString,
                                    null
                                )
                            }
                            deepLinkViewModel.setDeepLinkUrl("")
                        }
                    }
                }

                @RequiresApi(Build.VERSION_CODES.M)
                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    try {
                        stopServer()
                        server = WebServer("localhost", port, www_root)
                        (server as WebServer).start()
                        Log.d(PayMEMiniApp.TAG, "start server")
                    } catch (e: Exception) {
                        Log.d(PayMEMiniApp.TAG, "error ${e.message}")
                    }
                    Log.d(PayMEMiniApp.TAG, "error https ${error?.description}")
                }
            }

            settings.apply {
                setSupportZoom(false)
                textZoom = 100
                builtInZoomControls = true
                displayZoomControls = false
                javaScriptEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                domStorageEnabled = true
                setGeolocationEnabled(true)
                mediaPlaybackRequiresUserGesture = false
                loadsImagesAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                allowContentAccess = true
                pluginState = WebSettings.PluginState.ON
                cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                requestFocus(View.FOCUS_DOWN)
                useWideViewPort = true
            }

            overScrollMode = View.OVER_SCROLL_NEVER

            setBackgroundColor(0)

            val javaScriptInterface = JavaScriptInterface(
                setNativePreferences = { data: String? ->
                    activity?.let {
                        Utils.setNativePref(
                            it,
                            data
                        )
                    }
                },
                sendNativePreferences = { activity?.let { Utils.sendNativePref(it, this) } },
                biometricAuthen = { data: String ->
                    Utils.biometricAuthenticate(
                        activity as AppCompatActivity,
                        myWebView!!,
                        data
                    )
                },
                startCardKyc = { data: String -> startCardKyc(data) },
                startFaceKyc = { data: String -> startFaceKyc(data) },
                startKalapaKyc = { data: String -> startKalapaKyc(data) },
                startKalapaNFC = { data: String -> startKalapaNFC(data) },
                startFaceAuthen = { data: String -> startFaceAuthen(data) },
                openSettings = { activity?.let { PermissionCameraUtil().openSetting(it) } },
                share = { data: String -> share(data) },
                requestPermission = { data: String -> requestPermission(data) },
                sendNativeDeviceInfo = { sendNativeDeviceInfo() },
                getContacts = { getContacts() },
                nativeOpenKeyboard = {
                    activity?.let {
                        Utils.nativeOpenKeyboard(
                            it,
                            myWebView
                        )
                    }
                },
                openWebView = { data: String -> openWebView(data) },
                onSuccess = { data: String -> returnSuccess(data) },
                onError = { data: String -> returnError(data) },
                closeMiniApp = { forceCloseMiniApp() },
                openUrl = { data: String -> openUrl(data) },
                saveQR = { data: String -> saveQR(data) },
                changeEnv = { data: String -> changeEnv(data) },
                changeLocale = { data: String -> changeLocale(data) },
                setListScreenBackBlocked = { data: JSONArray -> setListScreenBackBlocked(data) },
                setModalHeight = { data: Int -> setModalHeight(data) },
                requestNFCPermission = {data: String -> requestNFCPermission(data)}
            )
            addJavascriptInterface(javaScriptInterface, "messageHandlers")

            WebStorage.getInstance().deleteAllData()

            loadUrl("javascript:localStorage.clear()")
        }

        payMEUpdatePatchViewModel.getDoneUpdate().observe(viewLifecycleOwner) {
            if (it) {
                if (payMEUpdatePatchViewModel.getLoadDefaultSource().value == true) {
                    unzipDefaultSource()
                } else {
                    unzip()
                }
                startServer()
                if (loadUrl.isNotEmpty()) {
                    activity?.runOnUiThread {
                        myWebView!!.loadUrl(loadUrl)
                    }
                }
            }
        }
        payMEUpdatePatchViewModel.getShowUpdatingUI().observe(viewLifecycleOwner) {
            activity?.runOnUiThread {
                if (it) {
                    loadingView.visibility = View.GONE
                    updatingView.visibility = View.VISIBLE
                } else {
                    updatingView.visibility = View.GONE
                }
            }
        }

        payMEUpdatePatchViewModel.getIsLostConnection().observe(viewLifecycleOwner) {
            if (!it) {
                if (payMEUpdatePatchViewModel.getIsForceUpdating().value == true) {
                    if (versionCheckingTask != null) {
                        if (!versionCheckingTask!!.isAlive) {
                            versionCheckingTask?.start()
                        }
                    }
                }
            }
        }

        notificationViewModel.getNotificationData().observe(viewLifecycleOwner) {
            if (it.length() != 0) {
                notificationViewModel.setNotificationJSON(it)
                activity?.let { it1 ->
                    Utils.evaluateJSWebView(
                        it1,
                        myWebView!!,
                        "nativeNotificationOpenedApp",
                        it.toString(),
                        null
                    )
                }
            }
        }

        subWebViewViewModel.getEvaluateJsData().observeForever(evaluateJsDataObserver)

        return view
    }

    private fun changeEnv(env: String) {
        PayMEMiniApp.onChangeEnv?.let { it(env) }
    }

    private fun changeLocale(locale: String) {
        PayMEMiniApp.onChangeLocale?.let { it(locale) }
    }

    private fun setListScreenBackBlocked(data: JSONArray?) {
        listScreenBackBlocked = data ?: JSONArray()
    }

    fun getListScreenBackBlocked(): JSONArray {
        return listScreenBackBlocked
    }

    private fun setModalHeight(height: Int) {
        height.let {
            if (it != 0 && it != modalHeight) {
                modalHeight = it
                onSetModalHeight(it)
            }
        }
    }

    private fun onSetWebViewUrlPart(url: String) {
        val action = getMiniAppAction()
        if (action != ActionOpenMiniApp.PAY &&
            action != ActionOpenMiniApp.SERVICE &&
            action != ActionOpenMiniApp.PAYMENT &&
            action != ActionOpenMiniApp.TRANSFER_QR
        ) return

        url.let {
            if (it != webViewUrl) {
                onChangeModalHeight(it)
                webViewUrl = url
            }
        }
    }

    private fun onChangeModalHeight(url: String) {
        Log.d(PayMEMiniApp.TAG, "webview url change $url")
        val maxHeight = 999
        if (url.contains("mini-app/link-merchant")) {
            setModalHeight(maxHeight)
        }
    }

    private fun downloadImageQR(data: String) {
        val bitmap = Utils.generateQRCode(data)
        Utils.saveImage(bitmap, requireContext(), getString(R.string.app_name), onSuccess = {
            val response = JSONObject()
            response.put("succeeded", true)
            activity?.let {
                Utils.evaluateJSWebView(
                    it,
                    myWebView!!,
                    "nativeSaveQR",
                    response.toString(),
                    null
                )
            }
        }, onError = {
            val response = JSONObject()
            response.put("error", "Tải mã QR thất bại")
            activity?.let {
                Utils.evaluateJSWebView(
                    it,
                    myWebView!!,
                    "nativeSaveQR",
                    response.toString(),
                    null
                )
            }
        })
    }

    private fun saveQR(data: String) {
        paramsSaveQr = data
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity?.let {
                Utils.nativePermissionStatus(
                    it,
                    myWebView!!,
                    "WRITE_EXTERNAL_STORAGE",
                    "GRANTED"
                )
            }
            paramsSaveQr?.let { downloadImageQR(it) }
            return
        }

        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                activity?.let {
                    Utils.nativePermissionStatus(
                        it,
                        myWebView!!,
                        "WRITE_EXTERNAL_STORAGE",
                        "GRANTED"
                    )
                }
                paramsSaveQr?.let { downloadImageQR(it) }
            }

            activity?.let {
                ActivityCompat.shouldShowRequestPermissionRationale(
                    it,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            } == true -> {
                activity?.let {
                    Utils.nativePermissionStatus(
                        it,
                        myWebView!!,
                        "WRITE_EXTERNAL_STORAGE",
                        "BLOCKED"
                    )
                }
            }

            else -> {
                requestWriteExternalStoragePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun returnSuccess(data: String) {
        try {
            val json = JSONObject(data)
            PayMEMiniApp.onResponse(openMiniAppData.action, json)
            val isCloseMiniApp = json.optBoolean("isCloseMiniApp", false)
            if (isCloseMiniApp) {
                closeMiniApp()
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "miniapp returnSuccess: ${e.message} ")
        }
    }

    private fun returnError(data: String) {
        try {
            val json = JSONObject(data)
            val code = json.optString("code", "")
            val description = json.optString("description", "")
            val isCloseMiniApp = json.optBoolean("isCloseMiniApp", false)
            PayMEMiniApp.onError(
                openMiniAppData.action,
                PayMEError(PayMEErrorType.MiniApp, code, description, isCloseMiniApp)
            )
            if (isCloseMiniApp) {
                closeMiniApp()
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "miniapp returnError: ${e.message} ")
        }
    }

    private fun closeMiniApp() {
        if (openType == OpenMiniAppType.modal) {
            MiniAppFragment.closeMiniApp()
        } else if (openType == OpenMiniAppType.screen) {
            (requireContext() as Activity).finish()
            MiniAppFragment.closeMiniApp()
        }
    }

    private fun forceCloseMiniApp() {
        PayMEMiniApp.onError(
            openMiniAppData.action,
            PayMEError(PayMEErrorType.UserCancel, "USER_CANCEL", "User đóng PayMEMiniApp")
        )
        closeMiniApp()
    }

    private fun openUrl(data: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(data.substring(1, data.length - 1)))
            (requireContext() as Activity).startActivity(intent)
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "openurl e ${e.message}")
        }
    }

    private var fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_CANCELED -> {
                    Log.d(PayMEMiniApp.TAG, "RESULT_CANCELED fileChooserLauncher")
                    if (fileChooserCallback != null) {
                        fileChooserCallback?.onReceiveValue(null)
                    }
                    fileChooserCallback = null
                }

                Activity.RESULT_OK -> {
                    Log.d(PayMEMiniApp.TAG, "RESULT_OK fileChooserLauncher")
                    if (fileChooserCallback == null) return@registerForActivityResult
                    fileChooserCallback?.onReceiveValue(
                        WebChromeClient.FileChooserParams.parseResult(
                            result.resultCode,
                            result.data
                        )
                    )
                    fileChooserCallback = null
                }
            }
        }

    private fun openWebView(data: String) {
        try {
            val json = JSONObject(data)
            val content = json.optString("content", "")
            val type = json.optString("type", "")
            val closeInstruction = json.optString("closeInstruction", "")
            if (content.isNotEmpty() && type.isNotEmpty()) {
                val subWebView = SubWebView(content, type, closeInstruction)
                subWebView.show(parentFragmentManager, "SUBWEBVIEW")
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "openWebView exception: ${e.message} ")
        }
    }

    private val requestContactsPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                activity?.let {
                    Utils.nativePermissionStatus(
                        it,
                        myWebView!!,
                        "READ_CONTACTS",
                        "GRANTED"
                    )
                }
                Utils.getContacts(requireContext(), myWebView!!)
            } else {
                activity?.let {
                    Utils.nativePermissionStatus(
                        it,
                        myWebView!!,
                        "READ_CONTACTS",
                        "DENIED"
                    )
                }
            }
        }

    private fun getContacts() {
        try {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_CONTACTS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    activity?.let {
                        Utils.nativePermissionStatus(
                            it,
                            myWebView!!,
                            "READ_CONTACTS",
                            "GRANTED"
                        )
                    }
                    Utils.getContacts(requireContext(), myWebView!!)
                }

                activity?.let {
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        it,
                        Manifest.permission.READ_CONTACTS
                    )
                } == true -> {
                    activity?.let {
                        Utils.nativePermissionStatus(
                            it,
                            myWebView!!,
                            "READ_CONTACTS",
                            "BLOCKED"
                        )
                    }
                }

                else -> {
                    requestContactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                }
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "getContacts exception: ${e.message} ")
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (permissionType.isEmpty()) {
                return@registerForActivityResult
            }
            if (isGranted) {
                activity?.let {
                    Utils.nativePermissionStatus(
                        it,
                        myWebView!!,
                        permissionType,
                        "GRANTED"
                    )
                }
            } else {
                activity?.let {
                    Utils.nativePermissionStatus(
                        it,
                        myWebView!!,
                        permissionType,
                        "DENIED"
                    )
                }
            }
            permissionType = ""
        }

    private fun requestPermission(data: String) {
        try {
            val json = JSONObject(data)
            val type = json.optString("type", "")
            val isCheckPermissionStatus = json.getBoolean("isCheckPermissionStatus")
            permissionType = type
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    "android.permission.$permissionType"
                ) == PackageManager.PERMISSION_GRANTED -> {
                    activity?.let {
                        Utils.nativePermissionStatus(
                            it,
                            myWebView!!,
                            permissionType,
                            "GRANTED"
                        )
                    }
                }

                activity?.let {
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        it,
                        "android.permission.$permissionType"
                    )
                } == true -> {
                    activity?.let {
                        Utils.nativePermissionStatus(
                            it,
                            myWebView!!,
                            permissionType,
                            "BLOCKED"
                        )
                    }
                }
                else -> {
                    if(isCheckPermissionStatus === false) {
                        if (permissionType == "CAMERA") {
                            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                            return
                        }
                        if (permissionType == "READ_EXTERNAL_STORAGE") {
                            requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                            return
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "requestPermission exception: ${e.message} ")
        }
    }

    private fun requestNFCPermission(data: String) {
        try {
            val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(context)
            if (nfcAdapter == null) {
                //Thiết bị không hỗ trợ NFC
                activity?.let {
                    Utils.nativePermissionStatus(
                        it,
                        myWebView!!,
                        "NFC",
                        "BLOCKED"
                    )
                }
            } else if (!nfcAdapter.isEnabled) {
                //NFC đã tắt. Vui lòng bật NFC trong cài đặt.
                // Mở cài đặt NFC cho người dùng
//                val intent = Intent(Settings.ACTION_NFC_SETTINGS)
//                context?.startActivity(intent)
                activity?.let {
                    Utils.nativePermissionStatus(
                        it,
                        myWebView!!,
                        "NFC",
                        "DENIED"
                    )
                }
            } else {
                // NFC đã bật, thực hiện các thao tác liên quan tới NFC ở đây
                activity?.let {
                    Utils.nativePermissionStatus(
                        it,
                        myWebView!!,
                        "NFC",
                        "GRANTED"
                    )
                }
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "requestNFCPermission exception: ${e.message} ")
        }
    }

    private fun share(data: String) {
        try {
            val json = JSONObject(data)
            val title = json.optString("title", "")
            val content = json.optString("content", "")
            if (content.isNotEmpty() && nativeAppState == "active") {
                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.type = "text/plain"
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, title)
                shareIntent.putExtra(Intent.EXTRA_TEXT, content)
                shareIntent.flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                startActivity(Intent.createChooser(shareIntent, title))
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "share exception: ${e.message} ")
        }
    }

    private fun reStartWithScreen() {
        closeMiniApp()

        val payMEMiniApp = PayMEMiniApp(
            requireContext(),
            PayMEMiniApp.appId,
            PayMEMiniApp.publicKey,
            PayMEMiniApp.privateKey,
            PayMEMiniApp.env
        )
        val phone = getPhoneFromOpenMiniAppData(openMiniAppData)
        phone?.let {
            payMEMiniApp.openMiniApp(
                OpenMiniAppType.screen, OpenMiniAppKYCData(it)
            )
        }
    }

    private fun startCardKyc(data: String) {
        try {
            if (openType == OpenMiniAppType.modal) {
                reStartWithScreen()
                return
            }

            val json = JSONObject(data)
            paramsKyc = json
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED -> {
                    activity?.let {
                        Utils.nativePermissionStatus(
                            it,
                            myWebView!!,
                            "CAMERA",
                            "GRANTED"
                        )
                    }
                    startIdentityCardActivity(json)
                }

                activity?.let {
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        it,
                        Manifest.permission.CAMERA
                    )
                } == true -> {
                    activity?.let {
                        Utils.nativePermissionStatus(
                            it,
                            myWebView!!,
                            "CAMERA",
                            "BLOCKED"
                        )
                    }
                }

                else -> {
                    requestCardKycPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "startCardKyc exception: ${e.message} ")
        }
    }

    private fun startKalapaKyc(data: String) {
        Log.d(PayMEMiniApp.TAG, "startKalapaKyc: ${JSONObject(data)} ")
        try {
            val json = JSONObject(data)
            paramsKyc = json
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED -> {
                    activity?.let {
                        Utils.nativePermissionStatus(
                            it,
                            myWebView!!,
                            "CAMERA",
                            "GRANTED"
                        )
                    }
                    startEKYC(json)
                }

                activity?.let {
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        it,
                        Manifest.permission.CAMERA
                    )
                } == true -> {
                    activity?.let {
                        Utils.nativePermissionStatus(
                            it,
                            myWebView!!,
                            "CAMERA",
                            "BLOCKED"
                        )
                    }
                }

                else -> {
                    requestKalapaKYCPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "startKalapaKyc exception: ${e.message} ")
        }
    }

    private fun startEKYC(data: JSONObject) {
        val sessionId = data.optString("token", "")
        if (sessionId != "") {
            val sdkConfig = KalapaSDKConfig.KalapaSDKConfigBuilder(requireContext())
                .withBackgroundColor("#FFFFFF")
                .withMainColor("#33CB33")
                .withBtnTextColor("#121212")
                .withMainTextColor("#121212")
                .withLivenessVersion(0)
                .withLanguage(PayMEMiniApp.locale.toString())
                .build()
            val flowType = KalapaFlowType.EKYC
            startFullEKYC(
                requireActivity(),
                sessionId,
                flowType.toString().lowercase(),
                sdkConfig,
                object : KalapaHandler() {
                    override fun onError(resultCode: KalapaSDKResultCode) {
                        Log.d(PayMEMiniApp.TAG, """startEKYC error: $resultCode""")
                    }

                    override fun onComplete(kalapaResult: KalapaResult) {
                        Log.d(PayMEMiniApp.TAG, """startEKYC onComplete: $kalapaResult""")
                        val response = JSONObject()
                        response.put("token", sessionId)
                        response.put("fieldType", kalapaResult.type)
                        activity?.let {
                            Utils.evaluateJSWebView(
                                it,
                                myWebView!!,
                                "nativeKalapaKYC",
                                response.toString(),
                                null
                            )
                        }
                    }
                })
            null
        } else {
            Log.d(PayMEMiniApp.TAG, "startKalapaKyc exception: sessionId null")
        }
    }

    private fun startKalapaNFC(data: String) {
        Log.d(PayMEMiniApp.TAG, "startKalapaNFC: ${JSONObject(data)} ")
        try {
            val json = JSONObject(data)
            paramsKyc = json
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED -> {
                    activity?.let {
                        Utils.nativePermissionStatus(
                            it,
                            myWebView!!,
                            "CAMERA",
                            "GRANTED"
                        )
                    }
                    startNFC(json)
                }

                activity?.let {
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        it,
                        Manifest.permission.CAMERA
                    )
                } == true -> {
                    activity?.let {
                        Utils.nativePermissionStatus(
                            it,
                            myWebView!!,
                            "CAMERA",
                            "BLOCKED"
                        )
                    }
                }

                else -> {
                    requestKalapaNFCPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "startKalapaNfc exception: ${e.message} ")
        }
    }

    private fun startNFC(data: JSONObject) {
        val sessionId = data.optString("token", "")
        if (sessionId != "") {
            val sdkConfig = KalapaSDKConfig.KalapaSDKConfigBuilder(requireContext())
                .withBackgroundColor("#FFFFFF")
                .withMainColor("#33CB33")
                .withBtnTextColor("#121212")
                .withMainTextColor("#121212")
                .withLivenessVersion(0)
                .withLanguage(PayMEMiniApp.locale.toString())
                .build()
            startFullEKYC(
                requireActivity(),
                sessionId,
                "nfc_only",
                sdkConfig,
                object : KalapaHandler() {
                    override fun onError(resultCode: KalapaSDKResultCode) {
                        Log.d(PayMEMiniApp.TAG, """startNFC error: $resultCode""")
                    }

                    override fun onComplete(kalapaResult: KalapaResult) {
                        Log.d(PayMEMiniApp.TAG, """Kalapa NFC complete: $kalapaResult""")
                        val action = data.optString("action", "")
                        val payload = data.optString("payload", "")
                        val response = JSONObject()
                        if (action != "") {
                            response.put("action", action)
                            if (payload != "" && action != "KLP_KYC") {
                                try {
                                    response.put("payload", JSONObject(payload))
                                } catch (e: JSONException) {
                                    Log.e(PayMEMiniApp.TAG, "Failed to parse payload as JSON", e)
                                }
                            }
                        } else {
                            response.put("action", "KLP_KYC")
                        }
                        activity?.let {
                            Utils.evaluateJSWebView(
                                it,
                                myWebView!!,
                                "nativeKalapaNFC",
                                response.toString(),
                                null
                            )
                        }
                    }
                })
        } else {
            Log.d(PayMEMiniApp.TAG, "startKalapaKyc exception: sessionId null")
        }
    }

    private fun startFaceKyc(data: String) {
        try {
            val json = JSONObject(data)
            paramsKyc = json
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED -> {
                    activity?.let {
                        Utils.nativePermissionStatus(
                            it,
                            myWebView!!,
                            "CAMERA",
                            "GRANTED"
                        )
                    }
                    startFaceDetectorActivity(json)
                }

                activity?.let {
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        it,
                        Manifest.permission.CAMERA
                    )
                } == true -> {
                    activity?.let {
                        Utils.nativePermissionStatus(
                            it,
                            myWebView!!,
                            "CAMERA",
                            "BLOCKED"
                        )
                    }
                }

                else -> {
                    requestFaceKycPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "startCardKyc exception: ${e.message} ")
        }
    }

    private fun startIdentityCardActivity(data: JSONObject) {
        val intent = Intent(requireContext(), IdentityCardActivity::class.java)
        val title = data.optString("title", "")
        val type = data.optString("type", "FRONT")
        val description = data.optString("description", "")
        val toastError = data.optString("toastError", "")
        intent.putExtra("title", title)
        intent.putExtra("type", type)
        intent.putExtra("description", description)
        intent.putExtra("toastError", toastError)
        identityCardLauncher.launch(intent)
    }

    private var identityCardLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_CANCELED -> Log.d(PayMEMiniApp.TAG, "RESULT_CANCELED")
                Activity.RESULT_OK -> {
                    val resultData = result.data
                    val fileName = resultData?.extras?.get("fileName")
                    val type = resultData?.extras?.get("type") ?: "FRONT"
                    val responseCardKyc = JSONObject()
                    responseCardKyc.put("image", fileName)
                    responseCardKyc.put("type", type)
                    activity?.let {
                        Utils.evaluateJSWebView(
                            it,
                            myWebView!!,
                            "nativeCardKYC",
                            responseCardKyc.toString(),
                            null
                        )
                    }
                }
            }
        }

    private fun startFaceDetectorActivity(data: JSONObject) {
        val title = data.optString("title", "")
        val jsonArray = JSONArray()
        jsonArray.put(getString(R.string.face_detector_hint1))
        jsonArray.put(getString(R.string.face_detector_hint2))
        jsonArray.put(getString(R.string.face_detector_hint3))
        val hints: JSONArray = data.optJSONArray("hints") ?: jsonArray
        val intent = Intent(requireContext(), com.payme.sdk.ui.FaceDetectorActivity::class.java)
        intent.putExtra("title", title)
        intent.putExtra("hint1", hints.get(0) as String)
        intent.putExtra("hint2", hints.get(1) as String)
        intent.putExtra("hint3", hints.get(2) as String)
        faceDetectorLauncher.launch(intent)
    }

    private var faceDetectorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_CANCELED -> Log.d(PayMEMiniApp.TAG, "RESULT_CANCELED")
                Activity.RESULT_OK -> {
                    val images3 = JSONArray()
                    images3.put("images/kycFace1.jpeg")
                    images3.put("images/kycFace2.jpeg")
                    images3.put("images/kycFace3.jpeg")
                    val responseFaceKyc = JSONObject()
                        .put("images", images3)
                    Log.d(PayMEMiniApp.TAG, "responseFaceKyc: $responseFaceKyc ")
                    activity?.let {
                        Utils.evaluateJSWebView(
                            it,
                            myWebView!!,
                            "nativeFaceKYC",
                            responseFaceKyc.toString(),
                            null
                        )
                    }
                }
            }
        }

    private fun startFaceAuthen(data: String) {
        try {
            val json = JSONObject(data)
            paramsKyc = json
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED -> {
                    activity?.let {
                        Utils.nativePermissionStatus(
                            it,
                            myWebView!!,
                            "CAMERA",
                            "GRANTED"
                        )
                    }
                    startFaceAuthenticationActivity(json)
                }

                activity?.let {
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        it,
                        Manifest.permission.CAMERA
                    )
                } == true -> {
                    activity?.let {
                        Utils.nativePermissionStatus(
                            it,
                            myWebView!!,
                            "CAMERA",
                            "BLOCKED"
                        )
                    }
                }

                else -> {
                    requestFaceAuthPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "startCardKyc exception: ${e.message} ")
        }
    }

    private fun startFaceAuthenticationActivity(data: JSONObject) {
        faceAuthenData = data
        Log.d(PayMEMiniApp.TAG, "faceAuthenData: $faceAuthenData , $data ")
        val title = data.optString("title", "")
        val jsonArray = JSONArray()
        jsonArray.put(getString(R.string.face_detector_hint1))
        val hints: JSONArray = data.optJSONArray("hints") ?: jsonArray
        val intent =
            Intent(requireContext(), com.payme.sdk.ui.FaceAuthenticationActivity::class.java)
        intent.putExtra("title", title)
        intent.putExtra("hint1", hints.get(0) as String)
        faceAuthenticationLauncher.launch(intent)
    }

    private var faceAuthenticationLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_CANCELED -> {
                    val responseFaceAuthen = JSONObject()
                    val action = faceAuthenData?.optString("action", "")
                    val payload = faceAuthenData?.optString("payload", "")
                    responseFaceAuthen.put("action", action)
                    responseFaceAuthen.put("payload", JSONObject(payload))
                    responseFaceAuthen.put("error", "CLOSE")
                    activity?.let {
                        Utils.evaluateJSWebView(
                            it,
                            myWebView!!,
                            "nativeFaceAuthen",
                            responseFaceAuthen.toString(),
                            null
                        )
                    }
                    Log.d(PayMEMiniApp.TAG, "RESULT_CANCELED")
                }

                Activity.RESULT_OK -> {
                    val resultData = result.data
                    val image = resultData?.extras?.get("image")
                    val action = faceAuthenData?.optString("action", "")
                    val payload = faceAuthenData?.optString("payload", "")
                    val responseFaceAuthen = JSONObject()
                    responseFaceAuthen.put("image", image)
                    responseFaceAuthen.put("action", action)
                    responseFaceAuthen.put("payload", JSONObject(payload))
                    Log.d(PayMEMiniApp.TAG, "responseFaceAuthen: $responseFaceAuthen")
                    activity?.let {
                        Utils.evaluateJSWebView(
                            it,
                            myWebView!!,
                            "nativeFaceAuthen",
                            responseFaceAuthen.toString(),
                            null
                        )
                    }
                }
            }
        }

    private val requestWriteExternalStoragePermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                activity?.let {
                    Utils.nativePermissionStatus(
                        it,
                        myWebView!!,
                        "WRITE_EXTERNAL_STORAGE",
                        "GRANTED"
                    )
                }
                paramsSaveQr?.let { downloadImageQR(it) }
            } else {
                activity?.let {
                    Utils.nativePermissionStatus(
                        it,
                        myWebView!!,
                        "WRITE_EXTERNAL_STORAGE",
                        "DENIED"
                    )
                }
            }
        }

    private val requestCardKycPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                activity?.let { Utils.nativePermissionStatus(it, myWebView!!, "CAMERA", "GRANTED") }
                paramsKyc?.let { startIdentityCardActivity(it) }
            } else {
                activity?.let { Utils.nativePermissionStatus(it, myWebView!!, "CAMERA", "DENIED") }
            }
        }

    private val requestFaceKycPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                activity?.let { Utils.nativePermissionStatus(it, myWebView!!, "CAMERA", "GRANTED") }
                paramsKyc?.let { startFaceDetectorActivity(it) }
            } else {
                activity?.let { Utils.nativePermissionStatus(it, myWebView!!, "CAMERA", "DENIED") }
            }
        }

    private val requestKalapaKYCPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                activity?.let { Utils.nativePermissionStatus(it, myWebView!!, "CAMERA", "GRANTED") }
                paramsKyc?.let { startEKYC(it) }
            } else {
                activity?.let { Utils.nativePermissionStatus(it, myWebView!!, "CAMERA", "DENIED") }
            }
        }

    private val requestKalapaNFCPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                activity?.let { Utils.nativePermissionStatus(it, myWebView!!, "CAMERA", "GRANTED") }
                paramsKyc?.let { startNFC(it) }
            } else {
                activity?.let { Utils.nativePermissionStatus(it, myWebView!!, "CAMERA", "DENIED") }
            }
        }

    private val requestFaceAuthPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                activity?.let { Utils.nativePermissionStatus(it, myWebView!!, "CAMERA", "GRANTED") }
                paramsKyc?.let { startFaceAuthenticationActivity(it) }
            } else {
                activity?.let { Utils.nativePermissionStatus(it, myWebView!!, "CAMERA", "DENIED") }
            }
        }

    private val evaluateJsDataObserver: Observer<Pair<String, String>> = Observer {
        if (it.first.isNotEmpty() && myWebView != null) {
            activity?.let { it1 ->
                Utils.evaluateJSWebView(
                    it1,
                    myWebView!!,
                    it.first,
                    it.second,
                    null
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        nativeAppState = "background"
        if (payMEUpdatePatchViewModel.getWebLoaded().value == false) {
            return
        }
        activity?.let {
            Utils.evaluateJSWebView(
                it,
                myWebView!!,
                "nativeAppState",
                "\"background\"",
                null
            )
        }
        MixpanelUtil.flushEvents()
    }

    override fun onResume() {
        super.onResume()
        nativeAppState = "active"
        if (payMEUpdatePatchViewModel.getWebLoaded().value == false) {
            return
        }
        activity?.let {
            Utils.evaluateJSWebView(
                it,
                myWebView!!,
                "nativeAppState",
                "\"active\"",
                null
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        miniappViewModel.openMiniAppData = openMiniAppData
        stopServer()
        Log.d("PAYMELOG", "on onDestroy " + miniappViewModel.openMiniAppData.toString())
        subWebViewViewModel.getEvaluateJsData().removeObserver(evaluateJsDataObserver)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        Log.d("PAYMELOG", "on onDestroy view " + miniappViewModel.openMiniAppData.toString())
        miniappViewModel.openMiniAppData = openMiniAppData
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (miniappViewModel.openMiniAppData != null) {
            outState.putString("openMiniAppData", Gson().toJson(miniappViewModel.openMiniAppData))
        }
        Log.d("PAYMELOG", "on onSaveInstanceState" + miniappViewModel.openMiniAppData.toString())
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (savedInstanceState != null) {
            Log.d("PAYMELOG", "chay vo recreated")
        }
    }

    companion object {
        internal lateinit var openMiniAppData: OpenMiniAppDataInterface
        internal var openType: OpenMiniAppType = OpenMiniAppType.screen
        internal lateinit var closeMiniApp: () -> Unit
        internal var onSetModalHeight: ((Int) -> Unit) = { _ -> {} }
        internal var loadUrl = ""
        internal var webViewUrl = ""
        internal var modalHeight: Int = 0

        var notificationViewModel: NotificationViewModel = NotificationViewModel()
        var deepLinkViewModel: DeepLinkViewModel = DeepLinkViewModel()
        var subWebViewViewModel: SubWebViewViewModel = SubWebViewViewModel()

        fun nativeNotificationOpenedApp(data: JSONObject) {
            notificationViewModel.setNotificationData(data)
        }

        fun evaluateJs(functionName: String, data: String) {
            subWebViewViewModel.setEvaluateJsData(Pair(functionName, data))
        }

        fun setOpenMiniAppData(data: OpenMiniAppDataInterface) {
            openMiniAppData = data
        }

        fun isOpenMiniAppInit(): Boolean {
            return ::openMiniAppData.isInitialized
        }

        fun setDeepLink(data: String) {
            deepLinkViewModel.setDeepLinkUrl(data)
        }

        fun setLoadUrl(data: String) {
            loadUrl = data
        }

        fun getMiniAppAction(): ActionOpenMiniApp {
            val json = openMiniAppData.toJsonData()
            val jsonObject = JSONObject(json.toString())
            val actionString = jsonObject.getString("action")

            return try {
                ActionOpenMiniApp.valueOf(((actionString ?: ActionOpenMiniApp.PAYME).toString()))
            } catch (e: IllegalArgumentException) {
                ActionOpenMiniApp.PAYME
            }
        }
    }

}