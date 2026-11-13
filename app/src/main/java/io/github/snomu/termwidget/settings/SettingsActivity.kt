package io.github.snomu.termwidget.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import io.github.snomu.termwidget.data.ConfigEdits
import io.github.snomu.termwidget.data.KeyVault
import io.github.snomu.termwidget.data.Store
import io.github.snomu.termwidget.model.AppConfig
import io.github.snomu.termwidget.model.City
import io.github.snomu.termwidget.model.Ticker
import io.github.snomu.termwidget.model.TickerDisplayMode
import io.github.snomu.termwidget.model.TickerSource
import io.github.snomu.termwidget.widget.TermWidgetProvider
import io.github.snomu.termwidget.work.RefreshScheduler
import kotlinx.coroutines.launch
import java.time.ZoneId

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // When opened as the widget's configure screen, report success up front:
        // every setting has a working default, so backing out still adds the widget.
        val appWidgetId = intent?.getIntExtra(
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
            MaterialTheme(colorScheme = TermDarkColors) {
                SettingsScreen(
                    store = store,
                    keyVault = keyVault,
                    onRefreshNow = {
                        RefreshScheduler.refreshNow(this)
                        Toast.makeText(this, "Refreshing…", Toast.LENGTH_SHORT).show()
                    },
                    onAddWidget = { requestPinWidget() },
                    onChanged = { applyChange() },
                )
            }
        }
    }

    /** Shows the change right away from the cache, then fetches whatever is new. */
    private fun applyChange() {
        val appContext = applicationContext
        lifecycleScope.launch {
            TermWidgetProvider.updateAll(appContext)
            RefreshScheduler.refreshNow(appContext)
        }
    }

    private fun requestPinWidget() {
        val manager = AppWidgetManager.getInstance(this)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(
                ComponentName(this, TermWidgetProvider::class.java),
                null,
                null,
            )
        } else {
            Toast.makeText(this, "This launcher cannot pin widgets", Toast.LENGTH_SHORT).show()
        }
    }
}

private val Teal = Color(0xFF6EE7D8)
private val TextColor = Color(0xFFE8E8E8)
private val Dim = Color(0xFF6B7480)
private val Card = Color(0xFF121212)
private val ErrorRed = Color(0xFFFF6B6B)

private val TermDarkColors = darkColorScheme(
    primary = Teal,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = TextColor,
    surface = Card,
    onSurface = TextColor,
    secondary = Color(0xFF9BE564),
)

/** Which item an editor dialog is for: a new one, or the one at [index]. */
private data class Editing(val index: Int?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    store: Store,
    keyVault: KeyVault,
    onRefreshNow: () -> Unit,
    onAddWidget: () -> Unit,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    // Null until the saved settings are read, so edits never start from defaults by mistake.
    val loaded: AppConfig? by store.configFlow.collectAsState(initial = null)
    var hasKey by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }
    var cityEditor by remember { mutableStateOf<Editing?>(null) }
    var tickerEditor by remember { mutableStateOf<Editing?>(null) }

    LaunchedEffect(Unit) {
        hasKey = keyVault.hasKey()
    }

    fun saveCities(cities: List<City>) {
        scope.launch {
            store.saveCities(cities)
            onChanged()
        }
    }

    fun saveTickers(tickers: List<Ticker>) {
        scope.launch {
            store.saveTickers(tickers)
            onChanged()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("term settings", fontFamily = FontFamily.Monospace) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = TextColor,
                ),
            )
        },
        containerColor = Color.Black,
    ) { padding ->
        val config = loaded ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle("Finnhub API key")
            Text(
                text = if (hasKey) {
                    "A key is saved. It is never shown again; enter a new one to replace it."
                } else {
                    "No key saved. Stock tickers need a free Finnhub key."
                },
                color = Dim,
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { apiKeyInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = apiKeyInput.isNotBlank(),
                    onClick = {
                        val key = apiKeyInput.trim()
                        scope.launch {
                            keyVault.save(key)
                            apiKeyInput = ""
                            hasKey = true
                            onChanged()
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
                ItemCard(
                    title = city.code + if (city.home) "  (home)" else "",
                    subtitle = "${city.latitude}, ${city.longitude}  ${city.timeZone}",
                    canMoveUp = index > 0,
                    canMoveDown = index < config.cities.lastIndex,
                    // The widget needs at least one city.
                    canDelete = config.cities.size > 1,
                    onMoveUp = { saveCities(ConfigEdits.move(config.cities, index, -1)) },
                    onMoveDown = { saveCities(ConfigEdits.move(config.cities, index, 1)) },
                    onEdit = { cityEditor = Editing(index) },
                    onDelete = { saveCities(ConfigEdits.removeCity(config.cities, index)) },
                )
            }
            TextButton(onClick = { cityEditor = Editing(null) }) { Text("+ Add city") }

            SectionTitle("Tickers")
            config.tickers.forEachIndexed { index, ticker ->
                ItemCard(
                    title = "${ticker.label}  →  ${ticker.symbol}",
                    subtitle = "${ticker.source} / ${ticker.displayMode}",
                    canMoveUp = index > 0,
                    canMoveDown = index < config.tickers.lastIndex,
                    canDelete = true,
                    onMoveUp = { saveTickers(ConfigEdits.move(config.tickers, index, -1)) },
                    onMoveDown = { saveTickers(ConfigEdits.move(config.tickers, index, 1)) },
                    onEdit = { tickerEditor = Editing(index) },
                    onDelete = { saveTickers(config.tickers.filterIndexed { i, _ -> i != index }) },
                )
            }
            TextButton(onClick = { tickerEditor = Editing(null) }) { Text("+ Add ticker") }

            Spacer(Modifier.height(8.dp))
            Button(onClick = onRefreshNow, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh now")
            }
            OutlinedButton(onClick = onAddWidget, modifier = Modifier.fillMaxWidth()) {
                Text("Add widget to home screen")
            }
        }
    }

    val config = loaded ?: return

    cityEditor?.let { editing ->
        CityEditorDialog(
            initial = editing.index?.let { config.cities.getOrNull(it) },
            isDuplicateCode = { ConfigEdits.isDuplicateCode(config.cities, it, editing.index) },
            onDismiss = { cityEditor = null },
            onSave = { city ->
                saveCities(ConfigEdits.upsertCity(config.cities, editing.index, city))
                cityEditor = null
            },
        )
    }

    tickerEditor?.let { editing ->
        TickerEditorDialog(
            initial = editing.index?.let { config.tickers.getOrNull(it) },
            onDismiss = { tickerEditor = null },
            onSave = { ticker ->
                val index = editing.index
                val next = config.tickers.toMutableList()
                if (index != null && index in next.indices) next[index] = ticker else next += ticker
                saveTickers(next)
                tickerEditor = null
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
        color = Teal,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ItemCard(
    title: String,
    subtitle: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canDelete: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Card)
            .padding(12.dp),
    ) {
        Text(text = title, fontFamily = FontFamily.Monospace, color = Color.White)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = Dim,
            fontFamily = FontFamily.Monospace,
        )
        Row {
            TextButton(onClick = onMoveUp, enabled = canMoveUp) { Text("Up") }
            TextButton(onClick = onMoveDown, enabled = canMoveDown) { Text("Down") }
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onDelete, enabled = canDelete) { Text("Delete") }
        }
    }
}

@Composable
private fun CityEditorDialog(
    initial: City?,
    isDuplicateCode: (String) -> Boolean,
    onDismiss: () -> Unit,
    onSave: (City) -> Unit,
) {
    var code by remember { mutableStateOf(initial?.code ?: "") }
    var lat by remember { mutableStateOf(initial?.latitude?.toString() ?: "") }
    var lon by remember { mutableStateOf(initial?.longitude?.toString() ?: "") }
    var zone by remember { mutableStateOf(initial?.timeZone ?: "") }
    var home by remember { mutableStateOf(initial?.home ?: false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add city" else "Edit city") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().filter(Char::isLetterOrDigit).take(3) },
                    label = { Text("Code (3 characters)") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = lat,
                    onValueChange = { lat = it },
                    label = { Text("Latitude") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = lon,
                    onValueChange = { lon = it },
                    label = { Text("Longitude") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = zone,
                    onValueChange = { zone = it },
                    label = { Text("IANA time zone, e.g. Europe/Paris") },
                    singleLine = true,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = home, onCheckedChange = { home = it })
                    Text("Home city")
                }
                error?.let { Text(it, color = ErrorRed) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val latitude = lat.trim().toDoubleOrNull()
                    val longitude = lon.trim().toDoubleOrNull()
                    val zoneId = zone.trim()
                    error = when {
                        code.length != 3 -> "Code must be 3 letters or digits"
                        isDuplicateCode(code) -> "Another city already uses $code"
                        latitude == null || latitude !in -90.0..90.0 ->
                            "Latitude must be between -90 and 90"
                        longitude == null || longitude !in -180.0..180.0 ->
                            "Longitude must be between -180 and 180"
                        !isValidZone(zoneId) -> "Unknown IANA time zone"
                        else -> null
                    }
                    if (error == null && latitude != null && longitude != null) {
                        onSave(City(code, latitude, longitude, zoneId, home))
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun isValidZone(id: String): Boolean =
    try {
        ZoneId.of(id)
        true
    } catch (_: Exception) {
        false
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
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add ticker" else "Edit ticker") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label shown on the widget") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it },
                    label = {
                        Text(
                            if (source == TickerSource.FINNHUB) "Finnhub symbol, e.g. SPY"
                            else "CoinGecko id, e.g. helium",
                        )
                    },
                    singleLine = true,
                )
                Dropdown(
                    label = "Source: ${source.name}",
                    options = TickerSource.entries,
                    optionLabel = { it.name },
                    onSelect = { source = it },
                )
                Dropdown(
                    label = "Show: ${mode.name}",
                    options = TickerDisplayMode.entries,
                    optionLabel = { it.name },
                    onSelect = { mode = it },
                )
                error?.let { Text(it, color = ErrorRed) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleanLabel = label.trim()
                    // Finnhub symbols are upper case; CoinGecko ids are lower case.
                    val cleanSymbol = symbol.trim().let {
                        if (source == TickerSource.FINNHUB) it.uppercase() else it.lowercase()
                    }
                    if (cleanLabel.isEmpty() || cleanSymbol.isEmpty()) {
                        error = "Label and symbol are required"
                    } else {
                        onSave(Ticker(cleanLabel, cleanSymbol, source, mode))
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun <T> Dropdown(
    label: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
