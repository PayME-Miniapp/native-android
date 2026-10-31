package com.example.sdk

import com.payme.sdk.ui.BackRedirectGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubWebViewBackTest {
    private val gateway = "https://payme.vn/en/web/payment-gateway"

    @Test
    fun backThatRedirectsStraightToTheStartingPageIsABounce() {
        val guard = BackRedirectGuard()
        guard.onBack(gateway, now = 1_000)

        assertFalse(guard.isBounce("https://payme.vn/", now = 1_200))
        assertTrue(guard.isBounce(gateway, now = 1_500))
    }

    @Test
    fun aBounceIsReportedOnce() {
        val guard = BackRedirectGuard()
        guard.onBack(gateway, now = 0)

        assertTrue(guard.isBounce(gateway, now = 100))
        assertFalse(guard.isBounce(gateway, now = 200))
    }

    @Test
    fun returningToTheStartingPageAfterTheWindowIsNotABounce() {
        val guard = BackRedirectGuard(windowMs = 3_000)
        guard.onBack(gateway, now = 0)

        assertFalse(guard.isBounce(gateway, now = 3_001))
        assertFalse(guard.isBounce(gateway, now = 3_100))
    }

    @Test
    fun navigationWithoutABackIsNeverABounce() {
        val guard = BackRedirectGuard()

        assertFalse(guard.isBounce(gateway, now = 0))
        assertFalse(guard.isBounce(null, now = 0))
    }
}
