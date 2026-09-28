package com.example.carcareformularioregistro.ui

import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.carcareformularioregistro.R
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupScreenTest {
    @Test fun backupEntryPointsRemainAvailableAfterRecreation() {
        ActivityScenario.launch(BackupActivity::class.java).use { scenario ->
            onView(withId(R.id.btnExport)).check(matches(isEnabled()))
            onView(withId(R.id.btnImport)).check(matches(isEnabled()))
            scenario.recreate()
            onView(withId(R.id.btnImport)).check(matches(isEnabled()))
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            val file = File(instrumentation.targetContext.getExternalFilesDir(null), "qa/backup-screen.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
