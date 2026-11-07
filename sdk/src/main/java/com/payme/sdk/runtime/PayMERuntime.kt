package com.payme.sdk.runtime

import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.OpenMiniAppDataInterface
import com.payme.sdk.models.OpenMiniAppType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal object PayMERuntime {
    const val DEFAULT_MODE = "miniapp_product"
    const val EXTRA_SESSION_ID = "com.payme.sdk.extra.SESSION_ID"

    private val sessions = ConcurrentHashMap<String, MiniAppSession>()
    private val callbacks = PayMECallbacks()
    private var defaultConfig: PayMEConfig? = null
    private var activeSessionId: String? = null
    // Đồng hồ cho hạn khởi động host; test thay được
    internal var clock: () -> Long = { android.os.SystemClock.elapsedRealtime() }

    fun initialize(config: PayMEConfig) {
        defaultConfig = config
    }

    fun requireConfig(): PayMEConfig {
        return defaultConfig ?: error("PayMEMiniApp instance is not initialized")
    }

    fun currentConfig(): PayMEConfig? {
        return activeSession()?.config ?: defaultConfig
    }

    fun callbacks(): PayMECallbacks = callbacks

    fun updateConfigs(update: (PayMEConfig) -> Unit) {
        defaultConfig?.let(update)
        activeSession()?.config?.let(update)
    }

    fun updateCallbacks(update: (PayMECallbacks) -> Unit) {
        update(callbacks)
        activeSession()?.callbacks?.let(update)
    }

    /** [config]/[callbacks]: mặc định chụp từ cấu hình chung; KYC từ modal truyền bản sao của phiên cũ. */
    fun createSession(
        openType: OpenMiniAppType,
        openMiniAppData: OpenMiniAppDataInterface,
        config: PayMEConfig = requireConfig().copy(),
        callbacks: PayMECallbacks = this.callbacks.copy()
    ): MiniAppSession {
        val session = MiniAppSession(
            id = UUID.randomUUID().toString(),
            config = config,
            callbacks = callbacks,
            openType = openType,
            openMiniAppData = openMiniAppData,
            createdAt = clock()
        )
        sessions[session.id] = session
        activeSessionId = session.id
        syncLegacyOpenState()
        return session
    }

    fun getSession(sessionId: String?): MiniAppSession? {
        if (sessionId.isNullOrEmpty()) return null
        return sessions[sessionId]
    }

    fun activeSession(): MiniAppSession? {
        return getSession(activeSessionId)
    }

    fun activate(sessionId: String?) {
        if (!sessionId.isNullOrEmpty() && sessions.containsKey(sessionId)) {
            activeSessionId = sessionId
        }
    }

    fun closeSession(sessionId: String?) {
        val session = getSession(sessionId) ?: return
        session.close()
        syncLegacyOpenState()
    }

    /**
     * Đặt trước khi gọi callback đóng (USER_CANCEL, isCloseMiniApp, close()): partner mở lại ngay trong callback
     * không bị MINIAPP_ALREADY_OPEN. Phiên vẫn isOpen để session.close() còn đóng được nơi chứa.
     */
    fun markCloseRequested(session: MiniAppSession) {
        session.isCloseRequested = true
        syncLegacyOpenState()
    }

    fun markSessionClosed(sessionId: String?) {
        getSession(sessionId)?.isOpen = false
        syncLegacyOpenState()
    }

    fun removeSession(sessionId: String?) {
        if (sessionId.isNullOrEmpty()) return
        sessions.remove(sessionId)
        if (activeSessionId == sessionId) {
            activeSessionId = sessions.keys.firstOrNull()
        }
        syncLegacyOpenState()
    }

    /**
     * Nơi chứa mini-app (modal hoặc MiniAppActivity) bị đóng/huỷ hẳn: xoá phiên, báo USER_CANCEL
     * nếu chưa có lệnh đóng hay kết quả đóng nào. Gọi lại nhiều lần chỉ có tác dụng lần đầu.
     */
    fun endSessionFromHost(sessionId: String?, cancelDescription: () -> String) {
        val session = getSession(sessionId) ?: return
        val shouldNotifyCancel = session.shouldNotifyUserCancelOnDismiss()
        // Đánh dấu đang đóng: kết quả web tới muộn bị bỏ qua, partner mở lại trong USER_CANCEL được
        session.isCloseRequested = true
        session.closeAction = null
        markSessionClosed(session.id)
        try {
            if (shouldNotifyCancel) session.notifyUserCancel(cancelDescription())
        } finally {
            removeSession(session.id)
        }
    }

    /** Phiên còn "mở" nhưng host đã mất mà không dọn: dọn (USER_CANCEL nếu chưa có kết quả đóng) để mở mới được. */
    fun clearStaleSessions(cancelDescription: () -> String) {
        val now = clock()
        sessions.values.filter { it.isOpen && !it.isHostAlive(now) }
            .forEach { endSessionFromHost(it.id, cancelDescription) }
    }

    fun isOpen(): Boolean {
        // Phiên đang đóng (đã có lệnh đóng) không chặn lần mở mới
        return sessions.values.any { it.isOpen && !it.isCloseRequested }
    }

    fun syncLegacyOpenState() {
        PayMEMiniApp.isOpen = isOpen()
    }
}
