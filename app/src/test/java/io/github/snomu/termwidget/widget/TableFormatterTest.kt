package io.github.snomu.termwidget.widget

import io.github.snomu.termwidget.data.Defaults
import io.github.snomu.termwidget.model.AppConfig
import io.github.snomu.termwidget.model.CityWeather
import io.github.snomu.termwidget.model.DailyForecast
import io.github.snomu.termwidget.model.MarketCache
import io.github.snomu.termwidget.model.Quote
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

    @Test
    fun sampleLayoutColumnsAlign() {
        val now = ZonedDateTime.of(
            LocalDate.of(2026, 11, 11),
            LocalTime.of(11, 17),
            ZoneId.of("America/New_York"),
        ).toInstant().toEpochMilli()

        val weather = WeatherCache(
            fetchedAtEpochMs = now,
            cities = listOf(
                cityWeather("NYC", 61, 1, true, listOf(60, 49, 47, 51, 53), listOf(0, 0, 0, 0, 0)),
                cityWeather("SEA", 48, 3, true, listOf(50, 47, 46, 49, 51), listOf(3, 3, 3, 3, 3)),
                cityWeather("LON", 52, 2, true, listOf(54, 50, 48, 51, 49), listOf(2, 2, 2, 2, 2)),
                cityWeather("BJS", 39, 95, true, listOf(55, 52, 48, 50, 53), listOf(0, 0, 0, 0, 0)),
                cityWeather("TYO", 55, 0, false, listOf(63, 61, 58, 60, 62), listOf(0, 0, 0, 0, 0)),
            ),
        )
        val market = MarketCache(
            fetchedAtEpochMs = now,
            quotes = listOf(
                Quote("helium", 0.26, -1.0),
                Quote("SPY", 500.0, 1.10),
                Quote("GOOG", 180.0, 1.34),
            ),
        )

        val model = TableFormatter.format(config, weather, market, now)
        assertEquals("             We  Th  Fr  Sa  Su  Mo", model.header)
        assertEquals(35, model.header.length)
        assertEquals(5, model.rows.size)

        val nycTemps = model.rows[0].markerAndTemps.joinToString("") { it.text }
        assertEquals("   61  60  49  47  51  53", nycTemps)

        val bjsTemps = model.rows[3].markerAndTemps.joinToString("") { it.text }
        assertTrue(bjsTemps.startsWith("⁺"))
        assertTrue(bjsTemps.contains("39"))

        val plainNyc = model.rows[0].code + "  " + "11:17" + nycTemps
        assertEquals(35, plainNyc.length)

        assertEquals("0.26", model.tickers[0].value)
        assertEquals("+1.10%", model.tickers[1].value)
        assertEquals("+1.34%", model.tickers[2].value)
        assertEquals(Palette.MARKET_DOWN, model.tickers[0].valueColor)
        assertEquals(Palette.MARKET_UP, model.tickers[1].valueColor)
    }

    @Test
    fun nextDayMarkerUsesHomeDate() {
        val now = ZonedDateTime.of(
            LocalDate.of(2026, 11, 11),
            LocalTime.of(22, 0),
            ZoneId.of("America/New_York"),
        ).toInstant().toEpochMilli()

        val model = TableFormatter.format(
            config = config,
            weather = null,
            market = null,
            nowEpochMs = now,
        )
        val markers = model.rows.associate { it.code to it.markerAndTemps.first().text }
        assertEquals(" ", markers["NYC"])
        assertEquals(" ", markers["SEA"])
        assertEquals("⁺", markers["BJS"])
        assertEquals("⁺", markers["TYO"])
    }

    @Test
    fun seaBehindNycStillHasLastForecastColumn() {
        // 01:00 NYC on Nov 12: SEA is still Nov 11 evening.
        val now = ZonedDateTime.of(
            LocalDate.of(2026, 11, 12),
            LocalTime.of(1, 0),
            ZoneId.of("America/New_York"),
        ).toInstant().toEpochMilli()

        val homeDate = LocalDate.of(2026, 11, 12)
        val dates = (-1..6).map { homeDate.plusDays(it.toLong()).toString() }
        val weather = WeatherCache(
            fetchedAtEpochMs = now,
            cities = listOf(
                CityWeather(
                    code = "SEA",
                    currentTempF = 48,
                    currentWeatherCode = 3,
                    isDay = false,
                    daily = dates.mapIndexed { index, date ->
                        DailyForecast(date, 40 + index, 3)
                    },
                ),
            ),
        )
        val model = TableFormatter.format(
            config = AppConfig(cities = listOf(config.cities[1].copy(home = true)), tickers = emptyList()),
            weather = weather,
            market = null,
            nowEpochMs = now,
        )
        val temps = model.rows[0].markerAndTemps.drop(2).map { it.text.trim() }
        assertEquals(6, temps.size)
        assertTrue(temps.all { it.isNotEmpty() })
    }

    @Test
    fun wmoColors() {
        assertEquals(Palette.SUN, Palette.weatherColor(0, isDay = true, currentColumn = true))
        assertEquals(Palette.CLEAR_NIGHT, Palette.weatherColor(0, isDay = false, currentColumn = true))
        assertEquals(Palette.SUN, Palette.weatherColor(0, isDay = false, currentColumn = false))
        assertEquals(Palette.PARTLY_CLOUDY, Palette.weatherColor(2, isDay = true, currentColumn = false))
        assertEquals(Palette.CLOUDY, Palette.weatherColor(3, isDay = true, currentColumn = false))
        assertEquals(Palette.RAIN, Palette.weatherColor(61, isDay = true, currentColumn = false))
        assertEquals(Palette.SNOW, Palette.weatherColor(71, isDay = true, currentColumn = false))
        assertEquals(Palette.THUNDER, Palette.weatherColor(95, isDay = true, currentColumn = false))
    }

    @Test
    fun staleDataUsesDim() {
        val now = System.currentTimeMillis()
        val weather = WeatherCache(
            fetchedAtEpochMs = now - Defaults.STALE_AFTER_MS - 1,
            cities = listOf(cityWeather("NYC", 61, 0, true, listOf(60, 49, 47, 51, 53), listOf(0, 0, 0, 0, 0))),
        )
        val market = MarketCache(
            fetchedAtEpochMs = now - Defaults.STALE_AFTER_MS - 1,
            quotes = listOf(Quote("helium", 0.26, 1.0)),
        )
        val model = TableFormatter.format(config, weather, market, now)
        val tempColors = model.rows[0].markerAndTemps.drop(2).map { it.color and 0x00FFFFFF }
        assertTrue(tempColors.all { it == (Palette.DIM and 0x00FFFFFF) })
        assertEquals(Palette.DIM, model.tickers[0].valueColor)
    }

    @Test
    fun nonHomeRowsUseHalfAlpha() {
        val now = System.currentTimeMillis()
        val model = TableFormatter.format(config, null, null, now)
        assertEquals(0xFF, model.rows[0].codeColor ushr 24)
        assertEquals(0x80, model.rows[1].codeColor ushr 24)
        assertEquals(0x80, model.rows[1].clockColor ushr 24)
    }

    @Test
    fun priceAndPercentFormatting() {
        assertEquals("314.33", TableFormatter.formatPrice(314.33))
        assertEquals("0.26", TableFormatter.formatPrice(0.26))
        assertEquals("+1.10%", TableFormatter.formatPercent(1.10))
        assertEquals("-2.50%", TableFormatter.formatPercent(-2.50))
        assertFalse(TableFormatter.formatPercent(1.0).contains("▲"))
    }

    private fun cityWeather(
        code: String,
        current: Int,
        codeWmo: Int,
        isDay: Boolean,
        highs: List<Int>,
        codes: List<Int>,
    ): CityWeather {
        val start = LocalDate.of(2026, 11, 11)
        return CityWeather(
            code = code,
            currentTempF = current,
            currentWeatherCode = codeWmo,
            isDay = isDay,
            daily = listOf(
                DailyForecast(start.minusDays(1).toString(), current, codeWmo),
                DailyForecast(start.toString(), current, codeWmo),
            ) + highs.mapIndexed { index, high ->
                // Forecast columns are the five days after the home "today".
                DailyForecast(start.plusDays((index + 1).toLong()).toString(), high, codes[index])
            },
        )
    }
}
