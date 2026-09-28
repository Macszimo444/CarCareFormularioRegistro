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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.Reminder
import com.example.carcareformularioregistro.ui.RecordatoriosActivity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object NotificationHelper {
    const val CHANNEL_ID = "carcare_reminders_channel"
    const val CHANNEL_NAME = "CarCare Recordatorios"
    private val deliveryLock = Mutex()

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Avisos de revisiones por fecha o por kilometraje registrado"
        }
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    fun notificationsAllowed(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            ((context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE)

    fun showNotification(context: Context, id: Int, title: String, message: String, vehicleId: Int = 0): Boolean {
        createNotificationChannel(context)
        if (!ReminderSettings(context).enabled || !notificationsAllowed(context)) return false
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val intent = Intent(context, RecordatoriosActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("vehicle_id", vehicleId)
        }
        val pendingIntent = PendingIntent.getActivity(context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell).setContentTitle(title).setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).setContentIntent(pendingIntent).setAutoCancel(true).build()
        return try { manager.notify(id, notification); true } catch (_: SecurityException) { false }
    }

    /** Date alarms are inexact; Android may defer them while the device is idle. */
    fun scheduleReminder(context: Context, reminder: Reminder): Boolean = try {
        cancelAlarmOnly(context, reminder.id)
        val settings = ReminderSettings(context)
        if (!reminder.enabled || !settings.enabled) {
            cancelReminderAlarm(context, reminder.id)
            true
        } else if (settings.wasDelivered(reminder) || reminder.dueDate.isBlank()) {
            true
        } else {
            val dueAt = ReminderSchedule.triggerAt(reminder)?.toInstant()?.toEpochMilli()
            if (dueAt == null) false else {
                scheduleReminderAlarm(context, reminder.id, reminder.title,
                    maxOf(dueAt, System.currentTimeMillis() + 60_000))
            }
        }
    } catch (error: Exception) {
        // Data is already committed. A scheduling failure must not cause another insert on retry.
        Log.e("CarCareReminders", "Could not schedule stored reminder", error)
        false
    }

    /** Called on app start, reboot, time-zone/time changes and notification-setting changes. */
    suspend fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        AppDatabase.getInstance(app).reminderDao().getAllReminders().forEach { scheduleReminder(app, it) }
    }

    /** Mileage is assessed only when the user updates the odometer; it is never tracked automatically. */
    suspend fun rescheduleVehicle(context: Context, vehicleId: Int) {
        val app = context.applicationContext
        val db = AppDatabase.getInstance(app)
        val vehicle = db.vehicleDao().getById(vehicleId) ?: return
        db.reminderDao().getAllReminders().filter { it.vehicleId == vehicleId }.forEach { reminder ->
            scheduleReminder(app, reminder)
            if (reminder.dueMileage > 0 && vehicle.mileage >= reminder.dueMileage) {
                deliverIfDue(app, reminder.id, mileageUpdate = true)
            }
        }
    }

    /** Re-read stored data to ignore cancelled, deleted or edited reminders and avoid duplicate deliveries. */
    suspend fun deliverIfDue(context: Context, reminderId: Int, mileageUpdate: Boolean = false) = deliveryLock.withLock {
        val db = AppDatabase.getInstance(context)
        val reminder = db.reminderDao().getById(reminderId) ?: return@withLock
        val settings = ReminderSettings(context)
        if (!reminder.enabled || !settings.enabled || settings.wasDelivered(reminder)) return@withLock
        val vehicle = db.vehicleDao().getById(reminder.vehicleId) ?: return@withLock
        val dateReached = ReminderSchedule.triggerAt(reminder)?.toInstant()?.toEpochMilli()
            ?.let { it <= System.currentTimeMillis() } == true
        val mileageReached = mileageUpdate && reminder.dueMileage > 0 && vehicle.mileage >= reminder.dueMileage
        if (!dateReached && !mileageReached) return@withLock
        val reason = if (mileageReached) "Alcanzaste ${reminder.dueMileage} km registrados." else if (reminder.dueTime == null) "Tienes una revisión para hoy o pendiente de días anteriores."
            else "Revisión programada para ${reminder.dueDate} a las ${reminder.dueTime}."
        val message = "${vehicle.displayName}: $reason ${reminder.description}".trim()
        if (showNotification(context, reminder.id, reminder.title, message, vehicle.id)) {
            settings.markDelivered(reminder)
            cancelAlarmOnly(context, reminder.id)
        }
    }

    private fun cancelAlarmOnly(context: Context, reminderId: Int) {
        val intent = PendingIntent.getBroadcast(context, reminderId, Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        intent?.let {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(it)
            it.cancel()
        }
    }

    fun cancelReminderAlarm(context: Context, reminderId: Int) {
        try {
            cancelAlarmOnly(context, reminderId)
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(reminderId)
        } catch (error: Exception) {
            Log.e("CarCareReminders", "Could not cancel reminder notification", error)
        }
    }

    fun scheduleReminderAlarm(context: Context, reminderId: Int, title: String, triggerAtMillis: Long): Boolean {
        val intent = Intent(context, ReminderReceiver::class.java).putExtra("reminder_id", reminderId)
        val pendingIntent = PendingIntent.getBroadcast(context, reminderId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return try {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
                .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            true
        } catch (error: Exception) {
            Log.e("CarCareReminders", "Could not schedule reminder", error)
            false
        }
    }
}
