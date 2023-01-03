package com.payme.sdk

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import com.payme.sdk.ui.MiniAppActivity

class PayMEMiniApp(
    context: Context,
    onOneSignalSendTags: ((String) -> Unit)?,
    onOneSignalDeleteTags: ((String) -> Unit)?,
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

    fun openMiniApp() {
        context.startActivity(Intent(context, MiniAppActivity::class.java))
    }
}