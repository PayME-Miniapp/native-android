package com.payme.sdk.ui.miniapp.source

import com.payme.sdk.BuildConfig
import com.payme.sdk.utils.ArchiveUtils
import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Signed OTA manifest entries (SDK 1.0.24+). Must match web `script/sign-ota.cjs`:
 * payload = "payme-ota-v1|sdkVersion|mode|patch|latestMandatoryPatch|url|sha256|size" (UTF-8),
 * signature = DER ECDSA P-256 / SHA-256, base64.
 */
internal object OtaManifest {
    const val PAYLOAD_VERSION = "payme-ota-v1"
    /** The manifest has miniapp_*, pm_* and bank modes; the mode is signed, so any well-formed name is accepted. */
    val MODE_NAME = Regex("[a-z][a-z0-9_]{0,31}")

    /** Production signing keys (SPKI DER, base64). Add the next key here before rotating. */
    val PINNED_PUBLIC_KEYS = listOf(
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEXffdQxmdluFTDprn4zvzPSSXCsNHnCl9Z+5pjOzkGPNcYQN/Su0xCyTD2CTVorLVwvltPgDB6VPTJZKh4mUvrg=="
    )

    fun canonicalPayload(
        sdkVersion: String,
        mode: String,
        patch: Int,
        latestMandatoryPatch: Int,
        url: String,
        sha256: String,
        size: Long
    ): String = listOf(PAYLOAD_VERSION, sdkVersion, mode, patch, latestMandatoryPatch, url, sha256, size).joinToString("|")

    fun decodePublicKey(spkiBase64: String): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(spkiBase64)))
}

/** Trusts a patch > 0 entry only if every field is well-formed and the signature verifies. */
internal class OtaEntryVerifier(
    private val publicKeys: List<PublicKey>,
    private val sdkVersion: String,
    private val maxSize: Long = ArchiveUtils.MAX_ARCHIVE_BYTES
) {
    fun isTrusted(entry: RemoteUpdateMode, mode: String): Boolean {
        val wellFormed = SDK_VERSION.matches(sdkVersion) &&
            OtaManifest.MODE_NAME.matches(mode) &&
            entry.patch >= 1 &&
            entry.latestMandatoryPatch >= 1 &&
            entry.latestMandatoryPatch <= entry.patch &&
            OtaConnection.isAllowed(entry.url) &&
            SHA256_HEX.matches(entry.sha256) &&
            entry.size in 1..maxSize
        if (!wellFormed) return false
        val signature = try {
            Base64.getDecoder().decode(entry.sig)
        } catch (_: IllegalArgumentException) {
            return false
        }
        if (signature.isEmpty()) return false
        val payload = OtaManifest.canonicalPayload(
            sdkVersion, mode, entry.patch, entry.latestMandatoryPatch, entry.url, entry.sha256, entry.size
        ).toByteArray(Charsets.UTF_8)
        return publicKeys.any { key ->
            try {
                Signature.getInstance("SHA256withECDSA").run {
                    initVerify(key)
                    update(payload)
                    verify(signature)
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    companion object {
        private val SDK_VERSION = Regex("\\d+\\.\\d+\\.\\d+")
        private val SHA256_HEX = Regex("[0-9a-f]{64}")

        fun pinned(sdkVersion: String = BuildConfig.SDK_VERSION): OtaEntryVerifier =
            OtaEntryVerifier(OtaManifest.PINNED_PUBLIC_KEYS.map(OtaManifest::decodePublicKey), sdkVersion)
    }
}

/**
 * What a verified manifest entry promises about its zip. Stored next to a downloaded zip so a
 * later install can re-check it; a zip without this record (e.g. left by SDK <= 1.0.23) is never installed.
 */
internal data class OtaArtifact(
    val mode: String,
    val patch: Int,
    val sha256: String,
    val size: Long
) {
    fun write(zip: File) {
        metaFile(zip).writeText(listOf(mode, patch, sha256, size).joinToString("|"))
    }

    /** True when [zip] still has exactly the promised size and SHA-256. */
    fun matches(zip: File): Boolean =
        zip.isFile && zip.length() == size && MessageDigest.isEqual(sha256Hex(zip).toByteArray(), sha256.toByteArray())

    companion object {
        fun metaFile(zip: File): File = File(zip.parentFile, zip.name + ".meta")

        fun read(zip: File): OtaArtifact? {
            val parts = try {
                metaFile(zip).takeIf { it.isFile }?.readText()?.split("|")
            } catch (_: Exception) {
                null
            } ?: return null
            if (parts.size != 4) return null
            val patch = parts[1].toIntOrNull() ?: return null
            val size = parts[3].toLongOrNull() ?: return null
            if (!OtaManifest.MODE_NAME.matches(parts[0]) || patch < 1 || size < 1 || !Regex("[0-9a-f]{64}").matches(parts[2])) return null
            return OtaArtifact(parts[0], patch, parts[2], size)
        }

        fun sha256Hex(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return hex(digest.digest())
        }

        fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
    }
}
