package com.example.sdk

import com.payme.sdk.ui.miniapp.source.MiniAppSourceConstants
import com.payme.sdk.ui.miniapp.source.SourceInstallFailureReason
import com.payme.sdk.ui.miniapp.source.SourceInstaller
import com.payme.sdk.ui.miniapp.source.StorageSpaceChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SourceInstallerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun sourceInstallerFindsReactBuildRoot() {
        val www = temporaryFolder.newFolder("www")
        val buildRoot = File(www, "build").apply { mkdirs() }
        File(buildRoot, "index.html").writeText("<html></html>")

        assertEquals(buildRoot.canonicalFile, SourceInstaller.findWebRoot(www)?.canonicalFile)
    }

    @Test
    fun sourceInstallerFindsLegacySourceRoot() {
        val www = temporaryFolder.newFolder("www")
        val legacyRoot = File(www, MiniAppSourceConstants.SOURCE_ROOT).apply { mkdirs() }
        File(legacyRoot, "index.html").writeText("<html></html>")

        assertEquals(legacyRoot.canonicalFile, SourceInstaller.findWebRoot(www)?.canonicalFile)
    }

    @Test
    fun sourceInstallerFindsDirectBuildContents() {
        val www = temporaryFolder.newFolder("www")
        File(www, "index.html").writeText("<html></html>")

        assertEquals(www.canonicalFile, SourceInstaller.findWebRoot(www)?.canonicalFile)
    }

    @Test
    fun invalidUpdateZipDoesNotDeleteExistingSource() {
        val filesDir = temporaryFolder.newFolder("files")
        val www = File(filesDir, MiniAppSourceConstants.WWW_DIR).apply { mkdirs() }
        File(www, "index.html").writeText("old source")
        val updateZip = File(
            File(filesDir, MiniAppSourceConstants.UPDATE_DIR).apply { mkdirs() },
            MiniAppSourceConstants.SOURCE_ZIP
        )
        updateZip.writeText("invalid")
        val installer = SourceInstaller(filesDir, unzipFile = { _, _ -> false })

        assertFalse(installer.installUpdatedSource())
        assertEquals("old source", File(www, "index.html").readText())
    }

    @Test
    fun validUpdateZipSwapsSourceAndCleansBackupAndZip() {
        val filesDir = temporaryFolder.newFolder("files")
        val www = File(filesDir, MiniAppSourceConstants.WWW_DIR).apply { mkdirs() }
        File(www, "index.html").writeText("old source")
        val updateZip = File(
            File(filesDir, MiniAppSourceConstants.UPDATE_DIR).apply { mkdirs() },
            MiniAppSourceConstants.SOURCE_ZIP
        )
        writeZip(updateZip, "build/index.html" to "new source")
        val installer = SourceInstaller(filesDir, unzipFile = ::unzipForTest)

        assertTrue(installer.installUpdatedSource())
        assertEquals("new source", File(filesDir, "www/build/index.html").readText())
        assertFalse(updateZip.exists())
        assertFalse(File(filesDir, SourceInstaller.BACKUP_DIR).exists())
    }

    @Test
    fun swapFailureRollsBackExistingSource() {
        val filesDir = temporaryFolder.newFolder("files")
        val www = File(filesDir, MiniAppSourceConstants.WWW_DIR).apply { mkdirs() }
        File(www, "index.html").writeText("old source")
        val updateZip = File(
            File(filesDir, MiniAppSourceConstants.UPDATE_DIR).apply { mkdirs() },
            MiniAppSourceConstants.SOURCE_ZIP
        )
        writeZip(updateZip, "index.html" to "new source")
        val installer = SourceInstaller(
            filesDir = filesDir,
            unzipFile = ::unzipForTest,
            renameDirectory = { source, destination ->
                if (source.name == SourceInstaller.STAGING_DIR &&
                    destination.name == MiniAppSourceConstants.WWW_DIR
                ) {
                    false
                } else {
                    source.renameTo(destination)
                }
            }
        )

        assertFalse(installer.installUpdatedSource())
        assertEquals("old source", File(www, "index.html").readText())
    }

    @Test
    fun bundledSourceInstallsThroughStaging() {
        val filesDir = temporaryFolder.newFolder("files")
        val installer = SourceInstaller(
            filesDir = filesDir,
            unzipFile = ::unzipForTest,
            copyBundledSource = { staging ->
                writeZip(File(staging, MiniAppSourceConstants.SOURCE_ZIP), "index.html" to "bundled source")
                true
            }
        )

        assertTrue(installer.installBundledSource())
        assertEquals("bundled source", File(filesDir, "www/index.html").readText())
        assertFalse(File(filesDir, SourceInstaller.STAGING_DIR).exists())
    }

    @Test
    fun bundledSourceStorageFailureKeepsExistingSourceAndCleansWorkingDirs() {
        val filesDir = temporaryFolder.newFolder("files")
        val www = File(filesDir, MiniAppSourceConstants.WWW_DIR).apply { mkdirs() }
        File(www, "index.html").writeText("old source")
        val installer = SourceInstaller(
            filesDir = filesDir,
            unzipFile = ::unzipForTest,
            copyBundledSource = { staging ->
                writeZip(File(staging, MiniAppSourceConstants.SOURCE_ZIP), "index.html" to "bundled source")
                true
            },
            storageSpaceChecker = StorageSpaceChecker { 1L }
        )

        val result = installer.installBundledSourceDetailed()

        assertFalse(result.success)
        assertEquals(SourceInstallFailureReason.INSUFFICIENT_STORAGE, result.failureReason)
        assertEquals("old source", File(www, "index.html").readText())
        assertFalse(File(filesDir, SourceInstaller.STAGING_DIR).exists())
        assertFalse(File(filesDir, SourceInstaller.BACKUP_DIR).exists())
        assertFalse(installer.updateTempZip.exists())
    }

    @Test
    fun unknownContentLengthInstallStorageFailureKeepsOldSource() {
        val filesDir = temporaryFolder.newFolder("files")
        val www = File(filesDir, MiniAppSourceConstants.WWW_DIR).apply { mkdirs() }
        File(www, "index.html").writeText("old source")
        val updateZip = File(
            File(filesDir, MiniAppSourceConstants.UPDATE_DIR).apply { mkdirs() },
            MiniAppSourceConstants.SOURCE_ZIP
        )
        writeZip(updateZip, "build/index.html" to "new source")
        val installer = SourceInstaller(
            filesDir = filesDir,
            unzipFile = ::unzipForTest,
            storageSpaceChecker = StorageSpaceChecker { 1L }
        )

        val installResult = installer.installUpdatedSourceDetailed()

        assertFalse(installResult.success)
        assertEquals(SourceInstallFailureReason.INSUFFICIENT_STORAGE, installResult.failureReason)
        assertEquals("old source", File(www, "index.html").readText())
        assertFalse(installer.updateZip.exists())
        assertFalse(installer.updateTempZip.exists())
        assertFalse(File(filesDir, SourceInstaller.STAGING_DIR).exists())
        assertFalse(File(filesDir, SourceInstaller.BACKUP_DIR).exists())
    }

    @Test
    fun storageSpaceCheckerUsesConservativeRequirement() {
        val checker = StorageSpaceChecker { Long.MAX_VALUE }

        assertEquals(StorageSpaceChecker.MIN_REQUIRED_BYTES, checker.requiredBytesFor(1L))
        assertEquals(150L * 1024L * 1024L, checker.requiredBytesFor(50L * 1024L * 1024L))
    }
}
