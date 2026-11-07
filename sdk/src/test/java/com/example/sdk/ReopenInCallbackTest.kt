package com.example.sdk

import android.content.ContextWrapper
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.ENV
import com.payme.sdk.models.Locale
import com.payme.sdk.models.OpenMiniAppPayMEData
import com.payme.sdk.models.OpenMiniAppType
import com.payme.sdk.models.PayMEError
import com.payme.sdk.models.PayMEErrorType
import com.payme.sdk.runtime.MiniAppSession
import com.payme.sdk.runtime.PayMERuntime
import com.payme.sdk.runtime.SessionClose
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Partner mở lại mini-app ngay trong callback đóng (USER_CANCEL, isCloseMiniApp) hoặc sau close():
 * không bị MINIAPP_ALREADY_OPEN, và việc dọn phiên cũ sau đó không đụng phiên mới.
 * Đi qua SessionClose (cùng thứ tự với MiniAppFragment) và PayMEMiniApp.openMiniApp thật (screen, android.jar giả).
 */
class ReopenInCallbackTest {
    private val context = object : ContextWrapper(null) {}
    private val errors = mutableListOf<PayMEError>()
    private lateinit var sdk: PayMEMiniApp
    private lateinit var old: MiniAppSession
    private var oldCloseActionCalls = 0
    private var reopened: MiniAppSession? = null

    @Before
    fun setUp() {
        // Lớp test khác có thể để lại phiên trong PayMERuntime (static)
        while (true) PayMERuntime.removeSession(PayMERuntime.activeSession()?.id ?: break)
        sdk = PayMEMiniApp(context, "app", "pub", "priv", ENV.SANDBOX, Locale.en)
        sdk.setUpListener(onError = { _, error -> errors.add(error) })
        sdk.openMiniApp(OpenMiniAppType.screen, OpenMiniAppPayMEData())
        old = PayMERuntime.activeSession()!!
        assertTrue(errors.isEmpty())
        old.closeAction = { oldCloseActionCalls++; PayMERuntime.markSessionClosed(old.id) }
    }

    @After
    fun tearDown() {
        PayMERuntime.activeSession()?.let { PayMERuntime.removeSession(it.id) }
        PayMERuntime.removeSession(old.id)
        sdk.setUpListener(onResponse = { _, _ -> }, onError = { _, _ -> })
        PayMEMiniApp.isOpen = false
    }

    /** Giống MiniAppFragment.closeMiniApp: đóng nơi chứa rồi xoá phiên. */
    private fun closeOld() {
        old.close()
        PayMERuntime.removeSession(old.id)
    }

    private fun reopen() {
        sdk.openMiniApp(OpenMiniAppType.screen, OpenMiniAppPayMEData())
        reopened = PayMERuntime.activeSession()
    }

    private fun assertReopenedAndOldCleanedUp() {
        val next = reopened
        assertNotNull(next)
        assertTrue(next !== old)
        assertTrue(errors.none { it.code == "MINIAPP_ALREADY_OPEN" })
        assertEquals(1, oldCloseActionCalls)
        assertNull(PayMERuntime.getSession(old.id))
        // Host cũ bị huỷ muộn (onDismiss/onDestroy) chỉ dọn phiên cũ
        PayMERuntime.endSessionFromHost(old.id) { "cancel" }
        assertSame(next, PayMERuntime.activeSession())
        assertSame(next, PayMERuntime.getSession(next!!.id))
        assertTrue(next.isOpen && !next.isCloseRequested)
        assertTrue(PayMEMiniApp.isOpen)
    }

    @Test
    fun reopenInsideUserCancel() {
        sdk.setUpListener(onError = { _, error ->
            errors.add(error)
            if (error.type == PayMEErrorType.UserCancel) reopen()
        })

        SessionClose.userCancel(old, "cancel") { closeOld() }

        assertEquals(listOf("USER_CANCEL"), errors.map { it.code })
        assertReopenedAndOldCleanedUp()
    }

    @Test
    fun reopenInsideClosingResponse() {
        sdk.setUpListener(onResponse = { _, _ -> reopen() })

        SessionClose.response(old, null, isCloseMiniApp = true) { closeOld() }

        assertTrue(errors.isEmpty())
        assertReopenedAndOldCleanedUp()
    }

    @Test
    fun reopenInsideClosingError() {
        sdk.setUpListener(onError = { _, error -> errors.add(error); reopen() })

        SessionClose.error(old, PayMEError(PayMEErrorType.MiniApp, "401", "expired", isCloseMiniApp = true)) { closeOld() }

        assertEquals(listOf("401"), errors.map { it.code })
        assertReopenedAndOldCleanedUp()
    }

    @Test
    fun reopenAfterClose() {
        sdk.close()
        reopen()

        assertTrue(errors.isEmpty())
        assertReopenedAndOldCleanedUp()
    }

    @Test
    fun nonClosingResponseKeepsTheSessionOpen() {
        SessionClose.response(old, null, isCloseMiniApp = false) { closeOld() }
        reopen()

        assertEquals(listOf("MINIAPP_ALREADY_OPEN"), errors.map { it.code })
        assertSame(old, PayMERuntime.activeSession())
        assertEquals(0, oldCloseActionCalls)
    }

    @Test
    fun closeCallbackThatThrowsStillCloses() {
        sdk.setUpListener(onError = { _, _ -> error("partner bug") })

        runCatching { SessionClose.userCancel(old, "cancel") { closeOld() } }

        assertEquals(1, oldCloseActionCalls)
        assertNull(PayMERuntime.getSession(old.id))
    }
}
