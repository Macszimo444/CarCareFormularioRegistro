package com.example.carcareformularioregistro.ui

import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.EditText
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises master's original form against the integrated app, restoring all profile changes. */
@RunWith(AndroidJUnit4::class)
class RegistrationUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val database by lazy { AppDatabase.getInstance(context) }
    private val prefix = "QARegistration${System.nanoTime()}"
    private var originalUser: User? = null
    private var wasSessionClosed = false
    private var initialUserCount = 0
    private val scenarios = mutableListOf<ActivityScenario<RegistroActivity>>()

    @Before
    fun rememberExistingProfile() = runBlocking {
        originalUser = database.userDao().getPrimaryUser()
        initialUserCount = database.userDao().obtenerTodos().size
        wasSessionClosed = LocalSession.isClosed(context)
        LocalSession.close(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(
                    "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS",
                ),
            ).use { it.readBytes() }
        }
    }

    @After
    fun restoreExistingProfile() = runBlocking {
        scenarios.forEach { it.close() }
        instrumentation.runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .toList().forEach { it.finishAffinity() }
        }
        val previous = originalUser
        if (previous != null) {
            database.userDao().actualizar(previous)
        } else {
            // The test may fail after Save, before it has read the newly generated ID.
            database.userDao().obtenerTodos()
                .filter { it.nombre == prefix && it.apellidos == "Registro UI" }
                .forEach { own ->
                    database.openHelper.writableDatabase.execSQL(
                        "DELETE FROM usuarios WHERE id = ?", arrayOf(own.id),
                    )
                }
        }
        if (wasSessionClosed) LocalSession.close(context) else LocalSession.resume(context)
    }

    @Test
    fun originalRegistrationValidatesSavesResumesAndClosesLocalProfile() {
        scenarios += ActivityScenario.launch(RegistroActivity::class.java)
        waitFor("registration form loaded") { root ->
            root.findViewById<EditText>(R.id.etNombre)?.isEnabled == true
        }
        listOf(R.id.etNombre, R.id.etApellidos, R.id.etDireccion, R.id.etTelefono).forEach { id ->
            onView(withId(id)).perform(scrollTo(), replaceText(""), closeSoftKeyboard())
        }
        onView(withId(R.id.btnGuardar)).perform(click(), closeSoftKeyboard())
        listOf(R.id.tilNombre, R.id.tilApellidos, R.id.tilDireccion, R.id.tilTelefono).forEach { id ->
            onView(withId(id)).check { view, error ->
                if (error != null) throw error
                assertEquals(context.getString(R.string.campo_obligatorio), (view as TextInputLayout).error)
            }
        }
        fill(R.id.etNombre, prefix)
        fill(R.id.etApellidos, "Registro UI")
        fill(R.id.etDireccion, "Calle de prueba 123")
        fill(R.id.etTelefono, "5551234567")
        onView(withId(R.id.btnGuardar)).perform(click())
        waitFor("main app after saving registration") { it.findViewById<View>(R.id.cardVehicle) != null }
        val saved = runBlocking { database.userDao().getPrimaryUser() }
        assertNotNull(saved)
        assertEquals(prefix, saved?.nombre)
        assertEquals("Registro UI", saved?.apellidos)
        assertEquals("Calle de prueba 123", saved?.direccion)
        assertEquals("5551234567", saved?.telefono)
        originalUser?.let { assertEquals(it.id, saved?.id) }
        assertEquals(initialUserCount + if (originalUser == null) 1 else 0,
            runBlocking { database.userDao().obtenerTodos().size })

        // Launching the entry screen with an open local profile resumes the main app.
        scenarios += ActivityScenario.launch(RegistroActivity::class.java)
        waitFor("automatic resume of saved profile") { it.findViewById<View>(R.id.cardVehicle) != null }
        assertEquals(saved?.id, runBlocking { database.userDao().getPrimaryUser()?.id })
        onView(withId(R.id.nav_gastos)).perform(click())
        waitFor("expenses screen") { it.findViewById<View>(R.id.tvMonthlyTotal) != null }
        onView(withId(R.id.nav_mantenimiento)).perform(click())
        waitFor("maintenance screen") { it.findViewById<View>(R.id.btnViewHistory) != null }
        onView(withId(R.id.btnViewHistory)).perform(click())
        waitFor("history screen") { it.findViewById<View>(R.id.rvHistory) != null }
        pressBack()
        waitFor("maintenance after history") { it.findViewById<View>(R.id.etSearchMaintenance) != null }
        onView(withId(R.id.nav_inicio)).perform(click())
        waitFor("home before reminders") { it.findViewById<View>(R.id.btnNotifications) != null }
        onView(withId(R.id.btnNotifications)).perform(click())
        waitFor("reminders screen") { it.findViewById<View>(R.id.rvReminders) != null }
        pressBack()
        waitFor("home after reminders") { it.findViewById<View>(R.id.cardVehicle) != null }
        onView(withId(R.id.nav_perfil)).perform(click())
        waitFor("profile screen") { it.findViewById<View>(R.id.btnLogout) != null }
        onView(withId(R.id.btnLogout)).perform(scrollTo(), click())
        waitFor("registration after closing local profile") { root ->
            root.findViewById<View>(R.id.btnContinuarPerfil)?.let {
                it.visibility == View.VISIBLE && it.isEnabled
            } == true
        }
        onView(withId(R.id.etNombre)).check(matches(withText(prefix)))
        onView(withId(R.id.btnContinuarPerfil)).check(matches(isDisplayed())).perform(click())
        waitFor("main app after continuing local profile") { it.findViewById<View>(R.id.cardVehicle) != null }
        assertEquals(saved?.id, runBlocking { database.userDao().getPrimaryUser()?.id })
        assertEquals(initialUserCount + if (originalUser == null) 1 else 0,
            runBlocking { database.userDao().obtenerTodos().size })
    }

    private fun fill(id: Int, value: String) {
        onView(withId(id)).perform(scrollTo(), replaceText(value), closeSoftKeyboard())
    }

    private fun waitFor(description: String, condition: (View) -> Boolean) {
        onView(isRoot()).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()
            override fun getDescription() = "Wait up to 5 seconds for $description"
            override fun perform(uiController: UiController, view: View) {
                val deadline = SystemClock.elapsedRealtime() + 5_000
                do {
                    val active = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED).firstOrNull()
                    if (condition(active?.window?.decorView ?: view)) return
                    uiController.loopMainThreadForAtLeast(50)
                } while (SystemClock.elapsedRealtime() < deadline)
                throw AssertionError("Timed out waiting for $description")
            }
        })
    }
}
