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

    fun createSession(
        openType: OpenMiniAppType,
        openMiniAppData: OpenMiniAppDataInterface
    ): MiniAppSession {
        val configSnapshot = requireConfig().copy()
        val callbackSnapshot = callbacks.copy()
        val session = MiniAppSession(
            id = UUID.randomUUID().toString(),
            config = configSnapshot,
            callbacks = callbackSnapshot,
            openType = openType,
            openMiniAppData = openMiniAppData
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

    fun isOpen(): Boolean {
        return sessions.values.any { it.isOpen }
    }

    fun syncLegacyOpenState() {
        PayMEMiniApp.isOpen = isOpen()
    }
}
