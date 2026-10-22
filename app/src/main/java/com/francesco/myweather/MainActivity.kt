package com.francesco.myweather

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.francesco.myweather.ui.HomeScreen
import com.francesco.myweather.ui.MyWeatherTheme
import com.francesco.myweather.ui.WeatherViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            MyWeatherTheme {
                Surface(Modifier.fillMaxSize()) {
                    val viewModel: WeatherViewModel = viewModel()
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    Box(Modifier.statusBarsPadding()) {
                        HomeScreen(state = state, onRefresh = viewModel::refresh)
                    }
                }
            }
        }
    }
}
