package com.example.carcareformularioregistro.utils

import com.example.carcareformularioregistro.data.Reminder
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ReminderScheduleTest {
    private val reminder = Reminder(id = 1, vehicleId = 1, title = "Taller", dueDate = "2026-10-10")
    @Test fun allDayAndTimedTasksHaveDifferentNotificationTimes() {
        val zone = ZoneId.of("America/Mexico_City")
        assertEquals(LocalTime.of(9, 0), ReminderSchedule.triggerAt(reminder, zone)?.toLocalTime())
        assertEquals(LocalTime.of(10, 30), ReminderSchedule.triggerAt(reminder.copy(dueTime = "10:30"), zone)?.toLocalTime())
        assertEquals(LocalTime.MIDNIGHT, ReminderSchedule.triggerAt(reminder.copy(dueTime = "00:00"), zone)?.toLocalTime())
    }
    @Test fun mileageOnlyHasNoDateAlarmAndInvalidTimesAreRejected() {
        assertNull(ReminderSchedule.triggerAt(reminder.copy(dueDate = "", dueMileage = 100000)))
        listOf("24:00", "10:60", "9:30", "10:30:00", "garbage").forEach {
            assertNull(ReminderSchedule.time(it))
            assertNull(ReminderSchedule.triggerAt(reminder.copy(dueTime = it)))
        }
    }
    @Test fun aFutureAppointmentTodayIsNotReportedAsReachedAndEarlierAppointmentComesFirst() {
        val later = reminder.copy(id = 2, dueTime = "15:00")
        val earlier = reminder.copy(dueTime = "10:30")
        val today = LocalDate.parse(reminder.dueDate)
        val morning = UpcomingReviews.next(emptyList(), listOf(later, earlier), 0, today, LocalTime.of(9, 0))!!
        assertEquals(1, morning.reminderId)
        assertFalse(morning.overdue)
        assertTrue(morning.details.contains("10:30"))
        assertTrue(UpcomingReviews.next(emptyList(), listOf(earlier), 0, today, LocalTime.of(10, 30))!!.overdue)
    }
    @Test fun timeZoneChangesKeepTheLocalAppointmentTime() {
        val item = reminder.copy(dueTime = "10:30")
        val first = ReminderSchedule.triggerAt(item, ZoneId.of("UTC"))!!
        val second = ReminderSchedule.triggerAt(item, ZoneId.of("America/Mexico_City"))!!
        assertEquals(first.toLocalTime(), second.toLocalTime())
        assertNotEquals(first.toInstant(), second.toInstant())
    }
}
