package com.example.carcareformularioregistro.utils

import com.example.carcareformularioregistro.data.Maintenance
import com.example.carcareformularioregistro.data.Reminder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** All targets come from user records; this does not estimate the mechanical health of a car. */
data class UpcomingReview(val title: String, val details: String, val maintenanceId: Int? = null,
    val reminderId: Int? = null, val overdue: Boolean = false, val days: Long? = null, val km: Int? = null)

object UpcomingReviews {
    fun next(maintenances: List<Maintenance>, reminders: List<Reminder>, mileage: Int,
             today: LocalDate = LocalDate.now()): UpcomingReview? {
        val linked = reminders.mapNotNull { it.maintenanceId }.toSet()
        val candidates = reminders.filter { it.enabled }.map {
            review(it.title, it.dueDate, it.dueMileage, mileage, today, reminderId = it.id)
        } + maintenances.filter {
            val legacyDefault = it.nextDate == it.date && it.nextMileage == it.mileage
            it.id !in linked && (it.status != Maintenance.STATUS_REALIZADO ||
                (!legacyDefault && (it.nextDate.isNotBlank() || it.nextMileage > 0)))
        }.map {
            val date = if (it.status == Maintenance.STATUS_REALIZADO) it.nextDate else it.date
            review(it.type, date, it.nextMileage, mileage, today, maintenanceId = it.id)
        }
        return candidates.sortedWith(compareByDescending<UpcomingReview> { it.overdue }
            .thenBy { it.days ?: Long.MAX_VALUE }.thenBy { it.km ?: Int.MAX_VALUE }).firstOrNull()
    }

    private fun review(title: String, date: String, targetMileage: Int, mileage: Int, today: LocalDate,
                       maintenanceId: Int? = null, reminderId: Int? = null): UpcomingReview {
        val parsed = FormValidation.date(date)?.let(LocalDate::parse)
        val days = parsed?.let { ChronoUnit.DAYS.between(today, it) }
        val km = targetMileage.takeIf { it > 0 }?.minus(mileage)
        val dateText = days?.let {
            when { it < 0 -> "Fecha vencida hace ${-it} días"; it == 0L -> "Programado para hoy"
                else -> "Faltan $it días" } + " · ${parsed.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}"
        }
        val kmText = km?.let {
            when { it < 0 -> "Superaste el objetivo por ${-it} km"; it == 0 -> "Alcanzaste el kilometraje objetivo"
                else -> "Faltan $it km" } + " · objetivo $targetMileage km"
        }
        val details = listOfNotNull(dateText, kmText).joinToString("\n").ifBlank { "Sin fecha ni kilometraje objetivo" }
        return UpcomingReview(title, "$details\nSegún las fechas y el odómetro que registraste.",
            maintenanceId, reminderId, (days != null && days <= 0) || (km != null && km <= 0), days, km)
    }
}
