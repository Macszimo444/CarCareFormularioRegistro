package com.example.carcareformularioregistro.ui

import android.content.Intent
import android.graphics.Rect
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.carcareformularioregistro.R
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/** Guidance-only activity: no registration, app database, alarm or preferences are read/written. */
@RunWith(AndroidJUnit4::class)
class GuidanceUiTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<GuidanceActivity>? = null

    @After fun closeActivity() { scenario?.close() }

    private fun launch(mode: String = GuidanceActivity.MODE_ASSISTANT) {
        scenario?.close()
        scenario = ActivityScenario.launch(Intent(context, GuidanceActivity::class.java)
            .putExtra(GuidanceActivity.EXTRA_MODE, mode))
    }

    @Test fun allFourModesLaunchIndependentlyWithoutAProfileOrVehicleDependency() {
        listOf(
            GuidanceActivity.MODE_ASSISTANT to R.string.guidance_assistant_title,
            GuidanceActivity.MODE_GUIDE to R.string.guidance_guide_title,
            GuidanceActivity.MODE_HELP to R.string.guidance_help_title,
            GuidanceActivity.MODE_PRIVACY to R.string.guidance_privacy_title
        ).forEach { (mode, title) ->
            launch(mode)
            onView(withId(R.id.tvGuidanceTitle)).check(matches(withText(title)))
            onView(withId(R.id.containerGuidance)).check(matches(isDisplayed()))
            onView(withId(R.id.btnGuidanceBack)).check(matches(isDisplayed()))
        }
    }

    @Test fun dangerResultStopsQuestioningAndSurvivesActivityRecreation() {
        launch()
        choose("Humo, fuego u olor intenso a combustible")
        onView(withText("Detén el uso y pide ayuda")).check(matches(isDisplayed()))
        onView(withText("¿Qué has observado?")).check(doesNotExist())
        onView(withText("Ruido o cambios al frenar")).check(doesNotExist())
        scenario!!.recreate()
        onView(withText("Detén el uso y pide ayuda")).check(matches(isDisplayed()))
        choose(context.getString(R.string.guidance_restart))
        onView(withText("Primero, tu seguridad")).check(matches(isDisplayed()))
        onView(withText("Detén el uso y pide ayuda")).check(doesNotExist())
    }

    @Test fun intermediateAnswersRestoreAndChangingAnswerReplacesTheOldOutcome() {
        launch()
        choose("Ninguna de esas señales")
        onView(withText("¿Qué has observado?")).check(matches(isDisplayed()))
        choose("Un testigo en el tablero")
        onView(withText("¿Qué testigo reconoces?")).check(matches(isDisplayed()))
        choose("Motor / Check engine")
        scenario!!.recreate()
        onView(withText("¿Cómo se presenta?")).check(matches(isDisplayed()))
        choose("Parpadea o hay pérdida de potencia / tirones")
        onView(withText("Solicita asistencia para el aviso del motor")).check(matches(isDisplayed()))
        choose(context.getString(R.string.guidance_previous))
        choose("Permanece fijo, sin esos síntomas")
        onView(withText("Consulta el manual y un taller")).check(matches(isDisplayed()))
        onView(withText("Solicita asistencia para el aviso del motor")).check(doesNotExist())
        pressBack()
        onView(withText("¿Cómo se presenta?")).check(matches(isDisplayed()))
        onView(withText("Consulta el manual y un taller")).check(doesNotExist())
    }

    @Test fun expandedHelpRemainsOpenAfterRecreationAndCanCollapseAgain() {
        launch(GuidanceActivity.MODE_HELP)
        val firstSection = GuidanceContent.help.first()
        onView(withText(firstSection.body)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        onView(withContentDescription(context.getString(R.string.guidance_expand_named, firstSection.title)))
            .perform(scrollInsideGuidance(), click())
        onView(withText(firstSection.body)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
        scenario!!.recreate()
        onView(withText(firstSection.body)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
        onView(withContentDescription(context.getString(R.string.guidance_collapse_named, firstSection.title)))
            .perform(scrollInsideGuidance(), click())
        onView(withText(firstSection.body)).check(matches(withEffectiveVisibility(Visibility.GONE)))
    }

    private fun choose(label: String) {
        onView(withText(label)).perform(scrollInsideGuidance(), click())
    }

    /** Works with NestedScrollView and long wrapped choices without a fixed emulator screen size. */
    private fun scrollInsideGuidance() = object : ViewAction {
        override fun getConstraints(): Matcher<View> = isDescendantOfA(withId(R.id.scrollGuidance))
        override fun getDescription() = "Bring the guidance control into its scroll viewport"
        override fun perform(uiController: UiController, view: View) {
            uiController.loopMainThreadUntilIdle()
            view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
            uiController.loopMainThreadUntilIdle()
        }
    }
}
