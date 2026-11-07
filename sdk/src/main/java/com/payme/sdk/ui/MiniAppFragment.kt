package com.payme.sdk.ui

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.R
import com.payme.sdk.models.ActionOpenMiniApp
import com.payme.sdk.models.OpenMiniAppDataInterface
import com.payme.sdk.models.OpenMiniAppKYCData
import com.payme.sdk.models.OpenMiniAppType
import com.payme.sdk.models.PayMEError
import com.payme.sdk.models.PayMEErrorType
import com.payme.sdk.models.getIdentityTokenFromOpenMiniAppData
import com.payme.sdk.runtime.MiniAppSession
import com.payme.sdk.runtime.PayMERuntime
import com.payme.sdk.runtime.SessionClose
import com.payme.sdk.utils.BridgeExecutor
import com.payme.sdk.utils.DeviceSecurityUtils
import com.payme.sdk.utils.LightContext
import com.payme.sdk.utils.MixpanelUtil
import com.payme.sdk.utils.SdkLocale
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
    private var sessionMissing = false
    private var sessionId: String? = null
    private lateinit var views: MiniAppViews
    private var rootView: View? = null
    private var myWebView: WebView? = null
    private val webViewRefreshGuard = MiniAppWebViewRefreshGuard { myWebView }
    private val mainHandler = Handler(Looper.getMainLooper())

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

    /**
     * Root detection (process execs) runs once per process on BridgeExecutor. The device-info
     * reply and then [onSent] always run on the UI thread, in that order.
     */
    private fun sendNativeDeviceInfo(onSent: () -> Unit = {}) {
        val appContext = context?.applicationContext
        if (appContext == null || DeviceSecurityUtils.isRootCheckCached()) {
            sendNativeDeviceInfoNow()
            onSent()
            return
        }
        BridgeExecutor.execute {
            try {
                DeviceSecurityUtils.isRooted(appContext)
            } catch (e: Exception) {
                Log.d(PayMEMiniApp.TAG, "root check failed: ${e.javaClass.simpleName}")
            }
            mainHandler.post {
                if (view == null) return@post
                sendNativeDeviceInfoNow()
                onSent()
            }
        }
    }

    private fun sendNativeDeviceInfoNow() {
        val safeContext = context ?: return
        val safeRootView = rootView ?: return
        val webView = myWebView ?: return
        val deviceInfo = MiniAppDeviceInfoBuilder.build(
            context = safeContext,
            activity = activity,
            rootView = safeRootView,
            openType = openType
        )

        activity?.let {
            WebViewJsDispatcher.evaluate(
                it, webView, "nativeDeviceInfo", deviceInfo.toString()
            )
        }
    }

    private fun stopWebViewAfterTerminalError() {
        webViewRefreshGuard.clear()
        myWebView?.stopLoading()
        myWebView?.let { webViewController.removeBridge(it) }
    }

    private fun handleUnauthorizedHttpError(errorUrl: String) {
        if (isTerminalMiniAppErrorHandled) {
            return
        }

        Log.e(PayMEMiniApp.TAG, "HTTP 401 unauthorized for URL: $errorUrl - closing mini app")
        activity?.runOnUiThread {
            val errorJson = JSONObject().apply {
                put("code", HTTP_STATUS_UNAUTHORIZED.toString())
                put("description", sdkString(R.string.session_expired))
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
        session = PayMERuntime.getSession(sessionId) ?: run {
            // Hệ thống khôi phục fragment sau khi process bị huỷ: phiên (cùng identityToken) đã mất, không chạy tiếp được.
            // Đóng nơi chứa (modal hoặc MiniAppActivity) thay vì crash app của partner.
            sessionMissing = true
            closeHostWithoutSession()
            return
        }
        PayMERuntime.activate(session.id)
        if (savedInstanceState == null) com.payme.sdk.utils.KycImageStore.clear(requireContext().filesDir)

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
            contextProvider = { localizedContext() },
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
            localOriginProvider = { updateController.localOrigin },
            onLoadUrlChanged = { loadUrl = it },
            openTypeProvider = { openType },
            onUrlPartChanged = { onSetWebViewUrlPart(it) },
            onUnauthorizedHttpError = { handleUnauthorizedHttpError(it) },
            onReturnError = { returnError(it) },
            onRestartLocalServer = { updateController.restartLocalServerAfterWebResourceError() },
            onSendNativeDeviceInfo = { sendNativeDeviceInfo(it) }
        )
        miniappViewModel = ViewModelProvider(requireActivity())[MiniappViewModel::class.java]
        miniappViewModel.openMiniAppData = openMiniAppData
    }

    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        val inflater = super.onGetLayoutInflater(savedInstanceState)
        // Trong modal, view (kể cả WebView/prefers-color-scheme) dùng context Activity partner: ép sáng cục bộ
        if (parentFragment !is DialogFragment) return inflater
        return inflater.cloneInContext(LightContext.wrap(inflater.context))
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        if (sessionMissing) return View(inflater.context)
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
            updateController = updateController,
            loadPage = { webView, url -> webViewController.load(webView, url) }
        ).bind()

        registerBackCallback()
        return view
    }

    /** Back của modal đi vào dialog, không vào Activity của partner; màn hình thì vào MiniAppActivity. */
    private fun registerBackCallback() {
        val dispatcher = when (val host = parentFragment) {
            is DialogFragment -> (host.dialog as? ComponentDialog)?.onBackPressedDispatcher
            else -> requireActivity().onBackPressedDispatcher
        } ?: return
        dispatcher.addCallback(
            viewLifecycleOwner,
            MiniAppBackPressCallback(
                webViewProvider = { myWebView },
                blockedScreensProvider = { listScreenBackBlocked },
                onClose = { forceCloseMiniApp() }
            )
        )
    }

    private fun createJavaScriptInterface(webView: WebView): JavaScriptInterface {
        return MiniAppBridgeFactory(
            activityProvider = { activity },
            webViewProvider = { myWebView },
            sessionProvider = { session },
            kycControllerProvider = { kycController },
            permissionControllerProvider = { permissionController },
            onSendNativeDeviceInfo = { sendNativeDeviceInfo(it) },
            onResendOpenMiniApp = { myWebView?.let { webViewController.redispatchOpenMiniApp(it) } },
            onSuccess = { returnSuccess(it) },
            onError = { returnError(it) },
            onForceClose = { forceCloseMiniApp() },
            onChangeEnv = { changeEnv(it) },
            onChangeLocale = { changeLocale(it) },
            onSetListScreenBackBlocked = { setListScreenBackBlocked(it) },
            onSetModalHeight = { updateModalHeight(it) }
        ).create(webView)
    }

    // Modal dùng context của Activity partner: chuỗi SDK phải lấy theo ngôn ngữ mini-app (SdkLocale)
    private var localized: Pair<Context, Context>? = null

    private fun localizedContext(): Context? {
        val base = context ?: return null
        localized?.takeIf { it.first === base }?.let { return it.second }
        return SdkLocale.wrap(base, session.config.locale).also { localized = base to it }
    }

    private fun sdkString(id: Int): String = (localizedContext() ?: requireContext()).getString(id)

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

    /** Phiên đã kết thúc (host huỷ, close()): kết quả web tới muộn không được gửi cho partner. */
    private fun isSessionEnded(): Boolean = PayMERuntime.getSession(session.id) == null

    private fun returnSuccess(data: String) {
        if (isSessionEnded()) return
        try {
            // Partner mở lại ngay trong onResponse được; callback ném lỗi thì vẫn đóng
            val json = JSONObject(data)
            SessionClose.response(session, json, json.optBoolean("isCloseMiniApp", false)) { closeMiniApp() }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "miniapp returnSuccess: ${e.message} ")
        }
    }

    private fun returnError(data: String) {
        if (isSessionEnded()) return
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
            SessionClose.error(session, PayMEError(PayMEErrorType.MiniApp, code, description, isCloseMiniApp)) {
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
        PayMERuntime.markCloseRequested(session)
        // closeAction của nơi chứa đóng modal / finish MiniAppActivity, chỉ một lần
        session.close()
        if (openType == OpenMiniAppType.screen) {
            activity?.takeIf { !it.isFinishing }?.finish()
        }
        PayMERuntime.removeSession(session.id)
        releaseOpenMiniAppData()
    }

    /**
     * openMiniAppData carries identityToken: kept in memory across a configuration change only, never past the session,
     * and never written to the saved-state Bundle (the system persists it). Recreation uses EXTRA_SESSION_ID instead.
     */
    private fun retainOrReleaseOpenMiniAppData() {
        val retained = retainedOpenMiniAppData(
            isChangingConfigurations = activity?.isChangingConfigurations == true,
            isSessionRegistered = PayMERuntime.getSession(session.id) != null,
            data = openMiniAppData
        )
        if (retained != null) miniappViewModel.openMiniAppData = retained else releaseOpenMiniAppData()
    }

    private fun closeHostWithoutSession() {
        Log.e(PayMEMiniApp.TAG, "Missing miniapp session after process restore. Closing the mini-app.")
        val host = parentFragment
        if (host is DialogFragment) host.dismissAllowingStateLoss() else activity?.finish()
    }

    private fun releaseOpenMiniAppData() {
        // ViewModel của modal thuộc Activity partner, có thể đã mang payload của phiên mới (mở lại trong callback)
        if (::miniappViewModel.isInitialized && miniappViewModel.openMiniAppData === session.openMiniAppData) {
            miniappViewModel.openMiniAppData = null
        }
        clearOpenMiniAppData()
    }

    private fun forceCloseMiniApp() {
        // Đã đóng (lỗi kết thúc, close() hoặc jsClose lặp lại): không báo USER_CANCEL lần nữa
        if (isCloseMiniAppRequested) return
        SessionClose.userCancel(session, sdkString(R.string.user_cancel_miniapp)) { closeMiniApp() }
    }

    /** KYC từ modal: mở lại dạng màn hình với bản sao cấu hình/callback của phiên (không khởi tạo lại SDK). */
    private fun reStartWithScreen() {
        val ctx = context ?: return
        val config = session.config.copy()
        val callbacks = session.callbacks.copy()
        val identityToken = getIdentityTokenFromOpenMiniAppData(openMiniAppData)
        closeMiniApp()
        if (identityToken.isNullOrBlank()) {
            // Modal đã đóng mà không mở được KYC (thiếu token, vd. PAYME/khách vãng lai): partner vẫn phải nhận kết quả
            callbacks.onError(session.action, PayMEMiniApp.Launcher.identityTokenRequiredError(ctx))
            return
        }
        PayMEMiniApp.Launcher.launch(ctx, OpenMiniAppType.screen, OpenMiniAppKYCData(identityToken), config, callbacks)
    }

    override fun onPause() {
        super.onPause()
        if (sessionMissing) return
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
        if (sessionMissing) return
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
        if (sessionMissing) return
        retainOrReleaseOpenMiniAppData()
        updateController.dispose()
        if (activity?.isChangingConfigurations != true) context?.let { com.payme.sdk.utils.KycImageStore.clear(it.filesDir) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (sessionMissing) return
        myWebView?.let {
            webViewController.removeBridge(it)
            // Huỷ WebView để không giữ Activity/renderer sau khi đóng
            it.stopLoading()
            (it.parent as? ViewGroup)?.removeView(it)
            it.destroy()
        }
        myWebView = null
        rootView = null
        // Không xoá payload ở đây: view có thể được dựng lại trong khi fragment và phiên vẫn sống,
        // khi đó redispatchOpenMiniApp cần payload. Payload được xoá ở onDestroy và khi đóng miniapp.
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        if (savedInstanceState != null) {
            Log.d("PAYMELOG", "chay vo recreated")
        }
    }

    companion object {
        private const val HTTP_STATUS_UNAUTHORIZED = 401

        @Volatile
        internal var openMiniAppData: OpenMiniAppDataInterface? = null

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

        /** What the activity-scoped ViewModel may keep when this fragment is destroyed: only data for a live session
         *  that is being recreated (rotation). A modal's ViewModel belongs to the partner's activity, so anything else
         *  would keep identityToken alive after the mini-app has closed. */
        internal fun retainedOpenMiniAppData(
            isChangingConfigurations: Boolean,
            isSessionRegistered: Boolean,
            data: OpenMiniAppDataInterface
        ): OpenMiniAppDataInterface? = data.takeIf { isChangingConfigurations && isSessionRegistered }

        /** Drops the legacy static reference when the mini-app session ends; it may carry identityToken. */
        internal fun clearOpenMiniAppData() {
            openMiniAppData = null
        }

        fun isOpenMiniAppInit(): Boolean {
            return PayMERuntime.activeSession() != null || openMiniAppData != null
        }

        fun setDeepLink(data: String) {
            deepLinkViewModel.setDeepLinkUrl(data)
        }

        fun setLoadUrl(data: String) {
            PayMERuntime.activeSession()?.loadUrl = data
        }

        fun getMiniAppAction(): ActionOpenMiniApp {
            PayMERuntime.activeSession()?.let { return it.action }
            val data = openMiniAppData ?: return ActionOpenMiniApp.PAYME
            val json = data.toJsonData()
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
