package com.example.carcareformularioregistro.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra("reminder_id", 100)
        val title = intent.getStringExtra("reminder_title") ?: "Recordatorio de Mantenimiento"
        val message = "CarCare: Tienes un recordatorio de servicio o mantenimiento pendiente."

        NotificationHelper.showNotification(
            context = context,
            id = reminderId,
            title = title,
            message = message
        )
    }
}
