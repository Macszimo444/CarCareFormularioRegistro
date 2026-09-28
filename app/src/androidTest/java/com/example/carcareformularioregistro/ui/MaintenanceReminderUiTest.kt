package com.example.carcareformularioregistro.ui

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.Reminder
import com.example.carcareformularioregistro.data.Vehicle
import com.example.carcareformularioregistro.data.VehicleRepository
import com.example.carcareformularioregistro.utils.LocalSession
import com.example.carcareformularioregistro.utils.NotificationHelper
import com.example.carcareformularioregistro.utils.ReminderSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Exercises the real form against unique fixtures. No user data, permissions or alarms are replaced. */
@RunWith(AndroidJUnit4::class)
class MaintenanceReminderUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val database by lazy { AppDatabase.getInstance(context) }
    private val vehicles by lazy { VehicleRepository.getInstance(context) }
    private val prefix = "QALink${System.nanoTime()}"
    private var vehicleId = 0
    private var independentReminderId = 0
    private var previousSelectedId = 0
    private var wasSessionClosed = false
    private var scenario: ActivityScenario<AddMaintenanceActivity>? = null

    @Before
    fun createOnlyOwnFixtures() = runBlocking {
        previousSelectedId = context.getSharedPreferences("carcare_vehicle_selection", Context.MODE_PRIVATE)
            .getInt("selected_id", 0)
        wasSessionClosed = LocalSession.isClosed(context)
        vehicleId = database.vehicleDao().insertVehicle(Vehicle(
            name = "$prefix Auto", brand = "Prueba", model = "Integración", year = 2020,
            mileage = 120000, plates = "",
        )).toInt()
        vehicles.selectVehicle(vehicleId)
        independentReminderId = database.reminderDao().insert(Reminder(
            vehicleId = vehicleId, title = "$prefix Independiente", dueDate = "", dueMileage = 160000,
        )).toInt()
    }

    @After
    fun removeOnlyOwnFixturesAndRestoreSelection() = runBlocking {
        scenario?.close()
        if (vehicleId > 0) {
            database.reminderDao().getForVehicle(vehicleId).forEach {
                NotificationHelper.cancelReminderAlarm(context, it.id)
                ReminderSettings(context).forget(it.id)
                database.reminderDao().delete(it)
            }
            database.maintenanceDao().getAllMaintenances().filter { it.vehicleId == vehicleId }.forEach {
                database.maintenanceDao().deleteById(it.id)
            }
            database.vehicleDao().deleteById(vehicleId)
        }
        vehicles.selectVehicle(previousSelectedId)
        if (wasSessionClosed) LocalSession.close(context) else LocalSession.resume(context)
    }

    @Test
    fun creatingEditingAndUnlinkingAPlanKeepsOneLinkedReminderAndPreservesIndependentReminders() {
        openForm()
        fill(R.id.etType, "$prefix Cambio de aceite")
        fill(R.id.etDate, LocalDate.now().toString())
        fill(R.id.etMileage, "120000")
        fill(R.id.etCost, "0")
        onView(withId(R.id.btnTogglePlan)).perform(scrollTo(), click())
        onView(withId(R.id.etNextDate)).check(matches(withText("")))
        fill(R.id.etNextMileage, "130000")
        onView(withId(R.id.checkCreateReminder)).perform(scrollTo(), click())
        onView(withId(R.id.btnSaveMaintenance)).perform(click())
        awaitDatabase("new service and linked reminder") {
            val item = database.maintenanceDao().getAllMaintenances().singleOrNull { it.vehicleId == vehicleId }
            item != null && database.reminderDao().getForMaintenance(item.id) != null
        }
        val maintenance = runBlocking {
            database.maintenanceDao().getAllMaintenances().single { it.vehicleId == vehicleId }
        }
        val linked = requireNotNull(runBlocking { database.reminderDao().getForMaintenance(maintenance.id) })
        assertEquals("", maintenance.nextDate)
        assertEquals(130000, maintenance.nextMileage)
        assertEquals("", linked.dueDate)
        assertEquals(130000, linked.dueMileage)
        assertEquals(vehicleId, linked.vehicleId)
        assertNotNull(runBlocking { database.reminderDao().getById(independentReminderId) })

        // Reopen the production edit route: changing a plan updates, rather than duplicating, its reminder.
        openForm(maintenance.id)
        onView(withId(R.id.checkCreateReminder)).check(matches(isChecked()))
        fill(R.id.etNextMileage, "140000")
        onView(withId(R.id.btnSaveMaintenance)).perform(click())
        awaitDatabase("updated target on both linked rows") {
            database.maintenanceDao().getById(maintenance.id)?.nextMileage == 140000 &&
                database.reminderDao().getForMaintenance(maintenance.id)?.dueMileage == 140000
        }
        val updated = runBlocking { database.reminderDao().getForMaintenance(maintenance.id) }
        assertEquals(linked.id, updated?.id)
        assertEquals(1, runBlocking {
            database.reminderDao().getForVehicle(vehicleId).count { it.maintenanceId == maintenance.id }
        })

        // Opting out deletes only this link; the saved next-review target remains available.
        openForm(maintenance.id)
        onView(withId(R.id.checkCreateReminder)).perform(scrollTo(), click())
        onView(withId(R.id.btnSaveMaintenance)).perform(click())
        awaitDatabase("linked reminder removed") { database.reminderDao().getForMaintenance(maintenance.id) == null }
        assertNull(runBlocking { database.reminderDao().getById(linked.id) })
        assertEquals(140000, runBlocking { database.maintenanceDao().getById(maintenance.id)?.nextMileage })
        val standalone = runBlocking { database.reminderDao().getById(independentReminderId) }
        assertEquals("$prefix Independiente", standalone?.title)
        assertEquals(160000, standalone?.dueMileage)
        assertNull(standalone?.maintenanceId)
        assertEquals(1, runBlocking { database.reminderDao().getForVehicle(vehicleId).size })
    }

    private fun openForm(maintenanceId: Int = 0) {
        scenario?.close()
        scenario = ActivityScenario.launch(Intent(context, AddMaintenanceActivity::class.java)
            .putExtra("maintenance_id", maintenanceId))
        val deadline = SystemClock.elapsedRealtime() + 5_000
        do {
            var loaded = false
            scenario!!.onActivity { activity ->
                val field = activity.findViewById<EditText>(R.id.etMileage)
                loaded = field.isEnabled && field.text.toString() == "120000"
            }
            if (loaded) return
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Maintenance form did not load fixture vehicle")
    }

    private fun fill(viewId: Int, value: String) {
        onView(withId(viewId)).perform(scrollTo(), replaceText(value), closeSoftKeyboard())
    }

    private fun awaitDatabase(description: String, condition: suspend () -> Boolean) = runBlocking {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        do {
            if (condition()) return@runBlocking
            delay(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Timed out waiting for $description")
    }
}
