package com.netplus

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.netplus.data.repository.BillingRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Netplus", appName)
  }

  @Test
  fun `test remember me without biometric is rejected and cleared`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = BillingRepository(context)

    // Save with biometric first
    repo.saveRememberedCustomer("123", "User", enableBiometric = true, enableAutoLogin = true)
    assertEquals("123", repo.getRememberedId())

    // Attempt to save without biometric -> must clear and reject
    repo.saveRememberedCustomer("123", "User", enableBiometric = false, enableAutoLogin = true)
    assertEquals("", repo.getRememberedId())
    assertEquals("", repo.getRememberedName())
    assertFalse(repo.isBiometricEnabled())
    assertFalse(repo.isAutoLoginEnabled())
  }
}

