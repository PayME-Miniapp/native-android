package com.payme.sdk.ui.miniapp.source

internal data class StoredUpdateState(
    val mode: String,
    val patch: Int
)

internal data class RemoteUpdateMode(
    val patch: Int,
    val latestMandatoryPatch: Int,
    val url: String,
    val sha256: String = "",
    val size: Long = 0L,
    val sig: String = ""
)

internal data class UpdateStateMutation(
    val mode: String,
    val patch: Int
)

internal sealed class UpdateDecision {
    data object NoUpdate : UpdateDecision()
    data object LoadDefaultSource : UpdateDecision()
    data class BackgroundUpdate(val url: String, val patch: Int, val artifact: OtaArtifact) : UpdateDecision()
    data class MandatoryUpdate(val url: String, val patch: Int, val artifact: OtaArtifact) : UpdateDecision()
}

internal data class UpdateDecisionResult(
    val decision: UpdateDecision,
    val mutation: UpdateStateMutation? = null
)

internal class UpdateDecisionResolver(
    private val isTrusted: (RemoteUpdateMode, String) -> Boolean = { entry, mode -> PINNED_VERIFIER.isTrusted(entry, mode) }
) {
    fun resolve(
        versionLookup: VersionLookupResult?,
        currentMode: String,
        storedState: StoredUpdateState
    ): UpdateDecisionResult {
        val modeJson = versionLookup?.modeJson?.optJSONObject(currentMode)
            ?: return UpdateDecisionResult(UpdateDecision.NoUpdate)
        return resolve(
            remoteMode = RemoteUpdateMode(
                patch = modeJson.optInt("patch", 0),
                latestMandatoryPatch = modeJson.optInt("latestMandatoryPatch", 0),
                url = modeJson.optString("url"),
                sha256 = modeJson.optString("sha256", ""),
                size = modeJson.optLong("size", 0L),
                sig = modeJson.optString("sig", "")
            ),
            currentMode = currentMode,
            storedState = storedState
        )
    }

    fun resolve(
        remoteMode: RemoteUpdateMode?,
        currentMode: String,
        storedState: StoredUpdateState
    ): UpdateDecisionResult {
        remoteMode ?: return UpdateDecisionResult(UpdateDecision.NoUpdate)
        // patch 0 + latestMandatoryPatch 0 is the kill switch (bundled source): no download, no sig.
        // Anything else must be signed and well-formed, or it is treated like an invalid manifest.
        val isKillSwitch = remoteMode.patch == 0 && remoteMode.latestMandatoryPatch == 0
        if (!isKillSwitch && !isTrusted(remoteMode, currentMode)) {
            return UpdateDecisionResult(UpdateDecision.NoUpdate)
        }
        val mutation = if (currentMode != storedState.mode) {
            UpdateStateMutation(
                mode = currentMode,
                patch = if (storedState.mode.isEmpty()) 0 else -1
            )
        } else {
            null
        }
        val localPatch = mutation?.patch ?: storedState.patch
        return resolve(remoteMode, currentMode, localPatch, mutation)
    }

    /**
     * Install-time check for a downloaded zip: same mode, and never lower than the installed patch
     * of that mode (anti-rollback). Another mode's patch number does not apply.
     */
    fun acceptsPendingUpdate(pending: OtaArtifact, currentMode: String, storedState: StoredUpdateState): Boolean =
        pending.mode == currentMode && (storedState.mode != pending.mode || pending.patch >= storedState.patch)

    private fun resolve(
        remoteMode: RemoteUpdateMode,
        currentMode: String,
        localPatch: Int,
        mutation: UpdateStateMutation?
    ): UpdateDecisionResult {
        val artifact = OtaArtifact(currentMode, remoteMode.patch, remoteMode.sha256, remoteMode.size)
        val decision = when {
            remoteMode.patch == 0 && remoteMode.latestMandatoryPatch == 0 -> UpdateDecision.LoadDefaultSource
            // Anti-rollback: never download a patch at or below the installed one.
            remoteMode.patch <= localPatch -> UpdateDecision.NoUpdate
            localPatch < remoteMode.latestMandatoryPatch -> {
                UpdateDecision.MandatoryUpdate(remoteMode.url, remoteMode.patch, artifact)
            }
            else -> UpdateDecision.BackgroundUpdate(remoteMode.url, remoteMode.patch, artifact)
        }
        return UpdateDecisionResult(decision, mutation)
    }

    private companion object {
        val PINNED_VERIFIER by lazy { OtaEntryVerifier.pinned() }
    }
}
