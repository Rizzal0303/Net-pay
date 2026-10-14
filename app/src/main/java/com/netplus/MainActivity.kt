package com.netplus

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.netplus.ui.screens.BillingMainScreen
import com.netplus.ui.theme.MyApplicationTheme
import com.netplus.ui.viewmodel.BillingViewModel

class MainActivity : FragmentActivity() {

    private val billingViewModel: BillingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if user enabled auto-login / biometric authentication on launch
        billingViewModel.performAutoLoginIfEligible(this)

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BillingMainScreen(
                        viewModel = billingViewModel,
                        fragmentActivity = this
                    )
                }
            }
        }
    }
}
