package com.example.sdk

import com.payme.sdk.models.ActionOpenMiniApp
import com.payme.sdk.runtime.PayMERuntime
import com.payme.sdk.ui.MiniAppFragment
import org.junit.Assert.assertEquals
import org.junit.Test

class MiniAppFragmentCompatibilityTest {
    @Test
    fun getMiniAppActionDefaultsWhenRuntimeSessionAndLegacyDataAreMissing() {
        PayMERuntime.activeSession()?.let { PayMERuntime.removeSession(it.id) }

        assertEquals(ActionOpenMiniApp.PAYME, MiniAppFragment.getMiniAppAction())
    }
}
