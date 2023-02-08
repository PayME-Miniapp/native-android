package com.payme.sdk.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.*
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.airbnb.lottie.LottieAnimationView
import com.payme.sdk.BuildConfig
import com.payme.sdk.R
import com.payme.sdk.models.*
import com.payme.sdk.utils.DeviceTypeResolver
import com.payme.sdk.utils.PermissionCameraUtil
import com.payme.sdk.utils.Utils
import com.payme.sdk.viewmodels.MiniappViewModel
import com.payme.sdk.viewmodels.NotificationViewModel
import com.payme.sdk.viewmodels.PayMEUpdatePatchViewModel
import com.payme.sdk.webServer.JavaScriptInterface
import com.payme.sdk.webServer.WebServer
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.net.ssl.SSLException

class MiniAppFragment : Fragment() {
    private var rootView: View? = null
    private var myWebView: WebView? = null
    private var www_root: File? = null
    private var server: com.payme.sdk.webServer.MySimpleWebServer? = null

    private var loadUrl = ""
    private var port = 4646
    private var permissionType = ""
    private var nativeAppState = "active"

    private var paramsKyc: JSONObject? = null
    private var backgroundDownload = false
    private var versionCheckingTask: Thread? = null

    private lateinit var payMEUpdatePatchViewModel: PayMEUpdatePatchViewModel

    private lateinit var updatingView: CardView
    private lateinit var progressBar: ProgressBar
    private lateinit var textProgress: TextView
    private lateinit var textUpdateLabel: TextView
    private lateinit var lottieView: LottieAnimationView
    private lateinit var lottieContainerView: LinearLayout

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    private fun unzip() {
        val filesDir = requireContext().filesDir
        val sourceWeb = File("${filesDir.path}/update", "sdkWebapp3-main.zip")
        if (sourceWeb.exists() && !backgroundDownload && sourceWeb.length() > 0) {
            Log.d("HIEU", "chay vo copy update")
            val wwwDirectory = File(filesDir.path, "www")
            wwwDirectory.delete()
            if (!wwwDirectory.exists()) {
                wwwDirectory.mkdir()
            }
            Utils.unzipFile("${filesDir.path}/update/sdkWebapp3-main.zip", "${filesDir.path}/www")
            sourceWeb.delete()
            port = Utils.findRandomOpenPort() ?: 4646
            return
        }

        Log.d("HIEU", "chay vo unzip source down san")

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

    private fun startServer() {
        if (server != null) {
            return
        }
        www_root = File("${requireContext().filesDir.path}/www", "sdkWebapp3-main")
        loadUrl = "http://localhost" + ":" + port + "/"
//        loadUrl = "http://10.7.0.112:3000/"
        try {
            server = WebServer("localhost", port, www_root)
            (server as WebServer).start()
            Log.d("HIEU", "start server")
        } catch (e: Exception) {
            Log.d("HIEU", "error ${e.message}")
        }
    }

    private fun stopServer() {
        if (server != null) {
            Log.w("HIEU", "Stopped Server")
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
        if (MiniAppFragment.openType == OpenMiniAppType.screen) {
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
            Log.d("HIEU", "source web length ${sourceWeb.length()}")
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
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view: View = inflater.inflate(R.layout.fragment_mini_app, container, false)
        payMEUpdatePatchViewModel = PayMEUpdatePatchViewModel()
        versionCheckingTask = Thread {
            try {
                val versionFile = File(requireContext().filesDir.path, "version.json")
                if (versionFile.exists()) {
                    versionFile.delete()
                }
                versionFile.createNewFile()
                Utils.downloadWithoutTemp(
                    "https://aws-payme-production.s3.ap-southeast-1.amazonaws.com/frontend/miniapp-store/PayMEMiniAppVersion.json",
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
                val patch = found.optInt("patch", 0)
                val mandatory = found.optBoolean("mandatory", false)
                val latestMandatory = found.optInt("latestMandatoryPatch", 0)
                val url = found.optString("url")

                val sharedPreference =
                    requireContext().getSharedPreferences(
                        "PAYME_NATIVE_UPDATE",
                        Context.MODE_PRIVATE
                    )
                val editor = sharedPreference.edit()

                val localPatch = sharedPreference.getInt("PAYME_PATCH", 0)

                val localMandatory = if (localPatch < latestMandatory) true else mandatory
                val payMEVersion = PayMEVersion(patch, version, localMandatory, url)
                if (payMEVersion.patch <= localPatch) {
                    Log.d("HIEU", "do not update")
                    payMEUpdatePatchViewModel.setDoneUpdate(true)
                    return@Thread
                }
                if (!payMEVersion.mandatory) {
                    Log.d("HIEU", "download ngầm")
                    backgroundDownload = true
                    payMEUpdatePatchViewModel.setDoneUpdate(true)
                    downloadSourceWeb(payMEVersion.url, editor, payMEVersion.patch)
                    return@Thread
                }
                Log.d("HIEU", "force update")
                activity?.runOnUiThread {
                    textUpdateLabel.text =
                        "Đang tải dữ liệu (${BuildConfig.SDK_VERSION}.${patch})..."
                }
                payMEUpdatePatchViewModel.setShowUpdatingUI(true)
                payMEUpdatePatchViewModel.setIsForceUpdating(true)
                downloadSourceWeb(payMEVersion.url, editor, payMEVersion.patch)
                Log.d("HIEU", "downloaded source moi")
                payMEUpdatePatchViewModel.setIsForceUpdating(false)
                payMEUpdatePatchViewModel.setDoneUpdate(true)
            } catch (e: SSLException) {
                Log.d("HIEU", "SSLException ex ${e}")
            } catch (e: Exception) {
                payMEUpdatePatchViewModel.setDoneUpdate(true)
                Log.d("HIEU", "thread ex ${e}")
            }
        }
        versionCheckingTask?.start()
        rootView = view.findViewById(R.id.root_view)
        myWebView = view.findViewById(R.id.webview)
        updatingView = view.findViewById(R.id.updating_view)
        progressBar = view.findViewById(R.id.progress)
        textProgress = view.findViewById(R.id.progress_text)
        textUpdateLabel = view.findViewById(R.id.update_label_text)
        lottieView = view.findViewById(R.id.lottieView)
        lottieContainerView = view.findViewById(R.id.lottie_container_view)

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
            if (payMEUpdatePatchViewModel.getShowUpdatingUI().value == true) {
                return@addOnGlobalLayoutListener
            }
            val r = Rect()
            rootView!!.getWindowVisibleDisplayFrame(r)
            val screenHeight: Int = rootView!!.rootView.height
            val heightDiff: Int = screenHeight - r.bottom

            val height = Utils.pxToDp(requireContext(), heightDiff)

            if (heightDiff > 100) {
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
                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.grant(request.resources)
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    Log.d("HIEU", "chay vo on file chooser $filePathCallback")

                    if (fileChooserCallback != null) {
                        fileChooserCallback?.onReceiveValue(null)
                    }

                    fileChooserCallback = filePathCallback;
                    val intent = fileChooserParams?.createIntent()
                    try {
                        fileChooserLauncher.launch(intent)
                    } catch (e: Exception) {
                        Log.d("HIEU", "chay vo catch ${e.message}")
                        return true
                    }
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    Log.d("HIEU", "shouldOverrideUrlLoading url: $url")
                    return if (url.contains(".pdf")) {
                        val pdfUrl = "https://docs.google.com/gview?embedded=true&url=${url}"
                        view?.loadUrl(pdfUrl)
                        false
                    } else if (url.startsWith("http://") || url.startsWith("https://")) {
                        view.loadUrl(url)
                        false
                    } else try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        view.context.startActivity(intent)
                        true
                    } catch (e: Exception) {
                        Log.d("HIEU", "shouldOverrideUrlLoading Exception: $e")
                        true
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url.toString()
                    Log.d("HIEU", "shouldOverrideUrlLoading url: $url")
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
                        Log.d("HIEU", "shouldOverrideUrlLoading Exception: $e")
                        true
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse
                ) {
                    Log.d("HIEU", "HTTP error " + errorResponse.statusCode + errorResponse.data)
                }

                override fun onPageStarted(view: WebView?, url: String?, facIcon: Bitmap?) {
                    Log.d("HIEU", "page started $url")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    Log.d("HIEU", "page finished $url")
                    if (url == loadUrl) {
                        payMEUpdatePatchViewModel.setShowUpdatingUI(false)
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

                        val openMiniAppData = miniappViewModel.getOpenMiniAppData().value
                        if (openMiniAppData != null) {
                            val json = openMiniAppData.toJsonData()
                            activity?.let {
                                Utils.evaluateJSWebView(
                                    it,
                                    myWebView!!,
                                    "openMiniApp",
                                    json.toString(),
                                    null
                                )
                            }
                            miniappViewModel.setOpenMiniAppData(null)
                        }
                    }
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
                    try {
                        stopServer()
                        server = WebServer("localhost", port, www_root)
                        (server as WebServer).start()
                        Log.d("HIEU", "start server")
                    } catch (e: Exception) {
                        Log.d("HIEU", "error ${e.message}")
                    }
                    Log.d("HIEU", "error https ${error?.description}")
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
                onSuccess = {data: String -> returnSuccess(data) },
                onError = {data: String -> returnError(data) },
                closeMiniApp = { closeMiniApp() }
            )
            addJavascriptInterface(javaScriptInterface, "messageHandlers")

            WebStorage.getInstance().deleteAllData()

            loadUrl("javascript:localStorage.clear()")
        }

        payMEUpdatePatchViewModel.getDoneUpdate().observe(viewLifecycleOwner) {
            if (it) {
                unzip()
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

        miniappViewModel.getEvaluateJsData().observeForever(evaluateJsDataObserver)

        view.setOnKeyListener(object : View.OnKeyListener {
            override fun onKey(v: View?, keyCode: Int, event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        Log.d("HIEU", "onBackPressed Called")
                        if (myWebView != null) {
                            if (myWebView!!.canGoBack()) {
                                val url = myWebView!!.url
                                val check = (!url.isNullOrEmpty() && url.endsWith("home"))
                                if (!check) {
                                    Log.d("HIEU", "webview back")
                                    myWebView!!.goBack()
                                }
                            }
                        }
                        return true
                    }
                }
                return false
            }
        })

        return view
    }

    private fun returnSuccess(data: String) {
        try {
            val json = JSONObject(data)
            MiniAppFragment.onSuccess(MiniAppFragment.openMiniAppData.action, json)
        } catch (e: Exception) {
            Log.d("HIEU", "miniapp returnSuccess: ${e.message} ")
        }
    }

    private fun returnError(data: String) {
        try {
            val json = JSONObject(data)
            val code = json.optString("code", "")
            val description = json.optString("description", "")
            val isCloseMiniApp = json.optBoolean("isCloseMiniApp", false)
            MiniAppFragment.onError(MiniAppFragment.openMiniAppData.action, PayMEError(code, description))
            if (isCloseMiniApp) {
                closeMiniApp()
            }
        } catch (e: Exception) {
            Log.d("HIEU", "miniapp returnError: ${e.message} ")
        }
    }


    private fun closeMiniApp() {
        if (MiniAppFragment.openType == OpenMiniAppType.modal) {
            MiniAppFragment.closeMiniApp()
        } else if (MiniAppFragment.openType == OpenMiniAppType.screen) {
            (requireContext() as Activity).finish()
        }
    }

    private var fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                Activity.RESULT_CANCELED -> {
                    Log.d("HIEU", "RESULT_CANCELED fileChooserLauncher")
                    if (fileChooserCallback != null) {
                        fileChooserCallback?.onReceiveValue(null)
                    }
                    fileChooserCallback = null
                }
                Activity.RESULT_OK -> {
                    Log.d("HIEU", "RESULT_OK fileChooserLauncher")
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
            Log.d("HIEU", "openWebView exception: ${e.message} ")
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
            Log.d("HIEU", "getContacts exception: ${e.message} ")
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
            permissionType = data
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
        } catch (e: Exception) {
            Log.d("HIEU", "requestPermission exception: ${e.message} ")
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
            Log.d("HIEU", "share exception: ${e.message} ")
        }
    }

    private fun startCardKyc(data: String) {
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
            Log.d("HIEU", "startCardKyc exception: ${e.message} ")
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
            Log.d("HIEU", "startCardKyc exception: ${e.message} ")
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
                Activity.RESULT_CANCELED -> Log.d("HIEU", "RESULT_CANCELED")
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
                Activity.RESULT_CANCELED -> Log.d("HIEU", "RESULT_CANCELED")
                Activity.RESULT_OK -> {
                    val images3 = JSONArray()
                    images3.put("images/kycFace1.jpeg")
                    images3.put("images/kycFace2.jpeg")
                    images3.put("images/kycFace3.jpeg")
                    val responseFaceKyc = JSONObject()
                    responseFaceKyc.put("images", images3)
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
        activity?.let {
            Utils.evaluateJSWebView(
                it,
                myWebView!!,
                "nativeAppState",
                "\"background\"",
                null
            )
        }
    }

    override fun onResume() {
        super.onResume()
        nativeAppState = "active"
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
        stopServer()
        miniappViewModel.getEvaluateJsData().removeObserver(evaluateJsDataObserver)
    }

    companion object {
        internal lateinit var onSuccess: (ActionOpenMiniApp, JSONObject?) -> Unit
        internal lateinit var onError: (ActionOpenMiniApp, PayMEError) -> Unit
        internal lateinit var openMiniAppData: OpenMiniAppData
        internal lateinit var openType: OpenMiniAppType
        internal lateinit var closeMiniApp: () -> Unit

        fun newInstance(action: ActionOpenMiniApp = ActionOpenMiniApp.PAYME) = MiniAppFragment().apply {
            arguments = Bundle().apply {
                putString("ACTION", action.toString())
            }
        }

        var notificationViewModel: NotificationViewModel = NotificationViewModel()
        var miniappViewModel: MiniappViewModel = MiniappViewModel()

        fun nativeNotificationOpenedApp(data: JSONObject) {
            notificationViewModel.setNotificationData(data)
        }

        fun evaluateJs(functionName: String, data: String) {
            miniappViewModel.setEvaluateJsData(Pair(functionName, data))
        }

        fun setOpenMiniAppData(data: OpenMiniAppData) {
            miniappViewModel.setOpenMiniAppData(data)
        }
    }

}