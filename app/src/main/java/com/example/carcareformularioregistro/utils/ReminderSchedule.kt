package com.example.carcareformularioregistro.utils

import com.example.carcareformularioregistro.data.Reminder
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Local wall-clock time. All-day describes the task; 09:00 is only its notification time. */
object ReminderSchedule {
    val allDayNotice: LocalTime = LocalTime.of(9, 0)
    fun time(value: String?): LocalTime? = value?.takeIf { it.matches(Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) }
        ?.let(LocalTime::parse)
    fun triggerAt(reminder: Reminder, zone: ZoneId = ZoneId.systemDefault()): ZonedDateTime? {
        val date = FormValidation.date(reminder.dueDate)?.let(LocalDate::parse) ?: return null
        val time = if (reminder.dueTime == null) allDayNotice else time(reminder.dueTime) ?: return null
        return date.atTime(time).atZone(zone)
    }
    fun label(reminder: Reminder): String = if (reminder.dueTime == null) "Todo el día" else "A las ${reminder.dueTime}"
}
