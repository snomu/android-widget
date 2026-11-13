package io.github.snomu.termwidget.model

import kotlinx.serialization.Serializable

@Serializable
data class City(
    val code: String,
    val latitude: Double,
    val longitude: Double,
    val timeZone: String,
    val home: Boolean = false,
)

@Serializable
enum class TickerSource {
    FINNHUB,
    COINGECKO,
}

@Serializable
enum class TickerDisplayMode {
    PRICE,
    PERCENT,
}

@Serializable
data class Ticker(
    val label: String,
    val symbol: String,
    val source: TickerSource,
    val displayMode: TickerDisplayMode,
)

@Serializable
data class DailyForecast(
    val date: String,
    val highF: Int,
    val weatherCode: Int,
)

@Serializable
data class CityWeather(
    val code: String,
    val currentTempF: Int,
    val currentWeatherCode: Int,
    val isDay: Boolean,
    val daily: List<DailyForecast>,
)

@Serializable
data class WeatherCache(
    val fetchedAtEpochMs: Long,
    val cities: List<CityWeather>,
)

@Serializable
data class Quote(
    val symbol: String,
    val price: Double,
    val changePercent: Double,
)

@Serializable
data class MarketCache(
    val fetchedAtEpochMs: Long,
    val quotes: List<Quote>,
)

@Serializable
data class AppConfig(
    val cities: List<City>,
    val tickers: List<Ticker>,
)
