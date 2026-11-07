package com.example.sdk

import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.ActionOpenMiniApp
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UserCancelTest {
    private val errors = mutableListOf<Pair<ActionOpenMiniApp, PayMEError>>()
    private lateinit var session: MiniAppSession

    @Before
    fun setUp() {
        PayMERuntime.initialize(
            PayMEConfig(appId = "app", publicKey = "pub", privateKey = "priv", env = ENV.SANDBOX, locale = Locale.en)
        )
        PayMERuntime.callbacks().onError = { action, error -> errors.add(action to error) }
        session = PayMERuntime.createSession(OpenMiniAppType.modal, OpenMiniAppPayMEData())
    }

    @After
    fun tearDown() {
        PayMERuntime.removeSession(session.id)
        PayMERuntime.callbacks().onError = { _, _ -> }
        PayMEMiniApp.isOpen = false
    }

    @Test
    fun userCancelUsesUserCancelTypeAndCode() {
        assertTrue(session.notifyUserCancel("cancel"))

        assertEquals(1, errors.size)
        val (action, error) = errors.single()
        assertEquals(ActionOpenMiniApp.PAYME, action)
        assertEquals(PayMEErrorType.UserCancel, error.type)
        assertEquals("USER_CANCEL", error.code)
        assertEquals("cancel", error.description)
        assertFalse(error.isCloseMiniApp)
    }

    @Test
    fun userCancelIsSentAtMostOncePerSession() {
        // jsClose rồi modal dismiss (hoặc jsClose lặp lại) chỉ được báo một lần
        assertTrue(session.notifyUserCancel("cancel"))
        assertFalse(session.notifyUserCancel("cancel"))
        assertFalse(session.notifyUserCancel("cancel"))

        assertEquals(1, errors.size)
    }

    @Test
    fun dismissByUserNotifiesCancel() {
        assertTrue(session.shouldNotifyUserCancelOnDismiss())
    }

    @Test
    fun dismissAfterRequestedCloseDoesNotNotifyCancel() {
        // jsClose / lỗi kết thúc / PayMEMiniApp.close() đặt isCloseRequested trước khi dismiss
        session.isCloseRequested = true
        assertFalse(session.shouldNotifyUserCancelOnDismiss())
    }

    @Test
    fun dismissAfterSessionClosedDoesNotNotifyCancel() {
        PayMERuntime.markSessionClosed(session.id)
        assertFalse(session.shouldNotifyUserCancelOnDismiss())
    }

    @Test
    fun closeRequestedByPartnerSendsNoCallback() {
        var closeActionCalls = 0
        session.closeAction = { closeActionCalls++ }
        session.isCloseRequested = true

        session.close()
        session.close()

        assertEquals(1, closeActionCalls)
        assertFalse(session.shouldNotifyUserCancelOnDismiss())
        assertTrue(errors.isEmpty())
    }
}

class LightContextTest {
    @Test
    fun lightUiModeClearsNightKeepsType() {
        val night = android.content.res.Configuration.UI_MODE_TYPE_NORMAL or android.content.res.Configuration.UI_MODE_NIGHT_YES
        val light = com.payme.sdk.utils.LightContext.lightUiMode(night)
        assertEquals(android.content.res.Configuration.UI_MODE_NIGHT_NO, light and android.content.res.Configuration.UI_MODE_NIGHT_MASK)
        assertEquals(android.content.res.Configuration.UI_MODE_TYPE_NORMAL, light and android.content.res.Configuration.UI_MODE_TYPE_MASK)
    }
}
