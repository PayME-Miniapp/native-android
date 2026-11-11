package com.payme.sdk

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.payme.sdk.models.*
import com.payme.sdk.presentation.AccountPresentation
import com.payme.sdk.runtime.PayMECallbacks
import com.payme.sdk.runtime.PayMEConfig
import com.payme.sdk.runtime.PayMERuntime
import com.payme.sdk.ui.MiniAppActivity
import com.payme.sdk.ui.MiniAppBottomSheetDialog
import com.payme.sdk.ui.dismissSafely
import com.payme.sdk.utils.MixpanelUtil
import com.payme.sdk.utils.SdkLocale
import org.json.JSONObject
import java.lang.ref.WeakReference

/** SDK dùng sẵn bộ key "app" của PayME; đối tác xác thực người dùng bằng identityToken. */
class PayMEMiniApp(
    private val context: Context,
    env: ENV = ENV.PRODUCTION,
    locale: Locale = Locale.vi
) {
    /** [appId], [publicKey], [privateKey] không còn dùng: SDK luôn dùng bộ key "app" có sẵn. */
    @Deprecated(
        "Dùng PayMEMiniApp(context, env, locale). SDK đã có sẵn key, appId/publicKey/privateKey bị bỏ qua.",
        ReplaceWith("PayMEMiniApp(context, env, locale)")
    )
    @Suppress("UNUSED_PARAMETER")
    constructor(
        context: Context,
        appId: String,
        publicKey: String,
        privateKey: String,
        env: ENV = ENV.PRODUCTION,
        locale: Locale = Locale.vi
    ) : this(context, env, locale)

    private var activeSessionId: String? = null

    companion object {
        var TAG: String = "PAYMELOG"
        internal var appId: String = ""
        internal lateinit var publicKey: String
        internal lateinit var privateKey: String
        internal lateinit var env: ENV
        internal lateinit var locale: Locale
        internal var onResponse: ((ActionOpenMiniApp, JSONObject?) -> Unit) = { _: ActionOpenMiniApp, _: JSONObject? -> }
        internal var onError: ((ActionOpenMiniApp, PayMEError) -> Unit) = { _: ActionOpenMiniApp, _: PayMEError -> }

        // only payme wallet
        internal var onOneSignalSendTags: ((String) -> Unit)? = null
        internal var onOneSignalDeleteTags: ((String) -> Unit)? = null
        internal var mode: String =
            "miniapp_product" // miniapp_product, miniapp_sandbox, miniapp_staging, pm_product, pm_staging, pm_sandbox, bank
        internal var onChangeEnv: ((String) -> Unit)? = null
        internal var onChangeLocale: ((String) -> Unit)? = null
        internal var isOpen = false
        // Không hiển thị được mini-app (cùng mã với iOS)
        internal const val MINIAPP_CANNOT_PRESENT = "MINIAPP_CANNOT_PRESENT"
        internal const val MINIAPP_ALREADY_OPEN = "MINIAPP_ALREADY_OPEN"
    }

    init {
        val config = PayMEConfig.create(env, locale)
        PayMERuntime.initialize(config)
        syncLegacyConfig(config)
        MixpanelUtil.initializeMixpanel(context, "b169d00f07bcf9b469ae9484ff4321cc")
        // Ngôn ngữ chỉ áp dụng cho UI mini-app (SdkLocale), không đổi Locale/Resources của app partner
    }

    fun setUpListener(
        onResponse: ((ActionOpenMiniApp, JSONObject?) -> Unit)? = null,
        onError: ((ActionOpenMiniApp, PayMEError) -> Unit)? = null
    ) {
        onResponse?.let {
            PayMEMiniApp.onResponse = it
            PayMERuntime.updateCallbacks { callbacks -> callbacks.onResponse = it }
        }
        onError?.let {
            PayMEMiniApp.onError = it
            PayMERuntime.updateCallbacks { callbacks -> callbacks.onError = it }
        }
    }

    /** [identityToken]: signed by PayME BE for the partner's user. Memory-only: never log or persist it. */
    fun getBalance(
        identityToken: String,
    ) {
        val callbacks = PayMERuntime.callbacks()
        if (identityToken.isBlank()) return callbacks.onError(ActionOpenMiniApp.GET_BALANCE, identityTokenRequiredError())
        AccountPresentation.getBalance(
            SdkLocale.wrap(context), identityToken, callbacks.onResponse, callbacks.onError
        )
    }

    /** [identityToken]: signed by PayME BE for the partner's user. Memory-only: never log or persist it. */
    fun getAccountInformation(
        identityToken: String,
    ) {
        val callbacks = PayMERuntime.callbacks()
        if (identityToken.isBlank()) {
            return callbacks.onError(ActionOpenMiniApp.GET_ACCOUNT_INFO, identityTokenRequiredError())
        }
        AccountPresentation.getAccountInfo(
            SdkLocale.wrap(context), identityToken, callbacks.onResponse, callbacks.onError
        )
    }

    private fun identityTokenRequiredError() = Launcher.identityTokenRequiredError(context)

    /** Đóng mini-app đang mở. Không bao giờ ném lỗi cho partner. */
    fun close() {
        try {
            val session = PayMERuntime.getSession(activeSessionId)?.takeIf { !it.isCloseRequested }
                ?: PayMERuntime.activeSession()
            if (session != null) {
                try {
                    PayMERuntime.markCloseRequested(session)
                    PayMERuntime.closeSession(session.id)
                } finally {
                    PayMERuntime.removeSession(session.id)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "close failed: ${e.javaClass.simpleName}")
        } finally {
            activeSessionId = null
        }
    }

    fun openMiniApp(
        openType: OpenMiniAppType = OpenMiniAppType.screen,
        openMiniAppData: OpenMiniAppDataInterface,
    ) {
        Launcher.launch(context, openType, openMiniAppData)?.let { activeSessionId = it }
    }

    internal object Launcher {
        /**
         * Mở một phiên mới; trả về id phiên hoặc null nếu bị từ chối (đã báo onError).
         * [config]/[callbacks] null: dùng cấu hình chung (KYC từ modal truyền bản sao của phiên cũ).
         */
        internal fun launch(
            context: Context,
            openType: OpenMiniAppType,
            openMiniAppData: OpenMiniAppDataInterface,
            config: PayMEConfig? = null,
            callbacks: PayMECallbacks? = null
        ): String? {
            val onError = (callbacks ?: PayMERuntime.callbacks()).onError
            fun reject(code: String, description: Int): String? {
                onError(openMiniAppData.action, openError(context, code, description))
                return null
            }
            // Thiếu token thì web không có định danh và đứng ở màn chờ: báo lỗi ngay cho app đối tác.
            if (isMissingRequiredIdentityToken(openMiniAppData)) {
                onError(openMiniAppData.action, identityTokenRequiredError(context))
                return null
            }
            // Modal gắn vào FragmentManager của partner: context phải là FragmentActivity
            if (openType == OpenMiniAppType.modal && context !is FragmentActivity) {
                return reject(MINIAPP_CANNOT_PRESENT, R.string.modal_requires_fragment_activity)
            }
            // Activity đang đóng/đã huỷ: modal không hiện được, không tạo phiên
            if (openType == OpenMiniAppType.modal && !canPresentModal(context as FragmentActivity)) {
                return reject(MINIAPP_CANNOT_PRESENT, R.string.error_occurred)
            }
            // Phiên kẹt (host đã mất) không được chặn mãi
            PayMERuntime.clearStaleSessions { SdkLocale.wrap(context).getString(R.string.user_cancel_miniapp).orEmpty() }
            // Không chồng mini-app: lần mở thứ hai bị từ chối, phiên đang mở giữ nguyên
            if (PayMERuntime.isOpen()) return reject(MINIAPP_ALREADY_OPEN, R.string.miniapp_already_open)
            var sessionId: String? = null
            return try {
                val session = if (config != null && callbacks != null) {
                    PayMERuntime.createSession(openType, openMiniAppData, config, callbacks)
                } else {
                    PayMERuntime.createSession(openType, openMiniAppData)
                }
                sessionId = session.id
                PayMERuntime.activate(session.id)
                when (openType) {
                    OpenMiniAppType.modal -> {
                        val fragmentManager = (context as FragmentActivity).supportFragmentManager
                        val modal = MiniAppBottomSheetDialog.newInstance(session.id)
                        // Giữ yếu; MiniAppBottomSheetDialog.onCreate thay closeAction này
                        val modalRef = WeakReference(modal)
                        session.closeAction = {
                            PayMERuntime.markSessionClosed(session.id)
                            modalRef.get()?.dismissSafely()
                        }
                        modal.show(fragmentManager, null)
                    }
                    OpenMiniAppType.screen -> {
                        session.closeAction = {
                            PayMERuntime.markSessionClosed(session.id)
                        }
                        val intent = Intent(context, MiniAppActivity::class.java)
                        intent.putExtra(PayMERuntime.EXTRA_SESSION_ID, session.id)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                }
                session.id
            } catch (e: Exception) {
                // Không hiển thị được (Activity đã lưu state, startActivity lỗi...): không để lại phiên mồ côi
                Log.e(TAG, "openMiniApp failed: ${e.javaClass.simpleName}")
                PayMERuntime.removeSession(sessionId)
                reject(MINIAPP_CANNOT_PRESENT, R.string.error_occurred)
            }
        }

        private fun canPresentModal(activity: FragmentActivity): Boolean =
            isModalHostUsable(activity.isFinishing, activity.isDestroyed, activity.supportFragmentManager.isDestroyed)

        /** Modal chỉ gắn được vào Activity chưa finish/huỷ và FragmentManager còn sống. */
        internal fun isModalHostUsable(isFinishing: Boolean, isDestroyed: Boolean, isFragmentManagerDestroyed: Boolean): Boolean =
            !isFinishing && !isDestroyed && !isFragmentManagerDestroyed

        private fun openError(context: Context, code: String, description: Int) =
            PayMEError(PayMEErrorType.MiniApp, code, SdkLocale.wrap(context).getString(description).orEmpty())

        internal fun identityTokenRequiredError(context: Context) = PayMEError(
            PayMEErrorType.MiniApp,
            "IDENTITY_TOKEN_REQUIRED",
            SdkLocale.wrap(context).getString(R.string.identity_token_required)
        )
    }

    fun setMode(mode: String) {
        if (PayMERuntime.currentConfig() == null) {
            error("PayMEMiniApp instance is not initialized")
        } else {
            PayMEMiniApp.mode = mode
            PayMERuntime.updateConfigs { config -> config.mode = mode }
        }
    }

    fun setLanguage(lang: Locale) {
        if (PayMERuntime.currentConfig() == null) {
            error("PayMEMiniApp instance is not initialized")
        } else {
            PayMEMiniApp.locale = lang
            PayMERuntime.updateConfigs { config -> config.locale = lang }
            // onChangeLocale chỉ báo khi người dùng đổi ngôn ngữ trong mini-app (jsChangeLocale), không gọi lại ở đây
        }
    }

    fun setChangeEnvFunction(
        onChangeEnv: ((String) -> Unit)? = null,
    ) {
        if (PayMERuntime.currentConfig() == null) {
            error("PayMEMiniApp instance is not initialized")
        } else {
            PayMEMiniApp.onChangeEnv = onChangeEnv
            PayMERuntime.updateCallbacks { callbacks -> callbacks.onChangeEnv = onChangeEnv }
        }
    }

    fun setChangeLocaleFunction(
        onChangeLocale: ((String) -> Unit)? = null,
    ) {
        if (PayMERuntime.currentConfig() == null) {
            error("PayMEMiniApp instance is not initialized")
        } else {
            PayMEMiniApp.onChangeLocale = onChangeLocale
            PayMERuntime.updateCallbacks { callbacks -> callbacks.onChangeLocale = onChangeLocale }
        }
    }

    fun setOneSignalFunctions(
        onOneSignalSendTags: ((String) -> Unit)? = null,
        onOneSignalDeleteTags: ((String) -> Unit)? = null,
    ) {
        if (PayMERuntime.currentConfig() == null) {
            error("PayMEMiniApp instance is not initialized")
        } else {
            PayMEMiniApp.onOneSignalSendTags = onOneSignalSendTags
            PayMEMiniApp.onOneSignalDeleteTags = onOneSignalDeleteTags
            PayMERuntime.updateCallbacks { callbacks ->
                callbacks.onOneSignalSendTags = onOneSignalSendTags
                callbacks.onOneSignalDeleteTags = onOneSignalDeleteTags
            }
        }
    }

    private fun syncLegacyConfig(config: PayMEConfig) {
        PayMEMiniApp.appId = config.appId
        PayMEMiniApp.publicKey = config.publicKey
        PayMEMiniApp.privateKey = config.privateKey
        PayMEMiniApp.env = config.env
        PayMEMiniApp.locale = config.locale
        PayMEMiniApp.mode = config.mode
    }
}
