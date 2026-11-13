package com.karthikmacherla.metrotracker

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karthikmacherla.metrotracker.plan.PlanScreen
import com.karthikmacherla.metrotracker.plan.PlanViewModel
import com.karthikmacherla.metrotracker.ui.theme.MetroTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MetroTrackerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // viewModel() returns the same PlanViewModel after rotation instead of a new one.
                    val planViewModel: PlanViewModel = viewModel()
                    val state by planViewModel.state.collectAsState()
                    PlanScreen(
                        state = state,
                        onSelectFrom = planViewModel::selectFrom,
                        onSelectTo = planViewModel::selectTo,
                        onSwap = planViewModel::swap,
                        onStartTrip = {
                            Toast.makeText(this, R.string.plan_tracking_not_ready, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}
