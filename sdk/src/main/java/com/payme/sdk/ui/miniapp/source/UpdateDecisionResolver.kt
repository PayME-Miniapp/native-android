package com.payme.sdk.ui.miniapp.source

internal data class StoredUpdateState(
    val mode: String,
    val patch: Int
)

internal data class RemoteUpdateMode(
    val patch: Int,
    val latestMandatoryPatch: Int,
    val url: String
)

internal data class UpdateStateMutation(
    val mode: String,
    val patch: Int
)

internal sealed class UpdateDecision {
    data object NoUpdate : UpdateDecision()
    data object LoadDefaultSource : UpdateDecision()
    data class BackgroundUpdate(val url: String, val patch: Int) : UpdateDecision()
    data class MandatoryUpdate(val url: String, val patch: Int) : UpdateDecision()
}

internal data class UpdateDecisionResult(
    val decision: UpdateDecision,
    val mutation: UpdateStateMutation? = null
)

internal class UpdateDecisionResolver {
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
                url = modeJson.optString("url")
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
        val mutation = if (currentMode != storedState.mode) {
            UpdateStateMutation(
                mode = currentMode,
                patch = if (storedState.mode.isEmpty()) 0 else -1
            )
        } else {
            null
        }
        val localPatch = mutation?.patch ?: storedState.patch
        return resolve(remoteMode, localPatch, mutation)
    }

    private fun resolve(
        remoteMode: RemoteUpdateMode,
        localPatch: Int,
        mutation: UpdateStateMutation?
    ): UpdateDecisionResult {
        val decision = when {
            remoteMode.patch == 0 && remoteMode.latestMandatoryPatch == 0 -> UpdateDecision.LoadDefaultSource
            remoteMode.patch <= localPatch -> UpdateDecision.NoUpdate
            localPatch < remoteMode.latestMandatoryPatch -> {
                UpdateDecision.MandatoryUpdate(remoteMode.url, remoteMode.patch)
            }
            else -> UpdateDecision.BackgroundUpdate(remoteMode.url, remoteMode.patch)
        }
        return UpdateDecisionResult(decision, mutation)
    }
}
