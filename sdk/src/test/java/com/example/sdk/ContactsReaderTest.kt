package com.example.sdk

import com.google.gson.JsonParser
import com.payme.sdk.utils.ContactsReader
import com.payme.sdk.utils.WebViewJsDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class ContactsReaderTest {
    @Test fun contactNamesAndNumbersSurviveJsonRoundTrip() {
        val hostile = "a\"b\\c\nd</script>e f g"
        val contacts = listOf(
            hostile to "+84 (90) 123\"45\\6\n7</script> ",
            "Nguyễn Văn Ánh – Đặng Thị Ửng" to "090.123.4567",
            "x\",phone:\"0999999999" to "0900000000",
            null to null
        )

        val json = ContactsReader.contactsJson(contacts)
        val parsed = JsonParser.parseString(json).asJsonArray

        assertEquals(4, parsed.size())
        val values = parsed.map { it.asJsonObject.let { o -> o["name"].asString to o["phone"].asString } }
        assertEquals(
            listOf(
                hostile to "84901234567",
                "Nguyễn Văn Ánh – Đặng Thị Ửng" to "0901234567",
                "x\",phone:\"0999999999" to "0900000000",
                "" to ""
            ),
            values
        )
        parsed.forEach { assertEquals(setOf("name", "phone"), it.asJsonObject.keySet()) }

        // The payload is strict JSON for the dispatcher and leaves no raw script terminator.
        val script = WebViewJsDispatcher.buildScript("nativeContacts", json)
        assertNotNull(script)
        assertFalse(script!!.contains("</script>"))
        assertFalse(script.contains(' '))
    }
}
