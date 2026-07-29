package com.example.sdk

import com.payme.sdk.PayMEMiniApp
import com.payme.sdk.models.ActionOpenMiniApp
import com.payme.sdk.models.ENV
import com.payme.sdk.models.Locale
import com.payme.sdk.models.OpenMiniAppPayMEData
import com.payme.sdk.models.OpenMiniAppType
import com.payme.sdk.runtime.PayMEConfig
import com.payme.sdk.runtime.PayMERuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PayMERuntimeTest {
    @Before
    fun setUp() {
        removeAllSessions()
        PayMEMiniApp.isOpen = false
        PayMERuntime.initialize(config("default"))
        PayMERuntime.updateCallbacks { callbacks ->
            callbacks.onResponse = { _, _ -> }
            callbacks.onError = { _, _ -> }
            callbacks.onOneSignalSendTags = null
            callbacks.onOneSignalDeleteTags = null
            callbacks.onChangeEnv = null
            callbacks.onChangeLocale = null
        }
    }

    @Test
    fun sessionKeepsConfigAndCallbackSnapshot() {
        val calls = mutableListOf<String>()
        PayMERuntime.callbacks().onResponse = { _, _ -> calls.add("old") }

        val session = PayMERuntime.createSession(OpenMiniAppType.screen, OpenMiniAppPayMEData())
        PayMERuntime.initialize(config("new-default"))
        PayMERuntime.callbacks().onResponse = { _, _ -> calls.add("new") }

        session.callbacks.onResponse(ActionOpenMiniApp.PAYME, null)

        assertEquals("default", session.config.appId)
        assertEquals(listOf("old"), calls)
    }

    @Test
    fun isOpenReflectsSessionLifecycleAndLegacyMirror() {
        val session = PayMERuntime.createSession(OpenMiniAppType.screen, OpenMiniAppPayMEData())

        assertTrue(PayMERuntime.isOpen())
        assertTrue(PayMEMiniApp.isOpen)

        PayMEMiniApp.isOpen = false
        assertTrue(PayMERuntime.isOpen())

        PayMERuntime.markSessionClosed(session.id)
        assertFalse(PayMERuntime.isOpen())
        assertFalse(PayMEMiniApp.isOpen)

        PayMERuntime.removeSession(session.id)
        assertFalse(PayMERuntime.isOpen())
        assertFalse(PayMEMiniApp.isOpen)
    }

    private fun config(appId: String): PayMEConfig {
        return PayMEConfig(
            appId = appId,
            publicKey = "public-key",
            privateKey = "private-key",
            env = ENV.SANDBOX,
            locale = Locale.en
        )
    }

    private fun removeAllSessions() {
        while (true) {
            val activeSession = PayMERuntime.activeSession() ?: return
            PayMERuntime.removeSession(activeSession.id)
        }
    }
}
