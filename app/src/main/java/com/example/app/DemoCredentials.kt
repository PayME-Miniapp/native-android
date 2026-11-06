package com.example.app

internal fun hasDemoCredentials(appId: String, publicKey: String, privateKey: String): Boolean =
    appId.isNotBlank() && publicKey.isNotBlank() && privateKey.isNotBlank()

/** A pasted identityToken, or null when blank. Callers must never log or persist the value. */
internal fun identityTokenOrNull(input: CharSequence?): String? = input?.toString()?.trim()?.takeIf { it.isNotEmpty() }
