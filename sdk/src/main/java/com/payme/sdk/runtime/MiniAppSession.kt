package com.payme.sdk.runtime

import com.payme.sdk.models.ActionOpenMiniApp
import com.payme.sdk.models.OpenMiniAppDataInterface
import com.payme.sdk.models.OpenMiniAppType
import com.payme.sdk.models.PayMEError
import com.payme.sdk.models.PayMEErrorType
import java.lang.ref.WeakReference

internal class MiniAppSession(
    val id: String,
    val config: PayMEConfig,
    val callbacks: PayMECallbacks,
    val openType: OpenMiniAppType,
    val openMiniAppData: OpenMiniAppDataInterface,
    val createdAt: Long = 0L
) {
    // Nơi chứa (MiniAppActivity / dialog fragment): giữ yếu để không rò Activity khi phiên bị kẹt
    private var hostRef: WeakReference<Any>? = null
    private var hostAliveCheck: (Any) -> Boolean = { true }

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

    /** [isAlive] nhận host làm tham số, không được bắt (capture) host. */
    fun <T : Any> attachHost(host: T, isAlive: (T) -> Boolean) {
        hostRef = WeakReference(host)
        @Suppress("UNCHECKED_CAST")
        hostAliveCheck = { isAlive(it as T) }
    }

    /** Chưa có host thì coi là đang khởi động trong [startTimeoutMs]; quá hạn hoặc host đã huỷ là phiên kẹt. */
    fun isHostAlive(now: Long, startTimeoutMs: Long = HOST_START_TIMEOUT_MS): Boolean {
        val ref = hostRef ?: return now - createdAt < startTimeoutMs
        val host = ref.get() ?: return false
        return hostAliveCheck(host)
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        closeAction?.invoke()
    }

    companion object {
        const val USER_CANCEL_CODE = "USER_CANCEL"
        const val HOST_START_TIMEOUT_MS = 15_000L
    }
}
