package com.navalrishi.busnotifier.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.navalrishi.busnotifier.BusNotifierApp
import com.navalrishi.busnotifier.data.Watch
import com.navalrishi.busnotifier.domain.EtaCalculator
import com.navalrishi.busnotifier.network.AtClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchEditScreen(nav: NavController, watchId: Long) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as BusNotifierApp
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf(watchId == 0L) }
    var label by remember { mutableStateOf("") }
    var route by remember { mutableStateOf("") }
    var stop by remember { mutableStateOf("") }
    var days by remember { mutableIntStateOf(0b0111110) } // Mon-Fri (bits 1..5)
    var startMin by remember { mutableIntStateOf(7 * 60 + 20) }
    var endMin by remember { mutableIntStateOf(8 * 60) }
    var threshold by remember { mutableIntStateOf(6) }
    var poll by remember { mutableIntStateOf(5) }
    var enabled by remember { mutableStateOf(true) }
    var testStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(watchId) {
        if (watchId != 0L) {
            val w = withContext(Dispatchers.IO) { app.database.watchDao().byId(watchId) }
            if (w != null) {
                label = w.label
                route = w.routeShortName
                stop = w.stopCode
                days = w.daysMask
                startMin = w.startMinute
                endMin = w.endMinute
                threshold = w.thresholdMin
                poll = w.pollIntervalMin
                enabled = w.enabled
            }
            loaded = true
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (watchId == 0L) "New watch" else "Edit watch") },
            navigationIcon = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (watchId != 0L) {
                    IconButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                app.database.watchDao().byId(watchId)?.let {
                                    app.database.watchDao().delete(it)
                                    app.scheduler.cancel(it.id)
                                }
                            }
                            nav.popBackStack()
                        }
                    }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                }
            }
        )
    }) { padding ->
        if (!loaded) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = label, onValueChange = { label = it },
                label = { Text("Label (optional)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = route, onValueChange = { route = it.trim() },
                label = { Text("Bus number (e.g. 712)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            OutlinedTextField(
                value = stop, onValueChange = { stop = it.trim() },
                label = { Text("Stop code (e.g. 6087)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Text("Active days", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val labels = listOf("S", "M", "T", "W", "T", "F", "S")
                labels.forEachIndexed { i, ch ->
                    FilterChip(
                        selected = (days shr i) and 1 == 1,
                        onClick = { days = days xor (1 shl i) },
                        label = { Text(ch) }
                    )
                }
            }

            TimePickerRow("Start", startMin) { startMin = it }
            TimePickerRow("End", endMin) { endMin = it }

            OutlinedTextField(
                value = threshold.toString(),
                onValueChange = { it.toIntOrNull()?.let { v -> threshold = v.coerceIn(1, 60) } },
                label = { Text("Notify when ETA ≤ (minutes)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = poll.toString(),
                onValueChange = { it.toIntOrNull()?.let { v -> poll = v.coerceIn(1, 30) } },
                label = { Text("Poll interval (minutes)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enabled", modifier = Modifier.weight(1f))
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            val w = buildWatch(watchId, label, route, stop, days, startMin, endMin, poll, threshold, enabled)
                                ?: run { testStatus = "Fill in bus number and stop code"; return@launch }
                            withContext(Dispatchers.IO) {
                                val id = app.database.watchDao().upsert(w)
                                val saved = w.copy(id = if (w.id == 0L) id else w.id)
                                if (saved.enabled) app.scheduler.scheduleNext(saved)
                                else app.scheduler.cancel(saved.id)
                            }
                            nav.popBackStack()
                        }
                    }
                ) { Text("Save") }

                OutlinedButton(onClick = {
                    testStatus = "Checking…"
                    scope.launch {
                        if (route.isBlank() || stop.isBlank()) {
                            testStatus = "Fill route and stop first"
                            return@launch
                        }
                        if (!app.keyStore.hasApiKey()) {
                            testStatus = "Set your AT API key in Settings first"
                            return@launch
                        }
                        val res = EtaCalculator(app.atClient).candidates(route, stop)
                        testStatus = when (res) {
                            is AtClient.Result.Ok -> {
                                val soonest = res.value.minByOrNull { it.etaMinutes }
                                if (soonest == null) "No upcoming arrivals right now."
                                else "Next: trip ${soonest.tripId} in ${soonest.etaMinutes} min"
                            }
                            is AtClient.Result.Err -> "Error: ${res.message}"
                        }
                    }
                }) { Text("Test now") }
            }
            testStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

private fun buildWatch(
    id: Long, label: String, route: String, stop: String,
    daysMask: Int, startMin: Int, endMin: Int,
    poll: Int, threshold: Int, enabled: Boolean,
): Watch? {
    if (route.isBlank() || stop.isBlank()) return null
    return Watch(
        id = id,
        label = label.trim(),
        routeShortName = route.trim(),
        stopCode = stop.trim(),
        daysMask = daysMask,
        startMinute = startMin.coerceIn(0, 1439),
        endMinute = endMin.coerceIn(0, 1440),
        pollIntervalMin = poll.coerceAtLeast(1),
        thresholdMin = threshold.coerceAtLeast(1),
        enabled = enabled,
    )
}

@Composable
private fun TimePickerRow(label: String, minutes: Int, onChange: (Int) -> Unit) {
    val h = minutes / 60
    val m = minutes % 60
    var hours by remember(minutes) { mutableStateOf(h.toString()) }
    var mins by remember(minutes) { mutableStateOf("%02d".format(m)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, modifier = Modifier.width(48.dp))
        OutlinedTextField(
            value = hours,
            onValueChange = { s -> hours = s.filter { it.isDigit() }.take(2); push(hours, mins, onChange) },
            label = { Text("HH") },
            modifier = Modifier.width(96.dp), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Text(":")
        OutlinedTextField(
            value = mins,
            onValueChange = { s -> mins = s.filter { it.isDigit() }.take(2); push(hours, mins, onChange) },
            label = { Text("MM") },
            modifier = Modifier.width(96.dp), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
    }
}

private fun push(h: String, m: String, onChange: (Int) -> Unit) {
    val hh = h.toIntOrNull()?.coerceIn(0, 23) ?: return
    val mm = m.toIntOrNull()?.coerceIn(0, 59) ?: return
    onChange(hh * 60 + mm)
}
