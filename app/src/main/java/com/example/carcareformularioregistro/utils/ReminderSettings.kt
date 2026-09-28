package com.example.carcareformularioregistro.utils

import android.content.Context
import com.example.carcareformularioregistro.data.Reminder

/** Local preferences only: no account, server or device mileage tracking. */
class ReminderSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("reminder_preferences", Context.MODE_PRIVATE)
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }
    var hour: Int
        get() = prefs.getInt("hour", 9).coerceIn(0, 23)
        set(value) { prefs.edit().putInt("hour", value.coerceIn(0, 23)).apply() }

    private fun signature(reminder: Reminder) = "${reminder.dueDate}|${reminder.dueMileage}"
    fun wasDelivered(reminder: Reminder): Boolean =
        prefs.getString("delivered_${reminder.id}", null) == signature(reminder)
    fun markDelivered(reminder: Reminder) {
        prefs.edit().putString("delivered_${reminder.id}", signature(reminder)).apply()
    }
    fun forget(reminderId: Int) { prefs.edit().remove("delivered_$reminderId").apply() }
}
