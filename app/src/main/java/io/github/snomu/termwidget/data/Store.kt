package io.github.snomu.termwidget.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.snomu.termwidget.model.AppConfig
import io.github.snomu.termwidget.model.City
import io.github.snomu.termwidget.model.MarketCache
import io.github.snomu.termwidget.model.Ticker
import io.github.snomu.termwidget.model.WeatherCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "term_store")

class Store(private val context: Context) {
    private val citiesKey = stringPreferencesKey("cities_json")
    private val tickersKey = stringPreferencesKey("tickers_json")
    private val weatherKey = stringPreferencesKey("weather_json")
    private val marketKey = stringPreferencesKey("market_json")

    val configFlow: Flow<AppConfig> = context.appDataStore.data.map { prefs ->
        AppConfig(
            cities = prefs[citiesKey]?.let { decodeList(it) } ?: Defaults.cities,
            tickers = prefs[tickersKey]?.let { decodeList(it) } ?: Defaults.tickers,
        )
    }

    val weatherFlow: Flow<WeatherCache?> = context.appDataStore.data.map { prefs ->
        prefs[weatherKey]?.let { decode(it) }
    }

    val marketFlow: Flow<MarketCache?> = context.appDataStore.data.map { prefs ->
        prefs[marketKey]?.let { decode(it) }
    }

    suspend fun getConfig(): AppConfig = configFlow.first()

    suspend fun getWeather(): WeatherCache? = weatherFlow.first()

    suspend fun getMarket(): MarketCache? = marketFlow.first()

    suspend fun saveCities(cities: List<City>) {
        context.appDataStore.edit { prefs ->
            prefs[citiesKey] = encode(cities)
        }
    }

    suspend fun saveTickers(tickers: List<Ticker>) {
        context.appDataStore.edit { prefs ->
            prefs[tickersKey] = encode(tickers)
        }
    }

    suspend fun saveWeather(cache: WeatherCache) {
        context.appDataStore.edit { prefs ->
            prefs[weatherKey] = encode(cache)
        }
    }

    suspend fun saveMarket(cache: MarketCache) {
        context.appDataStore.edit { prefs ->
            prefs[marketKey] = encode(cache)
        }
    }

    private inline fun <reified T> encode(value: T): String =
        Jsonx.json.encodeToString(value)

    private inline fun <reified T> decode(value: String): T =
        Jsonx.json.decodeFromString(value)

    private inline fun <reified T> decodeList(value: String): List<T> =
        Jsonx.json.decodeFromString(value)
}
