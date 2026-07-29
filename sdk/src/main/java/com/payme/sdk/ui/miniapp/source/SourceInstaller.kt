package com.payme.sdk.ui.miniapp.source

import android.content.Context
import com.payme.sdk.utils.ArchiveUtils
import java.io.File

internal enum class SourceInstallFailureReason {
    MISSING_UPDATE_ZIP,
    INSUFFICIENT_STORAGE,
    BUNDLED_SOURCE_COPY_FAILED,
    UNZIP_FAILED,
    SWAP_FAILED
}

internal data class SourceInstallResult(
    val success: Boolean,
    val failureReason: SourceInstallFailureReason? = null
) {
    companion object {
        fun success(): SourceInstallResult {
            return SourceInstallResult(success = true)
        }

        fun failure(reason: SourceInstallFailureReason): SourceInstallResult {
            return SourceInstallResult(success = false, failureReason = reason)
        }
    }
}

internal class SourceInstaller internal constructor(
    private val filesDir: File,
    private val unzipFile: (File, File) -> Boolean,
    private val copyBundledSource: (File) -> Boolean = { false },
    private val renameDirectory: (File, File) -> Boolean = { source, destination -> source.renameTo(destination) },
    private val storageSpaceChecker: StorageSpaceChecker = StorageSpaceChecker.unbounded()
) {
    constructor(context: Context) : this(
        filesDir = context.filesDir,
        unzipFile = { source: File, destination: File ->
            ArchiveUtils.unzipFile(source.absolutePath, destination.absolutePath)
        },
        copyBundledSource = { destination: File ->
            ArchiveUtils.copyDir(context, MiniAppSourceConstants.WWW_ASSET_DIR, destination)
        },
        renameDirectory = { source: File, destination: File ->
            source.renameTo(destination)
        },
        storageSpaceChecker = StorageSpaceChecker(context)
    )

    val updateZip: File
        get() = File(File(filesDir, MiniAppSourceConstants.UPDATE_DIR), MiniAppSourceConstants.SOURCE_ZIP)

    val updateTempZip: File
        get() = File(File(filesDir, MiniAppSourceConstants.UPDATE_DIR), MiniAppSourceConstants.SOURCE_TEMP_ZIP)

    val wwwDirectory: File
        get() = File(filesDir, MiniAppSourceConstants.WWW_DIR)

    val stagingDirectory: File
        get() = File(filesDir, STAGING_DIR)

    val backupDirectory: File
        get() = File(filesDir, BACKUP_DIR)

    val wwwRoot: File
        get() = findWebRoot(wwwDirectory) ?: File(wwwDirectory, MiniAppSourceConstants.SOURCE_ROOT)

    fun prepareUpdateDirectory(): File {
        val updateDirectory = File(filesDir, MiniAppSourceConstants.UPDATE_DIR)
        if (!updateDirectory.exists()) {
            updateDirectory.mkdirs()
        }
        return updateDirectory
    }

    fun installUpdatedSource(): Boolean {
        return installUpdatedSourceDetailed().success
    }

    fun installUpdatedSourceDetailed(): SourceInstallResult {
        if (!updateZip.exists() || updateZip.length() == 0L) {
            return SourceInstallResult.failure(SourceInstallFailureReason.MISSING_UPDATE_ZIP)
        }

        cleanWorkingDirectories()
        if (!storageSpaceChecker.hasEnoughSpace(updateZip.length())) {
            cleanUpdateArtifacts()
            return SourceInstallResult.failure(SourceInstallFailureReason.INSUFFICIENT_STORAGE)
        }

        stagingDirectory.mkdirs()
        val unzipped = unzipFile(updateZip, stagingDirectory)
        if (!unzipped || findWebRoot(stagingDirectory) == null) {
            stagingDirectory.deleteRecursively()
            return SourceInstallResult.failure(SourceInstallFailureReason.UNZIP_FAILED)
        }

        val swapped = swapStagingIntoPlace()
        if (swapped) {
            updateZip.delete()
        }
        if (!swapped || findWebRoot(wwwDirectory) == null) {
            return SourceInstallResult.failure(SourceInstallFailureReason.SWAP_FAILED)
        }
        return SourceInstallResult.success()
    }

    fun ensureExistingOrBundledSource(): Boolean {
        return ensureExistingOrBundledSourceDetailed().success
    }

    fun ensureExistingOrBundledSourceDetailed(): SourceInstallResult {
        if (findWebRoot(wwwDirectory) != null) {
            return SourceInstallResult.success()
        }
        return installBundledSourceDetailed()
    }

    fun installBundledSource(): Boolean {
        return installBundledSourceDetailed().success
    }

    fun installBundledSourceDetailed(): SourceInstallResult {
        cleanWorkingDirectories()
        stagingDirectory.mkdirs()
        if (!copyBundledSource(stagingDirectory)) {
            stagingDirectory.deleteRecursively()
            return SourceInstallResult.failure(SourceInstallFailureReason.BUNDLED_SOURCE_COPY_FAILED)
        }

        val bundledZip = File(stagingDirectory, MiniAppSourceConstants.SOURCE_ZIP)
        if (bundledZip.length() > 0L && !storageSpaceChecker.hasEnoughSpace(bundledZip.length())) {
            stagingDirectory.deleteRecursively()
            backupDirectory.deleteRecursively()
            updateTempZip.delete()
            return SourceInstallResult.failure(SourceInstallFailureReason.INSUFFICIENT_STORAGE)
        }

        val unzipped = unzipFile(bundledZip, stagingDirectory)
        if (!unzipped || findWebRoot(stagingDirectory) == null) {
            stagingDirectory.deleteRecursively()
            return SourceInstallResult.failure(SourceInstallFailureReason.UNZIP_FAILED)
        }

        if (!swapStagingIntoPlace() || findWebRoot(wwwDirectory) == null) {
            return SourceInstallResult.failure(SourceInstallFailureReason.SWAP_FAILED)
        }
        return SourceInstallResult.success()
    }

    fun cleanUpdateArtifacts() {
        updateZip.delete()
        updateTempZip.delete()
        cleanWorkingDirectories()
    }

    private fun cleanWorkingDirectories() {
        stagingDirectory.deleteRecursively()
        backupDirectory.deleteRecursively()
    }

    private fun swapStagingIntoPlace(): Boolean {
        val hadExistingSource = wwwDirectory.exists()
        if (hadExistingSource && !renameDirectory(wwwDirectory, backupDirectory)) {
            stagingDirectory.deleteRecursively()
            return false
        }

        if (renameDirectory(stagingDirectory, wwwDirectory)) {
            backupDirectory.deleteRecursively()
            return true
        }

        if (hadExistingSource) {
            wwwDirectory.deleteRecursively()
            renameDirectory(backupDirectory, wwwDirectory)
        }
        stagingDirectory.deleteRecursively()
        return false
    }

    companion object {
        fun findWebRoot(wwwDirectory: File): File? {
            if (!wwwDirectory.exists() || !wwwDirectory.isDirectory) {
                return null
            }

            val preferredRoots = listOf(
                wwwDirectory,
                File(wwwDirectory, MiniAppSourceConstants.SOURCE_ROOT),
                File(wwwDirectory, "build"),
                File(wwwDirectory, "dist")
            )
            preferredRoots.firstOrNull { hasIndexFile(it) }?.let { return it }

            return wwwDirectory.walkTopDown()
                .maxDepth(MAX_WEB_ROOT_SEARCH_DEPTH)
                .filter { it.isFile && it.name == INDEX_FILE_NAME }
                .mapNotNull { it.parentFile }
                .sortedWith(compareBy<File> { depthFrom(wwwDirectory, it) }.thenBy { it.absolutePath })
                .firstOrNull()
        }

        private fun hasIndexFile(directory: File): Boolean {
            return File(directory, INDEX_FILE_NAME).isFile
        }

        private fun depthFrom(baseDirectory: File, candidate: File): Int {
            val basePath = baseDirectory.absoluteFile.normalizePath()
            val candidatePath = candidate.absoluteFile.normalizePath()
            val relativePath = candidatePath.removePrefix(basePath).trim(File.separatorChar)
            if (relativePath.isEmpty()) {
                return 0
            }
            return relativePath.split(File.separatorChar).size
        }

        private fun File.normalizePath(): String {
            return try {
                canonicalPath
            } catch (e: Exception) {
                absolutePath
            }
        }

        private const val INDEX_FILE_NAME = "index.html"
        private const val MAX_WEB_ROOT_SEARCH_DEPTH = 4
        internal const val STAGING_DIR = "www_staging"
        internal const val BACKUP_DIR = "www_backup"
    }
}
