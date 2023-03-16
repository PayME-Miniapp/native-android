package com.payme.sdk

import android.content.Context
import android.content.Intent
import android.util.Log
import com.payme.sdk.models.*
import com.payme.sdk.presentation.AccountPresentation
import com.payme.sdk.ui.MiniAppActivity
import com.payme.sdk.ui.MiniAppFragment
import com.payme.sdk.utils.MixpanelUtil
import org.json.JSONObject

class PayMEMiniApp(
    private val context: Context,
    appId: String,
    publicKey: String,
    privateKey: String,
    env: ENV,
    onOneSignalSendTags: ((String) -> Unit)? = null,
    onOneSignalDeleteTags: ((String) -> Unit)? = null,
    onChangeEnv: ((String) -> Unit)? = null,
) {
    companion object {
        val TAG = "PAYMELOG"
        internal lateinit var appId: String
        internal lateinit var publicKey: String
        internal lateinit var privateKey: String
        internal lateinit var env: ENV
        internal var onResponse: ((ActionOpenMiniApp, JSONObject?) -> Unit) = { _, _ -> {} }
        internal var onError: ((ActionOpenMiniApp, PayMEError) -> Unit) = { _, _ -> {} }
        internal var onOneSignalSendTags: ((String) -> Unit)? = null
        internal var onOneSignalDeleteTags: ((String) -> Unit)? = null
        internal val mode : MiniAppMode = MiniAppMode.bank
        internal var onChangeEnv: ((String) -> Unit)? = null
    }

    init {
        PayMEMiniApp.appId = appId
        PayMEMiniApp.publicKey = publicKey.trim().replace("  ", "").replace("\\n", "")
        PayMEMiniApp.privateKey = privateKey.trim().replace("  ", "").replace("\\n", "")
        PayMEMiniApp.env = env
        PayMEMiniApp.onOneSignalSendTags = onOneSignalSendTags
        PayMEMiniApp.onOneSignalDeleteTags = onOneSignalDeleteTags
        PayMEMiniApp.onChangeEnv = onChangeEnv
        MixpanelUtil.initializeMixpanel(context, "b169d00f07bcf9b469ae9484ff4321cc")
    }

    fun setUpListener(
        onResponse: ((ActionOpenMiniApp, JSONObject?) -> Unit)?,
        onError: ((ActionOpenMiniApp, PayMEError) -> Unit)?
    ) {
        if (onResponse != null) {
            PayMEMiniApp.onResponse = onResponse
        }
        if (onError != null) {
            PayMEMiniApp.onError = onError
        }
    }

    fun getBalance(
        phone: String,
    ) {
        AccountPresentation.getBalance(
            context,
            phone,
            PayMEMiniApp.onResponse,
            PayMEMiniApp.onError
        )
    }

    fun openMiniApp(
        openType: OpenMiniAppType = OpenMiniAppType.screen,
        openMiniAppData: OpenMiniAppDataInterface,
    ) {
        try {
//            if (openType == OpenMiniAppType.modal) {
//                val modal = MiniAppBottomSheetDialog()
//                MiniAppFragment.openType = openType
//                MiniAppFragment.openMiniAppData = openMiniAppData
//                MiniAppFragment.closeMiniApp = {
//                    modal.dismiss()
//                }
//                modal.show((context as FragmentActivity).supportFragmentManager, null)
//                return
//            }
            if (openType == OpenMiniAppType.screen) {
                MiniAppFragment.openType = openType
                MiniAppFragment.openMiniAppData = openMiniAppData
                val intent = Intent(context, MiniAppActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            }
        } catch (e: Exception) {
            Log.d(PayMEMiniApp.TAG, "ex cast: ${e.message}")
        }
    }
}