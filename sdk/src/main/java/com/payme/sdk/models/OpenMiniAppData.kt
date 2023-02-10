package com.payme.sdk.models

import com.payme.sdk.PayMEMiniApp
import org.json.JSONObject

abstract class OpenMiniAppDataInterface (open val action: ActionOpenMiniApp = ActionOpenMiniApp.PAYME, open val phone: String) {
    fun toJsonData(): JSONObject {
        val json = JSONObject()
        json.put("action", action)
        json.put("appId", PayMEMiniApp.appId)
        json.put("phone", phone)
        json.put("publicKey", PayMEMiniApp.publicKey)
        json.put("privateKey", PayMEMiniApp.privateKey)
        json.put("env", PayMEMiniApp.env)
        return appendAdditionalData(json)
    }
    abstract fun appendAdditionalData(jsonObject: JSONObject): JSONObject
}

data class OpenMiniAppPaymentData (
    override val phone: String,
    var paymentData: PaymentData
): OpenMiniAppDataInterface(ActionOpenMiniApp.PAY, phone) {
    override fun appendAdditionalData(jsonObject: JSONObject): JSONObject {
        jsonObject.put("transactionId", paymentData.transactionId)
        jsonObject.put("amount", paymentData.amount)
        jsonObject.put("note", paymentData.note)
        jsonObject.put("ipnUrl", paymentData.ipnUrl)
        return jsonObject
    }
}

data class OpenMiniAppPayMEData (
    override val phone: String,
    var paymentData: PaymentData
): OpenMiniAppDataInterface(ActionOpenMiniApp.PAYME, phone) {
    override fun appendAdditionalData(jsonObject: JSONObject): JSONObject {
        return jsonObject
    }
}

data class OpenMiniAppOpenData (
    override val action: ActionOpenMiniApp = ActionOpenMiniApp.OPEN,
    override val phone: String,
    var paymentData: PaymentData
): OpenMiniAppDataInterface(ActionOpenMiniApp.OPEN, phone) {
    override fun appendAdditionalData(jsonObject: JSONObject): JSONObject {
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