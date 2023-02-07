package com.payme.sdk.models

data class OpenMiniAppData(
    val action: ActionOpenMiniApp = ActionOpenMiniApp.PAYME,
    val appId: String,
    val phone: String,
    val publicKey: String,
    val privateKey: String,
    var paymentData: PaymentData? = null
) {
    init {
        if (action != ActionOpenMiniApp.PAY) {
            paymentData = null
        } else {
            requireNotNull(paymentData)
        }
    }
}

data class PaymentData(
    val transactionId: String,
    val amount: Int,
    val note: String?,
    val ipnUrl: String
)
