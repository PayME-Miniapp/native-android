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
        fun create(env: ENV, locale: Locale): PayMEConfig =
            create(EmbeddedAppKey.APP_ID, EmbeddedAppKey.publicKey, EmbeddedAppKey.privateKey, env, locale)

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

        // Bỏ xuống dòng thật lẫn "\n"/"\r" dạng chữ (dấu "\" và chữ "n"): partner viết khóa trong chuỗi raw
        // ("""...\n...""") thì "\n" là chữ; để nguyên, web báo "Invalid PEM formatted message".
        internal fun sanitizeKey(value: String): String {
            return value.trim()
                .replace("\\r", "")
                .replace("\\n", "")
                .replace("  ", "")
                .replace("\r", "")
                .replace("\n", "")
        }
    }
}
