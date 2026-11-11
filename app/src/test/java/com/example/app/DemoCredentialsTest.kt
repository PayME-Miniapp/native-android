package com.example.app

import org.junit.Assert.*
import org.junit.Test

class DemoCredentialsTest {
    @Test fun identityTokenIsTrimmedAndBlankMeansNone() {
        assertNull(identityTokenOrNull(null))
        assertNull(identityTokenOrNull("   "))
        assertEquals("dummy-identity-token", identityTokenOrNull("  dummy-identity-token \n"))
    }
}
