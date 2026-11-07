package com.payme.sdk.runtime

/** Quyết định khi nơi chứa mini-app bị huỷ: giữ phiên (đang dựng lại) hay kết thúc phiên (USER_CANCEL). */
internal object HostLifecycle {
    /**
     * Modal onDismiss: view bị huỷ mà fragment không bị gỡ và Activity partner không finish
     * → Activity đang dựng lại (xoay, đổi cấu hình, Don't keep activities): giữ phiên.
     */
    fun isModalHostRecreating(isDestroyingView: Boolean, isRemoving: Boolean, isActivityFinishing: Boolean): Boolean =
        isDestroyingView && !isRemoving && !isActivityFinishing

    /** MiniAppActivity.onDestroy: chỉ kết thúc phiên khi Activity đóng hẳn (không phải hệ thống huỷ tạm). */
    fun shouldEndSessionOnActivityDestroy(isFinishing: Boolean): Boolean = isFinishing
}
