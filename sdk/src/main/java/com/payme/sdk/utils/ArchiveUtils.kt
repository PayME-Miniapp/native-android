package com.payme.sdk.utils

import android.content.Context
import android.util.Log
import com.payme.sdk.PayMEMiniApp
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

internal object ArchiveUtils {
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

    private fun unzipEntries(zipStream: ZipInputStream, destination: File): Boolean {
        var entry: ZipEntry?
        var unzipSuccess = false
        val destinationCanonicalPath = destination.canonicalPath

        while (zipStream.nextEntry.also { entry = it } != null) {
            val zipEntry = entry ?: continue
            val entryName = zipEntry.name
            if (shouldSkipEntry(entryName)) {
                Log.d(PayMEMiniApp.TAG, "Skipping macOS special file: $entryName")
                continue
            }

            val outputFile = File(destination, entryName)
            if (!isSafeOutputPath(outputFile, destinationCanonicalPath)) {
                continue
            }

            if (zipEntry.isDirectory) {
                outputFile.mkdirs()
            } else {
                outputFile.parentFile?.mkdirs()
                FileOutputStream(outputFile).buffered().use { output ->
                    zipStream.copyTo(output)
                }
                unzipSuccess = true
            }
        }

        return if (unzipSuccess) {
            Log.d(PayMEMiniApp.TAG, "Unzipping complete. path: ${destination.absolutePath}")
            true
        } else {
            Log.e(PayMEMiniApp.TAG, "No files were successfully extracted to ${destination.absolutePath}")
            false
        }
    }

    private fun shouldSkipEntry(entryName: String): Boolean {
        return entryName.contains("__MACOSX") || File(entryName).name.startsWith("._")
    }

    private fun isSafeOutputPath(outputFile: File, destinationCanonicalPath: String): Boolean {
        return try {
            val outputFileCanonicalPath = outputFile.canonicalPath
            if (!outputFileCanonicalPath.startsWith(destinationCanonicalPath)) {
                Log.e(
                    PayMEMiniApp.TAG,
                    "Security warning: Path traversal detected with $outputFileCanonicalPath"
                )
                false
            } else {
                true
            }
        } catch (e: Exception) {
            Log.e(PayMEMiniApp.TAG, "Error checking path for: ${outputFile.path} - ${e.message}")
            false
        }
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
