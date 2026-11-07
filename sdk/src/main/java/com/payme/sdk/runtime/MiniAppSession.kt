package com.payme.sdk.runtime

import com.payme.sdk.models.ActionOpenMiniApp
import com.payme.sdk.models.OpenMiniAppDataInterface
import com.payme.sdk.models.OpenMiniAppType
import com.payme.sdk.models.PayMEError
import com.payme.sdk.models.PayMEErrorType

internal class MiniAppSession(
    val id: String,
    val config: PayMEConfig,
    val callbacks: PayMECallbacks,
    val openType: OpenMiniAppType,
    val openMiniAppData: OpenMiniAppDataInterface
) {
    var loadUrl: String = ""
    var webViewUrl: String = ""
    var modalHeight: Int = 0
    var onSetModalHeight: (Int) -> Unit = {}
    var closeAction: (() -> Unit)? = null
    var isOpen: Boolean = true
    var isCloseRequested: Boolean = false
    var isTerminalErrorHandled: Boolean = false
    private var isUserCancelNotified: Boolean = false

    val action: ActionOpenMiniApp
        get() = openMiniAppData.action

    /** Báo USER_CANCEL (type UserCancel) cho partner, tối đa một lần mỗi phiên. */
    fun notifyUserCancel(description: String): Boolean {
        if (isUserCancelNotified) return false
        isUserCancelNotified = true
        callbacks.onError(action, PayMEError(PayMEErrorType.UserCancel, USER_CANCEL_CODE, description))
        return true
    }

    /** Modal bị đóng (chạm ngoài/back): chỉ là người dùng huỷ khi chưa có lệnh đóng chủ động. */
    fun shouldNotifyUserCancelOnDismiss(): Boolean = !isCloseRequested && isOpen

    fun close() {
        if (!isOpen) return
        isOpen = false
        closeAction?.invoke()
    }

    companion object {
        const val USER_CANCEL_CODE = "USER_CANCEL"
    }
}
