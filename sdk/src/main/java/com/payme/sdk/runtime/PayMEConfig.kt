package com.payme.sdk.runtime

import com.payme.sdk.BuildConfig
import com.payme.sdk.models.ENV
import com.payme.sdk.models.Locale

internal data class PayMEConfig(
    var appId: String,
    var publicKey: String,
    var privateKey: String,
    var env: ENV = ENV.PRODUCTION,
    var locale: Locale = Locale.vi,
    var mode: String = PayMERuntime.DEFAULT_MODE,
    val sdkVersion: String = BuildConfig.SDK_VERSION,
    val buildNumber: Int = BuildConfig.BUILD_NUMBER
) {
    companion object {
        fun create(
            appId: String,
            publicKey: String,
            privateKey: String,
            env: ENV,
            locale: Locale
        ): PayMEConfig {
            return PayMEConfig(
                appId = appId,
                publicKey = sanitizeKey(publicKey),
                privateKey = sanitizeKey(privateKey),
                env = env,
                locale = locale
            )
        }

        private fun sanitizeKey(value: String): String {
            return value.trim().replace("  ", "").replace("\n", "")
        }
    }
}
