package com.payme.sdk.ui.miniapp.source

import android.content.Context
import com.payme.sdk.BuildConfig
import com.payme.sdk.models.PayMEVersion
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal data class VersionLookupResult(
    val version: String,
    val modeJson: JSONObject?
)

internal class VersionRepository(
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val maxManifestBytes: Long = MAX_MANIFEST_BYTES
) {
    fun fetchVersionForCurrentSdk(context: Context): VersionLookupResult? {
        val versionFile = File(context.filesDir.path, "version.json")
        downloadManifest(versionFile)
        return findVersion(versionFile.readText(Charsets.UTF_8), BuildConfig.SDK_VERSION)
    }

    internal fun downloadManifest(
        versionFile: File,
        url: String = MiniAppSourceConstants.VERSION_MANIFEST_URL
    ) {
        if (versionFile.exists()) {
            versionFile.delete()
        }
        versionFile.createNewFile()
        val connection = OtaConnection.open(url, connectionFactory)
        try {
            check(connection.responseCode in 200..299) { "Manifest download failed" }
            if (connection.contentLengthLong > maxManifestBytes) throw IOException("OTA manifest exceeds size limit")
            connection.inputStream.use { input ->
                versionFile.outputStream().use { output ->
                    // Content-Length can be absent or wrong: count what is actually streamed.
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maxManifestBytes) throw IOException("OTA manifest exceeds size limit")
                        output.write(buffer, 0, read)
                    }
                }
            }
        } catch (e: Exception) {
            versionFile.delete()
            throw e
        } finally {
            connection.disconnect()
        }
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

    internal companion object {
        /** The manifest is a small JSON list of SDK versions; 1 MiB is far above its expected size. */
        const val MAX_MANIFEST_BYTES = 1024L * 1024
    }
}
