package com.payme.sdk.ui.miniapp.source

internal enum class SourceKind(val pref: String) {
    BUNDLED("bundled"), OTA("ota");

    companion object {
        fun fromPref(value: String?): SourceKind? = entries.firstOrNull { it.pref == value }
    }
}

/** Bản web đang cài trong www ([mode] chỉ có nghĩa với OTA: mỗi mode có bản OTA riêng, www thì dùng chung). */
internal data class InstalledSource(val kind: SourceKind, val patch: Int, val mode: String = "")

/**
 * Khi nào cài lại web đóng gói (bundled): nâng cấp SDK phải nhận bundled mới thay vì chạy mãi www cũ,
 * nhưng không được hạ cấp một bản OTA mới hơn bundled.
 */
internal object BundledSourcePolicy {
    /**
     * SDK cũ không ghi bản đang cài. PREF_PATCH cũ > bundled chỉ có thể là OTA đã cài (giữ lại);
     * còn lại (0.7.x OTA 0411, bundled 0500/0501, không rõ) coi như chưa biết → cài lại bundled.
     */
    fun installedOrLegacy(recorded: InstalledSource?, legacyMode: String, legacyPatch: Int, bundledPatch: Int): InstalledSource? =
        recorded ?: InstalledSource(SourceKind.OTA, legacyPatch, legacyMode).takeIf { legacyPatch > bundledPatch }

    /** [forceBundled]: manifest kill switch (patch 0) yêu cầu chạy bundled. */
    fun shouldInstallBundled(
        hasWebRoot: Boolean,
        installed: InstalledSource?,
        bundledPatch: Int,
        currentMode: String,
        forceBundled: Boolean = false
    ): Boolean = when {
        !hasWebRoot || installed == null -> true
        installed.kind == SourceKind.BUNDLED -> installed.patch != bundledPatch
        forceBundled -> true
        // OTA của mode khác (đổi setMode): về bundled, OTA của mode này (nếu mới hơn) tải sau
        installed.mode != currentMode -> true
        else -> installed.patch < bundledPatch
    }

    /** OTA chỉ có ích khi mới hơn cả bản đã cài lẫn bundled. */
    fun effectiveLocalPatch(localPatch: Int, bundledPatch: Int): Int = maxOf(localPatch, bundledPatch)
}
