package com.example.carcareformularioregistro.utils

import org.junit.Assert.*
import org.junit.Test

class ProfileValidationTest {
    @Test fun namesAllowAccentsHyphensAndApostrophesWithoutNumbers() {
        assertTrue(ProfileValidation.name("María José"))
        assertTrue(ProfileValidation.name("O’Connor-López"))
        assertFalse(ProfileValidation.name("   "))
        assertFalse(ProfileValidation.name("Ana123"))
        assertFalse(ProfileValidation.name("A".repeat(61)))
    }
    @Test fun phoneIsOptionalAndMexicoNumberFormattingDoesNotChangeIdentity() {
        assertEquals("", ProfileValidation.phone(" "))
        assertEquals("5512345678", ProfileValidation.phone("55-1234-5678"))
        assertEquals("5512345678", ProfileValidation.phone("+52 (55) 1234 5678"))
        assertNull(ProfileValidation.phone("551234567"))
        assertNull(ProfileValidation.phone("+1 5512345678"))
        assertNull(ProfileValidation.phone("55abcd5678"))
    }
}
