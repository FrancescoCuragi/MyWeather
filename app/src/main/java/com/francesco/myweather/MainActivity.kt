package com.francesco.myweather

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.francesco.myweather.ui.MyWeatherApp
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

                    val permissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestMultiplePermissions(),
                    ) { granted ->
                        if (granted.values.any { it }) viewModel.refreshGps()
                    }
                    LaunchedEffect(Unit) {
                        val permissions = arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        )
                        val hasPermission = permissions.any {
                            ContextCompat.checkSelfPermission(this@MainActivity, it) ==
                                PackageManager.PERMISSION_GRANTED
                        }
                        if (hasPermission) viewModel.refreshGps() else permissionLauncher.launch(permissions)
                    }

                    MyWeatherApp(viewModel)
                }
            }
        }
    }
}
