package com.example.carcareformularioregistro.utils

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.carcareformularioregistro.MainActivity
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.data.Reminder
import java.time.LocalDate
import java.time.ZoneId

object NotificationHelper {

    const val CHANNEL_ID = "carcare_reminders_channel"
    const val CHANNEL_NAME = "CarCare Recordatorios"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notificaciones de mantenimiento y recordatorios de CarCare"
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showNotification(context: Context, id: Int, title: String, message: String) {
        createNotificationChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(id, builder.build())
    }

    /** Alarms are inexact, so Android can deliver them later when the device is idle. */
    fun scheduleReminder(context: Context, reminder: Reminder): Boolean {
        if (!reminder.enabled) {
            cancelReminderAlarm(context, reminder.id)
            return true
        }
        val date = FormValidation.date(reminder.dueDate) ?: return false
        val dueAt = LocalDate.parse(date).atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // A reminder entered/re-enabled after its due time is scheduled for the next minute.
        val triggerAt = maxOf(dueAt, System.currentTimeMillis() + 60_000)
        return scheduleReminderAlarm(context, reminder.id, reminder.title, triggerAt)
    }

    fun cancelReminderAlarm(context: Context, reminderId: Int) {
        val pendingIntent = PendingIntent.getBroadcast(
            context, reminderId, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(it)
            it.cancel()
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(reminderId)
    }

    fun scheduleReminderAlarm(context: Context, reminderId: Int, title: String, triggerAtMillis: Long): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminder_id", reminderId)
            putExtra("reminder_title", title)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return try {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
            true
        } catch (e: Exception) {
            Log.e("CarCareReminders", "Could not schedule reminder", e)
            false
        }
    }
}
