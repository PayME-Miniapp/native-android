package com.payme.sdk.ui.miniapp

import android.content.Context
import android.content.Intent
import com.payme.sdk.ui.IdentityCardActivity
import org.json.JSONObject

internal object CardKycGateway {
    fun createIntent(context: Context, data: JSONObject): Intent {
        return Intent(context, IdentityCardActivity::class.java).apply {
            putExtra("title", data.optString("title", ""))
            putExtra("type", data.optString("type", "FRONT"))
            putExtra("description", data.optString("description", ""))
            putExtra("toastError", data.optString("toastError", ""))
        }
    }

    fun buildResponse(fileName: String?, type: String): JSONObject {
        return JSONObject().apply {
            put("image", fileName)
            put("type", type)
        }
    }
}
