package com.payme.sdk.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import com.payme.sdk.models.Locale
import com.payme.sdk.runtime.PayMERuntime

/**
 * Ngôn ngữ của mini-app chỉ áp dụng cho UI/chuỗi của SDK: không đổi Locale mặc định
 * hay Resources của app partner (giống LightContext với chế độ tối).
 */
internal object SdkLocale {
    fun current(): Locale = PayMERuntime.currentConfig()?.locale ?: Locale.vi

    fun javaLocale(locale: Locale): java.util.Locale = when (locale) {
        Locale.vi -> java.util.Locale("vi", "VN")
        Locale.en -> java.util.Locale("en", "US")
    }

    fun apply(config: Configuration, locale: Locale = current()): Configuration =
        config.apply { setLocale(javaLocale(locale)) }

    /** Configuration override rỗng (mọi trường "không đổi"); fontScale = 0 để không ghim cỡ chữ. */
    fun emptyOverride(): Configuration = Configuration().apply { fontScale = 0f }

    /** Cho attachBaseContext của Activity SDK. Delta (chỉ ngôn ngữ): các trường khác vẫn theo cấu hình hiện tại. */
    fun configurationContext(base: Context, locale: Locale = current()): Context =
        base.createConfigurationContext(apply(emptyOverride(), locale))

    /** Bọc context của partner: chỉ Resources đổi ngôn ngữ, mọi thứ khác (Activity, service...) giữ nguyên. */
    fun wrap(base: Context, locale: Locale = current()): Context = try {
        LocalizedContext(base, configurationContext(base, locale).resources)
    } catch (e: Exception) {
        // Không được làm crash app partner chỉ vì ngôn ngữ
        base
    }

    private class LocalizedContext(base: Context, private val localized: Resources) : ContextWrapper(base) {
        override fun getResources(): Resources = localized
    }
}
