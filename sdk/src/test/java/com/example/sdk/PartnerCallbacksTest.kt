package com.example.sdk

import com.payme.sdk.runtime.PartnerCallbacks
import org.junit.Assert.assertEquals
import org.junit.Test

class PartnerCallbacksTest {
    @Test
    fun partnerCallbackErrorIsSwallowed() {
        var calls = 0
        val guarded = PartnerCallbacks.guard<String, Int>("onError") { _, _ -> calls++; error("partner bug") }

        guarded("a", 1)

        assertEquals(1, calls)
    }
}
