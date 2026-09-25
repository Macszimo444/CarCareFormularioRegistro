package com.example.carcareformularioregistro.utils

import com.example.carcareformularioregistro.data.Expense
import com.example.carcareformularioregistro.data.Maintenance
import java.time.LocalDate
import java.time.YearMonth

data class ExpenseStatistics(
    val monthlyTotal: Double,
    val yearlyTotal: Double,
    val monthlyAverage: Double,
    val comparisonPercent: Double?
)

data class HistoryStatistics(val services: Int, val spent: Double)

/** Uses recorded dates for the current calendar periods, including legacy day/month dates. */
object StatisticsCalculator {
    fun expenses(items: List<Expense>, today: LocalDate = LocalDate.now()): ExpenseStatistics {
        val currentMonth = YearMonth.from(today)
        val previousMonth = currentMonth.minusMonths(1)
        var monthTotal = 0.0
        var previousTotal = 0.0
        var yearTotal = 0.0
        for (expense in items) {
            val date = parsedDate(expense.date) ?: continue
            val month = YearMonth.from(date)
            if (month == currentMonth) monthTotal += expense.amount
            if (month == previousMonth) previousTotal += expense.amount
            if (date.year == today.year) yearTotal += expense.amount
        }
        return ExpenseStatistics(
            monthlyTotal = monthTotal,
            yearlyTotal = yearTotal,
            // Average over the calendar months elapsed this year, including the current month.
            monthlyAverage = yearTotal / today.monthValue,
            comparisonPercent = if (previousTotal > 0.0) {
                (monthTotal - previousTotal) / previousTotal * 100.0
            } else null
        )
    }

    fun history(items: List<Maintenance>, today: LocalDate = LocalDate.now()): HistoryStatistics {
        val completedThisYear = items.filter {
            it.status == Maintenance.STATUS_REALIZADO && parsedDate(it.date)?.year == today.year
        }
        return HistoryStatistics(completedThisYear.size, completedThisYear.sumOf { it.cost })
    }

    private fun parsedDate(value: String): LocalDate? = FormValidation.date(value)?.let(LocalDate::parse)
}
