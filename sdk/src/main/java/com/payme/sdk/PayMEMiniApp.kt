package com.payme.sdk

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentActivity
import com.payme.sdk.models.*
import com.payme.sdk.ui.MiniAppActivity
import com.payme.sdk.ui.MiniAppBottomSheetDialog
import com.payme.sdk.ui.MiniAppFragment
import org.json.JSONObject

class PayMEMiniApp(
    context: Context,
    appId: String,
    publicKey: String,
    privateKey : String,
    env: ENV,
    onOneSignalSendTags: ((String) -> Unit)? = null,
    onOneSignalDeleteTags: ((String) -> Unit)? = null,
) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        internal lateinit var context: Context
        internal lateinit var appId: String
        internal lateinit var publicKey: String
        internal lateinit var privateKey: String
        internal lateinit var env: ENV
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

    fun openMiniApp(
        openType: OpenMiniAppType = OpenMiniAppType.screen,
        openMiniAppData: OpenMiniAppData,
        onSuccess: (ActionOpenMiniApp ,JSONObject?) -> Unit,
        onError: (ActionOpenMiniApp, PayMEError) -> Unit
    ) {
        try {
            if (openType == OpenMiniAppType.modal) {
                val modal = MiniAppBottomSheetDialog()
                MiniAppFragment.setOpenMiniAppData(openMiniAppData)
                MiniAppFragment.openType = openType
                MiniAppFragment.openMiniAppData = openMiniAppData
                MiniAppFragment.onSuccess = onSuccess
                MiniAppFragment.onError = onError
                MiniAppFragment.closeMiniApp = {
                    modal.dismiss()
                }
                modal.show((context as FragmentActivity).supportFragmentManager, null)
                return
            }
            if (openType == OpenMiniAppType.screen) {
                MiniAppFragment.setOpenMiniAppData(openMiniAppData)
                MiniAppFragment.openType = openType
                MiniAppFragment.openMiniAppData = openMiniAppData
                MiniAppFragment.onSuccess = onSuccess
                MiniAppFragment.onError = onError
                val intent = Intent(context, MiniAppActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            }
        } catch (e: Exception) {
            Log.d("PAYME", "ex cast: ${e.message}")
        }
    }
}