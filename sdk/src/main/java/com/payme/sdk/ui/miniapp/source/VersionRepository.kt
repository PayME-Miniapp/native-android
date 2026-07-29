package com.payme.sdk.ui.miniapp.source

import android.content.Context
import com.payme.sdk.BuildConfig
import com.payme.sdk.models.PayMEVersion
import com.payme.sdk.utils.Utils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class VersionLookupResult(
    val version: String,
    val modeJson: JSONObject?
)

internal class VersionRepository {
    fun fetchVersionForCurrentSdk(context: Context): VersionLookupResult? {
        val versionFile = File(context.filesDir.path, "version.json")
        if (versionFile.exists()) {
            versionFile.delete()
        }
        versionFile.createNewFile()
        Utils.downloadWithoutTemp(MiniAppSourceConstants.VERSION_MANIFEST_URL, versionFile.absolutePath)
        return findVersion(versionFile.readText(Charsets.UTF_8), BuildConfig.SDK_VERSION)
    }

    fun findVersion(jsonString: String, sdkVersion: String): VersionLookupResult? {
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            if (item.getString("version") == sdkVersion) {
                return VersionLookupResult(
                    version = item.getString("version"),
                    modeJson = item
                )
            }
        }
        return null
    }

    fun buildPayMEVersion(mode: JSONObject, version: String, localPatch: Int): PayMEVersion {
        val patch = mode.optInt("patch", 0)
        val latestMandatory = mode.optInt("latestMandatoryPatch", 0)
        val url = mode.optString("url")
        return PayMEVersion(
            patch = patch,
            version = version,
            mandatory = localPatch < latestMandatory,
            url = url
        )
    }
}
