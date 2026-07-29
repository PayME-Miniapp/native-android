package com.example.sdk

import com.payme.sdk.utils.Utils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class UtilsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun unzipSkipsMacOsFilesAndPathTraversal() {
        val zip = temporaryFolder.newFile("source.zip")
        val destination = temporaryFolder.newFolder("destination")
        val outside = File(destination.parentFile, "evil.txt")

        ZipOutputStream(zip.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("safe.txt"))
            output.write("safe".toByteArray())
            output.closeEntry()

            output.putNextEntry(ZipEntry("__MACOSX/._safe.txt"))
            output.write("skip".toByteArray())
            output.closeEntry()

            output.putNextEntry(ZipEntry("../evil.txt"))
            output.write("evil".toByteArray())
            output.closeEntry()
        }

        assertTrue(Utils.unzipFile(zip.absolutePath, destination.absolutePath))
        assertTrue(File(destination, "safe.txt").isFile)
        assertFalse(outside.exists())
    }

    @Test
    fun formatStringToValidJsonStringUnwrapsEscapedJson() {
        assertEquals(
            """{"a":1}""",
            Utils.formatStringToValidJsonString(""""{\"a\":1}"""")
        )
    }
}
