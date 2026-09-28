package com.example.carcareformularioregistro.ui

import com.example.carcareformularioregistro.R

/** UI choice only: existing Room date and mileage columns remain the source of truth. */
enum class ReviewTargetMode(val label: Int, val help: Int, val usesDate: Boolean, val usesMileage: Boolean) {
    DATE(R.string.target_date, R.string.target_date_help, true, false),
    MILEAGE(R.string.target_mileage, R.string.target_mileage_help, false, true),
    BOTH(R.string.target_both, R.string.target_both_help, true, true);

    val shortHelp: Int get() = when (this) {
        DATE -> R.string.target_short_date
        MILEAGE -> R.string.target_short_mileage
        BOTH -> R.string.target_short_both
    }

    companion object {
        fun fromTargets(date: String, mileage: Int): ReviewTargetMode = when {
            date.isNotBlank() && mileage > 0 -> BOTH
            mileage > 0 -> MILEAGE
            else -> DATE
        }
        fun restore(name: String?): ReviewTargetMode = entries.firstOrNull { it.name == name } ?: DATE
    }
}
