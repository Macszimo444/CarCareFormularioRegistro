package com.example.carcareformularioregistro.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Locale

/** Keeps dates sortable in Room while accepting the two formats shown to the user. */
object FormValidation {
    private val displayDate = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)

    fun date(value: String): String? {
        val text = value.trim()
        if (!text.matches(Regex("(?:[0-9]{4}-[0-9]{2}-[0-9]{2}|[0-9]{2}/[0-9]{2}/[0-9]{4})"))) return null
        val format = if (text.contains('/')) displayDate else DateTimeFormatter.ISO_LOCAL_DATE
        return try {
            LocalDate.parse(text, format).toString()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun mileage(value: String): Int? = value.trim().takeIf { it.matches(Regex("[0-9]+")) }
        ?.toIntOrNull()

    fun amount(value: String): Double? {
        val text = value.trim()
        if (!text.matches(Regex("[0-9]+(?:[.,][0-9]{1,2})?"))) return null
        return text.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
    }
}
