package com.example.app

import org.junit.Assert.*
import org.junit.Test

class DemoCredentialsTest {
    @Test fun requiresAllThreeLocalProperties() {
        assertFalse(hasDemoCredentials("", "dummy-public", "dummy-private"))
        assertFalse(hasDemoCredentials("dummy-app", " ", "dummy-private"))
        assertFalse(hasDemoCredentials("dummy-app", "dummy-public", ""))
        assertTrue(hasDemoCredentials("dummy-app", "dummy-public", "dummy-private"))
    }

    @Test fun identityTokenIsTrimmedAndBlankMeansNone() {
        assertNull(identityTokenOrNull(null))
        assertNull(identityTokenOrNull("   "))
        assertEquals("dummy-identity-token", identityTokenOrNull("  dummy-identity-token \n"))
    }
}
