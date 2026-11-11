package com.example.app

/** A pasted identityToken, or null when blank. Callers must never log or persist the value. */
internal fun identityTokenOrNull(input: CharSequence?): String? = input?.toString()?.trim()?.takeIf { it.isNotEmpty() }
