package com.payme.sdk.models

import com.payme.sdk.PayMEMiniApp
import org.json.JSONObject

abstract class OpenMiniAppDataInterface (open val action: ActionOpenMiniApp = ActionOpenMiniApp.PAYME) {
    fun toJsonData(): JSONObject {
        val json = JSONObject()
        json.put("action", action)
        json.put("appId", PayMEMiniApp.appId)
        json.put("publicKey", PayMEMiniApp.publicKey)
        json.put("privateKey", PayMEMiniApp.privateKey)
        json.put("env", PayMEMiniApp.env)
        return appendAdditionalData(json)
    }
    abstract fun appendAdditionalData(jsonObject: JSONObject): JSONObject
}

data class OpenMiniAppPaymentData (
    val phone: String,
    var paymentData: PaymentData
): OpenMiniAppDataInterface(ActionOpenMiniApp.PAY) {
    override fun appendAdditionalData(jsonObject: JSONObject): JSONObject {
        jsonObject.put("transactionId", paymentData.transactionId)
        jsonObject.put("amount", paymentData.amount)
        jsonObject.put("phone", phone)
        jsonObject.put("note", paymentData.note)
        jsonObject.put("ipnUrl", paymentData.ipnUrl)
        return jsonObject
    }
}

// vi payme
data class OpenMiniAppPayMEData (
    var paymentData: PaymentData
): OpenMiniAppDataInterface(ActionOpenMiniApp.PAYME) {
    override fun appendAdditionalData(jsonObject: JSONObject): JSONObject {
        return jsonObject
    }
}

// open wallet
data class OpenMiniAppOpenData (
    val phone: String,
): OpenMiniAppDataInterface(ActionOpenMiniApp.OPEN) {
    override fun appendAdditionalData(jsonObject: JSONObject): JSONObject {
        jsonObject.put("phone", phone)
        return jsonObject
    }
}

data class PaymentData(
    val transactionId: String,
    val amount: Int,
    val note: String?,
    val ipnUrl: String?
)

enum class ENV {
    LOCAL, PRODUCTION, SANDBOX, DEV
}