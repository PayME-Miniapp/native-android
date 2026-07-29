package com.example.sdk

import com.payme.sdk.ui.miniapp.source.RemoteUpdateMode
import com.payme.sdk.ui.miniapp.source.StoredUpdateState
import com.payme.sdk.ui.miniapp.source.UpdateDecision
import com.payme.sdk.ui.miniapp.source.UpdateDecisionResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateDecisionResolverTest {
    @Test
    fun updateDecisionLoadsDefaultWhenRemotePatchIsZero() {
        val result = resolver().resolve(
            remoteMode = RemoteUpdateMode(patch = 0, latestMandatoryPatch = 0, url = ""),
            currentMode = "miniapp_product",
            storedState = StoredUpdateState("miniapp_product", 0)
        )

        assertEquals(UpdateDecision.LoadDefaultSource, result.decision)
        assertNull(result.mutation)
    }

    @Test
    fun updateDecisionDoesNothingWhenLocalPatchIsCurrent() {
        val result = resolver().resolve(
            remoteMode = RemoteUpdateMode(
                patch = 3,
                latestMandatoryPatch = 0,
                url = "https://cdn/app.zip"
            ),
            currentMode = "miniapp_product",
            storedState = StoredUpdateState("miniapp_product", 3)
        )

        assertEquals(UpdateDecision.NoUpdate, result.decision)
        assertNull(result.mutation)
    }

    @Test
    fun updateDecisionUsesBackgroundUpdateWhenPatchIsOptional() {
        val result = resolver().resolve(
            remoteMode = RemoteUpdateMode(
                patch = 4,
                latestMandatoryPatch = 2,
                url = "https://cdn/app.zip"
            ),
            currentMode = "miniapp_product",
            storedState = StoredUpdateState("miniapp_product", 3)
        )

        val decision = result.decision
        assertTrue(decision is UpdateDecision.BackgroundUpdate)
        assertEquals(4, (decision as UpdateDecision.BackgroundUpdate).patch)
        assertEquals("https://cdn/app.zip", decision.url)
    }

    @Test
    fun updateDecisionUsesMandatoryUpdateWhenLocalPatchIsBelowMandatoryPatch() {
        val result = resolver().resolve(
            remoteMode = RemoteUpdateMode(
                patch = 4,
                latestMandatoryPatch = 3,
                url = "https://cdn/app.zip"
            ),
            currentMode = "miniapp_product",
            storedState = StoredUpdateState("miniapp_product", 2)
        )

        val decision = result.decision
        assertTrue(decision is UpdateDecision.MandatoryUpdate)
        assertEquals(4, (decision as UpdateDecision.MandatoryUpdate).patch)
    }

    @Test
    fun updateDecisionResetsStoredPatchWhenModeChanges() {
        val result = resolver().resolve(
            remoteMode = RemoteUpdateMode(
                patch = 1,
                latestMandatoryPatch = 0,
                url = "https://cdn/app.zip"
            ),
            currentMode = "miniapp_sandbox",
            storedState = StoredUpdateState("miniapp_product", 9)
        )

        assertEquals("miniapp_sandbox", result.mutation?.mode)
        assertEquals(-1, result.mutation?.patch)
        assertTrue(result.decision is UpdateDecision.MandatoryUpdate)
    }

    private fun resolver() = UpdateDecisionResolver()
}
