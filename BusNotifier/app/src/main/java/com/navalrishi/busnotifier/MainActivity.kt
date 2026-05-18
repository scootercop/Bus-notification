package com.navalrishi.busnotifier

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.navalrishi.busnotifier.ui.BusNotifierTheme
import com.navalrishi.busnotifier.ui.SettingsScreen
import com.navalrishi.busnotifier.ui.WatchEditScreen
import com.navalrishi.busnotifier.ui.WatchListScreen

class MainActivity : ComponentActivity() {

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result ignored — UI re-checks on resume */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            BusNotifierTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = "list") {
                        composable("list") { WatchListScreen(nav) }
                        composable("edit/{watchId}") { backStackEntry ->
                            val id = backStackEntry.arguments?.getString("watchId")?.toLongOrNull() ?: 0L
                            WatchEditScreen(nav, watchId = id)
                        }
                        composable("settings") { SettingsScreen(nav) }
                    }
                }
            }
        }
    }
}
