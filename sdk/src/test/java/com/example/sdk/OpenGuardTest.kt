package com.example.sdk

import android.content.ContextWrapper
import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.ENV
import com.payme.sdk.models.Locale
import com.payme.sdk.models.OpenMiniAppPayMEData
import com.payme.sdk.models.OpenMiniAppType
import com.payme.sdk.models.PayMEError
import com.payme.sdk.models.PayMEErrorType
import com.payme.sdk.runtime.PayMERuntime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Các nhánh kiểm tra của openMiniApp: từ chối bằng onError không để lại phiên, phiên kẹt không chặn lần mở mới.
 * Unit test không có Android thật (android.jar trả giá trị mặc định): nhánh mở screen chỉ kiểm tra phiên, không kiểm tra Activity.
 */
class OpenGuardTest {
    private val errors = mutableListOf<PayMEError>()
    private lateinit var sdk: PayMEMiniApp

    // Context không phải FragmentActivity (vd. applicationContext)
    private val plainContext = object : ContextWrapper(null) {
        override fun getApplicationContext() = this
    }

    @Before
    fun setUp() {
        sdk = PayMEMiniApp(plainContext, ENV.SANDBOX, Locale.en)
        sdk.setUpListener(onError = { _, error -> errors.add(error) })
    }

    @After
    fun tearDown() {
        PayMERuntime.activeSession()?.let { PayMERuntime.removeSession(it.id) }
        PayMEMiniApp.isOpen = false
    }

    @Test
    fun modalWithoutFragmentActivityReportsCannotPresent() {
        sdk.openMiniApp(OpenMiniAppType.modal, OpenMiniAppPayMEData())

        assertEquals(1, errors.size)
        assertEquals(PayMEErrorType.MiniApp, errors.single().type)
        assertEquals("MINIAPP_CANNOT_PRESENT", errors.single().code)
        assertNull(PayMERuntime.activeSession())
        assertFalse(PayMEMiniApp.isOpen)
    }

    @Test
    fun secondOpenWhileOneIsOpenIsRejected() {
        val first = PayMERuntime.createSession(OpenMiniAppType.screen, OpenMiniAppPayMEData())

        sdk.openMiniApp(OpenMiniAppType.screen, OpenMiniAppPayMEData())

        assertEquals("MINIAPP_ALREADY_OPEN", errors.single().code)
        assertEquals(first, PayMERuntime.activeSession())
        assertTrue(PayMEMiniApp.isOpen)
    }

    @Test
    fun staleSessionIsClearedAndTheNextOpenCreatesASession() {
        val first = PayMERuntime.createSession(OpenMiniAppType.screen, OpenMiniAppPayMEData())
        first.attachHost(Any()) { false }

        sdk.openMiniApp(OpenMiniAppType.screen, OpenMiniAppPayMEData())

        // Phiên kẹt được dọn với một USER_CANCEL; lần mở mới không bị MINIAPP_ALREADY_OPEN
        assertNull(PayMERuntime.getSession(first.id))
        assertEquals(listOf("USER_CANCEL"), errors.map { it.code })
        val next = PayMERuntime.activeSession()
        assertTrue(next != null && next !== first)
        assertTrue(PayMEMiniApp.isOpen)
    }

    @Test
    fun modalNeedsALiveActivityAndFragmentManager() {
        assertTrue(PayMEMiniApp.Launcher.isModalHostUsable(isFinishing = false, isDestroyed = false, isFragmentManagerDestroyed = false))
        assertFalse(PayMEMiniApp.Launcher.isModalHostUsable(isFinishing = true, isDestroyed = false, isFragmentManagerDestroyed = false))
        assertFalse(PayMEMiniApp.Launcher.isModalHostUsable(isFinishing = false, isDestroyed = true, isFragmentManagerDestroyed = false))
        assertFalse(PayMEMiniApp.Launcher.isModalHostUsable(isFinishing = false, isDestroyed = false, isFragmentManagerDestroyed = true))
    }
}
