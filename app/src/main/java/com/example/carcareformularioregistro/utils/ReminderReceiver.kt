package com.example.carcareformularioregistro.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getIntExtra("reminder_id", 0)
        if (reminderId <= 0) return
        val result = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try { NotificationHelper.deliverIfDue(app, reminderId) }
            catch (error: Exception) { Log.e("CarCareReminders", "Could not deliver reminder", error) }
            finally { result.finish() }
        }
    }
}
