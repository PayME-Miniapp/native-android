package com.payme.sdk.models

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.payme.sdk.PayMEMiniApp
import org.json.JSONObject

abstract class OpenMiniAppDataInterface (open val action: ActionOpenMiniApp = ActionOpenMiniApp.PAYME) {
    fun toJsonData(): JsonObject {
        val json = JsonObject()
        json.addProperty("action", action.toString())
        json.addProperty("appId", PayMEMiniApp.appId)
        json.addProperty("publicKey", PayMEMiniApp.publicKey)
        json.addProperty("privateKey", PayMEMiniApp.privateKey)
        json.addProperty("env", PayMEMiniApp.env.toString())
        json.addProperty("locale", PayMEMiniApp.locale.toString())
        return appendAdditionalData(json)
    }

    abstract fun appendAdditionalData(jsonObject: JsonObject): JsonObject
}

// service
data class OpenMiniAppServiceData (
    val phone: String,
    var additionalData: ServiceData
): OpenMiniAppDataInterface(ActionOpenMiniApp.SERVICE) {
    override fun appendAdditionalData(jsonObject: JsonObject): JsonObject {
        jsonObject.addProperty("service", additionalData.service)
        additionalData.extraData?.let { addExtraData(jsonObject, it) }
        jsonObject.addProperty("isBackToApp", additionalData.isBackToApp)
        jsonObject.addProperty("isShowResult", additionalData.isShowResult)
        jsonObject.addProperty("phone", phone)
        return jsonObject
    }
}

// kyc
data class OpenMiniAppKYCData (
    val phone: String,
): OpenMiniAppDataInterface(ActionOpenMiniApp.KYC) {
    override fun appendAdditionalData(jsonObject: JsonObject): JsonObject {
        jsonObject.addProperty("phone", phone)
        return jsonObject
    }
}

// deposit
data class OpenMiniAppDepositData (
    val phone: String,
    var additionalData: DepositWithdrawTransferData
): OpenMiniAppDataInterface(ActionOpenMiniApp.DEPOSIT) {
    override fun appendAdditionalData(jsonObject: JsonObject): JsonObject {
        jsonObject.addProperty("description", additionalData.description)
        jsonObject.addProperty("amount", additionalData.amount)
        jsonObject.addProperty("phone", phone)
        additionalData.extraData?.let { addExtraData(jsonObject, it) }
        jsonObject.addProperty("isBackToApp", additionalData.isBackToApp)
        jsonObject.addProperty("isShowResult", additionalData.isShowResult)
        return jsonObject
    }
}

// withdraw
data class OpenMiniAppWithdrawData (
    val phone: String,
    var additionalData: DepositWithdrawTransferData
): OpenMiniAppDataInterface(ActionOpenMiniApp.WITHDRAW) {
    override fun appendAdditionalData(jsonObject: JsonObject): JsonObject {
        jsonObject.addProperty("description", additionalData.description)
        jsonObject.addProperty("amount", additionalData.amount)
        jsonObject.addProperty("phone", phone)
        additionalData.extraData?.let { addExtraData(jsonObject, it) }
        jsonObject.addProperty("isBackToApp", additionalData.isBackToApp)
        jsonObject.addProperty("isShowResult", additionalData.isShowResult)
        return jsonObject
    }
}

// transfer
data class OpenMiniAppTransferData (
    val phone: String,
    var additionalData: DepositWithdrawTransferData
): OpenMiniAppDataInterface(ActionOpenMiniApp.TRANSFER) {
    override fun appendAdditionalData(jsonObject: JsonObject): JsonObject {
        jsonObject.addProperty("description", additionalData.description)
        jsonObject.addProperty("amount", additionalData.amount)
        jsonObject.addProperty("phone", phone)
        additionalData.extraData?.let { addExtraData(jsonObject, it) }
        jsonObject.addProperty("isBackToApp", additionalData.isBackToApp)
        jsonObject.addProperty("isShowResult", additionalData.isShowResult)
        return jsonObject
    }
}

// payment có chọn method
data class OpenMiniAppPaymentData (
    val phone: String,
    var paymentData: PaymentData
): OpenMiniAppDataInterface(ActionOpenMiniApp.PAY) {
    override fun appendAdditionalData(jsonObject: JsonObject): JsonObject {
        jsonObject.addProperty("transactionId", paymentData.transactionId)
        jsonObject.addProperty("amount", paymentData.amount)
        jsonObject.addProperty("phone", phone)
        jsonObject.addProperty("note", paymentData.note)
        jsonObject.addProperty("ipnUrl", paymentData.ipnUrl)
        jsonObject.addProperty("isShowResult", paymentData.isShowResult)
        paymentData.extraData?.let { addExtraData(jsonObject, it) }
        return jsonObject
    }
}

// payment ko chọn method
data class OpenMiniAppPaymentDirectData (
    val phone: String,
    var paymentDirectData: PaymentDirectData
): OpenMiniAppDataInterface(ActionOpenMiniApp.PAYMENT) {
    override fun appendAdditionalData(jsonObject: JsonObject): JsonObject {
        jsonObject.addProperty("transaction", paymentDirectData.transaction)
        paymentDirectData.extraData?.let { addExtraData(jsonObject, it) }
        jsonObject.addProperty("isShowResult", paymentDirectData.isShowResult)
        jsonObject.addProperty("phone", phone)
        return jsonObject
    }
}

// quét QR chuyển tiền
data class OpenMiniAppTransferQRData (
    val phone: String,
    var transferQRData: TransferQRData
): OpenMiniAppDataInterface(ActionOpenMiniApp.TRANSFER_QR) {
    override fun appendAdditionalData(jsonObject: JsonObject): JsonObject {
        jsonObject.addProperty("amount", transferQRData.amount)
        jsonObject.addProperty("bankNumber", transferQRData.bankNumber)
        jsonObject.addProperty("swiftCode", transferQRData.swiftCode)
        jsonObject.addProperty("cardHolder", transferQRData.cardHolder)
        jsonObject.addProperty("note", transferQRData.note)
        transferQRData.extraData?.let { addExtraData(jsonObject, it) }
        jsonObject.addProperty("isShowResult", transferQRData.isShowResult)
        jsonObject.addProperty("phone", phone)
        return jsonObject
    }
}

// vi payme
class OpenMiniAppPayMEData (): OpenMiniAppDataInterface(ActionOpenMiniApp.PAYME) {
    override fun appendAdditionalData(jsonObject: JsonObject): JsonObject {
        return jsonObject
    }
}

// open wallet
data class OpenMiniAppOpenData (
    val phone: String,
): OpenMiniAppDataInterface(ActionOpenMiniApp.OPEN) {
    override fun appendAdditionalData(jsonObject: JsonObject): JsonObject {
        jsonObject.addProperty("phone", phone)
        return jsonObject
    }
}

data class PaymentData(
    val transactionId: String,
    val amount: Int,
    val note: String?,
    val ipnUrl: String?,
    val extraData: Map<String, Any>?,
    val isShowResult: Boolean?
)

data class PaymentDirectData(
    val transaction: String,
    val extraData: Map<String, Any>?,
    val isShowResult: Boolean?
)

data class TransferQRData(
    val amount: Int,
    val bankNumber: String,
    val swiftCode: String,
    val cardHolder: String,
    val note: String?,
    val extraData: Map<String, Any>?,
    val isShowResult: Boolean?
)

data class DepositWithdrawTransferData(
    val description: String?,
    val amount: Int?,
    val extraData: Map<String, Any>?,
    val isBackToApp: Boolean?,
    val isShowResult: Boolean?
)

data class ServiceData(
    val service: String,
    val extraData: Map<String, Any>?,
    val isBackToApp: Boolean?,
    val isShowResult: Boolean?
)

enum class ENV {
    PRODUCTION, STAGING, SANDBOX, DEV, LOCAL
}

enum class Locale {
    vi, en
}

private fun addExtraData(jsonObject: JsonObject, extraData: Map<String, Any>) {
    val extraDataObject = JsonObject().apply {
        extraData.forEach { (key, value) ->
            when (value) {
                is Int -> addProperty(key, value)
                is String -> addProperty(key, value.toString())
                is Map<*, *> -> add(key, JsonObject().apply {
                    value.forEach { (nestedKey, nestedValue) ->
                        addProperty(nestedKey.toString(), nestedValue.toString())
                    }
                })
                else -> addProperty(key, value.toString())
            }
        }
    }
    jsonObject.add("extraData", extraDataObject)
}