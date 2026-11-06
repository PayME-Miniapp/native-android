package com.payme.sdk.models

import com.google.gson.JsonObject
import com.payme.sdk.runtime.PayMEConfig
import com.payme.sdk.runtime.PayMERuntime

abstract class OpenMiniAppDataInterface(open val action: ActionOpenMiniApp = ActionOpenMiniApp.PAYME) {
    fun toJsonData(): JsonObject {
        return toJsonData(PayMERuntime.requireConfig())
    }

    internal fun toJsonData(config: PayMEConfig): JsonObject {
        val json = JsonObject()
        json.addProperty("action", action.toString())
        json.addProperty("appId", config.appId)
        json.addProperty("publicKey", config.publicKey)
        json.addProperty("privateKey", config.privateKey)
        json.addProperty("env", config.env.toString())
        json.addProperty("locale", config.locale.toString())
        return appendAdditionalData(json)
    }

    abstract fun appendAdditionalData(jsonObject: JsonObject): JsonObject

    /**
     * Final, so data classes do not generate a toString() that prints identityToken (or any other field).
     * identityToken is memory-only: never log, persist or put it in a URL.
     */
    final override fun toString(): String = "${this::class.simpleName}(action=$action)"
}

// service
data class OpenMiniAppServiceData(
    val identityToken: String,
    var additionalData: ServiceData
) : OpenMiniAppDataInterface(ActionOpenMiniApp.SERVICE) {
    override fun appendAdditionalData(jsonObject: JsonObject): JsonObject {
        jsonObject.addProperty("service", additionalData.service)
        additionalData.extraData?.let { addExtraData(jsonObject, it) }
        jsonObject.addProperty("isBackToApp", additionalData.isBackToApp)
        jsonObject.addProperty("isShowResult", additionalData.isShowResult)
        addIdentityToken(jsonObject, identityToken)
        return jsonObject
    }
}

// kyc
data class OpenMiniAppKYCData(
    val identityToken: String,
) : OpenMiniAppDataInterface(ActionOpenMiniApp.KYC) {
    override fun appendAdditionalData(jsonObject: JsonObject): JsonObject {
        addIdentityToken(jsonObject, identityToken)
        return jsonObject
    }
}

// deposit
data class OpenMiniAppDepositData(
    val identityToken: String,
    var additionalData: DepositWithdrawTransferData
) : OpenMiniAppDataInterface(ActionOpenMiniApp.DEPOSIT) {
    override fun appendAdditionalData(jsonObject: JsonObject): JsonObject {
        jsonObject.addProperty("description", additionalData.description)
        jsonObject.addProperty("amount", additionalData.amount)
        addIdentityToken(jsonObject, identityToken)
        additionalData.extraData?.let { addExtraData(jsonObject, it) }
        jsonObject.addProperty("isBackToApp", additionalData.isBackToApp)
        jsonObject.addProperty("isShowResult", additionalData.isShowResult)
        return jsonObject
    }
}

// withdraw
data class OpenMiniAppWithdrawData(
    val identityToken: String,
    var additionalData: DepositWithdrawTransferData
) : OpenMiniAppDataInterface(ActionOpenMiniApp.WITHDRAW) {
    override fun appendAdditionalData(jsonObject: JsonObject): JsonObject {
        jsonObject.addProperty("description", additionalData.description)
        jsonObject.addProperty("amount", additionalData.amount)
        addIdentityToken(jsonObject, identityToken)
        additionalData.extraData?.let { addExtraData(jsonObject, it) }
        jsonObject.addProperty("isBackToApp", additionalData.isBackToApp)
        jsonObject.addProperty("isShowResult", additionalData.isShowResult)
        return jsonObject
    }
}

// transfer
data class OpenMiniAppTransferData(
    val identityToken: String,
    var additionalData: DepositWithdrawTransferData
) : OpenMiniAppDataInterface(ActionOpenMiniApp.TRANSFER) {
    override fun appendAdditionalData(jsonObject: JsonObject): JsonObject {
        jsonObject.addProperty("description", additionalData.description)
        jsonObject.addProperty("amount", additionalData.amount)
        addIdentityToken(jsonObject, identityToken)
        additionalData.extraData?.let { addExtraData(jsonObject, it) }
        jsonObject.addProperty("isBackToApp", additionalData.isBackToApp)
        jsonObject.addProperty("isShowResult", additionalData.isShowResult)
        return jsonObject
    }
}

// payment có chọn method
data class OpenMiniAppPaymentData(
    val identityToken: String,
    var paymentData: PaymentData
) : OpenMiniAppDataInterface(ActionOpenMiniApp.PAY) {
    override fun appendAdditionalData(jsonObject: JsonObject): JsonObject {
        jsonObject.addProperty("transactionId", paymentData.transactionId)
        jsonObject.addProperty("amount", paymentData.amount)
        addIdentityToken(jsonObject, identityToken)
        jsonObject.addProperty("note", paymentData.note)
        jsonObject.addProperty("ipnUrl", paymentData.ipnUrl)
        jsonObject.addProperty("isShowResult", paymentData.isShowResult)
        paymentData.extraData?.let { addExtraData(jsonObject, it) }
        return jsonObject
    }
}

// payment ko chọn method
data class OpenMiniAppPaymentDirectData(
    val identityToken: String? = null,
    var paymentDirectData: PaymentDirectData
) : OpenMiniAppDataInterface(ActionOpenMiniApp.PAYMENT) {
    override fun appendAdditionalData(jsonObject: JsonObject): JsonObject {
        jsonObject.addProperty("transaction", paymentDirectData.transaction)
        paymentDirectData.extraData?.let { addExtraData(jsonObject, it) }
        jsonObject.addProperty("isShowResult", paymentDirectData.isShowResult)
        addIdentityToken(jsonObject, identityToken)
        return jsonObject
    }
}

// quét QR chuyển tiền
data class OpenMiniAppTransferQRData(
    val identityToken: String,
    var transferQRData: TransferQRData
) : OpenMiniAppDataInterface(ActionOpenMiniApp.TRANSFER_QR) {
    override fun appendAdditionalData(jsonObject: JsonObject): JsonObject {
        jsonObject.addProperty("amount", transferQRData.amount)
        jsonObject.addProperty("bankNumber", transferQRData.bankNumber)
        jsonObject.addProperty("swiftCode", transferQRData.swiftCode)
        jsonObject.addProperty("cardHolder", transferQRData.cardHolder)
        jsonObject.addProperty("note", transferQRData.note)
        jsonObject.addProperty("partnerTransaction", transferQRData.partnerTransaction)
        transferQRData.extraData?.let { addExtraData(jsonObject, it) }
        jsonObject.addProperty("isShowResult", transferQRData.isShowResult)
        addIdentityToken(jsonObject, identityToken)
        return jsonObject
    }
}

// vi payme
class OpenMiniAppPayMEData() : OpenMiniAppDataInterface(ActionOpenMiniApp.PAYME) {
    override fun appendAdditionalData(jsonObject: JsonObject): JsonObject {
        return jsonObject
    }
}

// open wallet
data class OpenMiniAppOpenData(
    val identityToken: String,
) : OpenMiniAppDataInterface(ActionOpenMiniApp.OPEN) {
    override fun appendAdditionalData(jsonObject: JsonObject): JsonObject {
        addIdentityToken(jsonObject, identityToken)
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
    val partnerTransaction: String?,
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

internal fun getIdentityTokenFromOpenMiniAppData(openMiniAppData: OpenMiniAppDataInterface): String? {
    return when (openMiniAppData) {
        is OpenMiniAppServiceData -> openMiniAppData.identityToken
        is OpenMiniAppKYCData -> openMiniAppData.identityToken
        is OpenMiniAppDepositData -> openMiniAppData.identityToken
        is OpenMiniAppWithdrawData -> openMiniAppData.identityToken
        is OpenMiniAppTransferData -> openMiniAppData.identityToken
        is OpenMiniAppPaymentData -> openMiniAppData.identityToken
        is OpenMiniAppPaymentDirectData -> openMiniAppData.identityToken
        is OpenMiniAppTransferQRData -> openMiniAppData.identityToken
        is OpenMiniAppOpenData -> openMiniAppData.identityToken
        else -> null
    }?.takeIf { it.isNotEmpty() }
}

/** identityToken goes to the web bootstrap payload only when present (optional for PAYMENT). */
private fun addIdentityToken(jsonObject: JsonObject, identityToken: String?) {
    if (!identityToken.isNullOrEmpty()) jsonObject.addProperty("identityToken", identityToken)
}

private fun addExtraData(jsonObject: JsonObject, extraData: Map<String, Any?>) {
    val extraDataObject = JsonObject().apply {
        extraData.forEach { (key, value) ->
            if (value != null) {
                addPropertyRecursively(this, key, value)
            }
        }
    }
    jsonObject.add("extraData", extraDataObject)
}

private fun addPropertyRecursively(jsonObject: JsonObject, key: String, value: Any) {
    when (value) {
        is Boolean -> jsonObject.addProperty(key, value)
        is Int -> jsonObject.addProperty(key, value)
        is String -> jsonObject.addProperty(key, value)
        is Map<*, *> -> {
            val nestedObject = JsonObject()
            value.forEach { (nestedKey, nestedValue) ->
                if (nestedKey is String && nestedValue != null) {
                    addPropertyRecursively(nestedObject, nestedKey, nestedValue)
                }
            }
            jsonObject.add(key, nestedObject)
        }

        else -> jsonObject.addProperty(key, value.toString())
    }
}
