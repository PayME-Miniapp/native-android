package com.payme.sdk.ui

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.fragment.app.Fragment
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
import com.payme.sdk.runtime.MiniAppSession
import com.payme.sdk.runtime.PayMERuntime
import com.payme.sdk.utils.MixpanelUtil
import com.payme.sdk.utils.NetworkMonitor
import com.payme.sdk.utils.WebViewJsDispatcher
import com.payme.sdk.ui.miniapp.MiniAppBackPressCallback
import com.payme.sdk.ui.miniapp.MiniAppBridgeFactory
import com.payme.sdk.ui.miniapp.MiniAppDeviceInfoBuilder
import com.payme.sdk.ui.miniapp.MiniAppFragmentObserverBinder
import com.payme.sdk.ui.miniapp.MiniAppKeyboardHeightDispatcher
import com.payme.sdk.ui.miniapp.MiniAppKycController
import com.payme.sdk.ui.miniapp.MiniAppPermissionController
import com.payme.sdk.ui.miniapp.MiniAppUpdateController
import com.payme.sdk.ui.miniapp.MiniAppWebViewController
import com.payme.sdk.ui.miniapp.MiniAppWebViewRefreshGuard
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
    private lateinit var session: MiniAppSession
    private var sessionId: String? = null
    private lateinit var views: MiniAppViews
    private var rootView: View? = null
    private var myWebView: WebView? = null
    private val webViewRefreshGuard = MiniAppWebViewRefreshGuard { myWebView }

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

    private val openMiniAppData: OpenMiniAppDataInterface
        get() = session.openMiniAppData

    private val openType: OpenMiniAppType
        get() = session.openType

    private var loadUrl: String
        get() = session.loadUrl
        set(value) {
            session.loadUrl = value
        }

    private var webViewUrl: String
        get() = session.webViewUrl
        set(value) {
            session.webViewUrl = value
        }

    private var modalHeight: Int
        get() = session.modalHeight
        set(value) {
            session.modalHeight = value
        }

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
            WebViewJsDispatcher.evaluate(
                it, myWebView!!, "nativeDeviceInfo", deviceInfo.toString()
            )
        }
    }

    private fun stopWebViewAfterTerminalError() {
        webViewRefreshGuard.clear()
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
        sessionId = arguments?.getString(PayMERuntime.EXTRA_SESSION_ID)
            ?: activity?.intent?.getStringExtra(PayMERuntime.EXTRA_SESSION_ID)
            ?: PayMERuntime.activeSession()?.id
        session = PayMERuntime.getSession(sessionId)
            ?: error("Missing miniapp session")
        PayMERuntime.activate(session.id)

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
            localeProvider = { session.config.locale },
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
            configProvider = { session.config },
            loadUrlProvider = { loadUrl },
            openTypeProvider = { openType },
            onUrlPartChanged = { onSetWebViewUrlPart(it) },
            onUnauthorizedHttpError = { handleUnauthorizedHttpError(it) },
            onReturnError = { returnError(it) },
            onRestartLocalServer = { updateController.restartLocalServerAfterWebResourceError() },
            onSendNativeDeviceInfo = { sendNativeDeviceInfo() }
        )
        miniappViewModel = ViewModelProvider(requireActivity())[MiniappViewModel::class.java]
        miniappViewModel.openMiniAppData = openMiniAppData
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

        MiniAppKeyboardHeightDispatcher(
            contextProvider = { context },
            activityProvider = { activity },
            rootViewProvider = { rootView },
            webViewProvider = { myWebView },
            isWebLoadedProvider = { payMEUpdatePatchViewModel.getWebLoaded().value == true }
        ).register()

        myWebView?.let { webView ->
            webViewController.configure(webView, createJavaScriptInterface(webView))
        }

        MiniAppFragmentObserverBinder(
            lifecycleOwner = viewLifecycleOwner,
            activityProvider = { activity },
            webViewProvider = { myWebView },
            updateViewModel = payMEUpdatePatchViewModel,
            notificationViewModel = notificationViewModel,
            subWebViewViewModel = subWebViewViewModel,
            updateController = updateController
        ).bind()

        return view
    }

    private fun createJavaScriptInterface(webView: WebView): JavaScriptInterface {
        return MiniAppBridgeFactory(
            activityProvider = { activity },
            webViewProvider = { myWebView },
            sessionProvider = { session },
            kycControllerProvider = { kycController },
            permissionControllerProvider = { permissionController },
            onSendNativeDeviceInfo = { sendNativeDeviceInfo() },
            onSuccess = { returnSuccess(it) },
            onError = { returnError(it) },
            onForceClose = { forceCloseMiniApp() },
            onChangeEnv = { changeEnv(it) },
            onChangeLocale = { changeLocale(it) },
            onSetListScreenBackBlocked = { setListScreenBackBlocked(it) },
            onSetModalHeight = { updateModalHeight(it) }
        ).create(webView)
    }

    private fun changeEnv(env: String) {
        session.callbacks.onChangeEnv?.let { it(env) }
    }

    private fun changeLocale(locale: String) {
        session.callbacks.onChangeLocale?.let { it(locale) }
    }

    private fun setListScreenBackBlocked(data: JSONArray?) {
        listScreenBackBlocked = data ?: JSONArray()
    }

    fun getListScreenBackBlocked(): JSONArray {
        return listScreenBackBlocked
    }

    private fun updateModalHeight(height: Int) {
        height.let {
            if (it != 0 && it != modalHeight) {
                modalHeight = it
                session.onSetModalHeight(it)
            }
        }
    }

    private fun onSetWebViewUrlPart(url: String) {
        val action = session.action
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
            updateModalHeight(maxHeight)
        }
    }

    private fun returnSuccess(data: String) {
        try {
            val json = JSONObject(data)
            session.callbacks.onResponse(openMiniAppData.action, json)
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
                if (isTerminalMiniAppErrorHandled || session.isTerminalErrorHandled) {
                    Log.d(PayMEMiniApp.TAG, "Ignore duplicated terminal miniapp error: $code")
                    return
                }
                isTerminalMiniAppErrorHandled = true
                session.isTerminalErrorHandled = true
                stopWebViewAfterTerminalError()
            }
            session.callbacks.onError(
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
        session.isCloseRequested = true
        if (openType == OpenMiniAppType.modal) {
            session.close()
        } else if (openType == OpenMiniAppType.screen) {
            (requireContext() as Activity).finish()
            session.close()
        }
        PayMERuntime.removeSession(session.id)
    }

    private fun forceCloseMiniApp() {
        session.callbacks.onError(
            openMiniAppData.action,
            PayMEError(PayMEErrorType.UserCancel, "USER_CANCEL", getString(R.string.user_cancel_miniapp))
        )
        closeMiniApp()
    }

    private fun reStartWithScreen() {
        closeMiniApp()

        val payMEMiniApp = PayMEMiniApp(
            requireContext(),
            session.config.appId,
            session.config.publicKey,
            session.config.privateKey,
            session.config.env,
            session.config.locale
        )
        val phone = getPhoneFromOpenMiniAppData(openMiniAppData)
        phone?.let {
            payMEMiniApp.openMiniApp(
                OpenMiniAppType.screen, OpenMiniAppKYCData(it)
            )
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
            WebViewJsDispatcher.evaluate(
                it, myWebView!!, "nativeAppState", "\"background\""
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
            WebViewJsDispatcher.evaluate(
                it, myWebView!!, "nativeAppState", "\"active\""
            )
        }
        
        // Kiểm tra và khôi phục WebView nếu bị trắng màn hình
        webViewRefreshGuard.scheduleCheck()
    }

    override fun onDestroy() {
        super.onDestroy()
        miniappViewModel.openMiniAppData = openMiniAppData
        updateController.dispose()
        Log.d("PAYMELOG", "on onDestroy " + miniappViewModel.openMiniAppData.toString())
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
            return PayMERuntime.activeSession() != null || ::openMiniAppData.isInitialized
        }

        fun setDeepLink(data: String) {
            deepLinkViewModel.setDeepLinkUrl(data)
        }

        fun setLoadUrl(data: String) {
            PayMERuntime.activeSession()?.loadUrl = data
        }

        fun getMiniAppAction(): ActionOpenMiniApp {
            PayMERuntime.activeSession()?.let { return it.action }
            if (!::openMiniAppData.isInitialized) {
                return ActionOpenMiniApp.PAYME
            }
            val json = openMiniAppData.toJsonData()
            val jsonObject = JSONObject(json.toString())
            val actionString = jsonObject.getString("action")

            return try {
                ActionOpenMiniApp.valueOf(((actionString ?: ActionOpenMiniApp.PAYME).toString()))
            } catch (e: IllegalArgumentException) {
                ActionOpenMiniApp.PAYME
            }
        }

        fun newInstance(sessionId: String): MiniAppFragment {
            return MiniAppFragment().apply {
                arguments = Bundle().apply {
                    putString(PayMERuntime.EXTRA_SESSION_ID, sessionId)
                }
            }
        }
    }
}
