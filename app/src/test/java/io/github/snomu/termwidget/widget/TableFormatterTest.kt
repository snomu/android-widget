package io.github.snomu.termwidget.widget

import io.github.snomu.termwidget.data.Defaults
import io.github.snomu.termwidget.model.AppConfig
import io.github.snomu.termwidget.model.City
import io.github.snomu.termwidget.model.CityWeather
import io.github.snomu.termwidget.model.DailyForecast
import io.github.snomu.termwidget.model.MarketCache
import io.github.snomu.termwidget.model.Quote
import io.github.snomu.termwidget.model.Ticker
import io.github.snomu.termwidget.model.TickerDisplayMode
import io.github.snomu.termwidget.model.TickerSource
import io.github.snomu.termwidget.model.WeatherCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class TableFormatterTest {
    private val config = Defaults.config

    private fun nycTime(date: LocalDate, hour: Int, minute: Int): Long =
        ZonedDateTime.of(date, LocalTime.of(hour, minute), ZoneId.of("America/New_York"))
            .toInstant()
            .toEpochMilli()

    private fun CityRowModel.text(): String = markerAndTemps.joinToString("") { it.text }

    private fun quote(source: TickerSource, symbol: String, price: Double, change: Double, at: Long) =
        Quote(source, symbol, price, change, fetchedAtEpochMs = at)

    @Test
    fun defaultCitiesAndTickers() {
        assertEquals(
            listOf(
                City("NYC", 40.69, -73.92, "America/New_York", home = true),
                City("SEA", 47.61, -122.33, "America/Los_Angeles"),
                City("LON", 51.51, -0.13, "Europe/London"),
                City("BJS", 39.90, 116.41, "Asia/Shanghai"),
                City("TYO", 35.68, 139.69, "Asia/Tokyo"),
            ),
            Defaults.cities,
        )
        assertEquals(
            listOf(
                Ticker("HNT", "helium", TickerSource.COINGECKO, TickerDisplayMode.PRICE),
                Ticker("S&P", "SPY", TickerSource.FINNHUB, TickerDisplayMode.PERCENT),
                Ticker("GOOG", "GOOG", TickerSource.FINNHUB, TickerDisplayMode.PERCENT),
            ),
            Defaults.tickers,
        )
    }

    @Test
    fun sampleLayoutColumnsAlign() {
        val now = nycTime(LocalDate.of(2026, 11, 11), 11, 17)
        val weather = WeatherCache(
            fetchedAtEpochMs = now,
            cities = listOf(
                cityWeather("NYC", 61, 1, true, listOf(60, 49, 47, 51, 53)),
                cityWeather("SEA", 48, 3, true, listOf(50, 47, 46, 49, 51)),
                cityWeather("LON", 52, 2, true, listOf(54, 50, 48, 51, 49)),
                cityWeather("BJS", 39, 95, true, listOf(55, 52, 48, 50, 53)),
                cityWeather("TYO", 105, 0, false, listOf(-3, 61, 58, 100, 62)),
            ),
        )
        val market = MarketCache(
            listOf(
                quote(TickerSource.COINGECKO, "helium", 0.26, 1.0, now),
                quote(TickerSource.FINNHUB, "SPY", 500.0, 1.10, now),
                quote(TickerSource.FINNHUB, "GOOG", 180.0, -1.34, now),
            ),
        )

        val model = TableFormatter.format(config, weather, market, now)

        assertEquals("             We  Th  Fr  Sa  Su  Mo", model.header)
        assertEquals(35, model.rowCharCount)
        // Every row is exactly as wide as the header, whatever the values are.
        val lines = model.rows.map { it.code + "  " + "00:00" + it.text() }
        assertEquals(
            listOf(
                "NYC  00:00   61  60  49  47  51  53",
                "SEA  00:00   48  50  47  46  49  51",
                "LON  00:00   52  54  50  48  51  49",
                "BJS  00:00⁺  39  55  52  48  50  53",
                "TYO  00:00⁺ 105  -3  61  58 100  62",
            ),
            lines,
        )
        assertTrue(lines.all { it.length == model.header.length })

        assertEquals(listOf("HNT", "S&P", "GOOG"), model.tickers.map { it.label })
        assertEquals(listOf("0.26", "+1.10%", "-1.34%"), model.tickers.map { it.value })
        // Prices are colored by the day's change too, not only the percentages.
        assertEquals(
            listOf(Palette.MARKET_UP, Palette.MARKET_UP, Palette.MARKET_DOWN),
            model.tickers.map { it.valueColor },
        )
    }

    @Test
    fun onlyTheCurrentTempIsBoldAndOnlyTheHomeRowIsMarkedHome() {
        val now = nycTime(LocalDate.of(2026, 11, 11), 11, 17)
        val weather = WeatherCache(now, listOf(cityWeather("NYC", 61, 1, true, listOf(60, 49, 47, 51, 53))))

        val model = TableFormatter.format(config, weather, null, now)

        assertEquals(listOf(true, false, false, false, false), model.rows.map { it.home })
        val bold = model.rows[0].markerAndTemps.filter { it.bold }.map { it.text }
        assertEquals(listOf(" 61"), bold)
    }

    @Test
    fun nextDayMarkerComparesEachCityWithTheHomeDate() {
        val now = nycTime(LocalDate.of(2026, 11, 11), 22, 0)

        val model = TableFormatter.format(config, weather = null, market = null, nowEpochMs = now)

        val markers = model.rows.associate { it.code to it.markerAndTemps.first().text }
        assertEquals(
            mapOf("NYC" to " ", "SEA" to " ", "LON" to "⁺", "BJS" to "⁺", "TYO" to "⁺"),
            markers,
        )
    }

    @Test
    fun aCityBehindTheHomeDateStillFillsEveryForecastColumn() {
        // 01:00 on Nov 12 in NYC is still Nov 11 in Seattle. Open-Meteo returns
        // Seattle's days in Seattle time: yesterday (Nov 10) through Nov 17.
        val now = nycTime(LocalDate.of(2026, 11, 12), 1, 0)
        val seattleDays = (10..17).map { day ->
            DailyForecast(LocalDate.of(2026, 11, day).toString(), highF = day, weatherCode = 3)
        }
        val weather = WeatherCache(
            fetchedAtEpochMs = now,
            cities = listOf(CityWeather("SEA", 48, 3, isDay = false, daily = seattleDays)),
        )

        val model = TableFormatter.format(config, weather, market = null, nowEpochMs = now)

        assertEquals("             Th  Fr  Sa  Su  Mo  Tu", model.header)
        val sea = model.rows.first { it.code == "SEA" }
        // Columns follow the home city's dates: Nov 13 to Nov 17.
        assertEquals("   48  13  14  15  16  17", sea.text())
    }

    @Test
    fun aMissingForecastDayLeavesABlankCellOfTheSameWidth() {
        val now = nycTime(LocalDate.of(2026, 11, 11), 11, 17)
        val weather = WeatherCache(
            fetchedAtEpochMs = now,
            cities = listOf(
                CityWeather(
                    code = "NYC",
                    currentTempF = 61,
                    currentWeatherCode = 0,
                    isDay = true,
                    daily = listOf(DailyForecast("2026-11-13", 49, 0)),
                ),
            ),
        )

        val model = TableFormatter.format(config, weather, null, now)

        assertEquals("   61      49            ", model.rows[0].text())
        // A city with no weather at all still keeps its row width.
        assertEquals(25, model.rows[1].text().length)
    }

    @Test
    fun wmoColors() {
        assertEquals(Palette.SUN, Palette.weatherColor(0, isDay = true, currentColumn = true))
        assertEquals(Palette.SUN, Palette.weatherColor(1, isDay = true, currentColumn = true))
        assertEquals(Palette.CLEAR_NIGHT, Palette.weatherColor(0, isDay = false, currentColumn = true))
        assertEquals(Palette.CLEAR_NIGHT, Palette.weatherColor(1, isDay = false, currentColumn = true))
        // Daily cells always use daytime colors.
        assertEquals(Palette.SUN, Palette.weatherColor(0, isDay = false, currentColumn = false))
        assertEquals(Palette.PARTLY_CLOUDY, Palette.weatherColor(2, isDay = false, currentColumn = true))
        for (code in listOf(3, 45, 48)) {
            assertEquals(Palette.CLOUDY, Palette.weatherColor(code, isDay = true, currentColumn = false))
        }
        for (code in listOf(51, 55, 61, 65, 67, 80, 82)) {
            assertEquals(Palette.RAIN, Palette.weatherColor(code, isDay = true, currentColumn = false))
        }
        for (code in listOf(71, 75, 77, 85, 86)) {
            assertEquals(Palette.SNOW, Palette.weatherColor(code, isDay = true, currentColumn = false))
        }
        for (code in listOf(95, 96, 99)) {
            assertEquals(Palette.THUNDER, Palette.weatherColor(code, isDay = true, currentColumn = false))
        }
    }

    @Test
    fun currentColumnUsesNightColorAndDailyCellsDoNot() {
        val now = nycTime(LocalDate.of(2026, 11, 11), 23, 0)
        val weather = WeatherCache(now, listOf(cityWeather("NYC", 61, 0, false, listOf(60, 49, 47, 51, 53))))

        val model = TableFormatter.format(config, weather, null, now)

        val colors = model.rows[0].markerAndTemps.drop(2).map { it.color }
        assertEquals(Palette.CLEAR_NIGHT, colors.first())
        assertTrue(colors.drop(1).all { it == Palette.SUN })
    }

    @Test
    fun weatherOlderThanTwoHoursIsDimmed() {
        val now = nycTime(LocalDate.of(2026, 11, 11), 11, 17)
        val cities = listOf(cityWeather("NYC", 61, 0, true, listOf(60, 49, 47, 51, 53)))

        val fresh = TableFormatter.format(config, WeatherCache(now - Defaults.STALE_AFTER_MS, cities), null, now)
        val stale = TableFormatter.format(config, WeatherCache(now - Defaults.STALE_AFTER_MS - 1, cities), null, now)

        assertTrue(fresh.rows[0].markerAndTemps.drop(2).all { it.color == Palette.SUN })
        assertTrue(stale.rows[0].markerAndTemps.drop(2).all { it.color == Palette.DIM })
        // The values themselves stay on screen.
        assertEquals(fresh.rows[0].text(), stale.rows[0].text())
    }

    @Test
    fun eachQuoteDimsOnItsOwnAge() {
        val now = nycTime(LocalDate.of(2026, 11, 11), 11, 17)
        val market = MarketCache(
            listOf(
                quote(TickerSource.COINGECKO, "helium", 0.26, -1.0, now),
                quote(TickerSource.FINNHUB, "SPY", 500.0, 1.0, now - Defaults.STALE_AFTER_MS - 1),
            ),
        )

        val model = TableFormatter.format(config, null, market, now)

        assertEquals("0.26", model.tickers[0].value)
        assertEquals(Palette.MARKET_DOWN, model.tickers[0].valueColor)
        // Too old: the value stays, in the dim color.
        assertEquals("+1.00%", model.tickers[1].value)
        assertEquals(Palette.DIM, model.tickers[1].valueColor)
        // No data yet: a dim placeholder, never a status message.
        assertEquals("—", model.tickers[2].value)
        assertEquals(Palette.DIM, model.tickers[2].valueColor)
    }

    @Test
    fun anUnchangedQuoteIsNeitherGreenNorRed() {
        val now = nycTime(LocalDate.of(2026, 11, 11), 11, 17)
        val market = MarketCache(listOf(quote(TickerSource.FINNHUB, "SPY", 500.0, 0.0, now)))

        val model = TableFormatter.format(config, null, market, now)

        assertEquals("+0.00%", model.tickers[1].value)
        assertEquals(Palette.DEFAULT_TEXT, model.tickers[1].valueColor)
    }

    @Test
    fun quotesAreMatchedBySourceAsWellAsSymbol() {
        val now = nycTime(LocalDate.of(2026, 11, 11), 11, 17)
        val tickers = listOf(
            Ticker("A", "abc", TickerSource.FINNHUB, TickerDisplayMode.PRICE),
            Ticker("B", "abc", TickerSource.COINGECKO, TickerDisplayMode.PRICE),
        )
        val market = MarketCache(listOf(quote(TickerSource.COINGECKO, "abc", 2.0, 1.0, now)))

        val model = TableFormatter.format(AppConfig(config.cities, tickers), null, market, now)

        assertEquals(listOf("—", "2.00"), model.tickers.map { it.value })
    }

    @Test
    fun nonHomeRowsUseHalfAlphaInTheColorItself() {
        val now = nycTime(LocalDate.of(2026, 11, 11), 11, 17)
        val weather = WeatherCache(
            now,
            listOf(
                cityWeather("NYC", 61, 0, true, listOf(60, 49, 47, 51, 53)),
                cityWeather("SEA", 48, 3, true, listOf(50, 47, 46, 49, 51)),
            ),
        )

        val model = TableFormatter.format(config, weather, null, now)

        val home = model.rows[0]
        assertEquals(Palette.HOME_CODE, home.codeColor)
        assertEquals(Palette.CLOCK, home.clockColor)
        assertTrue(home.markerAndTemps.all { it.color ushr 24 == 0xFF })

        val other = model.rows[1]
        assertEquals(Palette.withAlpha(Palette.DEFAULT_TEXT, 0x80), other.codeColor)
        assertEquals(Palette.withAlpha(Palette.CLOCK, 0x80), other.clockColor)
        assertTrue(other.markerAndTemps.all { it.color ushr 24 == 0x80 })
        assertEquals(Palette.withAlpha(Palette.CLOUDY, 0x80), other.markerAndTemps[2].color)
    }

    @Test
    fun priceAndPercentFormatting() {
        assertEquals("314.33", TableFormatter.formatPrice(314.33))
        assertEquals("0.26", TableFormatter.formatPrice(0.256))
        assertEquals("+1.10%", TableFormatter.formatPercent(1.10))
        assertEquals("-2.50%", TableFormatter.formatPercent(-2.50))
        assertFalse(TableFormatter.formatPercent(1.0).any { it == '▲' || it == '▼' })
    }

    @Test
    fun anEmptyOrBrokenConfigDoesNotCrash() {
        val now = nycTime(LocalDate.of(2026, 11, 11), 11, 17)

        val empty = TableFormatter.format(AppConfig(emptyList(), emptyList()), null, null, now)
        assertTrue(empty.rows.isEmpty())
        assertEquals(35, empty.rowCharCount)

        val badZone = AppConfig(listOf(City("XXX", 0.0, 0.0, "Not/AZone", home = true)), emptyList())
        assertEquals(1, TableFormatter.format(badZone, null, null, now).rows.size)
    }

    /** Weather whose forecast columns are the five days after Nov 11, 2026. */
    private fun cityWeather(
        code: String,
        current: Int,
        wmoCode: Int,
        isDay: Boolean,
        highs: List<Int>,
    ): CityWeather {
        val today = LocalDate.of(2026, 11, 11)
        return CityWeather(
            code = code,
            currentTempF = current,
            currentWeatherCode = wmoCode,
            isDay = isDay,
            daily = listOf(
                DailyForecast(today.minusDays(1).toString(), current, wmoCode),
                DailyForecast(today.toString(), current, wmoCode),
            ) + highs.mapIndexed { index, high ->
                DailyForecast(today.plusDays(index + 1L).toString(), high, wmoCode)
            },
        )
    }
}
