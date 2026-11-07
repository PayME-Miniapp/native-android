package com.payme.sdk.utils

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.view.ContextThemeWrapper

/**
 * Giao diện mini-app luôn sáng nhưng không được đổi chế độ tối của app partner
 * (AppCompatDelegate.setDefaultNightMode áp dụng cho cả process).
 * Bọc context: cấu hình uiMode = NIGHT_NO và theme sáng chỉ cho view/dialog của mini-app.
 */
internal object LightContext {
    fun lightUiMode(uiMode: Int): Int =
        (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO

    fun wrap(base: Context): Context {
        val config = Configuration(base.resources.configuration).apply { uiMode = lightUiMode(uiMode) }
        return ContextThemeWrapper(base, androidx.appcompat.R.style.Theme_AppCompat_Light).apply {
            applyOverrideConfiguration(config)
        }
    }
}
