package com.navalrishi.busnotifier.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.navalrishi.busnotifier.BusNotifierApp
import com.navalrishi.busnotifier.data.Watch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchListScreen(nav: NavController) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as BusNotifierApp
    val watches by app.database.watchDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bus Notifier") },
                actions = {
                    IconButton(onClick = { nav.navigate("settings") }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { nav.navigate("edit/0") },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add watch") }
            )
        }
    ) { padding ->
        if (watches.isEmpty()) {
            Column(
                modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "No watches yet",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Add a watch with a bus number, stop code, and the times you want to be alerted.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                if (!app.keyStore.hasApiKey()) {
                    AssistChip(
                        onClick = { nav.navigate("settings") },
                        label = { Text("Set your Auckland Transport API key first") }
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(watches, key = { it.id }) { w ->
                    WatchRow(
                        watch = w,
                        onToggle = { enabled ->
                            scope.launch(Dispatchers.IO) {
                                app.database.watchDao().update(w.copy(enabled = enabled))
                                if (enabled) app.scheduler.scheduleNext(w.copy(enabled = true))
                                else app.scheduler.cancel(w.id)
                            }
                        },
                        onEdit = { nav.navigate("edit/${w.id}") }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun WatchRow(watch: Watch, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    ListItem(
        headlineContent = { Text(watch.label.ifBlank { "Bus ${watch.routeShortName} @ ${watch.stopCode}" }) },
        supportingContent = {
            Text(
                "Route ${watch.routeShortName} · stop ${watch.stopCode} · " +
                "${formatDays(watch.daysMask)} · ${formatMinute(watch.startMinute)}–${formatMinute(watch.endMinute)} · " +
                "≤${watch.thresholdMin} min · poll ${watch.pollIntervalMin}m"
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = watch.enabled, onCheckedChange = onToggle)
            }
        },
        modifier = Modifier.padding(horizontal = 8.dp).then(Modifier).let {
            it
        },
    )
    TextButton(onClick = onEdit, modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)) {
        Text("Edit")
    }
}

internal fun formatDays(mask: Int): String {
    val names = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val on = names.indices.filter { (mask shr it) and 1 == 1 }.map { names[it] }
    return if (on.isEmpty()) "Never" else on.joinToString("·")
}

internal fun formatMinute(min: Int): String {
    val h = (min / 60).coerceIn(0, 23)
    val m = (min % 60).coerceIn(0, 59)
    return "%02d:%02d".format(h, m)
}
