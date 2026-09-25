package com.example.carcareformularioregistro.utils

import com.example.carcareformularioregistro.data.Expense
import com.example.carcareformularioregistro.data.Maintenance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class StatisticsCalculatorTest {
    private val today = LocalDate.of(2026, 9, 25)

    @Test
    fun expensesUseCalendarPeriodsRatherThanAllTimeTotals() {
        val stats = StatisticsCalculator.expenses(listOf(
            expense("2026-09-10", 100.0),
            expense("2026-09-20", 50.0),
            expense("2026-08-15", 200.0),
            expense("2026-01-01", 550.0),
            expense("2025-09-10", 9000.0)
        ), today)

        assertEquals(150.0, stats.monthlyTotal, 0.001)
        assertEquals(900.0, stats.yearlyTotal, 0.001)
        assertEquals(100.0, stats.monthlyAverage, 0.001)
        assertEquals(-25.0, stats.comparisonPercent!!, 0.001)
    }

    @Test
    fun JanuaryComparesWithDecemberOfPreviousYear() {
        val stats = StatisticsCalculator.expenses(listOf(
            expense("2027-01-15", 300.0),
            expense("2026-12-15", 200.0)
        ), LocalDate.of(2027, 1, 25))

        assertEquals(300.0, stats.monthlyTotal, 0.001)
        assertEquals(300.0, stats.yearlyTotal, 0.001)
        assertEquals(300.0, stats.monthlyAverage, 0.001)
        assertEquals(50.0, stats.comparisonPercent!!, 0.001)
    }

    @Test
    fun absentOrZeroPreviousMonthHasNoPercentageComparison() {
        val records = listOf(expense("2026-09-15", 100.0))
        assertNull(StatisticsCalculator.expenses(records, today).comparisonPercent)
        assertNull(StatisticsCalculator.expenses(records + expense("2026-08-15", 0.0), today).comparisonPercent)
    }

    @Test
    fun noCurrentMonthExpensesIsARealHundredPercentDecrease() {
        val stats = StatisticsCalculator.expenses(listOf(expense("2026-08-15", 100.0)), today)
        assertEquals(0.0, stats.monthlyTotal, 0.001)
        assertEquals(-100.0, stats.comparisonPercent!!, 0.001)
    }

    @Test
    fun legacyDayFirstDatesAreIncludedAndInvalidDatesAreNotMiscounted() {
        val stats = StatisticsCalculator.expenses(listOf(
            expense("15/09/2026", 100.0),
            expense("31/09/2026", 9000.0),
            expense("sin fecha", 9000.0)
        ), today)
        assertEquals(100.0, stats.monthlyTotal, 0.001)
        assertEquals(100.0, stats.yearlyTotal, 0.001)
    }

    @Test
    fun emptyExpensesHaveZeroTotalsWithoutAnInventedComparison() {
        val stats = StatisticsCalculator.expenses(emptyList(), today)
        assertEquals(0.0, stats.monthlyTotal, 0.001)
        assertEquals(0.0, stats.yearlyTotal, 0.001)
        assertEquals(0.0, stats.monthlyAverage, 0.001)
        assertNull(stats.comparisonPercent)
    }

    @Test
    fun historySummaryCountsOnlyCompletedServicesFromCurrentYear() {
        val records = listOf(
            maintenance("2026-09-15", 100.0),
            maintenance("15/01/2026", 200.0),
            maintenance("2025-09-15", 9000.0),
            maintenance("2026-09-20", 9000.0, Maintenance.STATUS_PROXIMO),
            maintenance("fecha inválida", 9000.0)
        )
        val stats = StatisticsCalculator.history(records, today)
        assertEquals(2, stats.services)
        assertEquals(300.0, stats.spent, 0.001)
        assertEquals(5, records.size) // The complete timeline remains available to the adapter.
    }

    @Test
    fun historySummaryIsEmptyWhenAllRecordsBelongToAnotherYear() {
        val stats = StatisticsCalculator.history(listOf(maintenance("2025-09-15", 100.0)), today)
        assertEquals(0, stats.services)
        assertEquals(0.0, stats.spent, 0.001)
    }

    private fun expense(date: String, amount: Double) = Expense(
        vehicleId = 1, category = Expense.CAT_COMBUSTIBLE, concept = "Gasolina",
        amount = amount, date = date
    )

    private fun maintenance(
        date: String,
        cost: Double,
        status: String = Maintenance.STATUS_REALIZADO
    ) = Maintenance(
        vehicleId = 1, type = "Cambio de aceite", date = date, mileage = 120000,
        cost = cost, workshop = "Taller López", nextDate = "2027-03-15",
        nextMileage = 130000, status = status
    )
}
