package com.example.carcareformularioregistro.ui

import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.typeTextIntoFocusedView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.carcareformularioregistro.MainActivity
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.adapter.MaintenanceAdapter
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.Maintenance
import com.example.carcareformularioregistro.data.User
import com.example.carcareformularioregistro.data.Vehicle
import com.example.carcareformularioregistro.utils.LocalSession
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Adds and removes only its own identifiable fixtures, preserving existing app data. */
@RunWith(AndroidJUnit4::class)
class MaintenanceSearchUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val database by lazy { AppDatabase.getInstance(context) }
    private val prefix = "QA${System.nanoTime()}"
    private val fixtureIds = mutableListOf<Int>()
    private lateinit var fixtures: List<Maintenance>
    private var createdUserId: Int? = null
    private var createdVehicleId: Int? = null
    private var wasSessionClosed = false
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun seedOnlyTestFixtures() = runBlocking {
        wasSessionClosed = LocalSession.isClosed(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(
                    "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
                ),
            ).use { it.readBytes() } // Wait until permission is granted before launching the activity.
        }
        if (database.userDao().getPrimaryUser() == null) {
            createdUserId = database.userDao().insertar(User(
                nombre = prefix, apellidos = "Prueba", direccion = "Dirección de prueba",
                telefono = "5550000000",
            )).toInt()
        }
        val vehicleId = database.vehicleDao().getPrimaryVehicle()?.id
            ?: database.vehicleDao().insertVehicle(Vehicle(
                name = "$prefix Auto", brand = "Prueba", model = "QA", year = 2020,
                mileage = 120000, plates = "QA-TEST",
            )).toInt().also { createdVehicleId = it }
        val rows = listOf(
            row(vehicleId, "Cambio de aceite", Maintenance.STATUS_REALIZADO, 120000, "Taller Lopez"),
            row(vehicleId, "Cambio de aceite", Maintenance.STATUS_PROXIMO, 125000, "Taller Central"),
            row(vehicleId, "Cambio de frenos", Maintenance.STATUS_PROXIMO, 130000, "Taller Central"),
            row(vehicleId, "Cambio de bateria", Maintenance.STATUS_REALIZADO, 110000, "Taller Central"),
            row(vehicleId, "Afinacion", Maintenance.STATUS_PENDIENTE, 135000, "Taller Central"),
        )
        fixtures = rows.map { row ->
            val id = database.maintenanceDao().insert(row).toInt()
            fixtureIds += id
            row.copy(id = id)
        }
        LocalSession.resume(context)
    }

    @After
    fun removeOnlyTestFixtures() = runBlocking {
        scenario?.close()
        fixtureIds.forEach { database.maintenanceDao().deleteById(it) }
        createdVehicleId?.let { id ->
            database.openHelper.writableDatabase.execSQL("DELETE FROM vehicles WHERE id = ?", arrayOf(id))
        }
        createdUserId?.let { id ->
            database.openHelper.writableDatabase.execSQL("DELETE FROM usuarios WHERE id = ?", arrayOf(id))
        }
        if (wasSessionClosed) LocalSession.close(context) else LocalSession.resume(context)
    }

    @Test
    fun typingTabsClearEmptyRoomChangesAndNavigationWorkTogether() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor("home loaded") { it.findViewById<View>(R.id.cardVehicle) != null }
        onView(withId(R.id.nav_mantenimiento)).perform(click())
        waitFor("search displayed") { it.findViewById<View>(R.id.etSearchMaintenance) != null }

        // Actual keyboard input, without pressing a Search button.
        onView(withId(R.id.etSearchMaintenance)).perform(click(), typeTextIntoFocusedView("$prefix a"))
        waitFor("typed query $prefix a") {
            it.findViewById<EditText>(R.id.etSearchMaintenance)?.text?.toString() == "$prefix a"
        }
        awaitIds(fixtures.map { it.id }.toSet())
        onView(withId(R.id.etSearchMaintenance)).perform(typeTextIntoFocusedView("c"))
        waitFor("typed query $prefix ac") {
            it.findViewById<EditText>(R.id.etSearchMaintenance)?.text?.toString() == "$prefix ac"
        }
        awaitIds(setOf(fixtures[0].id, fixtures[1].id, fixtures[4].id))
        onView(withId(R.id.etSearchMaintenance)).perform(typeTextIntoFocusedView("e"), closeSoftKeyboard())
        waitFor("typed query $prefix ace") {
            it.findViewById<EditText>(R.id.etSearchMaintenance)?.text?.toString() == "$prefix ace"
        }
        awaitIds(setOf(fixtures[0].id, fixtures[1].id))
        search("$prefix ACEITE")
        awaitIds(setOf(fixtures[0].id, fixtures[1].id))
        captureScreenshot("maintenance-filtered.png")
        search("$prefix Taller Lopez")
        awaitIds(setOf(fixtures[0].id))
        search("$prefix 120000")
        awaitIds(setOf(fixtures[0].id))
        search("$prefix 15/09/2026")
        awaitIds(fixtures.map { it.id }.toSet())

        selectTab(R.string.filter_proximos)
        search("$prefix aceite")
        awaitIds(setOf(fixtures[1].id))
        val upcomingIds = runBlocking {
            database.maintenanceDao().getAllMaintenances()
                .filter { it.status == Maintenance.STATUS_PROXIMO }.map { it.id }.toSet()
        }
        // Use Material's actual X control, not setText, and retain the selected tab.
        onView(withId(R.id.etSearchMaintenance)).perform(click())
        onView(allOf(
            withId(com.google.android.material.R.id.text_input_end_icon),
            isDescendantOfA(withId(R.id.tilSearchMaintenance)),
        )).perform(click(), closeSoftKeyboard())
        awaitIds(upcomingIds)
        onView(withId(R.id.etSearchMaintenance)).check(matches(withText("")))
        assertSelectedTab(1)

        search("$prefix nonexistent")
        awaitIds(emptySet())
        onView(withId(R.id.tvEmptyTitle)).check(matches(withText(R.string.maintenance_no_results)))
        onView(withId(R.id.tvEmptyMessage)).check(matches(withText(R.string.maintenance_try_another_term)))
        onView(withId(R.id.containerEmpty)).check(matches(isDisplayed()))
        captureScreenshot("maintenance-empty.png")

        search("$prefix aceite")
        awaitIds(setOf(fixtures[1].id))
        // Room changes must re-filter automatically while query and tab remain active.
        runBlocking { database.maintenanceDao().update(fixtures[1].copy(status = Maintenance.STATUS_REALIZADO)) }
        awaitIds(emptySet())
        assertSelectedTab(1)
        runBlocking { database.maintenanceDao().update(fixtures[1]) }
        awaitIds(setOf(fixtures[1].id))
        runBlocking { database.maintenanceDao().deleteById(fixtures[1].id) }
        awaitIds(emptySet())
        runBlocking { database.maintenanceDao().insert(fixtures[1]) }
        awaitIds(setOf(fixtures[1].id))

        scenario!!.recreate()
        waitFor("restored search and filter") { root ->
            root.findViewById<EditText>(R.id.etSearchMaintenance)?.text?.toString() == "$prefix aceite" &&
                root.findViewById<TabLayout>(R.id.tabFilters)?.selectedTabPosition == 1
        }
        awaitIds(setOf(fixtures[1].id))
        onView(withId(R.id.nav_inicio)).perform(click())
        waitFor("home after leaving search") { it.findViewById<View>(R.id.cardVehicle) != null }
        onView(withId(R.id.nav_mantenimiento)).perform(click())
        waitFor("search retained after bottom navigation") { root ->
            root.findViewById<EditText>(R.id.etSearchMaintenance)?.text?.toString() == "$prefix aceite"
        }
        awaitIds(setOf(fixtures[1].id))
        assertSelectedTab(1)
    }

    @Test
    fun editAndDeleteExistingMaintenanceThroughItsOriginalForm() {
        val original = fixtures[2]
        val updatedType = "${original.type} revisado"
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor("home loaded") { it.findViewById<View>(R.id.cardVehicle) != null }
        onView(withId(R.id.nav_mantenimiento)).perform(click())
        waitFor("search displayed") { it.findViewById<View>(R.id.etSearchMaintenance) != null }
        search("$prefix frenos")
        awaitIds(setOf(original.id))
        onView(allOf(withId(R.id.tvTitle), withText(original.type))).perform(click())
        waitFor("existing maintenance form loaded") { root ->
            root.findViewById<EditText>(R.id.etType)?.let {
                it.isEnabled && it.text.toString() == original.type
            } == true
        }
        onView(withId(R.id.actStatus)).check(matches(withText(original.status)))
        onView(withId(R.id.etType)).perform(replaceText(updatedType), closeSoftKeyboard())
        onView(withId(R.id.btnSaveMaintenance)).perform(click())
        waitFor("return from saving maintenance") { it.findViewById<View>(R.id.etSearchMaintenance) != null }
        awaitIds(setOf(original.id))
        val updated = runBlocking { database.maintenanceDao().getById(original.id) }
        assertEquals(updatedType, updated?.type)
        assertEquals(original.vehicleId, updated?.vehicleId)
        assertEquals(original.status, updated?.status)
        assertEquals(original.mileage, updated?.mileage)
        assertEquals(original.date, updated?.date)
        onView(withId(R.id.etSearchMaintenance)).check(matches(withText("$prefix frenos")))

        waitFor("updated card layout settled") { root ->
            root.findViewById<RecyclerView>(R.id.rvMaintenances)?.let {
                !it.isComputingLayout && it.itemAnimator?.isRunning != true
            } == true
        }
        onView(allOf(withId(R.id.tvTitle), withText(updatedType))).perform(click())
        waitFor("edited maintenance reloaded") { root ->
            root.findViewById<EditText>(R.id.etType)?.let {
                it.isEnabled && it.text.toString() == updatedType
            } == true
        }
        onView(withId(R.id.btnDeleteMaintenance)).perform(click())
        onView(withId(android.R.id.button1)).perform(click())
        waitFor("return from deleting maintenance") { it.findViewById<View>(R.id.etSearchMaintenance) != null }
        awaitIds(emptySet())
        assertNull(runBlocking { database.maintenanceDao().getById(original.id) })
        onView(withId(R.id.tvEmptyTitle)).check(matches(withText(R.string.maintenance_no_results)))
    }

    private fun row(vehicleId: Int, service: String, status: String, mileage: Int, workshop: String) = Maintenance(
        vehicleId = vehicleId, type = "$prefix $service", date = "2026-09-15", mileage = mileage,
        cost = 900.0, workshop = workshop, nextDate = "2026-10-20", nextMileage = mileage + 5000,
        description = "Fixture $prefix", status = status,
    )

    private fun search(text: String) {
        onView(withId(R.id.etSearchMaintenance)).perform(replaceText(text), closeSoftKeyboard())
    }

    private fun selectTab(textResource: Int) {
        onView(allOf(withText(textResource), isDescendantOfA(withId(R.id.tabFilters)))).perform(click())
    }

    private fun assertSelectedTab(position: Int) {
        onView(withId(R.id.tabFilters)).check { view, error ->
            if (error != null) throw error
            assertEquals(position, (view as TabLayout).selectedTabPosition)
        }
    }

    private fun awaitIds(expected: Set<Int>) {
        waitFor("maintenance IDs $expected") { root ->
            val adapter = root.findViewById<RecyclerView>(R.id.rvMaintenances)?.adapter as? MaintenanceAdapter
            val loading = root.findViewById<View>(R.id.progressLoading)?.visibility == View.VISIBLE
            val emptyVisible = root.findViewById<View>(R.id.containerEmpty)?.visibility == View.VISIBLE
            !loading && adapter != null && adapter.currentList.map { it.id }.toSet() == expected &&
                (expected.isNotEmpty() || emptyVisible)
        }
    }

    private fun captureScreenshot(name: String) {
        val output = java.io.File(requireNotNull(context.getExternalFilesDir("qa")), name)
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        output.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun waitFor(description: String, condition: (View) -> Boolean) {
        onView(isRoot()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()
            override fun getDescription() = "Wait up to 5 seconds for $description"
            override fun perform(uiController: UiController, view: View) {
                val deadline = SystemClock.elapsedRealtime() + 5_000
                var activeRoot = view
                do {
                    // Activity transitions can complete after Espresso selects the previous root.
                    activeRoot = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED).firstOrNull()?.window?.decorView ?: view
                    if (condition(activeRoot)) return
                    uiController.loopMainThreadForAtLeast(50)
                } while (SystemClock.elapsedRealtime() < deadline)
                val query = activeRoot.findViewById<EditText>(R.id.etSearchMaintenance)?.text
                val tab = activeRoot.findViewById<TabLayout>(R.id.tabFilters)?.selectedTabPosition
                val adapter = activeRoot.findViewById<RecyclerView>(R.id.rvMaintenances)?.adapter as? MaintenanceAdapter
                val items = adapter?.currentList?.joinToString { "${it.id}: ${it.type} (${it.status})" }
                val loading = activeRoot.findViewById<View>(R.id.progressLoading)?.visibility
                throw AssertionError(
                    "Timed out waiting for $description; query=$query; tab=$tab; loading=$loading; actual=[$items]",
                )
            }
        })
    }
}
