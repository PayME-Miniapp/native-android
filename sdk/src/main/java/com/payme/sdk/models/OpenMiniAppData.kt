package com.payme.sdk.models

data class OpenMiniAppData(
    val action: ActionOpenMiniApp = ActionOpenMiniApp.PayME,
    val appId: String,
    val phone: String,
    val publicKey: String,
    val privateKey: String
)
