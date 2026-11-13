package io.github.snomu.termwidget.net

import io.github.snomu.termwidget.data.Defaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Feeds captured response shapes through the same parsers the clients use. */
class ApiParsingTest {
    private val twoCities = Defaults.cities.take(2)

    @Test
    fun openMeteoBatchedArrayParses() {
        val body = """
            [{"latitude":40.69,"longitude":-73.92,"timezone":"America/New_York",
              "current":{"time":"2026-11-12T11:15","interval":900,"temperature_2m":37.2,"weather_code":73,"is_day":1},
              "daily":{"time":["2026-11-11","2026-11-12"],"temperature_2m_max":[60.8,47.5],"weather_code":[63,73]}},
             {"latitude":47.61,"longitude":-122.33,"timezone":"America/Los_Angeles",
              "current":{"time":"2026-11-12T08:15","interval":900,"temperature_2m":50.6,"weather_code":0,"is_day":0},
              "daily":{"time":["2026-11-11","2026-11-12"],"temperature_2m_max":[58.7,56.1],"weather_code":[3,61]}}]
        """.trimIndent()

        val cache = OpenMeteoClient.parse(body, twoCities, nowEpochMs = 1234L)

        assertEquals(1234L, cache.fetchedAtEpochMs)
        assertEquals(listOf("NYC", "SEA"), cache.cities.map { it.code })
        val nyc = cache.cities[0]
        assertEquals(37, nyc.currentTempF)
        assertEquals(73, nyc.currentWeatherCode)
        assertTrue(nyc.isDay)
        assertEquals(listOf("2026-11-11", "2026-11-12"), nyc.daily.map { it.date })
        assertEquals(listOf(61, 48), nyc.daily.map { it.highF })
        assertEquals(listOf(63, 73), nyc.daily.map { it.weatherCode })
        val sea = cache.cities[1]
        assertEquals(51, sea.currentTempF)
        assertTrue(!sea.isDay)
    }

    @Test
    fun openMeteoSingleLocationIsAnObjectNotAnArray() {
        val body = """
            {"latitude":40.69,"longitude":-73.92,
             "current":{"temperature_2m":61.0,"weather_code":1,"is_day":1},
             "daily":{"time":["2026-11-12"],"temperature_2m_max":[60.2],"weather_code":[2]}}
        """.trimIndent()

        val cache = OpenMeteoClient.parse(body, twoCities.take(1), nowEpochMs = 0L)

        assertEquals(1, cache.cities.size)
        assertEquals(61, cache.cities[0].currentTempF)
        assertEquals(60, cache.cities[0].daily.single().highF)
    }

    @Test
    fun openMeteoSkipsDaysWithMissingValues() {
        val body = """
            {"current":{"temperature_2m":61.0,"weather_code":1,"is_day":1},
             "daily":{"time":["2026-11-12","2026-11-13"],"temperature_2m_max":[60.2,null],"weather_code":[2,null]}}
        """.trimIndent()

        val cache = OpenMeteoClient.parse(body, twoCities.take(1), nowEpochMs = 0L)

        assertEquals(listOf("2026-11-12"), cache.cities[0].daily.map { it.date })
    }

    @Test
    fun openMeteoRejectsAResponseForTheWrongNumberOfCities() {
        val body = """
            {"current":{"temperature_2m":61.0,"weather_code":1,"is_day":1},
             "daily":{"time":[],"temperature_2m_max":[],"weather_code":[]}}
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            OpenMeteoClient.parse(body, twoCities, nowEpochMs = 0L)
        }
    }

    @Test
    fun finnhubQuoteParses() {
        val body = """{"c":314.33,"d":3.42,"dp":1.1,"h":320.0,"l":310.0,"o":312.0,"pc":310.91,"t":1794000000}"""

        assertEquals(PriceChange(314.33, 1.1), FinnhubClient.parseQuote(body))
    }

    @Test
    fun finnhubUnknownSymbolIsNotAQuote() {
        val body = """{"c":0,"d":null,"dp":null,"h":0,"l":0,"o":0,"pc":0,"t":0}"""

        assertNull(FinnhubClient.parseQuote(body))
    }

    @Test
    fun coinGeckoSimplePriceParses() {
        val body = """{"helium":{"usd":0.242394,"usd_24h_change":-3.048}}"""

        val prices = CoinGeckoClient.parsePrices(body, listOf("helium", "not-a-coin"))

        assertEquals(mapOf("helium" to PriceChange(0.242394, -3.048)), prices)
    }
}
