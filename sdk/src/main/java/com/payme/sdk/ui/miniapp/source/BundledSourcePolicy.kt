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
     * SDK cũ không ghi bản đang cài; PREF_PATCH cũ không đáng tin (kill switch không reset nó).
     * Dùng patch đọc từ chính www ([wwwPatch], xem [parseWebPatch]): chỉ giữ khi mới hơn bundled;
     * không đọc được (0.7.x, không rõ) → chưa biết → cài lại bundled (OTA mới hơn sẽ được tải lại).
     */
    fun installedOrLegacy(recorded: InstalledSource?, legacyMode: String, wwwPatch: Int?, bundledPatch: Int): InstalledSource? =
        recorded ?: wwwPatch?.takeIf { it > bundledPatch }?.let { InstalledSource(SourceKind.OTA, it, legacyMode) }

    private val WEB_PATCH = Regex("patch:\"(\\d{3,5})\"")

    /** Patch ghi trong JS của web (patch:"0502"); không có hoặc nhiều giá trị khác nhau → null. */
    fun parseWebPatch(js: String): Int? =
        WEB_PATCH.findAll(js).map { it.groupValues[1].toInt() }.distinct().toList().singleOrNull()

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
