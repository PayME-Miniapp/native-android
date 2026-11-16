package com.example.sdk

import com.google.gson.JsonParser
import com.payme.sdk.utils.PickedContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PickedContactTest {
    @Test fun keepsDigitsOnlyAndEscapesName() {
        val o = JsonParser.parseString(PickedContact.json("a\"b</script>", "+84 (90) 123.4567")).asJsonObject
        assertEquals("a\"b</script>", o["name"].asString)
        assertEquals("84901234567", o["phone"].asString)
    }

    @Test fun nullNameBecomesEmpty() {
        val o = JsonParser.parseString(PickedContact.json(null, "0901234567")).asJsonObject
        assertEquals("", o["name"].asString)
        assertEquals("0901234567", o["phone"].asString)
    }

    @Test fun noDigitsMeansNothingToSend() {
        assertNull(PickedContact.json("A", null))
        assertNull(PickedContact.json("A", "abc"))
    }
}
