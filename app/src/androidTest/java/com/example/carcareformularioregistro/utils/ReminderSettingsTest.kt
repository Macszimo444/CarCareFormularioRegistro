package com.example.carcareformularioregistro.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.carcareformularioregistro.data.Reminder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses a namespaced preference file; never alters the installed user's settings or database. */
@RunWith(AndroidJUnit4::class)
class ReminderSettingsTest {
    private val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            super.getSharedPreferences("test_reminder_settings_$name", mode)
    }
    private val prefs get() = context.getSharedPreferences("reminder_preferences", Context.MODE_PRIVATE)
    @Before fun prepare() { prefs.edit().clear().commit() }
    @After fun cleanUp() { prefs.edit().clear().commit() }

    @Test fun aDeliveredTargetSurvivesRecreationAndDoesNotRepeatWhenTheTitleChanges() {
        val reminder = Reminder(id = 42, vehicleId = 3, title = "Aceite", dueDate = "2027-01-20", dueMileage = 95000)
        val settings = ReminderSettings(context)
        assertFalse(settings.wasDelivered(reminder))
        settings.markDelivered(reminder)
        val reopened = ReminderSettings(context)
        assertTrue(reopened.wasDelivered(reminder))
        assertTrue(reopened.wasDelivered(reminder.copy(title = "Cambio de aceite", enabled = false)))
        assertFalse(reopened.wasDelivered(reminder.copy(dueMileage = 105000)))
        assertFalse(reopened.wasDelivered(reminder.copy(dueDate = "2027-07-20")))
        assertFalse(reopened.wasDelivered(reminder.copy(id = 43)))
        assertFalse(reopened.wasDelivered(reminder.copy(dueTime = "10:30")))
        reopened.forget(reminder.id)
        assertFalse(ReminderSettings(context).wasDelivered(reminder))
    }

    @Test fun notificationPreferencesPersistIndependentlyOfReminderDelivery() {
        val settings = ReminderSettings(context)
        assertTrue(settings.enabled)
        settings.enabled = false
        val reopened = ReminderSettings(context)
        assertFalse(reopened.enabled)
    }
}
