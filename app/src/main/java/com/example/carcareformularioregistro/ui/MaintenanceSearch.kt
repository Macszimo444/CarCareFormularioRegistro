package com.example.carcareformularioregistro.ui

import com.example.carcareformularioregistro.data.Maintenance
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class MaintenanceFilter(val status: String?) {
    ALL(null),
    UPCOMING(Maintenance.STATUS_PROXIMO),
    COMPLETED(Maintenance.STATUS_REALIZADO)
}

/** No database or Android dependency: text and tab filters always apply together. */
object MaintenanceSearch {
    private val accents = "\\p{M}+".toRegex()
    private val spaces = "\\s+".toRegex()
    private val numberSeparator = "(?<=\\d)[,.\\s](?=\\d{3}(?:\\D|$))".toRegex()
    private val displayDate = DateTimeFormatter.ofPattern("dd/MM/uuuu")

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(accents, "")
            .lowercase(Locale.ROOT)
            .replace(numberSeparator, "")
            .trim()

    private fun dateVariants(value: String): String {
        val parsed = runCatching { LocalDate.parse(value) }.getOrNull()
            ?: runCatching { LocalDate.parse(value, displayDate) }.getOrNull()
        return if (parsed == null) value else "$value $parsed ${parsed.format(displayDate)}"
    }

    fun filter(
        items: List<Maintenance>,
        query: String,
        filter: MaintenanceFilter
    ): List<Maintenance> {
        val words = normalize(query).split(spaces).filter { it.isNotEmpty() }
        return items.filter { item ->
            val matchesStatus = filter.status == null || item.status == filter.status
            matchesStatus && (words.isEmpty() || run {
                // The existing type field is also the service name; no duplicate column.
                val searchable = normalize(
                    listOf(
                        item.type, item.workshop, item.description, item.status,
                        item.mileage.toString(), item.nextMileage.toString(),
                        dateVariants(item.date), dateVariants(item.nextDate)
                    ).joinToString(" ")
                )
                words.all { searchable.contains(it) }
            })
        }
    }
}
