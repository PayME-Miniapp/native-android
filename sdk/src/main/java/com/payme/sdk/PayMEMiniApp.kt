package com.payme.sdk

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.payme.sdk.models.OpenMiniAppData
import com.payme.sdk.models.OpenMiniAppType
import com.payme.sdk.ui.MiniAppActivity
import com.payme.sdk.ui.MiniAppBottomSheetDialog
import com.payme.sdk.ui.MiniAppFragment

class PayMEMiniApp(
    context: Context,
    onOneSignalSendTags: ((String) -> Unit)? = null,
    onOneSignalDeleteTags: ((String) -> Unit)? = null,
) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        internal lateinit var context: Context
        internal var onOneSignalSendTags: ((String) -> Unit)? = null
        internal var onOneSignalDeleteTags: ((String) -> Unit)? = null
    }

    init {
        PayMEMiniApp.context = context
        PayMEMiniApp.onOneSignalSendTags = onOneSignalSendTags
        PayMEMiniApp.onOneSignalDeleteTags = onOneSignalDeleteTags
    }

    fun openMiniApp(
        type: OpenMiniAppType = OpenMiniAppType.screen,
        openMiniAppData: OpenMiniAppData
    ) {
        if (type == OpenMiniAppType.modal) {
            try {
                val modal = MiniAppBottomSheetDialog()
                MiniAppFragment.setOpenMiniAppData(openMiniAppData)
                modal.show((context as AppCompatActivity).supportFragmentManager, null)
            } catch (e: ClassCastException) {
                Log.d("HIEU", "khong phai activty")
            }
            return
        }
        if (type == OpenMiniAppType.screen) {
            val intent = Intent(context, MiniAppActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        }
    }
}