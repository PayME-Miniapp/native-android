package com.example.sdk

import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.ENV
import com.payme.sdk.models.Locale
import com.payme.sdk.models.OpenMiniAppPayMEData
import com.payme.sdk.models.OpenMiniAppType
import com.payme.sdk.models.PayMEError
import com.payme.sdk.models.PayMEErrorType
import com.payme.sdk.runtime.MiniAppSession
import com.payme.sdk.runtime.PayMEConfig
import com.payme.sdk.runtime.PayMERuntime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** MINIAPP_ALREADY_OPEN không được chặn mãi vì một phiên mà host đã mất. */
class StaleSessionTest {
    private class Host(var alive: Boolean = true)

    private val errors = mutableListOf<PayMEError>()
    private var now = 1_000L
    private lateinit var session: MiniAppSession

    @Before
    fun setUp() {
        PayMERuntime.clock = { now }
        PayMERuntime.initialize(
            PayMEConfig(appId = "app", publicKey = "pub", privateKey = "priv", env = ENV.SANDBOX, locale = Locale.en)
        )
        PayMERuntime.callbacks().onError = { _, error -> errors.add(error) }
        session = PayMERuntime.createSession(OpenMiniAppType.modal, OpenMiniAppPayMEData())
    }

    @After
    fun tearDown() {
        PayMERuntime.removeSession(session.id)
        PayMERuntime.callbacks().onError = { _, _ -> }
        PayMERuntime.clock = { android.os.SystemClock.elapsedRealtime() }
        PayMEMiniApp.isOpen = false
    }

    @Test
    fun sessionWaitingForItsHostIsKeptDuringStart() {
        now += MiniAppSession.HOST_START_TIMEOUT_MS - 1

        PayMERuntime.clearStaleSessions { "cancel" }

        assertNotNull(PayMERuntime.getSession(session.id))
        assertTrue(PayMEMiniApp.isOpen)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun hostThatNeverStartedIsStaleAfterTheTimeout() {
        now += MiniAppSession.HOST_START_TIMEOUT_MS

        PayMERuntime.clearStaleSessions { "cancel" }
        PayMERuntime.clearStaleSessions { "cancel" }

        assertNull(PayMERuntime.getSession(session.id))
        assertFalse(PayMEMiniApp.isOpen)
        assertEquals(1, errors.size)
        assertEquals(PayMEErrorType.UserCancel, errors.single().type)
    }

    @Test
    fun liveHostKeepsTheSessionOpen() {
        val host = Host()
        session.attachHost(host) { it.alive }
        now += MiniAppSession.HOST_START_TIMEOUT_MS * 10

        PayMERuntime.clearStaleSessions { "cancel" }

        assertNotNull(PayMERuntime.getSession(session.id))
        assertTrue(errors.isEmpty())
    }

    @Test
    fun deadHostIsClearedWithOneUserCancel() {
        val host = Host()
        session.attachHost(host) { it.alive }
        host.alive = false

        PayMERuntime.clearStaleSessions { "cancel" }

        assertNull(PayMERuntime.getSession(session.id))
        assertEquals(1, errors.size)
        // Mở mới được
        val next = PayMERuntime.createSession(OpenMiniAppType.modal, OpenMiniAppPayMEData())
        assertEquals(next, PayMERuntime.activeSession())
        PayMERuntime.removeSession(next.id)
    }

    @Test
    fun deadHostAfterACloseResultSendsNoCallback() {
        session.attachHost(Host(alive = false)) { it.alive }
        session.isCloseRequested = true

        PayMERuntime.clearStaleSessions { "cancel" }

        assertNull(PayMERuntime.getSession(session.id))
        assertTrue(errors.isEmpty())
    }

    @Test
    fun reattachedHostAfterRecreationIsAlive() {
        // Xoay màn hình: host cũ huỷ, host mới gắn lại
        session.attachHost(Host(alive = false)) { it.alive }
        session.attachHost(Host(alive = true)) { it.alive }

        PayMERuntime.clearStaleSessions { "cancel" }

        assertNotNull(PayMERuntime.getSession(session.id))
    }
}
