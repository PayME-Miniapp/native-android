package com.payme.sdk

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import com.payme.sdk.models.*
import com.payme.sdk.network_requests.NetworkRequest
import com.payme.sdk.network_requests.NetworkUtils
import com.payme.sdk.presentation.AccountPresentation
import com.payme.sdk.ui.MiniAppActivity
import com.payme.sdk.ui.MiniAppBottomSheetDialog
import com.payme.sdk.ui.MiniAppFragment
import org.json.JSONObject

class PayMEMiniApp(
    context: Context,
    appId: String,
    publicKey: String,
    privateKey: String,
    env: ENV,
    onOneSignalSendTags: ((String) -> Unit)? = null,
    onOneSignalDeleteTags: ((String) -> Unit)? = null,
) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        val TAG = "PAYMELOG"
        internal lateinit var context: Context
        internal lateinit var appId: String
        internal lateinit var publicKey: String
        internal lateinit var privateKey: String
        internal lateinit var env: ENV
        internal var onResponse: ((ActionOpenMiniApp, JSONObject?) -> Unit) = { _, _ -> {} }
        internal var onError: ((ActionOpenMiniApp, PayMEError) -> Unit) = { _, _ -> {} }
        internal var onOneSignalSendTags: ((String) -> Unit)? = null
        internal var onOneSignalDeleteTags: ((String) -> Unit)? = null
    }

    init {
        PayMEMiniApp.context = context
        PayMEMiniApp.appId = appId
        PayMEMiniApp.publicKey = publicKey.trim().replace("  ", "").replace("\\n", "")
        PayMEMiniApp.privateKey = privateKey.trim().replace("  ", "").replace("\\n", "")
        PayMEMiniApp.env = env
        PayMEMiniApp.onOneSignalSendTags = onOneSignalSendTags
        PayMEMiniApp.onOneSignalDeleteTags = onOneSignalDeleteTags
    }

    fun setUpListener(onResponse: ((ActionOpenMiniApp, JSONObject?) -> Unit)?, onError: ((ActionOpenMiniApp, PayMEError) -> Unit)?) {
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
        AccountPresentation.getBalance(PayMEMiniApp.context, phone, PayMEMiniApp.onResponse, PayMEMiniApp.onError)
    }

    fun openMiniApp(
        openType: OpenMiniAppType = OpenMiniAppType.screen,
        openMiniAppData: OpenMiniAppDataInterface,
    ) {
        try {
            if (openType == OpenMiniAppType.modal) {
                val modal = MiniAppBottomSheetDialog()
                MiniAppFragment.openType = openType
                MiniAppFragment.openMiniAppData = openMiniAppData
                MiniAppFragment.closeMiniApp = {
                    modal.dismiss()
                }
                modal.show((context as FragmentActivity).supportFragmentManager, null)
                return
            }
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