package com.payme.sdk.utils

import android.content.Context
import android.util.Log
import com.payme.sdk.PayMEMiniApp
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

internal object ArchiveUtils {
    /** Upper bound for an OTA bundle: extracted bytes and downloaded zip bytes. */
    const val MAX_ARCHIVE_BYTES = 128L * 1024 * 1024

    fun unzipFile(filePath: String, destination: String): Boolean {
        return try {
            File(filePath).inputStream().use { inputStream ->
                ZipInputStream(inputStream).use { zipStream ->
                    unzipEntries(zipStream, File(destination))
                }
            }
        } catch (e: Exception) {
            Log.e(PayMEMiniApp.TAG, "Unzipping failed: ${e.message}")
            false
        }
    }

    fun copyDir(context: Context, path: String) {
        val assets = context.assets.list(path).orEmpty()
        assets.forEach { asset ->
            val assetPath = "$path/$asset"
            val childAssets = context.assets.list(assetPath).orEmpty()
            if (childAssets.isNotEmpty()) {
                File(context.filesDir.path, assetPath).mkdir()
                copyDir(context, assetPath)
            } else {
                copyAssetFile(context, assetPath, File(context.filesDir, assetPath))
            }
        }
    }

    fun copyDir(context: Context, path: String, destination: File): Boolean {
        return try {
            if (!destination.exists()) {
                destination.mkdirs()
            }
            copyAssetDirectory(context, path, destination)
        } catch (e: Exception) {
            false
        }
    }

    fun copyFileToFile(sourcePath: String, destinationPath: String) {
        File(sourcePath).copyTo(File(destinationPath), overwrite = true)
    }

    internal fun unzipEntries(
        zipStream: ZipInputStream,
        destination: File,
        maxBytes: Long = MAX_ARCHIVE_BYTES,
        maxEntries: Int = 10_000
    ): Boolean {
        val boundary = destination.canonicalPath + File.separator
        var total = 0L
        var count = 0
        var extracted = false
        val buffer = ByteArray(8192)
        while (true) {
            val entry = zipStream.nextEntry ?: break
            require(++count <= maxEntries) { "Archive entry limit exceeded" }
            val outputFile = File(destination, entry.name)
            require(outputFile.canonicalPath.startsWith(boundary)) { "Invalid archive path" }
            val skip = entry.name.contains("__MACOSX") || outputFile.name.startsWith("._")
            val output = if (!skip && !entry.isDirectory) {
                outputFile.parentFile?.mkdirs()
                FileOutputStream(outputFile).buffered()
            } else null
            output.use {
                while (true) {
                    val size = zipStream.read(buffer)
                    if (size == -1) break
                    total += size
                    require(total <= maxBytes) { "Archive size limit exceeded" }
                    it?.write(buffer, 0, size)
                }
            }
            if (!skip) {
                if (entry.isDirectory) outputFile.mkdirs() else extracted = true
            }
            zipStream.closeEntry()
        }
        return extracted
    }

    private fun copyAssetDirectory(context: Context, assetPath: String, destination: File): Boolean {
        val assets = context.assets.list(assetPath).orEmpty()
        for (asset in assets) {
            val childAssetPath = "$assetPath/$asset"
            val childDestination = File(destination, asset)
            val childAssets = context.assets.list(childAssetPath).orEmpty()
            if (childAssets.isNotEmpty()) {
                if (!childDestination.exists()) {
                    childDestination.mkdirs()
                }
                if (!copyAssetDirectory(context, childAssetPath, childDestination)) {
                    return false
                }
            } else {
                childDestination.parentFile?.mkdirs()
                copyAssetFile(context, childAssetPath, childDestination)
            }
        }
        return true
    }

    private fun copyAssetFile(context: Context, assetPath: String, destination: File) {
        destination.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }
}
