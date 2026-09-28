package com.example.carcareformularioregistro.ui

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.EditText
import android.view.View
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import com.example.carcareformularioregistro.data.Maintenance
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
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
        onView(withId(R.id.tilMileage)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        fill(R.id.etCost, "0")
        onView(withId(R.id.btnTogglePlan)).perform(scrollTo(), click())
        onView(withId(R.id.etNextDate)).check(matches(withText("")))
        chooseTarget(R.string.target_mileage)
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
        assertEquals(120000, maintenance.mileage)
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

    @Test
    fun historicalMileageSurvivesRecreationAndDoesNotChangeVehicleReading() {
        openForm()
        fill(R.id.etType, "$prefix Histórico")
        onView(withId(R.id.btnToggleServiceMileage)).perform(scrollTo(), click())
        fill(R.id.etMileage, "90000")
        scenario!!.recreate()
        onView(withId(R.id.etMileage)).check(matches(withText("90000")))
        onView(withId(R.id.btnSaveMaintenance)).perform(click())
        awaitDatabase("historical service") {
            database.maintenanceDao().getAllMaintenances().any { it.vehicleId == vehicleId && it.mileage == 90000 }
        }
        val item = runBlocking { database.maintenanceDao().getAllMaintenances().single { it.vehicleId == vehicleId } }
        assertEquals(120000, runBlocking { database.vehicleDao().getById(vehicleId)?.mileage })
        openForm(item.id, "90000")
        onView(withId(R.id.tilMileage)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        onView(withId(R.id.etMileage)).check(matches(withText("90000")))
    }

    @Test
    fun reminderModeSurvivesRecreationAndDateOnlyClearsHiddenMileageOnBothLinkedRows() {
        val serviceId = runBlocking { database.maintenanceDao().insert(Maintenance(
            vehicleId = vehicleId, type = "$prefix Servicio", date = "2020-01-01", mileage = 90000,
            cost = 0.0, workshop = "", nextDate = "2090-01-01", nextMileage = 150000,
            status = Maintenance.STATUS_REALIZADO
        )).toInt() }
        val reminderId = runBlocking { database.reminderDao().insert(Reminder(
            vehicleId = vehicleId, title = "$prefix Revisión", dueDate = "2090-01-01", dueMileage = 150000,
            maintenanceId = serviceId, enabled = false
        )).toInt() }
        openForm()
        scenario!!.onActivity { AddReminderDialogFragment.edit(reminderId).show(it.supportFragmentManager, "target_test") }
        awaitReminderLoaded()
        onView(withId(R.id.actTargetMode)).check(matches(withText(R.string.target_both)))
        chooseTarget(R.string.target_mileage)
        onView(withId(R.id.tilDueDate)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        scenario!!.recreate()
        awaitReminderLoaded()
        onView(withId(R.id.actTargetMode)).check(matches(withText(R.string.target_mileage)))
        onView(withId(R.id.etDueMileage)).check(matches(withText("150000")))
        chooseTarget(R.string.target_date)
        onView(withId(R.id.tilDueMileage)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        onView(withId(R.id.btnSave)).perform(scrollTo(), click())
        awaitDatabase("date-only targets") {
            database.reminderDao().getById(reminderId)?.dueMileage == 0 &&
                database.maintenanceDao().getById(serviceId)?.nextMileage == 0
        }
        assertEquals("2090-01-01", runBlocking { database.reminderDao().getById(reminderId)?.dueDate })
        assertEquals("2090-01-01", runBlocking { database.maintenanceDao().getById(serviceId)?.nextDate })
        assertNotNull(runBlocking { database.reminderDao().getById(independentReminderId) })
    }

    @Test
    fun editingVehicleDetailsPreservesNewerOdometerReading() {
        openForm()
        scenario!!.onActivity { EditVehicleDialogFragment.newInstance(vehicleId).show(it.supportFragmentManager, "vehicle_test") }
        val deadline = SystemClock.elapsedRealtime() + 5_000
        var ready = false
        do {
            scenario!!.onActivity {
                val view = it.supportFragmentManager.findFragmentByTag("vehicle_test")?.view
                ready = view?.findViewById<View>(R.id.btnSave)?.isEnabled == true
            }
            if (!ready) SystemClock.sleep(50)
        } while (!ready && SystemClock.elapsedRealtime() < deadline)
        check(ready)
        // The reading changes after the edit form has loaded its snapshot.
        runBlocking {
            val car = requireNotNull(database.vehicleDao().getById(vehicleId))
            database.vehicleDao().updateVehicle(car.copy(mileage = 125000))
        }
        fill(R.id.etModel, "Modelo actualizado")
        onView(withId(R.id.btnSave)).perform(scrollTo(), click())
        awaitDatabase("vehicle details saved") { database.vehicleDao().getById(vehicleId)?.model == "Modelo actualizado" }
        assertEquals(125000, runBlocking { database.vehicleDao().getById(vehicleId)?.mileage })
    }

    @Test
    fun completingPendingReviewUpdatesTheSameServiceAndConsumesOnlyItsReminder() {
        val serviceId = runBlocking {
            val serviceId = database.maintenanceDao().insert(Maintenance(vehicleId = vehicleId, type = "$prefix Pendiente",
                date = "2026-01-01", mileage = 90000, cost = 0.0, workshop = "", nextDate = "", nextMileage = 0,
                status = Maintenance.STATUS_PENDIENTE)).toInt()
            val reminder = requireNotNull(database.reminderDao().getById(independentReminderId))
            database.reminderDao().update(reminder.copy(maintenanceId = serviceId))
            serviceId
        }
        openForm(completeReminderId = independentReminderId)
        onView(withId(R.id.actStatus)).check(matches(withText(Maintenance.STATUS_REALIZADO)))
        onView(withId(R.id.etMileage)).check(matches(withText("120000")))
        onView(withId(R.id.btnSaveMaintenance)).perform(click())
        awaitDatabase("review completed") { database.reminderDao().getById(independentReminderId) == null }
        val services = runBlocking { database.maintenanceDao().getAllMaintenances().filter { it.vehicleId == vehicleId } }
        assertEquals(1, services.size)
        assertEquals(serviceId, services.single().id)
        assertEquals(Maintenance.STATUS_REALIZADO, services.single().status)
        assertEquals(120000, services.single().mileage)
    }

    @Test
    fun completingNextReviewKeepsTheHistoricalServiceAndCreatesOneNewService() {
        val oldId = runBlocking {
            val id = database.maintenanceDao().insert(Maintenance(vehicleId = vehicleId, type = "$prefix Aceite",
                date = "2026-01-01", mileage = 90000, cost = 700.0, workshop = "Taller anterior", nextDate = "", nextMileage = 130000,
                status = Maintenance.STATUS_REALIZADO)).toInt()
            val reminder = requireNotNull(database.reminderDao().getById(independentReminderId))
            database.reminderDao().update(reminder.copy(maintenanceId = id))
            id
        }
        openForm(completeReminderId = independentReminderId)
        onView(withId(R.id.etType)).check(matches(withText("$prefix Aceite")))
        scenario!!.recreate()
        onView(withId(R.id.btnSaveMaintenance)).perform(click())
        awaitDatabase("next review completed") { database.reminderDao().getById(independentReminderId) == null }
        val services = runBlocking { database.maintenanceDao().getAllMaintenances().filter { it.vehicleId == vehicleId } }
        assertEquals(2, services.size)
        val old = services.single { it.id == oldId }
        assertEquals(90000, old.mileage)
        assertEquals(700.0, old.cost, 0.01)
        assertEquals(0, old.nextMileage)
        assertEquals(120000, services.single { it.id != oldId }.mileage)
    }

    @Test
    fun unlinkedReviewPrefillsServiceAndReceiptCanBeRemovedWithoutLosingHistory() {
        openForm(completeReminderId = independentReminderId)
        onView(withId(R.id.etType)).check(matches(withText("$prefix Independiente")))
        onView(withId(R.id.btnSaveMaintenance)).perform(click())
        awaitDatabase("standalone review completed") { database.reminderDao().getById(independentReminderId) == null }
        val service = runBlocking { database.maintenanceDao().getAllMaintenances().single { it.vehicleId == vehicleId } }
        val store = com.example.carcareformularioregistro.utils.ReceiptStore(context)
        val bytes = java.io.ByteArrayOutputStream()
        val bitmap = android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, bytes); bitmap.recycle()
        val receipt = store.import(java.io.ByteArrayInputStream(bytes.toByteArray()))
        try {
            runBlocking { database.maintenanceDao().update(service.copy(receipt = receipt)) }
            openForm(service.id)
            onView(withId(R.id.btnReceipt)).perform(scrollTo(), click())
            onView(withText("Quitar del servicio")).perform(click())
            onView(withId(R.id.btnSaveMaintenance)).perform(click())
            awaitDatabase("receipt removed") { database.maintenanceDao().getById(service.id)?.receipt == null }
            assertNotNull(runBlocking { database.maintenanceDao().getById(service.id) })
        } finally { store.delete(receipt) }
    }

    @Test
    fun individualTimeSurvivesRecreationAndAllDayOrMileageOnlyClearsIt() {
        runBlocking {
            val old = database.reminderDao().getById(independentReminderId)!!
            database.reminderDao().update(old.copy(dueDate = "2090-01-01", dueMileage = 0, enabled = false))
        }
        openForm()
        scenario!!.onActivity { AddReminderDialogFragment.edit(independentReminderId).show(it.supportFragmentManager, "target_test") }
        awaitReminderLoaded()
        onView(withId(R.id.switchAllDay)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(isChecked())).perform(scrollTo(), click())
        onView(withId(R.id.btnDueTime)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(scrollTo(), click())
        onView(androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.TimePicker::class.java))
            .perform(object : androidx.test.espresso.ViewAction {
                override fun getConstraints(): org.hamcrest.Matcher<View> = androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom(android.widget.TimePicker::class.java)
                override fun getDescription() = "Choose 10:30 in the time picker"
                override fun perform(controller: androidx.test.espresso.UiController, view: View) {
                    (view as android.widget.TimePicker).apply { hour = 10; minute = 30 }
                }
            })
        onView(withId(android.R.id.button1)).perform(click())
        scenario!!.recreate()
        awaitReminderLoaded()
        onView(withId(R.id.btnDueTime)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(withText(context.getString(R.string.reminder_pick_time, "10:30"))))
        onView(withId(R.id.btnDueTime)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(scrollTo())
        // Allow the recreated dialog's window animation to finish before the visual capture.
        android.os.SystemClock.sleep(500)
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        val shotFile = java.io.File(context.getExternalFilesDir(null), "qa/reminder-time.png")
        shotFile.parentFile!!.mkdirs()
        shotFile.outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
        onView(withId(R.id.btnSave)).perform(scrollTo(), click())
        awaitDatabase("time saved") { database.reminderDao().getById(independentReminderId)?.dueTime == "10:30" }
        scenario!!.onActivity { AddReminderDialogFragment.edit(independentReminderId).show(it.supportFragmentManager, "target_test") }
        awaitReminderLoaded()
        onView(withId(R.id.switchAllDay)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(scrollTo(), click())
        onView(withId(R.id.btnDueTime)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(withEffectiveVisibility(Visibility.GONE)))
        onView(withId(R.id.btnSave)).perform(scrollTo(), click())
        awaitDatabase("all day saved") { database.reminderDao().getById(independentReminderId)?.dueTime == null }
        // An existing timed appointment switched to mileage-only must not retain a hidden hour.
        runBlocking {
            val old = database.reminderDao().getById(independentReminderId)!!
            database.reminderDao().update(old.copy(dueTime = "10:30"))
        }
        scenario!!.onActivity { AddReminderDialogFragment.edit(independentReminderId).show(it.supportFragmentManager, "target_test") }
        awaitReminderLoaded()
        chooseTarget(R.string.target_mileage)
        onView(withId(R.id.containerSchedule)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        fill(R.id.etDueMileage, "150000")
        onView(withId(R.id.btnSave)).perform(scrollTo(), click())
        awaitDatabase("mileage only saved") { database.reminderDao().getById(independentReminderId)?.dueDate == "" }
        assertNull(runBlocking { database.reminderDao().getById(independentReminderId)?.dueTime })
    }

    @Test
    fun editingMaintenancePreservesTheTimeOfItsLinkedReminder() {
        val serviceId = runBlocking {
            val id = database.maintenanceDao().insert(Maintenance(vehicleId = vehicleId, type = "$prefix Aceite",
                date = "2026-01-01", mileage = 120000, cost = 0.0, workshop = "", nextDate = "2090-01-01", nextMileage = 0,
                status = Maintenance.STATUS_REALIZADO)).toInt()
            val reminder = database.reminderDao().getById(independentReminderId)!!
            database.reminderDao().update(reminder.copy(maintenanceId = id, dueDate = "2090-01-01", dueMileage = 0,
                dueTime = "11:45", enabled = false))
            id
        }
        openForm(serviceId)
        fill(R.id.etType, "$prefix Aceite editado")
        onView(withId(R.id.btnSaveMaintenance)).perform(click())
        awaitDatabase("maintenance edited") { database.maintenanceDao().getById(serviceId)?.type == "$prefix Aceite editado" }
        assertEquals("11:45", runBlocking { database.reminderDao().getById(independentReminderId)?.dueTime })
    }

    private fun chooseTarget(label: Int) {
        onView(withId(R.id.actTargetMode)).perform(scrollTo(), click())
        onView(withText(label)).inRoot(isPlatformPopup()).perform(click())
    }

    private fun awaitReminderLoaded() {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        do {
            var ready = false
            scenario!!.onActivity {
                val view = it.supportFragmentManager.findFragmentByTag("target_test")?.view
                ready = view?.findViewById<EditText>(R.id.etDueMileage)?.isEnabled == true && view.hasWindowFocus()
            }
            if (ready) return
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Reminder did not load")
    }

    private fun openForm(maintenanceId: Int = 0, expectedMileage: String = "120000", completeReminderId: Int = 0) {
        scenario?.close()
        scenario = ActivityScenario.launch(Intent(context, AddMaintenanceActivity::class.java)
            .putExtra("maintenance_id", maintenanceId).putExtra("complete_reminder_id", completeReminderId))
        val deadline = SystemClock.elapsedRealtime() + 5_000
        do {
            var loaded = false
            scenario!!.onActivity { activity ->
                val field = activity.findViewById<EditText>(R.id.etMileage)
                loaded = field.isEnabled && field.text.toString() == expectedMileage
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
