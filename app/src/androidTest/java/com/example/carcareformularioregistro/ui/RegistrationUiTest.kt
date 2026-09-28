package com.example.carcareformularioregistro.ui

import android.os.SystemClock
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
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
import com.example.carcareformularioregistro.RegistroActivity
import com.example.carcareformularioregistro.data.AppDatabase
import com.example.carcareformularioregistro.data.User
import com.example.carcareformularioregistro.utils.LocalSession
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses a temporary highest-id profile; never edits or deletes the user's existing profile. */
@RunWith(AndroidJUnit4::class)
class RegistrationUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val database by lazy { AppDatabase.getInstance(context) }
    // Names deliberately contain letters only, matching the real name validation.
    private val prefix = "QARegistro" + System.nanoTime().toString().map { 'a' + (it - '0') }.joinToString("")
    private var fixtureUserId: Int? = null
    private var wasSessionClosed = false
    private var initialUserCount = 0
    private val scenarios = mutableListOf<ActivityScenario<*>>()

    @Before
    fun prepareTemporaryProfile() = runBlocking {
        initialUserCount = database.userDao().obtenerTodos().size
        wasSessionClosed = LocalSession.isClosed(context)
        fixtureUserId = database.userDao().insertar(User(nombre = prefix, apellidos = "Registro UI")).toInt()
        LocalSession.close(context)
    }

    @After
    fun removeOnlyTemporaryProfile() = runBlocking {
        scenarios.forEach { it.close() }
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .toList().forEach { it.finishAffinity() }
        }
        fixtureUserId?.let {
            database.openHelper.writableDatabase.execSQL("DELETE FROM usuarios WHERE id = ?", arrayOf(it))
        }
        if (wasSessionClosed) LocalSession.close(context) else LocalSession.resume(context)
    }

    @Test
    fun requiredNamesOptionalContactSaveResumeAndCloseLocalProfile() {
        scenarios += ActivityScenario.launch(RegistroActivity::class.java)
        waitFor("registration form loaded") { it.findViewById<EditText>(R.id.etNombre)?.isEnabled == true }
        fill(R.id.etNombre, "")
        fill(R.id.etApellidos, "")
        onView(withId(R.id.btnGuardar)).perform(click(), closeSoftKeyboard())
        listOf(R.id.tilNombre, R.id.tilApellidos).forEach { assertError(it, R.string.campo_obligatorio) }
        listOf(R.id.tilDireccion, R.id.tilTelefono).forEach { assertNoError(it) }
        fill(R.id.etNombre, prefix)
        fill(R.id.etApellidos, "Registro UI")
        onView(withId(R.id.btnGuardar)).perform(click())
        waitFor("main app after saving without contact") { it.findViewById<View>(R.id.tvVehicleName) != null }
        val saved = runBlocking { database.userDao().getPrimaryUser() }
        assertEquals(fixtureUserId, saved?.id)
        assertEquals(prefix, saved?.nombre)
        assertEquals("", saved?.direccion)
        assertEquals("", saved?.telefono)
        assertEquals(initialUserCount + 1, runBlocking { database.userDao().obtenerTodos().size })

        scenarios += ActivityScenario.launch(RegistroActivity::class.java)
        waitFor("saved profile resumes automatically") { it.findViewById<View>(R.id.tvVehicleName) != null }
        onView(withId(R.id.nav_gastos)).perform(click())
        waitFor("expenses screen") { it.findViewById<View>(R.id.rvExpenses) != null }
        onView(withId(R.id.nav_mantenimiento)).perform(click())
        waitFor("maintenance screen") { it.findViewById<View>(R.id.btnViewHistory) != null }
        onView(withId(R.id.btnViewHistory)).perform(click())
        waitFor("history screen") { it.findViewById<View>(R.id.rvHistory) != null }
        pressBack()
        waitFor("maintenance after history") { it.findViewById<View>(R.id.etSearchMaintenance) != null }
        onView(withId(R.id.nav_inicio)).perform(click())
        waitFor("home before reminders") { it.findViewById<View>(R.id.btnReminders) != null }
        onView(withId(R.id.btnReminders)).perform(scrollTo(), click())
        waitFor("reminders screen") { it.findViewById<View>(R.id.rvReminders) != null }
        pressBack()
        waitFor("home after reminders") { it.findViewById<View>(R.id.tvVehicleName) != null }
        onView(withId(R.id.nav_perfil)).perform(click())
        waitFor("profile screen") { it.findViewById<View>(R.id.btnLogout) != null }
        onView(withId(R.id.btnLogout)).perform(scrollTo(), click())
        waitFor("registration after closing local profile") { root ->
            root.findViewById<View>(R.id.btnContinuarPerfil)?.let { it.visibility == View.VISIBLE && it.isEnabled } == true
        }
        onView(withId(R.id.etNombre)).check(matches(withText(prefix)))
        onView(withId(R.id.btnContinuarPerfil)).check(matches(isDisplayed())).perform(click())
        waitFor("main app after continuing local profile") { it.findViewById<View>(R.id.tvVehicleName) != null }
        assertEquals(fixtureUserId, runBlocking { database.userDao().getPrimaryUser()?.id })
        assertEquals(initialUserCount + 1, runBlocking { database.userDao().obtenerTodos().size })
    }

    @Test
    fun profileEditRouteValidatesContactAndCancelDoesNotOverwriteProfile() {
        LocalSession.resume(context)
        scenarios += ActivityScenario.launch(MainActivity::class.java)
        waitFor("home") { it.findViewById<View>(R.id.tvVehicleName) != null }
        onView(withId(R.id.nav_perfil)).perform(click())
        waitFor("profile edit action") { it.findViewById<View>(R.id.btnEditProfile) != null }
        onView(withId(R.id.btnEditProfile)).perform(scrollTo(), click())
        waitFor("explicit edit route stays on form despite open session") { root ->
            root.findViewById<EditText>(R.id.etNombre)?.isEnabled == true &&
                root.findViewById<TextView>(R.id.tvRegistrationTitle)?.text == context.getString(R.string.profile_edit_title)
        }
        fill(R.id.etNombre, "Nombre 123")
        onView(withId(R.id.btnGuardar)).perform(click(), closeSoftKeyboard())
        assertError(R.id.tilNombre, R.string.profile_name_error)
        fill(R.id.etNombre, "$prefix Editado")
        onView(withId(R.id.btnOptionalProfile)).perform(scrollTo(), click())
        fill(R.id.etDireccion, "Calle de prueba 123")
        fill(R.id.etTelefono, "12345")
        onView(withId(R.id.btnGuardar)).perform(click(), closeSoftKeyboard())
        assertError(R.id.tilTelefono, R.string.profile_phone_error)
        assertEquals(prefix, runBlocking { database.userDao().obtenerPorId(requireNotNull(fixtureUserId))?.nombre })
        fill(R.id.etTelefono, "+52 55 1234 5678")
        onView(withId(R.id.btnGuardar)).perform(click())
        waitFor("return to profile after edit") {
            it.findViewById<TextView>(R.id.tvUserName)?.text?.toString() == "$prefix Editado Registro UI"
        }
        val saved = runBlocking { database.userDao().obtenerPorId(requireNotNull(fixtureUserId)) }
        assertEquals("5512345678", saved?.telefono)
        assertEquals("Calle de prueba 123", saved?.direccion)
        assertEquals(initialUserCount + 1, runBlocking { database.userDao().obtenerTodos().size })

        onView(withId(R.id.btnEditProfile)).perform(scrollTo(), click())
        waitFor("edit reopened") { it.findViewById<EditText>(R.id.etNombre)?.isEnabled == true }
        fill(R.id.etNombre, "$prefix Cancelado")
        onView(withId(R.id.btnContinuarPerfil)).perform(click())
        waitFor("cancel returns to profile") { it.findViewById<View>(R.id.btnEditProfile) != null }
        assertEquals("$prefix Editado", runBlocking { database.userDao().obtenerPorId(requireNotNull(fixtureUserId))?.nombre })
    }

    private fun fill(id: Int, value: String) {
        onView(withId(id)).perform(scrollTo(), replaceText(value), closeSoftKeyboard())
    }

    private fun assertError(id: Int, errorResource: Int) {
        onView(withId(id)).check { view, error ->
            if (error != null) throw error
            assertEquals(context.getString(errorResource), (view as TextInputLayout).error?.toString())
        }
    }

    private fun assertNoError(id: Int) {
        onView(withId(id)).check { view, error ->
            if (error != null) throw error
            assertNull((view as TextInputLayout).error)
        }
    }

    private fun waitFor(description: String, condition: (View) -> Boolean) {
        onView(isRoot()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()
            override fun getDescription() = "Wait up to 5 seconds for $description"
            override fun perform(uiController: UiController, view: View) {
                val deadline = SystemClock.elapsedRealtime() + 5_000
                do {
                    val active = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
                    if (condition(active?.window?.decorView ?: view)) return
                    uiController.loopMainThreadForAtLeast(50)
                } while (SystemClock.elapsedRealtime() < deadline)
                throw AssertionError("Timed out waiting for $description")
            }
        })
    }
}
