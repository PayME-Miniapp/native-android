package com.example.app

internal fun hasDemoCredentials(appId: String, publicKey: String, privateKey: String): Boolean =
    appId.isNotBlank() && publicKey.isNotBlank() && privateKey.isNotBlank()
