package io.github.snomu.termwidget.settings

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier as ComposeModifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.snomu.termwidget.data.KeyVault
import io.github.snomu.termwidget.data.Store
import io.github.snomu.termwidget.model.City
import io.github.snomu.termwidget.model.Ticker
import io.github.snomu.termwidget.model.TickerDisplayMode
import io.github.snomu.termwidget.model.TickerSource
import io.github.snomu.termwidget.widget.TermWidgetProvider
import io.github.snomu.termwidget.work.RefreshScheduler
import kotlinx.coroutines.launch
import java.time.ZoneId

class SettingsActivity : ComponentActivity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
        }

        val store = Store(this)
        val keyVault = KeyVault(this)

        setContent {
            TermTheme {
                SettingsScreen(
                    store = store,
                    keyVault = keyVault,
                    onRefreshNow = {
                        RefreshScheduler.refreshNow(this)
                        Toast.makeText(this, "Refreshing…", Toast.LENGTH_SHORT).show()
                    },
                    onAddWidget = { requestPinWidget() },
                    onConfigSaved = {
                        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                            TermWidgetProvider.updateOne(
                                this,
                                AppWidgetManager.getInstance(this),
                                appWidgetId,
                                null,
                            )
                        } else {
                            TermWidgetProvider.updateAll(this)
                        }
                        RefreshScheduler.refreshNow(this)
                    },
                )
            }
        }
    }

    private fun requestPinWidget() {
        val manager = AppWidgetManager.getInstance(this)
        val provider = ComponentName(this, TermWidgetProvider::class.java)
        if (manager.isRequestPinAppWidgetSupported) {
            val success = PendingIntent.getBroadcast(
                this,
                0,
                Intent(this, TermWidgetProvider::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            manager.requestPinAppWidget(provider, null, success)
        } else {
            Toast.makeText(this, "Pinning not supported on this launcher", Toast.LENGTH_SHORT).show()
        }
    }
}

private val TermDarkColors = darkColorScheme(
    primary = Color(0xFF6EE7D8),
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color(0xFFE8E8E8),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE8E8E8),
    secondary = Color(0xFF9BE564),
)

@Composable
private fun TermTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TermDarkColors, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    store: Store,
    keyVault: KeyVault,
    onRefreshNow: () -> Unit,
    onAddWidget: () -> Unit,
    onConfigSaved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val config by store.configFlow.collectAsState(
        initial = io.github.snomu.termwidget.data.Defaults.config,
    )
    var hasKey by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }
    var editingCity by remember { mutableStateOf<City?>(null) }
    var editingTicker by remember { mutableStateOf<Ticker?>(null) }
    var addingCity by remember { mutableStateOf(false) }
    var addingTicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        hasKey = keyVault.hasKey()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("term settings", fontFamily = FontFamily.Monospace)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color(0xFFE8E8E8),
                ),
            )
        },
        containerColor = Color.Black,
    ) { padding ->
        Column(
            modifier = ComposeModifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle("Finnhub API key")
            Text(
                text = if (hasKey) "Key saved (write-only; enter a new value to replace)."
                else "No key saved. Stocks need a free Finnhub key.",
                color = Color(0xFF6B7480),
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                modifier = ComposeModifier.fillMaxWidth(),
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            val trimmed = apiKeyInput.trim()
                            if (trimmed.isEmpty()) {
                                return@launch
                            }
                            keyVault.save(trimmed)
                            apiKeyInput = ""
                            hasKey = true
                            onConfigSaved()
                        }
                    },
                ) { Text("Save key") }
                if (hasKey) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                keyVault.clear()
                                hasKey = false
                            }
                        },
                    ) { Text("Clear") }
                }
            }

            SectionTitle("Cities")
            config.cities.forEachIndexed { index, city ->
                CityRow(
                    city = city,
                    onEdit = { editingCity = city },
                    onDelete = {
                        scope.launch {
                            val next = config.cities.toMutableList().also { it.removeAt(index) }
                            if (next.isNotEmpty() && next.none { it.home }) {
                                next[0] = next[0].copy(home = true)
                            }
                            if (next.isNotEmpty()) {
                                store.saveCities(next)
                                onConfigSaved()
                            }
                        }
                    },
                    onMoveUp = {
                        if (index == 0) return@CityRow
                        scope.launch {
                            val next = config.cities.toMutableList()
                            val item = next.removeAt(index)
                            next.add(index - 1, item)
                            store.saveCities(next)
                            onConfigSaved()
                        }
                    },
                    onMoveDown = {
                        if (index >= config.cities.lastIndex) return@CityRow
                        scope.launch {
                            val next = config.cities.toMutableList()
                            val item = next.removeAt(index)
                            next.add(index + 1, item)
                            store.saveCities(next)
                            onConfigSaved()
                        }
                    },
                )
            }
            TextButton(onClick = { addingCity = true }) { Text("+ Add city") }

            SectionTitle("Tickers")
            config.tickers.forEachIndexed { index, ticker ->
                TickerRow(
                    ticker = ticker,
                    onEdit = { editingTicker = ticker },
                    onDelete = {
                        scope.launch {
                            val next = config.tickers.toMutableList().also { it.removeAt(index) }
                            store.saveTickers(next)
                            onConfigSaved()
                        }
                    },
                    onMoveUp = {
                        if (index == 0) return@TickerRow
                        scope.launch {
                            val next = config.tickers.toMutableList()
                            val item = next.removeAt(index)
                            next.add(index - 1, item)
                            store.saveTickers(next)
                            onConfigSaved()
                        }
                    },
                    onMoveDown = {
                        if (index >= config.tickers.lastIndex) return@TickerRow
                        scope.launch {
                            val next = config.tickers.toMutableList()
                            val item = next.removeAt(index)
                            next.add(index + 1, item)
                            store.saveTickers(next)
                            onConfigSaved()
                        }
                    },
                )
            }
            TextButton(onClick = { addingTicker = true }) { Text("+ Add ticker") }

            Spacer(ComposeModifier.height(8.dp))
            Button(onClick = onRefreshNow, modifier = ComposeModifier.fillMaxWidth()) {
                Text("Refresh now")
            }
            OutlinedButton(onClick = onAddWidget, modifier = ComposeModifier.fillMaxWidth()) {
                Text("Add widget to home screen")
            }
        }
    }

    if (addingCity || editingCity != null) {
        CityEditorDialog(
            initial = editingCity,
            onDismiss = {
                addingCity = false
                editingCity = null
            },
            onSave = { city ->
                scope.launch {
                    val next = config.cities.toMutableList()
                    if (editingCity != null) {
                        val idx = next.indexOfFirst { it.code == editingCity!!.code && it.timeZone == editingCity!!.timeZone }
                        if (idx >= 0) next[idx] = city else next += city
                    } else {
                        next += city
                    }
                    val withCity = next.map { existing ->
                        if (existing.code == city.code && existing.timeZone == city.timeZone) city else existing
                    }.let { list ->
                        if (list.any { it.code == city.code && it.timeZone == city.timeZone }) list
                        else list + city
                    }
                    val normalized = when {
                        city.home -> withCity.map {
                            if (it.code == city.code && it.timeZone == city.timeZone) city
                            else it.copy(home = false)
                        }
                        withCity.none { it.home } -> withCity.mapIndexed { i, c ->
                            if (i == 0) c.copy(home = true) else c
                        }
                        else -> withCity
                    }
                    store.saveCities(normalized)
                    addingCity = false
                    editingCity = null
                    onConfigSaved()
                }
            },
        )
    }

    if (addingTicker || editingTicker != null) {
        TickerEditorDialog(
            initial = editingTicker,
            onDismiss = {
                addingTicker = false
                editingTicker = null
            },
            onSave = { ticker ->
                scope.launch {
                    val next = config.tickers.toMutableList()
                    if (editingTicker != null) {
                        val idx = next.indexOfFirst {
                            it.label == editingTicker!!.label && it.symbol == editingTicker!!.symbol
                        }
                        if (idx >= 0) next[idx] = ticker else next += ticker
                    } else {
                        next += ticker
                    }
                    store.saveTickers(next)
                    addingTicker = false
                    editingTicker = null
                    onConfigSaved()
                }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontFamily = FontFamily.Monospace,
        color = Color(0xFF6EE7D8),
        modifier = ComposeModifier.padding(top = 8.dp),
    )
}

@Composable
private fun CityRow(
    city: City,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Column(
        modifier = ComposeModifier
            .fillMaxWidth()
            .background(Color(0xFF121212))
            .clickable(onClick = onEdit)
            .padding(12.dp),
    ) {
        Text(
            text = "${city.code}${if (city.home) "  (home)" else ""}",
            fontFamily = FontFamily.Monospace,
            color = Color.White,
        )
        Text(
            text = "${city.latitude}, ${city.longitude}  ${city.timeZone}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6B7480),
            fontFamily = FontFamily.Monospace,
        )
        Row {
            TextButton(onClick = onMoveUp) { Text("Up") }
            TextButton(onClick = onMoveDown) { Text("Down") }
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun TickerRow(
    ticker: Ticker,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Column(
        modifier = ComposeModifier
            .fillMaxWidth()
            .background(Color(0xFF121212))
            .clickable(onClick = onEdit)
            .padding(12.dp),
    ) {
        Text(
            text = "${ticker.label}  →  ${ticker.symbol}",
            fontFamily = FontFamily.Monospace,
            color = Color.White,
        )
        Text(
            text = "${ticker.source} / ${ticker.displayMode}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6B7480),
            fontFamily = FontFamily.Monospace,
        )
        Row {
            TextButton(onClick = onMoveUp) { Text("Up") }
            TextButton(onClick = onMoveDown) { Text("Down") }
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun CityEditorDialog(
    initial: City?,
    onDismiss: () -> Unit,
    onSave: (City) -> Unit,
) {
    var code by remember { mutableStateOf(initial?.code ?: "") }
    var lat by remember { mutableStateOf(initial?.latitude?.toString() ?: "") }
    var lon by remember { mutableStateOf(initial?.longitude?.toString() ?: "") }
    var zone by remember { mutableStateOf(initial?.timeZone ?: "") }
    var home by remember { mutableStateOf(initial?.home ?: false) }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add city" else "Edit city") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = code, onValueChange = { code = it.uppercase().take(3) }, label = { Text("Code") }, singleLine = true)
                OutlinedTextField(value = lat, onValueChange = { lat = it }, label = { Text("Latitude") }, singleLine = true)
                OutlinedTextField(value = lon, onValueChange = { lon = it }, label = { Text("Longitude") }, singleLine = true)
                OutlinedTextField(value = zone, onValueChange = { zone = it }, label = { Text("IANA time zone") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = home, onCheckedChange = { home = it })
                    Text("Home city")
                }
                if (error != null) {
                    Text(error!!, color = Color(0xFFFF6B6B))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val latitude = lat.toDoubleOrNull()
                    val longitude = lon.toDoubleOrNull()
                    if (code.length != 3) {
                        error = "Code must be 3 characters"
                        return@TextButton
                    }
                    if (latitude == null || longitude == null) {
                        error = "Latitude/longitude must be numbers"
                        return@TextButton
                    }
                    try {
                        ZoneId.of(zone.trim())
                    } catch (_: Exception) {
                        error = "Invalid IANA time zone"
                        return@TextButton
                    }
                    onSave(
                        City(
                            code = code.trim().uppercase(),
                            latitude = latitude,
                            longitude = longitude,
                            timeZone = zone.trim(),
                            home = home,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun TickerEditorDialog(
    initial: Ticker?,
    onDismiss: () -> Unit,
    onSave: (Ticker) -> Unit,
) {
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var symbol by remember { mutableStateOf(initial?.symbol ?: "") }
    var source by remember { mutableStateOf(initial?.source ?: TickerSource.FINNHUB) }
    var mode by remember { mutableStateOf(initial?.displayMode ?: TickerDisplayMode.PRICE) }
    var sourceMenu by remember { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add ticker" else "Edit ticker") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label") }, singleLine = true)
                OutlinedTextField(value = symbol, onValueChange = { symbol = it }, label = { Text("Symbol / CoinGecko id") }, singleLine = true)
                BoxDropdown("Source: $source", expanded = sourceMenu, onExpand = { sourceMenu = true }, onDismiss = { sourceMenu = false }) {
                    TickerSource.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.name) },
                            onClick = {
                                source = option
                                sourceMenu = false
                            },
                        )
                    }
                }
                BoxDropdown("Mode: $mode", expanded = modeMenu, onExpand = { modeMenu = true }, onDismiss = { modeMenu = false }) {
                    TickerDisplayMode.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.name) },
                            onClick = {
                                mode = option
                                modeMenu = false
                            },
                        )
                    }
                }
                if (error != null) {
                    Text(error!!, color = Color(0xFFFF6B6B))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (label.isBlank() || symbol.isBlank()) {
                        error = "Label and symbol are required"
                        return@TextButton
                    }
                    onSave(
                        Ticker(
                            label = label.trim(),
                            symbol = symbol.trim(),
                            source = source,
                            displayMode = mode,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun BoxDropdown(
    label: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column {
        OutlinedButton(onClick = onExpand, modifier = ComposeModifier.fillMaxWidth()) {
            Text(label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            content()
        }
    }
}
