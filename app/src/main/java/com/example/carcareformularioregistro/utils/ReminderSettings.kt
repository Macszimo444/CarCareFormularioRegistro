package com.example.carcareformularioregistro.utils

import android.content.Context
import com.example.carcareformularioregistro.data.Reminder

/** Local preferences only: no account, server or device mileage tracking. */
class ReminderSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("reminder_preferences", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }
    private fun signature(reminder: Reminder): String {
        val base = "${reminder.dueDate}|${reminder.dueMileage}"
        // Keep old all-day delivery markers, but a changed appointment time is a new target.
        return reminder.dueTime?.takeIf { reminder.dueDate.isNotBlank() }?.let { "$base|$it" } ?: base
    }
    fun wasDelivered(reminder: Reminder): Boolean =
        prefs.getString("delivered_${reminder.id}", null) == signature(reminder)
    fun markDelivered(reminder: Reminder) {
        prefs.edit().putString("delivered_${reminder.id}", signature(reminder)).apply()
    }
    fun forget(reminderId: Int) { prefs.edit().remove("delivered_$reminderId").apply() }
}
