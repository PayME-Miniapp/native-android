package com.example.sdk

import com.payme.sdk.ui.miniapp.DeviceCapabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/** Google Play (27/01/2027, target API 37): SDK không được kéo READ_CONTACTS vào app đối tác. */
class ContactPermissionRemovalTest {
    @Test fun manifestDoesNotDeclareReadContacts() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertFalse(manifest.contains("READ_CONTACTS"))
    }

    @Test fun sdkSourceNeverChecksOrRequestsReadContacts() {
        val hits = File("src/main/java").walkTopDown()
            .filter { it.extension == "kt" }
            .filter { it.readText().contains("Manifest.permission.READ_CONTACTS") }
            .map { it.name }
            .toList()
        assertEquals(emptyList<String>(), hits)
    }

    @Test fun capabilitiesAdvertiseContactPicker() {
        assertEquals(listOf("PICK_CONTACT"), DeviceCapabilities.ALL)
    }
}
