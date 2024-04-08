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
        json.put("locale", PayMEMiniApp.locale)
        return appendAdditionalData(json)
    }
    abstract fun appendAdditionalData(jsonObject: JSONObject): JSONObject
}

// service
data class OpenMiniAppServiceData (
    val phone: String,
    var additionalData: ServiceData
): OpenMiniAppDataInterface(ActionOpenMiniApp.SERVICE) {
    override fun appendAdditionalData(jsonObject: JSONObject): JSONObject {
        jsonObject.put("service", additionalData.service)
        jsonObject.put("isBackToApp", additionalData.isBackToApp)
        jsonObject.put("isShowResult", additionalData.isShowResult)
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
        jsonObject.put("isBackToApp", additionalData.isBackToApp)
        jsonObject.put("isShowResult", additionalData.isShowResult)
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
        jsonObject.put("isBackToApp", additionalData.isBackToApp)
        jsonObject.put("isShowResult", additionalData.isShowResult)
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
        jsonObject.put("isBackToApp", additionalData.isBackToApp)
        jsonObject.put("isShowResult", additionalData.isShowResult)
        return jsonObject
    }
}

// payment có chọn method
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
        jsonObject.put("isShowResult", paymentData.isShowResult)
        return jsonObject
    }
}

// payment ko chọn method
data class OpenMiniAppPaymentDirectData (
    val phone: String,
    var paymentDirectData: PaymentDirectData
): OpenMiniAppDataInterface(ActionOpenMiniApp.PAYMENT) {
    override fun appendAdditionalData(jsonObject: JSONObject): JSONObject {
        jsonObject.put("transaction", paymentDirectData.transaction)
        jsonObject.put("isShowResult", paymentDirectData.isShowResult)
        jsonObject.put("phone", phone)
        return jsonObject
    }
}

// quét QR chuyển tiền
data class OpenMiniAppTransferQRData (
    val phone: String,
    var transferQRData: TransferQRData
): OpenMiniAppDataInterface(ActionOpenMiniApp.TRANSFER_QR) {
    override fun appendAdditionalData(jsonObject: JSONObject): JSONObject {
        jsonObject.put("amount", transferQRData.amount)
        jsonObject.put("bankNumber", transferQRData.bankNumber)
        jsonObject.put("swiftCode", transferQRData.swiftCode)
        jsonObject.put("cardHolder", transferQRData.cardHolder)
        jsonObject.put("note", transferQRData.note)
        jsonObject.put("isShowResult", transferQRData.isShowResult)
        jsonObject.put("phone", phone)
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
    val ipnUrl: String?,
    val isShowResult: Boolean?
)

data class PaymentDirectData(
    val transaction: String,
    val isShowResult: Boolean?
)

data class TransferQRData(
    val amount: Int,
    val bankNumber: String,
    val swiftCode: String,
    val cardHolder: String,
    val note: String?,
    val isShowResult: Boolean?
)

data class DepositWithdrawTransferData(
    val description: String?,
    val amount: Int?,
    val isBackToApp: Boolean?,
    val isShowResult: Boolean?
)

data class ServiceData(
    val service: String,
    val isBackToApp: Boolean?,
    val isShowResult: Boolean?
)

enum class ENV {
    PRODUCTION, STAGING, SANDBOX, DEV, LOCAL
}

enum class Locale {
    vi, en
}