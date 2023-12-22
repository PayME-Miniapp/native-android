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

// service
data class OpenMiniAppServiceData (
    val phone: String,
    val service: String,
): OpenMiniAppDataInterface(ActionOpenMiniApp.SERVICE) {
    override fun appendAdditionalData(jsonObject: JSONObject): JSONObject {
        jsonObject.put("service", service)
        jsonObject.put("phone", phone)
        return jsonObject
    }
}

// kyc
data class OpenMiniAppKYCData (
    val phone: String,
): OpenMiniAppDataInterface(ActionOpenMiniApp.KYC) {
    override fun appendAdditionalData(jsonObject: JSONObject): JSONObject {
        jsonObject.put("phone", phone)
        return jsonObject
    }
}

// deposit
data class OpenMiniAppDepositData (
    val phone: String,
    var additionalData: DepositWithdrawTransferData
): OpenMiniAppDataInterface(ActionOpenMiniApp.DEPOSIT) {
    override fun appendAdditionalData(jsonObject: JSONObject): JSONObject {
        jsonObject.put("description", additionalData.description)
        jsonObject.put("amount", additionalData.amount)
        jsonObject.put("phone", phone)
        return jsonObject
    }
}

// withdraw
data class OpenMiniAppWithdrawData (
    val phone: String,
    var additionalData: DepositWithdrawTransferData
): OpenMiniAppDataInterface(ActionOpenMiniApp.WITHDRAW) {
    override fun appendAdditionalData(jsonObject: JSONObject): JSONObject {
        jsonObject.put("description", additionalData.description)
        jsonObject.put("amount", additionalData.amount)
        jsonObject.put("phone", phone)
        return jsonObject
    }
}

// transfer
data class OpenMiniAppTransferData (
    val phone: String,
    var additionalData: DepositWithdrawTransferData
): OpenMiniAppDataInterface(ActionOpenMiniApp.TRANSFER) {
    override fun appendAdditionalData(jsonObject: JSONObject): JSONObject {
        jsonObject.put("description", additionalData.description)
        jsonObject.put("amount", additionalData.amount)
        jsonObject.put("phone", phone)
        return jsonObject
    }
}

// payment
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
class OpenMiniAppPayMEData (): OpenMiniAppDataInterface(ActionOpenMiniApp.PAYME) {
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
data class DepositWithdrawTransferData(
    val description: String?,
    val amount: Int?,
)

enum class ENV {
    PRODUCTION, SANDBOX
}

enum class Locale {
    vi, en
}