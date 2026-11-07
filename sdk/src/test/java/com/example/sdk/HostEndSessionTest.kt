package com.example.sdk

import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.ENV
import com.payme.sdk.models.Locale
import com.payme.sdk.models.OpenMiniAppPayMEData
import com.payme.sdk.models.OpenMiniAppType
import com.payme.sdk.models.PayMEError
import com.payme.sdk.models.PayMEErrorType
import com.payme.sdk.runtime.HostLifecycle
import com.payme.sdk.runtime.MiniAppSession
import com.payme.sdk.runtime.PayMEConfig
import com.payme.sdk.runtime.PayMERuntime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * PayMERuntime.endSessionFromHost: điều xảy ra với phiên khi nơi chứa đã được xác định là đóng hẳn.
 * Quyết định "đóng hẳn hay đang dựng lại" nằm ở HostLifecycle (HostLifecycleTest).
 */
class HostEndSessionTest {
    private val errors = mutableListOf<PayMEError>()
    private lateinit var session: MiniAppSession

    @Before
    fun setUp() {
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
        PayMEMiniApp.isOpen = false
    }

    @Test
    fun hostGoneSendsOneUserCancelAndClearsTheSession() {
        var closeActionCalls = 0
        session.closeAction = { closeActionCalls++ }
        assertTrue(PayMEMiniApp.isOpen)

        PayMERuntime.endSessionFromHost(session.id) { "cancel" }
        PayMERuntime.endSessionFromHost(session.id) { "cancel" }

        assertEquals(1, errors.size)
        assertEquals(PayMEErrorType.UserCancel, errors.single().type)
        assertEquals("USER_CANCEL", errors.single().code)
        assertNull(PayMERuntime.getSession(session.id))
        assertFalse(PayMEMiniApp.isOpen)
        assertEquals(0, closeActionCalls)
        // Kết quả web tới muộn của phiên này bị bỏ qua
        assertTrue(session.isCloseRequested)
    }

    @Test
    fun hostGoneAfterACloseResultSendsNothing() {
        // jsResponse/jsError có isCloseMiniApp hoặc PayMEMiniApp.close() đã đặt cờ này
        session.isCloseRequested = true

        PayMERuntime.endSessionFromHost(session.id) { "cancel" }

        assertTrue(errors.isEmpty())
        assertNull(PayMERuntime.getSession(session.id))
    }

    @Test
    fun hostGoneAfterJsCloseDoesNotRepeatUserCancel() {
        session.notifyUserCancel("cancel")

        PayMERuntime.endSessionFromHost(session.id) { "cancel" }

        assertEquals(1, errors.size)
    }

    @Test
    fun sessionIsClearedEvenIfThePartnerCallbackThrows() {
        PayMERuntime.removeSession(session.id)
        PayMERuntime.callbacks().onError = { _, _ -> error("partner bug") }
        session = PayMERuntime.createSession(OpenMiniAppType.modal, OpenMiniAppPayMEData())

        runCatching { PayMERuntime.endSessionFromHost(session.id) { "cancel" } }

        assertNull(PayMERuntime.getSession(session.id))
        assertFalse(PayMEMiniApp.isOpen)
    }

    @Test
    fun openingAgainAfterTheHostWasGoneWorks() {
        PayMERuntime.endSessionFromHost(session.id) { "cancel" }

        val next = PayMERuntime.createSession(OpenMiniAppType.modal, OpenMiniAppPayMEData())
        try {
            assertEquals(next, PayMERuntime.activeSession())
            assertTrue(PayMEMiniApp.isOpen)
        } finally {
            PayMERuntime.removeSession(next.id)
        }
    }

    @Test
    fun unknownSessionIsIgnored() {
        PayMERuntime.endSessionFromHost("missing") { "cancel" }
        PayMERuntime.endSessionFromHost(null) { "cancel" }

        assertTrue(errors.isEmpty())
        assertTrue(PayMEMiniApp.isOpen)
    }
}

/** Khi nào nơi chứa bị huỷ được coi là đóng hẳn (USER_CANCEL) hay đang dựng lại (giữ phiên). */
class HostLifecycleTest {
    @Test
    fun modalViewDestroyedWhileTheActivityIsRecreatedKeepsTheSession() {
        // Xoay, dark mode, ngôn ngữ, multi-window, Don't keep activities
        assertTrue(HostLifecycle.isModalHostRecreating(isDestroyingView = true, isRemoving = false, isActivityFinishing = false))
    }

    @Test
    fun partnerFinishingItsActivityEndsTheModalSession() {
        assertFalse(HostLifecycle.isModalHostRecreating(isDestroyingView = true, isRemoving = false, isActivityFinishing = true))
    }

    @Test
    fun modalDismissedOrRemovedEndsTheSession() {
        // Chạm ngoài / dismiss: onDismiss tới trước onDestroyView
        assertFalse(HostLifecycle.isModalHostRecreating(isDestroyingView = false, isRemoving = false, isActivityFinishing = false))
        // Fragment bị gỡ (dismissAllowingStateLoss)
        assertFalse(HostLifecycle.isModalHostRecreating(isDestroyingView = true, isRemoving = true, isActivityFinishing = false))
    }

    @Test
    fun miniAppActivityEndsTheSessionOnlyWhenFinishing() {
        assertTrue(HostLifecycle.shouldEndSessionOnActivityDestroy(isFinishing = true))
        assertFalse(HostLifecycle.shouldEndSessionOnActivityDestroy(isFinishing = false))
    }
}
