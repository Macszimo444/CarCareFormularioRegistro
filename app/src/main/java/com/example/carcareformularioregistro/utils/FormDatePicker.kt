package com.example.carcareformularioregistro.utils

import android.app.DatePickerDialog
import com.example.carcareformularioregistro.R
import com.google.android.material.textfield.TextInputLayout
import java.time.LocalDate
import java.time.ZoneId

/** Keep manual entry while offering an accessible calendar for date fields. */
object FormDatePicker {
    fun attach(layout: TextInputLayout) {
        layout.endIconMode = TextInputLayout.END_ICON_CUSTOM
        layout.setEndIconDrawable(R.drawable.ic_calendar)
        layout.endIconContentDescription = "Elegir fecha en el calendario"
        layout.setEndIconOnClickListener {
            val current = FormValidation.date(layout.editText?.text?.toString().orEmpty())
                ?.let(LocalDate::parse)?.takeIf { it.year in 1900..2100 } ?: LocalDate.now()
            DatePickerDialog(layout.context, { _, year, month, day ->
                layout.editText?.setText(LocalDate.of(year, month + 1, day).toString())
                layout.error = null
            }, current.year, current.monthValue - 1, current.dayOfMonth).apply {
                datePicker.minDate = LocalDate.of(1900, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                datePicker.maxDate = LocalDate.of(2100, 12, 31).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.show()
        }
    }
}
