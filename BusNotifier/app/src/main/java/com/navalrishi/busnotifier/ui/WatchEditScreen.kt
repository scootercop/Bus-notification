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
    val snackbar = remember { SnackbarHostState() }

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
    var pickerOpenFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(watchId) {
        if (watchId != 0L) {
            val w = withContext(Dispatchers.IO) { app.database.watchDao().byId(watchId) }
            if (w != null) {
                label = w.label
                route = w.routeShortName
                stop = w.stopCode
                days = w.daysMask
                startMin = w.startMinute.coerceIn(0, 23 * 60 + 59)
                endMin = w.endMinute.coerceIn(1, 23 * 60 + 59)
                threshold = w.thresholdMin
                poll = w.pollIntervalMin
                enabled = w.enabled
            }
            loaded = true
        }
    }

    Scaffold(
        topBar = {
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
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
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

            TimeRow(label = "Start", minutes = startMin) { pickerOpenFor = "start" }
            TimeRow(label = "End", minutes = endMin) { pickerOpenFor = "end" }

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
                Button(onClick = {
                    scope.launch {
                        if (route.isBlank() || stop.isBlank()) {
                            snackbar.showSnackbar("Fill in bus number and stop code")
                            return@launch
                        }
                        if (endMin <= startMin) {
                            snackbar.showSnackbar("End time must be after start time")
                            return@launch
                        }
                        val w = Watch(
                            id = watchId,
                            label = label.trim(),
                            routeShortName = route.trim(),
                            stopCode = stop.trim(),
                            daysMask = days,
                            startMinute = startMin.coerceIn(0, 1439),
                            endMinute = endMin.coerceIn(1, 1439),
                            pollIntervalMin = poll.coerceAtLeast(1),
                            thresholdMin = threshold.coerceAtLeast(1),
                            enabled = enabled,
                        )
                        withContext(Dispatchers.IO) {
                            val id = app.database.watchDao().upsert(w)
                            val saved = w.copy(id = if (w.id == 0L) id else w.id)
                            if (saved.enabled) app.scheduler.scheduleNext(saved)
                            else app.scheduler.cancel(saved.id)
                        }
                        nav.popBackStack()
                    }
                }) { Text("Save") }

                OutlinedButton(onClick = {
                    scope.launch {
                        if (route.isBlank() || stop.isBlank()) {
                            snackbar.showSnackbar("Fill route and stop first"); return@launch
                        }
                        if (!app.keyStore.hasApiKey()) {
                            snackbar.showSnackbar("Set your AT API key in Settings first"); return@launch
                        }
                        snackbar.showSnackbar("Checking…", duration = SnackbarDuration.Short)
                        val res = EtaCalculator(app.atClient).candidates(route, stop)
                        val msg = when (res) {
                            is AtClient.Result.Ok -> {
                                val soonest = res.value.minByOrNull { it.etaMinutes }
                                if (soonest == null) "No upcoming arrivals right now"
                                else "Next bus: ${soonest.etaMinutes} min away (trip ${soonest.tripId.take(12)}…)"
                            }
                            is AtClient.Result.Err -> "Error: ${res.message}"
                        }
                        snackbar.showSnackbar(msg, duration = SnackbarDuration.Long)
                    }
                }) { Text("Test now") }
            }
        }
    }

    val target = pickerOpenFor
    if (target != null) {
        val initial = if (target == "start") startMin else endMin
        TimePickDialog(
            initialMinutes = initial,
            onDismiss = { pickerOpenFor = null },
            onConfirm = { newMin ->
                if (target == "start") startMin = newMin else endMin = newMin
                pickerOpenFor = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeRow(label: String, minutes: Int, onClick: () -> Unit) {
    val h = minutes / 60
    val m = minutes % 60
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("%02d:%02d".format(h, m), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val safeMinutes = initialMinutes.coerceIn(0, 23 * 60 + 59)
    val state = rememberTimePickerState(
        initialHour = safeMinutes / 60,
        initialMinute = safeMinutes % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        text = {
            TimePicker(state = state)
        }
    )
}
