package com.example.sdk

import com.payme.sdk.ui.miniapp.source.BundledSourcePolicy
import com.payme.sdk.ui.miniapp.source.InstalledSource
import com.payme.sdk.ui.miniapp.source.MiniAppSourceConstants
import com.payme.sdk.ui.miniapp.source.OtaArtifact
import com.payme.sdk.ui.miniapp.source.RemoteUpdateMode
import com.payme.sdk.ui.miniapp.source.SourceKind
import com.payme.sdk.ui.miniapp.source.StoredUpdateState
import com.payme.sdk.ui.miniapp.source.UpdateDecision
import com.payme.sdk.ui.miniapp.source.UpdateDecisionResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

/** Nâng cấp SDK phải nhận web đóng gói mới; không hạ cấp OTA mới hơn bundled. */
class BundledSourcePolicyTest {
    private val bundled = 502

    private val mode = "miniapp_product"

    private fun install(hasWebRoot: Boolean, installed: InstalledSource?, force: Boolean = false) =
        BundledSourcePolicy.shouldInstallBundled(hasWebRoot, installed, bundled, mode, force)

    @Test
    fun firstInstallOrMissingWebRootInstallsBundled() {
        assertTrue(install(hasWebRoot = false, installed = null))
        assertTrue(install(hasWebRoot = false, installed = InstalledSource(SourceKind.OTA, 600, mode)))
    }

    @Test
    fun sourceLeftByAnOlderSdkIsReplaced() {
        // SDK cũ không ghi bản đang cài: bundled 0500/0501 hoặc OTA 0411 từ 0.7.x (PREF_PATCH 411)
        assertNull(BundledSourcePolicy.installedOrLegacy(null, legacyMode = mode, legacyPatch = 0, bundledPatch = bundled))
        assertNull(BundledSourcePolicy.installedOrLegacy(null, legacyMode = mode, legacyPatch = 411, bundledPatch = bundled))
        assertNull(BundledSourcePolicy.installedOrLegacy(null, legacyMode = mode, legacyPatch = -1, bundledPatch = bundled))
        assertTrue(install(hasWebRoot = true, installed = null))
    }

    @Test
    fun legacyOtaNewerThanBundledIsKept() {
        val legacy = BundledSourcePolicy.installedOrLegacy(null, legacyMode = mode, legacyPatch = 503, bundledPatch = bundled)
        assertEquals(InstalledSource(SourceKind.OTA, 503, mode), legacy)
        assertFalse(install(hasWebRoot = true, installed = legacy))
    }

    @Test
    fun recordedSourceWinsOverLegacyPatch() {
        val recorded = InstalledSource(SourceKind.BUNDLED, 502)
        assertEquals(recorded, BundledSourcePolicy.installedOrLegacy(recorded, legacyMode = mode, legacyPatch = 600, bundledPatch = bundled))
    }

    @Test
    fun currentBundledIsNotReinstalledEveryOpen() {
        assertFalse(install(hasWebRoot = true, installed = InstalledSource(SourceKind.BUNDLED, 502)))
        assertFalse(install(hasWebRoot = true, installed = InstalledSource(SourceKind.BUNDLED, 502), force = true))
    }

    @Test
    fun bundledFromAnotherSdkIsReplaced() {
        assertTrue(install(hasWebRoot = true, installed = InstalledSource(SourceKind.BUNDLED, 501)))
    }

    @Test
    fun olderOtaIsReplacedNewerOtaIsKept() {
        assertTrue(install(hasWebRoot = true, installed = InstalledSource(SourceKind.OTA, 501, mode)))
        assertFalse(install(hasWebRoot = true, installed = InstalledSource(SourceKind.OTA, 502, mode)))
        assertFalse(install(hasWebRoot = true, installed = InstalledSource(SourceKind.OTA, 503, mode)))
    }

    @Test
    fun otaOfAnotherModeIsReplacedAfterSetMode() {
        assertTrue(install(hasWebRoot = true, installed = InstalledSource(SourceKind.OTA, 600, "miniapp_sandbox")))
    }

    @Test
    fun killSwitchReplacesAnyOta() {
        assertTrue(install(hasWebRoot = true, installed = InstalledSource(SourceKind.OTA, 600, mode), force = true))
    }

    @Test
    fun otaNotNewerThanBundledIsNeitherDownloadedNorInstalled() {
        val resolver = UpdateDecisionResolver(isTrusted = { _, _ -> true }, bundledPatch = bundled)
        val mode = "miniapp_product"
        val old = resolver.resolve(RemoteUpdateMode(501, 501, "https://cdn/p501.zip"), mode, StoredUpdateState(mode, 0))
        assertEquals(UpdateDecision.NoUpdate, old.decision)
        // Đổi mode (PREF_PATCH -1) cũng không tải bản cũ hơn bundled
        val otherMode = resolver.resolve(RemoteUpdateMode(502, 502, "https://cdn/p502.zip"), mode, StoredUpdateState("miniapp_sandbox", 600))
        assertEquals(UpdateDecision.NoUpdate, otherMode.decision)
        val newer = resolver.resolve(RemoteUpdateMode(503, 503, "https://cdn/p503.zip"), mode, StoredUpdateState(mode, 0))
        assertTrue(newer.decision is UpdateDecision.MandatoryUpdate)

        // Zip OTA tải dở/tải trước khi nâng cấp SDK: không cài nếu không mới hơn bundled
        assertFalse(resolver.acceptsPendingUpdate(OtaArtifact(mode, 501, "", 1), mode, StoredUpdateState(mode, 0)))
        assertTrue(resolver.acceptsPendingUpdate(OtaArtifact(mode, 503, "", 1), mode, StoredUpdateState(mode, 0)))
    }

    @Test
    fun bundledPatchConstantMatchesTheBundledZip() {
        // Đổi assets/www/sdkWebapp3-main.zip mà quên đổi BUNDLED_PATCH thì người dùng cũ không nhận bản mới
        val zip = listOf(File("src/main/assets/www/sdkWebapp3-main.zip"), File("sdk/src/main/assets/www/sdkWebapp3-main.zip"))
            .first { it.isFile }
        val expected = "patch:\"%04d\"".format(MiniAppSourceConstants.BUNDLED_PATCH)
        val found = ZipFile(zip).use { file ->
            file.entries().asSequence()
                .filter { it.name.matches(Regex(".*/static/js/index\\.[^/]*\\.js")) }
                .any { entry -> file.getInputStream(entry).bufferedReader().readText().contains(expected) }
        }
        assertTrue("bundled zip does not contain $expected", found)
    }
}
