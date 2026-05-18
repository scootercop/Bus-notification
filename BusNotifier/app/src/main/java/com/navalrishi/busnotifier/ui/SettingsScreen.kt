package com.navalrishi.busnotifier.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.navigation.NavController
import com.navalrishi.busnotifier.BusNotifierApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as BusNotifierApp
    var apiKey by remember { mutableStateOf(app.keyStore.getApiKey().orEmpty()) }
    var reveal by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
    }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Auckland Transport API key", style = MaterialTheme.typography.titleMedium)
            Text(
                "Sign up at dev-portal.at.govt.nz, subscribe to the Public Restricted API, copy the primary key here. " +
                "Stored encrypted on device.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; saved = false },
                label = { Text("API key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions.Default,
                trailingIcon = {
                    TextButton(onClick = { reveal = !reveal }) {
                        Text(if (reveal) "Hide" else "Show")
                    }
                }
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    app.keyStore.setApiKey(apiKey)
                    saved = true
                }) { Text("Save") }
                if (saved) Text("Saved", modifier = Modifier.align(Alignment.CenterVertically))
            }

            HorizontalDivider()
            Text("System permissions", style = MaterialTheme.typography.titleMedium)

            val pm = ctx.getSystemService<PowerManager>()
            val ignoringBattery = pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true
            Text(
                if (ignoringBattery) "Battery optimization: disabled for this app ✓"
                else "Battery optimization is ON — alarms may be delayed.",
                style = MaterialTheme.typography.bodyMedium
            )
            if (!ignoringBattery) {
                OutlinedButton(onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${ctx.packageName}"))
                    ctx.startActivity(intent)
                }) { Text("Disable battery optimization") }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val am = ctx.getSystemService(android.app.AlarmManager::class.java)
                val canExact = am?.canScheduleExactAlarms() == true
                Text(
                    if (canExact) "Exact alarms: granted ✓"
                    else "Exact alarms are NOT allowed — open system settings to enable.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!canExact) {
                    OutlinedButton(onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${ctx.packageName}"))
                        ctx.startActivity(intent)
                    }) { Text("Allow exact alarms") }
                }
            }
        }
    }
}
