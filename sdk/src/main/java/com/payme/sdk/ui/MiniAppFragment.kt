package com.payme.sdk.ui

import android.app.Activity
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.R
import com.payme.sdk.models.ActionOpenMiniApp
import com.payme.sdk.models.OpenMiniAppDataInterface
import com.payme.sdk.models.OpenMiniAppKYCData
import com.payme.sdk.models.OpenMiniAppType
import com.payme.sdk.models.PayMEError
import com.payme.sdk.models.PayMEErrorType
import com.payme.sdk.models.getPhoneFromOpenMiniAppData
import com.payme.sdk.utils.MixpanelUtil
import com.payme.sdk.utils.NetworkMonitor
import com.payme.sdk.utils.PermissionCameraUtil
import com.payme.sdk.utils.Utils
import com.payme.sdk.ui.miniapp.MiniAppBackPressCallback
import com.payme.sdk.ui.miniapp.MiniAppDeviceInfoBuilder
import com.payme.sdk.ui.miniapp.MiniAppKycController
import com.payme.sdk.ui.miniapp.MiniAppPermissionController
import com.payme.sdk.ui.miniapp.MiniAppUpdateController
import com.payme.sdk.ui.miniapp.MiniAppWebViewController
import com.payme.sdk.ui.miniapp.MiniAppViews
import com.payme.sdk.viewmodels.DeepLinkViewModel
import com.payme.sdk.viewmodels.MiniappViewModel
import com.payme.sdk.viewmodels.NotificationViewModel
import com.payme.sdk.viewmodels.PayMEUpdatePatchViewModel
import com.payme.sdk.viewmodels.SubWebViewViewModel
import com.payme.sdk.webServer.JavaScriptInterface
import org.json.JSONArray
import org.json.JSONObject

class MiniAppFragment : Fragment() {
    private lateinit var views: MiniAppViews
    private var rootView: View? = null
    private var myWebView: WebView? = null
    private val refreshHandler = Handler(Looper.getMainLooper())
    private var isWebViewRefreshCheckNeeded = false

    private var nativeAppState = "active"
    private var listScreenBackBlocked = JSONArray()
    private var isCloseMiniAppRequested = false
    private var isTerminalMiniAppErrorHandled = false

    private lateinit var kycController: MiniAppKycController
    private lateinit var permissionController: MiniAppPermissionController
    private lateinit var updateController: MiniAppUpdateController
    private lateinit var webViewController: MiniAppWebViewController

    private lateinit var payMEUpdatePatchViewModel: PayMEUpdatePatchViewModel
    private lateinit var miniappViewModel: MiniappViewModel

    private fun sendNativeDeviceInfo() {
        val safeContext = context ?: return
        val safeRootView = rootView ?: return
        val deviceInfo = MiniAppDeviceInfoBuilder.build(
            context = safeContext,
            activity = activity,
            rootView = safeRootView,
            openType = openType
        )

        activity?.let {
            Utils.evaluateJSWebView(
                it, myWebView!!, "nativeDeviceInfo", deviceInfo.toString(), null
            )
        }
    }

    private fun stopWebViewAfterTerminalError() {
        refreshHandler.removeCallbacksAndMessages(null)
        myWebView?.stopLoading()
        myWebView?.removeJavascriptInterface("messageHandlers")
    }

    private fun handleUnauthorizedHttpError(errorUrl: String) {
        if (isTerminalMiniAppErrorHandled) {
            return
        }

        Log.e(PayMEMiniApp.TAG, "HTTP 401 unauthorized for URL: $errorUrl - closing mini app")
        activity?.runOnUiThread {
            val errorJson = JSONObject().apply {
                put("code", HTTP_STATUS_UNAUTHORIZED.toString())
                put("description", getString(R.string.session_expired))
                put("isCloseMiniApp", true)
            }.toString()
            returnError(errorJson)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(
            this,
            MiniAppBackPressCallback(
                webViewProvider = { myWebView },
                blockedScreensProvider = { listScreenBackBlocked }
            )
        )
        permissionController = MiniAppPermissionController(
            fragment = this,
            webViewProvider = { myWebView },
            nativeAppStateProvider = { nativeAppState }
        )
        kycController = MiniAppKycController(
            fragment = this,
            webViewProvider = { myWebView },
            openTypeProvider = { openType },
            restartWithScreen = { reStartWithScreen() }
        )
        updateController = MiniAppUpdateController(
            contextProvider = { context },
            activityProvider = { activity },
            viewsProvider = { views },
            updateViewModelProvider = { payMEUpdatePatchViewModel },
            getLoadUrl = { loadUrl },
            setLoadUrl = { loadUrl = it },
            returnError = { returnError(it) },
            closeMiniApp = { closeMiniApp() }
        )
        webViewController = MiniAppWebViewController(
            fragment = this,
            viewsProvider = { views },
            updateViewModelProvider = { payMEUpdatePatchViewModel },
            notificationViewModelProvider = { notificationViewModel },
            miniappViewModelProvider = { miniappViewModel },
            deepLinkViewModelProvider = { deepLinkViewModel },
            loadUrlProvider = { loadUrl },
            openTypeProvider = { openType },
            onUrlPartChanged = { onSetWebViewUrlPart(it) },
            onUnauthorizedHttpError = { handleUnauthorizedHttpError(it) },
            onReturnError = { returnError(it) },
            onRestartLocalServer = { updateController.restartLocalServerAfterWebResourceError() },
            onSendNativeDeviceInfo = { sendNativeDeviceInfo() }
        )
        miniappViewModel = ViewModelProvider(requireActivity())[MiniappViewModel::class.java]

        if (isOpenMiniAppInit()) {
            miniappViewModel.openMiniAppData = openMiniAppData
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.fragment_mini_app, container, false)
        payMEUpdatePatchViewModel = PayMEUpdatePatchViewModel()
        views = MiniAppViews.bind(view)
        rootView = views.rootView
        myWebView = views.webView
        
        // Khởi tạo NetworkMonitor để theo dõi kết nối
        context?.let { ctx ->
            NetworkMonitor.initialize(ctx)
        }
        updateController.startVersionCheck()
        updateController.registerConnectivityCallback()

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
                        it, myWebView!!, "nativeKeyboardHeight", height.toString(), null
                    )
                }
            } else {
                activity?.let {
                    Utils.evaluateJSWebView(
                        it, myWebView!!, "nativeKeyboardHeight", "0", null
                    )
                }
            }
        }

        myWebView?.let { webView ->
            webViewController.configure(webView, createJavaScriptInterface(webView))
        }

        payMEUpdatePatchViewModel.getDoneUpdate().observe(viewLifecycleOwner) {
            if (it) {
                updateController.onDoneUpdate(
                    loadDefaultSource = payMEUpdatePatchViewModel.getLoadDefaultSource().value == true
                ) { url ->
                    myWebView?.loadUrl(url)
                }
            }
        }
        payMEUpdatePatchViewModel.getShowUpdatingUI().observe(viewLifecycleOwner) {
            updateController.setUpdatingUiVisible(it)
        }

        payMEUpdatePatchViewModel.getIsLostConnection().observe(viewLifecycleOwner) {
            if (!it) {
                updateController.onConnectionRestoredIfForceUpdating()
            }
        }

        notificationViewModel.getNotificationData().observe(viewLifecycleOwner) {
            if (it.length() != 0) {
                notificationViewModel.setNotificationJSON(it)
                activity?.let { it1 ->
                    Utils.evaluateJSWebView(
                        it1, myWebView!!, "nativeNotificationOpenedApp", it.toString(), null
                    )
                }
            }
        }

        subWebViewViewModel.getEvaluateJsData().observeForever(evaluateJsDataObserver)

        return view
    }

    private fun createJavaScriptInterface(webView: WebView): JavaScriptInterface {
        return JavaScriptInterface(setNativePreferences = { data: String? ->
            activity?.let {
                Utils.setNativePref(
                    it, data
                )
            }
        },
            sendNativePreferences = { activity?.let { Utils.sendNativePref(it, webView) } },
            biometricAuthen = { data: String ->
                Utils.biometricAuthenticate(
                    activity as AppCompatActivity, myWebView!!, data
                )
            },
            startCardKyc = { data: String -> kycController.startCardKyc(data) },
            startFaceKyc = { data: String -> kycController.startFaceKyc(data) },
            startKalapaKyc = { data: String -> kycController.startKalapaKyc(data) },
            startKalapaNFC = { data: String -> kycController.startKalapaNFC(data) },
            startFaceAuthen = { data: String -> kycController.startFaceAuthen(data) },
            openSettings = { activity?.let { PermissionCameraUtil().openSetting(it) } },
            share = { data: String -> permissionController.share(data) },
            requestPermission = { data: String -> permissionController.requestPermission(data) },
            sendNativeDeviceInfo = { sendNativeDeviceInfo() },
            getContacts = { permissionController.getContacts() },
            nativeOpenKeyboard = {
                activity?.let {
                    Utils.nativeOpenKeyboard(
                        it, myWebView
                    )
                }
            },
            openWebView = { data: String -> permissionController.openWebView(data) },
            onSuccess = { data: String -> returnSuccess(data) },
            onError = { data: String -> returnError(data) },
            closeMiniApp = { forceCloseMiniApp() },
            openUrl = { data: String -> permissionController.openUrl(data) },
            saveQR = { data: String -> permissionController.saveQR(data) },
            changeEnv = { data: String -> changeEnv(data) },
            changeLocale = { data: String -> changeLocale(data) },
            setListScreenBackBlocked = { data: JSONArray -> setListScreenBackBlocked(data) },
            setModalHeight = { data: Int -> setModalHeight(data) },
            requestNFCPermission = { _: String -> permissionController.requestNFCPermission() })
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
        if (action != ActionOpenMiniApp.PAY && action != ActionOpenMiniApp.SERVICE && action != ActionOpenMiniApp.PAYMENT && action != ActionOpenMiniApp.TRANSFER_QR) return

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
            if (isCloseMiniApp) {
                if (isTerminalMiniAppErrorHandled) {
                    Log.d(PayMEMiniApp.TAG, "Ignore duplicated terminal miniapp error: $code")
                    return
                }
                isTerminalMiniAppErrorHandled = true
                stopWebViewAfterTerminalError()
            }
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
        if (isCloseMiniAppRequested) {
            return
        }
        isCloseMiniAppRequested = true
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
            PayMEError(PayMEErrorType.UserCancel, "USER_CANCEL", getString(R.string.user_cancel_miniapp))
        )
        closeMiniApp()
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

    private val evaluateJsDataObserver: Observer<Pair<String, String>> = Observer {
        if (it.first.isNotEmpty() && myWebView != null) {
            activity?.let { it1 ->
                Utils.evaluateJSWebView(
                    it1, myWebView!!, it.first, it.second, null
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
        // Thông báo cho JavaScript về trạng thái
        activity?.let {
            Utils.evaluateJSWebView(
                it, myWebView!!, "nativeAppState", "\"background\"", null
            )
        }
        // Tạm dừng WebView để tối ưu tài nguyên
        myWebView?.onPause()
        MixpanelUtil.flushEvents()
    }

    override fun onResume() {
        super.onResume()
        nativeAppState = "active"
        if (payMEUpdatePatchViewModel.getWebLoaded().value == false) {
            return
        }
        // Khôi phục WebView từ trạng thái tạm dừng
        myWebView?.onResume()
        myWebView?.requestFocus()
        
        // Thông báo cho JavaScript về trạng thái
        activity?.let {
            Utils.evaluateJSWebView(
                it, myWebView!!, "nativeAppState", "\"active\"", null
            )
        }
        
        // Kiểm tra và khôi phục WebView nếu bị trắng màn hình
        checkAndRefreshWebView()
    }
    
    /**
     * Kiểm tra và làm mới WebView nếu nó bị trắng màn hình
     */
    private fun checkAndRefreshWebView() {
        // Đánh dấu cần kiểm tra WebView
        isWebViewRefreshCheckNeeded = true
        
        // Lên lịch kiểm tra sau một khoảng thời gian ngắn để đảm bảo WebView đã được khởi tạo đầy đủ
        refreshHandler.postDelayed({
            if (isWebViewRefreshCheckNeeded && myWebView?.visibility == View.VISIBLE) {
                if (!isWebViewContentVisible()) {
                    Log.d(PayMEMiniApp.TAG, "WebView phát hiện màn hình trắng, đang làm mới...")
                    // Thực hiện tải lại WebView để khôi phục nội dung
                    val currentUrl = myWebView?.url
                    if (!currentUrl.isNullOrEmpty()) {
                        myWebView?.loadUrl(currentUrl)
                    }
                }
                isWebViewRefreshCheckNeeded = false
            }
        }, 500) // Đợi 500ms sau khi onResume
    }
    
    /**
     * Kiểm tra xem nội dung WebView có đang hiển thị hay không
     */
    private fun isWebViewContentVisible(): Boolean {
        return (myWebView?.contentHeight ?: 0) > 0 && myWebView?.progress == 100
    }

    override fun onDestroy() {
        super.onDestroy()
        miniappViewModel.openMiniAppData = openMiniAppData
        updateController.dispose()
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
        private const val HTTP_STATUS_UNAUTHORIZED = 401

        internal lateinit var openMiniAppData: OpenMiniAppDataInterface
        internal var openType: OpenMiniAppType = OpenMiniAppType.screen
        internal lateinit var closeMiniApp: () -> Unit
        internal var onSetModalHeight: ((Int) -> Unit) = { _ -> run {} }
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
