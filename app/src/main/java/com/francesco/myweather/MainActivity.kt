package com.francesco.myweather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.francesco.myweather.ui.HomeScreen
import com.francesco.myweather.ui.WeatherViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(Modifier.statusBarsPadding()) {
                    val viewModel: WeatherViewModel = viewModel()
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    HomeScreen(state = state, onRetry = viewModel::refresh)
                }
            }
        }
    }
}
