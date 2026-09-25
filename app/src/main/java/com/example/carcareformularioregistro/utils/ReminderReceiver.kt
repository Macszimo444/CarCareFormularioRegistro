package com.example.carcareformularioregistro.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.carcareformularioregistro.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra("reminder_id", 0)
        if (reminderId <= 0) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = AppDatabase.getInstance(appContext).reminderDao().getById(reminderId)
                if (reminder?.enabled == true) {
                    NotificationHelper.showNotification(
                        context = appContext,
                        id = reminder.id,
                        title = reminder.title,
                        message = reminder.description.ifBlank {
                            "CarCare: Tienes un recordatorio de servicio o mantenimiento pendiente."
                        }
                    )
                }
            } catch (error: Exception) {
                Log.e("CarCareReminders", "Could not read reminder", error)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
