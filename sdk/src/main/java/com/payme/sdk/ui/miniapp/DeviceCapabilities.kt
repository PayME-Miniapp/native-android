package com.payme.sdk.ui.miniapp

/** Gửi web qua deviceInfo.capabilities để web biết native hỗ trợ gì (miniAppVersion là phiên bản dòng web, không dùng được). */
internal object DeviceCapabilities {
    /** jsPickContact → bộ chọn danh bạ hệ thống → nativePickedContact. */
    const val PICK_CONTACT = "PICK_CONTACT"

    val ALL: List<String> = listOf(PICK_CONTACT)
}
