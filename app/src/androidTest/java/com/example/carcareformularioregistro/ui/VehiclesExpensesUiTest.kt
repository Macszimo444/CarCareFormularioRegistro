package com.example.carcareformularioregistro.ui

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.carcareformularioregistro.MainActivity
import com.example.carcareformularioregistro.R
import com.example.carcareformularioregistro.adapter.ExpenseAdapter
import com.example.carcareformularioregistro.adapter.VehicleAdapter
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.Expense
import com.example.carcareformularioregistro.data.User
import com.example.carcareformularioregistro.data.Vehicle
import com.example.carcareformularioregistro.data.VehicleRepository
import com.example.carcareformularioregistro.utils.LocalSession
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Isolated QA vehicles own every expense touched; existing vehicles, profiles and selection are restored. */
@RunWith(AndroidJUnit4::class)
class VehiclesExpensesUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val db by lazy { AppDatabase.getInstance(context) }
    private val vehicles by lazy { VehicleRepository.getInstance(context) }
    private val prefix = "QAVehicles${System.nanoTime()}"
    private val ownVehicleIds = mutableListOf<Int>()
    private var ownUserId: Int? = null
    private var previousSelectedId = 0
    private var previousPrimaryId: Int? = null
    private var wasSessionClosed = false
    private var firstVehicle = 0
    private var secondVehicle = 0
    private var firstExpense = 0
    private var secondExpense = 0
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun createOnlyOwnFixtures() = runBlocking {
        previousSelectedId = context.getSharedPreferences("carcare_vehicle_selection", Context.MODE_PRIVATE)
            .getInt("selected_id", 0)
        previousPrimaryId = db.vehicleDao().getPrimaryVehicle()?.id
        wasSessionClosed = LocalSession.isClosed(context)
        if (db.userDao().getPrimaryUser() == null) {
            ownUserId = db.userDao().insertar(User(nombre = "Prueba", apellidos = "Vehículos UI")).toInt()
        }
        firstVehicle = db.vehicleDao().insertVehicle(Vehicle(name = "$prefix A", brand = "Nissan", model = "Versa",
            year = 2022, mileage = 85000, plates = "", isPrimary = previousPrimaryId == null)).toInt()
            .also { ownVehicleIds += it }
        secondVehicle = db.vehicleDao().insertVehicle(Vehicle(name = "$prefix B", brand = "Toyota", model = "Corolla",
            year = 2023, mileage = 50000, plates = "")).toInt().also { ownVehicleIds += it }
        firstExpense = db.expenseDao().insert(expense(firstVehicle, "$prefix Inicial A", 120.0)).toInt()
        secondExpense = db.expenseDao().insert(expense(secondVehicle, "$prefix Inicial B", 450.0)).toInt()
        vehicles.selectVehicle(firstVehicle)
        LocalSession.resume(context)
    }

    @After
    fun removeOnlyOwnFixturesAndRestoreSelection() = runBlocking {
        scenario?.close()
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .toList().forEach { it.finishAffinity() }
        }
        ownVehicleIds.forEach { id ->
            db.expenseDao().deleteForVehicle(id)
            db.vehicleDao().deleteById(id)
        }
        ownUserId?.let { db.openHelper.writableDatabase.execSQL("DELETE FROM usuarios WHERE id = ?", arrayOf(it)) }
        vehicles.selectVehicle(previousSelectedId)
        if (wasSessionClosed) LocalSession.close(context) else LocalSession.resume(context)
    }

    @Test
    fun switchingVehiclesChangesExpensesAndPersistsWithoutChangingPrimary() {
        launchApp()
        onView(withId(R.id.nav_gastos)).perform(click())
        awaitExpenseIds(setOf(firstExpense))
        onView(withId(R.id.nav_inicio)).perform(click())
        waitFor("home before choosing vehicle") { it.findViewById<View>(R.id.btnVehicles) != null }
        onView(withId(R.id.btnVehicles)).perform(scrollTo(), click())
        waitFor("vehicle list loaded") { root ->
            (root.findViewById<RecyclerView>(R.id.rvVehicles)?.adapter as? VehicleAdapter)
                ?.currentList?.any { it.vehicle.id == secondVehicle } == true
        }
        clickVehicleButton(secondVehicle, R.id.btnSelect)
        waitFor("second vehicle selected") { root ->
            (root.findViewById<RecyclerView>(R.id.rvVehicles)?.adapter as? VehicleAdapter)
                ?.currentList?.any { it.vehicle.id == secondVehicle && it.selected } == true
        }
        pressBack()
        waitFor("home reflects second vehicle") { it.findViewById<TextView>(R.id.tvVehicleName)?.text?.toString() == "$prefix B" }
        onView(withId(R.id.nav_gastos)).perform(click())
        awaitExpenseIds(setOf(secondExpense))
        waitFor("expense context is second vehicle") { it.findViewById<TextView>(R.id.tvVehicle)?.text?.contains("$prefix B") == true }
        scenario!!.recreate()
        awaitExpenseIds(setOf(secondExpense))
        assertEquals(secondVehicle, runBlocking { vehicles.getSelectedVehicle()?.id })
        assertEquals(previousPrimaryId ?: firstVehicle, runBlocking { db.vehicleDao().getPrimaryVehicle()?.id })
        assertEquals(firstVehicle, runBlocking { db.expenseDao().getById(firstExpense)?.vehicleId })
    }

    @Test
    fun expenseCreateRestoreEditDeleteRetainsCapturedVehicleAndOtherCarsData() {
        launchApp()
        onView(withId(R.id.nav_gastos)).perform(click())
        awaitExpenseIds(setOf(firstExpense))
        onView(withId(R.id.fabAddExpense)).perform(click())
        waitFor("new expense form loaded") { it.findViewById<EditText>(R.id.etConcept)?.isEnabled == true }
        fill(R.id.etConcept, "$prefix Alta")
        fill(R.id.etAmount, "0")
        onView(withId(R.id.btnSave)).perform(scrollTo(), click(), closeSoftKeyboard())
        onView(withId(R.id.tilAmount)).check { view, error ->
            if (error != null) throw error
            assertEquals(context.getString(R.string.expense_amount_invalid), (view as TextInputLayout).error?.toString())
        }
        fill(R.id.etAmount, "100.50")
        onView(withId(R.id.btnDetails)).perform(scrollTo(), click())
        fill(R.id.etDescription, "$prefix Recibo guardado")
        scenario!!.recreate()
        waitFor("draft restored after recreation") { root ->
            root.findViewById<EditText>(R.id.etConcept)?.let { it.isEnabled && it.text.toString() == "$prefix Alta" } == true &&
                root.findViewById<EditText>(R.id.etDescription)?.text?.toString() == "$prefix Recibo guardado"
        }
        // A selection update behind the open form must not reassign the captured expense.
        vehicles.selectVehicle(secondVehicle)
        onView(withId(R.id.btnSave)).perform(scrollTo(), click())
        awaitExpenseIds(setOf(secondExpense))
        val added = runBlocking { db.expenseDao().getAllExpenses().singleOrNull { it.concept == "$prefix Alta" } }
        assertNotNull(added)
        val addedId = requireNotNull(added).id
        assertEquals(firstVehicle, added.vehicleId)
        assertEquals(100.50, added.amount, 0.001)
        assertEquals("$prefix Recibo guardado", added.description)

        vehicles.selectVehicle(firstVehicle)
        awaitExpenseIds(setOf(firstExpense, addedId))
        openExpenseOptions("$prefix Alta")
        onView(withText(R.string.ve_edit)).perform(click())
        waitFor("existing expense form loaded") { root ->
            root.findViewById<EditText>(R.id.etConcept)?.let { it.isEnabled && it.text.toString() == "$prefix Alta" } == true
        }
        fill(R.id.etConcept, "$prefix Editado")
        fill(R.id.etAmount, "150.75")
        onView(withId(R.id.btnSave)).perform(scrollTo(), click())
        waitFor("updated expense observed in list") { root ->
            (root.findViewById<RecyclerView>(R.id.rvExpenses)?.adapter as? ExpenseAdapter)?.currentList
                ?.any { it.id == addedId && it.concept == "$prefix Editado" && it.amount == 150.75 } == true
        }
        assertEquals(firstVehicle, runBlocking { db.expenseDao().getById(addedId)?.vehicleId })
        openExpenseOptions("$prefix Editado")
        onView(withText(R.string.ve_delete)).perform(click())
        onView(withId(android.R.id.button1)).perform(click())
        awaitExpenseIds(setOf(firstExpense))
        assertNull(runBlocking { db.expenseDao().getById(addedId) })
        assertEquals(450.0, requireNotNull(runBlocking { db.expenseDao().getById(secondExpense) }).amount, 0.001)

        scenario!!.close()
        launchApp()
        onView(withId(R.id.nav_gastos)).perform(click())
        awaitExpenseIds(setOf(firstExpense))
        assertEquals(secondVehicle, runBlocking { db.expenseDao().getById(secondExpense)?.vehicleId })
    }

    private fun launchApp() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor("home ready") { it.findViewById<View>(R.id.tvVehicleName) != null }
    }

    private fun expense(vehicleId: Int, concept: String, amount: Double) = Expense(vehicleId = vehicleId,
        category = Expense.CAT_COMBUSTIBLE, concept = concept, amount = amount, date = LocalDate.now().toString())

    private fun fill(id: Int, value: String) {
        onView(withId(id)).perform(scrollTo(), replaceText(value), closeSoftKeyboard())
    }

    private fun awaitExpenseIds(expected: Set<Int>) = waitFor("expense IDs $expected") { root ->
        val list = root.findViewById<RecyclerView>(R.id.rvExpenses)
        val adapter = list?.adapter as? ExpenseAdapter
        val active = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
        val editorOpen = (active as? FragmentActivity)?.supportFragmentManager?.fragments
            ?.filterIsInstance<AddExpenseDialogFragment>()?.any { it.dialog?.isShowing == true } == true
        list != null && adapter != null && !editorOpen && adapter.currentList.map { it.id }.toSet() == expected &&
            !list.isComputingLayout && list.itemAnimator?.isRunning != true
    }

    private fun openExpenseOptions(concept: String) {
        // Expense rows live inside a wrap_content RecyclerView in a NestedScrollView.
        // Scroll the actual parent before tapping, so the popup is anchored to a visible button.
        onView(withContentDescription(context.getString(R.string.expense_options, concept)))
            .perform(scrollTo(), click())
    }

    /** Vehicle rows use a full-height RecyclerView, with its own scrolling. */
    private fun clickVehicleButton(itemId: Int, childId: Int) {
        onView(withId(R.id.rvVehicles)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = withId(R.id.rvVehicles)
            override fun getDescription() = "Click row action for fixture $itemId"
            override fun perform(uiController: UiController, view: View) {
                val list = view as RecyclerView
                val position = (list.adapter as VehicleAdapter).currentList.indexOfFirst { it.vehicle.id == itemId }
                check(position >= 0) { "Fixture missing from list" }
                list.scrollToPosition(position)
                uiController.loopMainThreadUntilIdle()
                val row = requireNotNull(list.findViewHolderForAdapterPosition(position)) { "Fixture row not laid out" }.itemView
                requireNotNull(row.findViewById<View>(childId)).performClick()
                uiController.loopMainThreadUntilIdle()
            }
        })
    }

    private fun waitFor(description: String, condition: (View) -> Boolean) {
        onView(isRoot()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()
            override fun getDescription() = "Wait up to 5 seconds for $description"
            override fun perform(uiController: UiController, view: View) {
                val deadline = SystemClock.elapsedRealtime() + 5_000
                do {
                    val active = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
                    val dialogRoots = (active as? FragmentActivity)?.supportFragmentManager?.fragments
                        ?.filterIsInstance<DialogFragment>()?.mapNotNull { it.dialog?.window?.decorView }.orEmpty()
                    if ((dialogRoots + listOfNotNull(active?.window?.decorView, view)).any(condition)) return
                    uiController.loopMainThreadForAtLeast(50)
                } while (SystemClock.elapsedRealtime() < deadline)
                throw AssertionError("Timed out waiting for $description")
            }
        })
    }
}
