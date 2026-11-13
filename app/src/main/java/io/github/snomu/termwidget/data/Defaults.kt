package io.github.snomu.termwidget.data

import io.github.snomu.termwidget.model.AppConfig
import io.github.snomu.termwidget.model.City
import io.github.snomu.termwidget.model.Ticker
import io.github.snomu.termwidget.model.TickerDisplayMode
import io.github.snomu.termwidget.model.TickerSource

object Defaults {
    val cities: List<City> = listOf(
        City("NYC", 40.69, -73.92, "America/New_York", home = true),
        City("SEA", 47.61, -122.33, "America/Los_Angeles"),
        City("LON", 51.51, -0.13, "Europe/London"),
        City("BJS", 39.90, 116.41, "Asia/Shanghai"),
        City("TYO", 35.68, 139.69, "Asia/Tokyo"),
    )

    val tickers: List<Ticker> = listOf(
        Ticker("HNT", "helium", TickerSource.COINGECKO, TickerDisplayMode.PRICE),
        Ticker("S&P", "SPY", TickerSource.FINNHUB, TickerDisplayMode.PERCENT),
        Ticker("GOOG", "GOOG", TickerSource.FINNHUB, TickerDisplayMode.PERCENT),
    )

    val config: AppConfig = AppConfig(cities = cities, tickers = tickers)

    const val STALE_AFTER_MS: Long = 2L * 60L * 60L * 1000L
    const val REFRESH_INTERVAL_MINUTES: Long = 30L
}
