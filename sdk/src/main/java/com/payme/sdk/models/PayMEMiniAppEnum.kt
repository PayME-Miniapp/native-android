package com.payme.sdk.models

enum class ActionOpenMiniApp {
    PAYME, OPEN, PAY, GET_BALANCE, SERVICE, DEPOSIT, WITHDRAW, TRANSFER, KYC
}

enum class OpenMiniAppType {
    screen, modal
}

val PayMEEcosystem = arrayOf("app", "264245066910", "250069027220")
