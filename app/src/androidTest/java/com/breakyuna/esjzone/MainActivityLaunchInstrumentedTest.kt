package com.breakyuna.esjzone

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.platform.ComposeView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLaunchInstrumentedTest {
    @Test
    fun launchesMainActivityOnDevice() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse(activity.isFinishing)
                assertNotNull(activity.window)
                assertNotNull(activity.window.decorView)
                val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
                assertTrue((0 until content.childCount).any { content.getChildAt(it) is ComposeView })
            }
        }
    }
}
