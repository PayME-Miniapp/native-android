package com.example.sdk

import com.payme.sdk.presentation.AccountPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * org.json của Android trả chuỗi "null" khi optString gặp JSON null, nên SDK phải đọc giá trị thô (opt).
 * Any() đại diện JSONObject.NULL (không phải String).
 */
class AccessTokenTest {
    @Test fun nullMissingOrBlankTokenMeansNotLinked() {
        assertNull(AccountPresentation.accessTokenOf(null))
        assertNull(AccountPresentation.accessTokenOf(Any()))
        assertNull(AccountPresentation.accessTokenOf(""))
        assertNull(AccountPresentation.accessTokenOf("   "))
    }

    @Test fun realTokenIsKept() {
        assertEquals("tok-123", AccountPresentation.accessTokenOf("tok-123"))
    }

    @Test fun balanceMessageFallsBackWhenNullOrBlank() {
        assertEquals("Mặc định", AccountPresentation.messageOf(null, "Mặc định"))
        assertEquals("Mặc định", AccountPresentation.messageOf(Any(), "Mặc định"))
        assertEquals("Mặc định", AccountPresentation.messageOf("", "Mặc định"))
        assertEquals("Thành công", AccountPresentation.messageOf("Thành công", "Mặc định"))
    }
}
