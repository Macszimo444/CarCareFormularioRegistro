package com.example.carcareformularioregistro.utils

import com.example.carcareformularioregistro.data.Maintenance
import com.example.carcareformularioregistro.data.Reminder
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class UpcomingReviewsTest {
    private val today = LocalDate.of(2026, 9, 27)
    private fun service(status: String = Maintenance.STATUS_REALIZADO, date: String = "", km: Int = 0) = Maintenance(
        id = 1, vehicleId = 1, type = "Aceite", date = "2026-09-15", mileage = 100_000,
        cost = 800.0, workshop = "", nextDate = date, nextMileage = km, status = status)
    @Test fun completedServiceWithoutPlanDoesNotInventANextAction() {
        assertNull(UpcomingReviews.next(listOf(service()), emptyList(), 100_000, today))
    }
    @Test fun userTargetsProduceActualTimeAndMileageRemaining() {
        val next = UpcomingReviews.next(listOf(service(date = "2026-10-07", km = 105_000)), emptyList(), 102_000, today)!!
        assertEquals(10L, next.days)
        assertEquals(3000, next.km)
        assertFalse(next.overdue)
    }
    @Test fun reachedMileageTakesPriorityEvenWhenItsDateIsLater() {
        val reminders = listOf(Reminder(id = 5, vehicleId = 1, title = "Llantas", dueDate = "2026-10-01"),
            Reminder(id = 6, vehicleId = 1, title = "Frenos", dueDate = "2026-11-01", dueMileage = 100_000))
        val next = UpcomingReviews.next(emptyList(), reminders, 100_010, today)!!
        assertEquals(6, next.reminderId)
        assertTrue(next.overdue)
        assertEquals(-10, next.km)
    }
    @Test fun disabledLinkedReminderDoesNotAppearAgainAsADuplicatePlan() {
        val reminder = Reminder(id = 5, vehicleId = 1, title = "Aceite", dueDate = "2026-10-07", enabled = false, maintenanceId = 1)
        assertNull(UpcomingReviews.next(listOf(service(date = "2026-10-07")), listOf(reminder), 100_000, today))
    }
    @Test fun reminderWithoutDateCanUseMileageAndCannotInventDayCount() {
        val next = UpcomingReviews.next(emptyList(), listOf(Reminder(id = 7, vehicleId = 1, title = "Filtro",
            dueDate = "", dueMileage = 101_000)), 100_000, today)!!
        assertNull(next.days)
        assertEquals(1000, next.km)
    }
}
