package com.example.sdk

import com.payme.sdk.ui.miniapp.source.OtaArtifact
import com.payme.sdk.ui.miniapp.source.OtaEntryVerifier
import com.payme.sdk.ui.miniapp.source.OtaManifest
import com.payme.sdk.ui.miniapp.source.RemoteUpdateMode
import com.payme.sdk.ui.miniapp.source.StoredUpdateState
import com.payme.sdk.ui.miniapp.source.UpdateDecision
import com.payme.sdk.ui.miniapp.source.UpdateDecisionResolver
import com.payme.sdk.utils.ArchiveUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class OtaManifestVerifierTest {
    // Vector signed by web script/sign-ota.cjs with a throwaway test key (public half only).
    private val testPublicKey = OtaManifest.decodePublicKey(
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE+/eCpTN4lYItIb2cGhE0LUy9WqiXJHOkxib7XQzj5bmgvNCULCiTN+rgVfKZMT1xkblAMHOQMddb9OwNElMqzg=="
    )
    private val sdkVersion = "1.0.24"
    private val mode = "miniapp_product"
    private val vector = RemoteUpdateMode(
        patch = 500,
        latestMandatoryPatch = 500,
        url = "https://static.payme.vn/frontend/miniapp-store/p0500.zip",
        sha256 = "ca5d45871c7fd3f0745ed2e2de9776ae8fbc8f79f2c1223a793118967ae448de",
        size = 4287341,
        sig = "MEUCIGwwo65MzJlzprigoIQ0hebu7D+sBn2K8q1vrK1au8cbAiEA2Ddjag1LvEsha2GydFieWS+lC5bbaRCtoGSUkp+dJ7U="
    )
    private val vectorPayload = "payme-ota-v1|1.0.24|miniapp_product|500|500|" +
        "https://static.payme.vn/frontend/miniapp-store/p0500.zip|" +
        "ca5d45871c7fd3f0745ed2e2de9776ae8fbc8f79f2c1223a793118967ae448de|4287341"

    private val testVerifier = OtaEntryVerifier(listOf(testPublicKey), sdkVersion)

    @Test fun canonicalPayloadMatchesTheNodeSigner() {
        assertEquals(
            vectorPayload,
            OtaManifest.canonicalPayload(sdkVersion, mode, vector.patch, vector.latestMandatoryPatch, vector.url, vector.sha256, vector.size)
        )
    }

    @Test fun nodeSignedVectorVerifies() {
        assertTrue(testVerifier.isTrusted(vector, mode))
    }

    @Test fun tamperingAnySignedFieldFails() {
        val tampered = listOf(
            "patch" to vector.copy(patch = 501),
            "latestMandatoryPatch" to vector.copy(latestMandatoryPatch = 499),
            "url" to vector.copy(url = "https://static.payme.vn/frontend/miniapp-store/p0501.zip"),
            "sha256" to vector.copy(sha256 = "da5d45871c7fd3f0745ed2e2de9776ae8fbc8f79f2c1223a793118967ae448de"),
            "size" to vector.copy(size = 4287342)
        )
        tampered.forEach { (field, entry) -> assertFalse(field, testVerifier.isTrusted(entry, mode)) }
        assertFalse("mode", testVerifier.isTrusted(vector, "miniapp_sandbox"))
        assertFalse("sdkVersion", OtaEntryVerifier(listOf(testPublicKey), "1.0.25").isTrusted(vector, mode))
    }

    @Test fun otherKeysFailIncludingTheProductionKey() {
        assertFalse(OtaEntryVerifier(listOf(newKeyPair().public), sdkVersion).isTrusted(vector, mode))
        assertFalse(OtaEntryVerifier.pinned(sdkVersion).isTrusted(vector, mode))
        assertFalse(OtaEntryVerifier(emptyList(), sdkVersion).isTrusted(vector, mode))
    }

    @Test fun missingOrGarbageSignatureFails() {
        val random = Base64.getEncoder().encodeToString(ByteArray(72) { it.toByte() })
        listOf("", "not base64!!", "AAAA", random, vector.sig.dropLast(4), " " + vector.sig)
            .forEach { assertFalse(it, testVerifier.isTrusted(vector.copy(sig = it), mode)) }
    }

    @Test fun anyPinnedKeyCanVerifyForRotation() {
        val rotation = OtaEntryVerifier(listOf(newKeyPair().public, testPublicKey), sdkVersion)
        assertTrue(rotation.isTrusted(vector, mode))
    }

    @Test fun productionKeyIsPinnedAsP256() {
        val keys = OtaManifest.PINNED_PUBLIC_KEYS.map(OtaManifest::decodePublicKey)
        assertEquals(1, keys.size)
        keys.forEach { assertEquals(256, (it as ECPublicKey).params.curve.field.fieldSize) }
    }

    @Test fun signedButMalformedEntriesAreRejected() {
        val pair = newKeyPair()
        val verifier = OtaEntryVerifier(listOf(pair.public), sdkVersion)
        val good = sign(vector.copy(sig = ""), pair.private)
        assertTrue(verifier.isTrusted(good, mode))
        val malformed = mapOf(
            "patch 0" to good.copy(patch = 0, latestMandatoryPatch = 0),
            "negative patch" to good.copy(patch = -1),
            "latestMandatoryPatch 0" to good.copy(latestMandatoryPatch = 0),
            "latestMandatoryPatch > patch" to good.copy(latestMandatoryPatch = 501),
            "http url" to good.copy(url = "http://static.payme.vn/p.zip"),
            "other host" to good.copy(url = "https://static.payme.vn.evil.example/p.zip"),
            "userinfo" to good.copy(url = "https://user@static.payme.vn/p.zip"),
            "uppercase sha256" to good.copy(sha256 = good.sha256.uppercase()),
            "short sha256" to good.copy(sha256 = good.sha256.drop(1)),
            "size 0" to good.copy(size = 0),
            "size above cap" to good.copy(size = ArchiveUtils.MAX_ARCHIVE_BYTES + 1)
        )
        malformed.forEach { (name, entry) -> assertFalse(name, verifier.isTrusted(sign(entry, pair.private), mode)) }
        assertFalse("malformed mode", verifier.isTrusted(sign(good, pair.private, signedMode = "Miniapp-Product"), "Miniapp-Product"))
        listOf("pm_product", "pm_staging", "pm_sandbox", "bank").forEach { other ->
            assertTrue(other, verifier.isTrusted(sign(good, pair.private, signedMode = other), other))
        }
        val badSdk = OtaEntryVerifier(listOf(pair.public), "1.0")
        assertFalse("sdk version", badSdk.isTrusted(sign(good, pair.private, sdk = "1.0"), mode))
    }

    @Test fun untrustedEntriesNeverDownloadAndLeaveStoredStateAlone() {
        val cases = listOf(
            // The default resolver pins the production key, which did not sign the test vector.
            UpdateDecisionResolver() to vector,
            resolver() to vector.copy(sig = ""),
            resolver() to vector.copy(size = 1),
            resolver() to vector.copy(patch = 600)
        )
        for (stored in listOf(StoredUpdateState(mode, 400), StoredUpdateState("miniapp_sandbox", 900))) {
            cases.forEach { (resolver, entry) ->
                val result = resolver.resolve(entry, mode, stored)
                assertEquals(UpdateDecision.NoUpdate, result.decision)
                assertNull(result.mutation)
            }
        }
    }

    @Test fun trustedVectorDownloadsWithItsArtifact() {
        val decision = resolver().resolve(vector, mode, StoredUpdateState(mode, 400)).decision
        assertEquals(
            UpdateDecision.MandatoryUpdate(vector.url, 500, OtaArtifact(mode, 500, vector.sha256, vector.size)),
            decision
        )
    }

    @Test fun killSwitchNeedsNoSignatureButPatchZeroNeverDownloads() {
        assertEquals(
            UpdateDecision.LoadDefaultSource,
            UpdateDecisionResolver().resolve(RemoteUpdateMode(0, 0, ""), mode, StoredUpdateState(mode, 500)).decision
        )
        // Mode change resets the local patch to -1; an unsigned patch 0 must still not download.
        val result = UpdateDecisionResolver().resolve(
            RemoteUpdateMode(0, 5, "https://static.payme.vn/p.zip"), "miniapp_sandbox", StoredUpdateState(mode, 500)
        )
        assertEquals(UpdateDecision.NoUpdate, result.decision)
    }

    @Test fun antiRollbackNeverDownloadsOrInstallsAnOlderPatch() {
        val pair = newKeyPair()
        val resolver = UpdateDecisionResolver(OtaEntryVerifier(listOf(pair.public), sdkVersion)::isTrusted)
        val older = sign(vector.copy(patch = 499, latestMandatoryPatch = 499, sig = ""), pair.private)
        assertTrue(OtaEntryVerifier(listOf(pair.public), sdkVersion).isTrusted(older, mode))
        assertEquals(UpdateDecision.NoUpdate, resolver.resolve(older, mode, StoredUpdateState(mode, 500)).decision)
        assertEquals(UpdateDecision.NoUpdate, resolver.resolve(sign(vector.copy(sig = ""), pair.private), mode, StoredUpdateState(mode, 500)).decision)

        val installed = StoredUpdateState(mode, 500)
        assertFalse(resolver.acceptsPendingUpdate(OtaArtifact(mode, 499, vector.sha256, 1), mode, installed))
        assertTrue(resolver.acceptsPendingUpdate(OtaArtifact(mode, 500, vector.sha256, 1), mode, installed))
        assertTrue(resolver.acceptsPendingUpdate(OtaArtifact(mode, 501, vector.sha256, 1), mode, installed))
        assertFalse(resolver.acceptsPendingUpdate(OtaArtifact("miniapp_sandbox", 900, vector.sha256, 1), mode, installed))
        // After a mode switch the stored patch belongs to the other mode.
        assertTrue(resolver.acceptsPendingUpdate(OtaArtifact(mode, 300, vector.sha256, 1), mode, StoredUpdateState("miniapp_sandbox", 900)))
    }

    private fun resolver() = UpdateDecisionResolver(testVerifier::isTrusted)

    private fun newKeyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private fun sign(entry: RemoteUpdateMode, key: PrivateKey, signedMode: String = mode, sdk: String = sdkVersion): RemoteUpdateMode {
        val payload = OtaManifest.canonicalPayload(sdk, signedMode, entry.patch, entry.latestMandatoryPatch, entry.url, entry.sha256, entry.size)
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(key)
            update(payload.toByteArray(Charsets.UTF_8))
            sign()
        }
        return entry.copy(sig = Base64.getEncoder().encodeToString(signature))
    }
}
