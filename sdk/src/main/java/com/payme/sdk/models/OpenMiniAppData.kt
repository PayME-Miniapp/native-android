package com.payme.sdk.models

import org.json.JSONObject

data class OpenMiniAppData(
    val action: ActionOpenMiniApp = ActionOpenMiniApp.PAYME,
    val appId: String,
    val phone: String,
    val publicKey: String,
    val privateKey: String,
    val env: ENV,
    var paymentData: PaymentData? = null
) {
    init {
        if (action != ActionOpenMiniApp.PAY) {
            paymentData = null
        } else {
            requireNotNull(paymentData)
        }
    }

    fun toJsonData(): JSONObject {
        val json = JSONObject()
        json.put("action", action)
        json.put("appId", appId)
        json.put("phone", phone)
        json.put("publicKey", publicKey)
        json.put("privateKey", privateKey)
        json.put("env", env)
        if (action == ActionOpenMiniApp.PAY && paymentData != null) {
            json.put("transactionId", paymentData!!.transactionId)
            json.put("amount", paymentData!!.amount)
            json.put("note", paymentData!!.note)
//            json.put("ipnUrl", paymentData!!.ipnUrl)
        }
        return json
    }
}

data class PaymentData(
    val transactionId: String,
    val amount: Int,
    val note: String?,
    val ipnUrl: String
)

enum class ENV {
    LOCAL, PRODUCTION, SANDBOX, DEV
}