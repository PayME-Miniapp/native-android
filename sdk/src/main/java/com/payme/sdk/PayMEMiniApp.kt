package com.payme.sdk

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.payme.sdk.models.*
import com.payme.sdk.presentation.AccountPresentation
import com.payme.sdk.runtime.PayMEConfig
import com.payme.sdk.runtime.PayMERuntime
import com.payme.sdk.ui.MiniAppActivity
import com.payme.sdk.ui.MiniAppBottomSheetDialog
import com.payme.sdk.utils.MixpanelUtil
import org.json.JSONObject

class PayMEMiniApp(
    private val context: Context,
    appId: String,
    publicKey: String,
    privateKey: String,
    env: ENV = ENV.PRODUCTION,
    locale: Locale = Locale.vi
) {
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
    }

    init {
        val config = PayMEConfig.create(appId, publicKey, privateKey, env, locale)
        PayMERuntime.initialize(config)
        syncLegacyConfig(config)
        MixpanelUtil.initializeMixpanel(context, "b169d00f07bcf9b469ae9484ff4321cc")
        
        // Áp dụng cấu hình ngôn ngữ ngay khi khởi tạo
        applyLanguageConfiguration(locale)
    }
    
    /**
     * Áp dụng cấu hình ngôn ngữ cho cả ứng dụng
     */
    private fun applyLanguageConfiguration(lang: Locale) {
        // Chuyển đổi từ PayME.Locale sang java.util.Locale
        val javaLocale = when (lang) {
            Locale.vi -> java.util.Locale("vi", "VN")
            Locale.en -> java.util.Locale("en", "US")
        }
        
        // Đặt Locale mặc định
        java.util.Locale.setDefault(javaLocale)
        
        // Cập nhật cấu hình resources
        try {
            val config = android.content.res.Configuration()
            config.setLocale(javaLocale)
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            
            Log.d(TAG, "Language configuration applied: $lang")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying language configuration: ${e.message}")
        }
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

    fun getBalance(
        phone: String,
    ) {
        val callbacks = PayMERuntime.callbacks()
        AccountPresentation.getBalance(
            context, phone, callbacks.onResponse, callbacks.onError
        )
    }

    fun getAccountInformation(
        phone: String,
    ) {
        val callbacks = PayMERuntime.callbacks()
        AccountPresentation.getAccountInfo(
            context, phone, callbacks.onResponse, callbacks.onError
        )
    }

    fun close() {
        val sessionId = activeSessionId ?: PayMERuntime.activeSession()?.id
        val session = PayMERuntime.getSession(sessionId)
        session?.isCloseRequested = true
        PayMERuntime.closeSession(sessionId)
        PayMERuntime.removeSession(sessionId)
        activeSessionId = null
    }

    fun openMiniApp(
        openType: OpenMiniAppType = OpenMiniAppType.screen,
        openMiniAppData: OpenMiniAppDataInterface,
    ) {
        try {
            val session = PayMERuntime.createSession(openType, openMiniAppData)
            activeSessionId = session.id
            PayMERuntime.activate(session.id)
            when (openType) {
                OpenMiniAppType.modal -> {
                    val modal = MiniAppBottomSheetDialog.newInstance(session.id)
                    session.closeAction = {
                        PayMERuntime.markSessionClosed(session.id)
                        modal.dismiss()
                    }
                    modal.show((context as FragmentActivity).supportFragmentManager, null)
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
        } catch (e: Exception) {
            PayMERuntime.syncLegacyOpenState()
            Log.d(TAG, "ex cast: ${e.message}")
        }
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
            
            // Áp dụng cấu hình ngôn ngữ mới
            applyLanguageConfiguration(lang)
            
            // Thông báo thay đổi ngôn ngữ cho các module khác nếu cần
            PayMERuntime.callbacks().onChangeLocale?.invoke(lang.toString())
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
