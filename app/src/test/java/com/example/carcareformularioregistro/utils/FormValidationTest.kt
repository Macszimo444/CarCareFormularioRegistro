package com.example.carcareformularioregistro.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormValidationTest {
    @Test fun datesAcceptBothFormatsAndRejectImpossibleDays() {
        assertEquals("2026-09-15", FormValidation.date("15/09/2026"))
        assertEquals("2026-09-15", FormValidation.date("2026-09-15"))
        assertEquals("2024-02-29", FormValidation.date("29/02/2024"))
        assertNull(FormValidation.date("29/02/2026"))
        assertNull(FormValidation.date("2026-02-30"))
        assertNull(FormValidation.date("+999999999-01-01"))
        assertNull(FormValidation.date(""))
    }

    @Test fun mileageRejectsOverflowNegativeFractionAndMissingValues() {
        assertEquals(120000, FormValidation.mileage("120000"))
        assertEquals(0, FormValidation.mileage("0"))
        assertNull(FormValidation.mileage("2147483648"))
        assertNull(FormValidation.mileage("-1"))
        assertNull(FormValidation.mileage("12.5"))
        assertNull(FormValidation.mileage(""))
    }

    @Test fun amountsAcceptDecimalCommaAndRejectInvalidMoney() {
        assertEquals(1250.50, FormValidation.amount("1250.50")!!, 0.0001)
        assertEquals(1250.50, FormValidation.amount("1250,50")!!, 0.0001)
        assertEquals(0.0, FormValidation.amount("0")!!, 0.0)
        assertNull(FormValidation.amount("-1"))
        assertNull(FormValidation.amount("1.999"))
        assertNull(FormValidation.amount("NaN"))
        assertNull(FormValidation.amount("1,250.50"))
        assertNull(FormValidation.amount(""))
    }
}
