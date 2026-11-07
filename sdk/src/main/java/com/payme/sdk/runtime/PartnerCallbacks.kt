package com.payme.sdk.runtime

import android.util.Log
import com.payme.sdk.PayMEMiniApp

/** Callback của partner: lỗi bên trong chỉ ghi log, không làm crash app và không sinh thêm callback. */
internal object PartnerCallbacks {
    fun <A, B> guard(name: String, callback: (A, B) -> Unit): (A, B) -> Unit = { a, b ->
        try {
            callback(a, b)
        } catch (e: Exception) {
            Log.e(PayMEMiniApp.TAG, "partner $name callback failed: ${e.javaClass.simpleName}")
        }
    }
}
