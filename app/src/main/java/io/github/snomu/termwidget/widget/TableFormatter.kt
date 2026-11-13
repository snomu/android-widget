package io.github.snomu.termwidget.widget

import io.github.snomu.termwidget.data.Defaults
import io.github.snomu.termwidget.model.AppConfig
import io.github.snomu.termwidget.model.City
import io.github.snomu.termwidget.model.CityWeather
import io.github.snomu.termwidget.model.MarketCache
import io.github.snomu.termwidget.model.Quote
import io.github.snomu.termwidget.model.Ticker
import io.github.snomu.termwidget.model.TickerDisplayMode
import io.github.snomu.termwidget.model.WeatherCache
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

object TableFormatter {
    private const val CURRENT_WIDTH = 3
    private const val FORECAST_WIDTH = 4
    private const val FORECAST_DAYS = 5

    fun format(
        config: AppConfig,
        weather: WeatherCache?,
        market: MarketCache?,
        nowEpochMs: Long,
    ): TableModel {
        val home = config.cities.firstOrNull { it.home } ?: config.cities.firstOrNull()
        val homeZone = zoneOf(home?.timeZone)
        val homeDate = Instant.ofEpochMilli(nowEpochMs).atZone(homeZone).toLocalDate()
        val headerDates = (0..FORECAST_DAYS).map { homeDate.plusDays(it.toLong()) }
        // Prefix matches code(3) + 2 spaces + HH:mm(5) + marker(1) + space(1) = 12.
        val headerPrefix = " ".repeat(12)
        val header = headerPrefix + headerDates.mapIndexed { index, date ->
            val label = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.US).take(2)
            label.padStart(if (index == 0) CURRENT_WIDTH else FORECAST_WIDTH)
        }.joinToString("")

        val weatherByCode = weather?.cities?.associateBy { it.code }.orEmpty()
        val weatherStale = weather == null ||
            nowEpochMs - weather.fetchedAtEpochMs > Defaults.STALE_AFTER_MS
        val quotesById = market?.quotes?.associateBy { it.source to it.symbol }.orEmpty()

        val rows = config.cities.map { city ->
            formatCityRow(
                city = city,
                weather = weatherByCode[city.code],
                homeDate = homeDate,
                headerDates = headerDates,
                nowEpochMs = nowEpochMs,
                weatherStale = weatherStale,
            )
        }

        val tickers = config.tickers.map { ticker ->
            formatTicker(ticker, quotesById[ticker.source to ticker.symbol], nowEpochMs)
        }

        val sampleRow = buildPlainRowSample(rows.firstOrNull())
        return TableModel(
            header = header,
            headerColor = Palette.DIM,
            rows = rows,
            tickers = tickers,
            rowCharCount = sampleRow.length.coerceAtLeast(header.length),
        )
    }

    private fun formatCityRow(
        city: City,
        weather: CityWeather?,
        homeDate: LocalDate,
        headerDates: List<LocalDate>,
        nowEpochMs: Long,
        weatherStale: Boolean,
    ): CityRowModel {
        val isHome = city.home
        val alpha = if (isHome) 0xFF else Palette.NON_HOME_ALPHA
        val codeColor = Palette.withAlpha(
            if (isHome) Palette.HOME_CODE else Palette.DEFAULT_TEXT,
            alpha,
        )
        val clockColor = Palette.withAlpha(Palette.CLOCK, alpha)

        val cityDate = Instant.ofEpochMilli(nowEpochMs)
            .atZone(zoneOf(city.timeZone))
            .toLocalDate()
        val marker = if (cityDate.isAfter(homeDate)) "⁺" else " "

        val spans = mutableListOf<ColoredText>()
        spans += ColoredText(marker, Palette.withAlpha(Palette.DIM, alpha))
        spans += ColoredText(" ", Palette.withAlpha(Palette.DIM, alpha))

        val currentColor = if (weather == null || weatherStale) {
            Palette.withAlpha(Palette.DIM, alpha)
        } else {
            Palette.withAlpha(
                Palette.weatherColor(
                    code = weather.currentWeatherCode,
                    isDay = weather.isDay,
                    currentColumn = true,
                ),
                alpha,
            )
        }
        val currentText = weather?.currentTempF?.let { formatTemp(it, CURRENT_WIDTH) }
            ?: " ".repeat(CURRENT_WIDTH)
        spans += ColoredText(currentText, currentColor, bold = true)

        val dailyByDate = weather?.daily?.associateBy { it.date }.orEmpty()
        for (date in headerDates.drop(1)) {
            val daily = dailyByDate[date.toString()]
            val color = when {
                daily == null || weatherStale -> Palette.withAlpha(Palette.DIM, alpha)
                else -> Palette.withAlpha(
                    Palette.weatherColor(
                        code = daily.weatherCode,
                        isDay = true,
                        currentColumn = false,
                    ),
                    alpha,
                )
            }
            val text = daily?.highF?.let { formatTemp(it, FORECAST_WIDTH) }
                ?: " ".repeat(FORECAST_WIDTH)
            spans += ColoredText(text, color)
        }

        return CityRowModel(
            code = city.code.padEnd(3).take(3),
            home = isHome,
            codeColor = codeColor,
            timeZone = city.timeZone,
            clockColor = clockColor,
            markerAndTemps = spans,
        )
    }

    private fun formatTicker(
        ticker: Ticker,
        quote: Quote?,
        nowEpochMs: Long,
    ): TickerItemModel {
        val labelColor = Palette.DEFAULT_TEXT
        if (quote == null) {
            return TickerItemModel(
                label = ticker.label,
                labelColor = labelColor,
                value = "—",
                valueColor = Palette.DIM,
            )
        }

        val value = when (ticker.displayMode) {
            TickerDisplayMode.PRICE -> formatPrice(quote.price)
            TickerDisplayMode.PERCENT -> formatPercent(quote.changePercent)
        }
        val stale = nowEpochMs - quote.fetchedAtEpochMs > Defaults.STALE_AFTER_MS
        val valueColor = when {
            stale -> Palette.DIM
            quote.changePercent > 0 -> Palette.MARKET_UP
            quote.changePercent < 0 -> Palette.MARKET_DOWN
            else -> Palette.DEFAULT_TEXT
        }
        return TickerItemModel(
            label = ticker.label,
            labelColor = labelColor,
            value = value,
            valueColor = valueColor,
        )
    }

    fun formatTemp(value: Int, width: Int): String =
        value.toString().padStart(width)

    fun formatPrice(price: Double): String = String.format(Locale.US, "%.2f", price)

    /** Always signed, so the direction is readable even when the value is dimmed. */
    fun formatPercent(change: Double): String = String.format(Locale.US, "%+.2f%%", change)

    /** A zone id that no longer resolves falls back to UTC instead of crashing the widget. */
    private fun zoneOf(id: String?): ZoneId =
        try {
            if (id == null) ZoneId.of("UTC") else ZoneId.of(id)
        } catch (_: Exception) {
            ZoneId.of("UTC")
        }

    private fun buildPlainRowSample(row: CityRowModel?): String {
        if (row == null) {
            return "NYC  00:00  " + " ".repeat(CURRENT_WIDTH + FORECAST_WIDTH * FORECAST_DAYS)
        }
        val temps = row.markerAndTemps.joinToString("") { it.text }
        // code(3) + 2 spaces + HH:mm(5) + markerAndTemps
        return row.code + "  " + "00:00" + temps
    }
}
