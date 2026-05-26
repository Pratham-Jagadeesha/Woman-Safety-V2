package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.ui.SafetyViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SafeHer", appName)
  }

  @Test
  fun `verify MainActivity doesnt crash on launch`() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        org.junit.Assert.assertNotNull(activity)
      }
    }
  }

  @Test
  fun `safety viewmodel live location sharing toggle and settings`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = SafetyViewModel(app)
    
    // Check initial state
    assertFalse(viewModel.isLiveTrackingActive.value)
    assertEquals(60, viewModel.autoExpireMinutes.value)

    // Set auto expire minutes
    viewModel.setAutoExpireMinutes(15)
    assertEquals(15, viewModel.autoExpireMinutes.value)

    // Start live tracking
    viewModel.startLiveTracking()
    assertTrue(viewModel.isLiveTrackingActive.value)
    assertEquals(15 * 60, viewModel.remainingSeconds.value)

    // Stop tracking
    viewModel.stopLiveTracking("User clicked stop button in controller tests.")
    assertFalse(viewModel.isLiveTrackingActive.value)
  }
}
