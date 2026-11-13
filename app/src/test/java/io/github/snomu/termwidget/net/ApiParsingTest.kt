package io.github.snomu.termwidget.net

import io.github.snomu.termwidget.data.Jsonx
import io.github.snomu.termwidget.model.City
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class ApiParsingTest {
    @Test
    fun openMeteoBatchedArrayParses() {
        val json = """
            [{"latitude":40.69,"longitude":-73.92,"current":{"temperature_2m":37.2,"weather_code":73,"is_day":1},
              "daily":{"time":["2026-11-12","2026-11-13"],"temperature_2m_max":[60.8,47.7],"weather_code":[63,73]}},
             {"latitude":47.61,"longitude":-122.33,"current":{"temperature_2m":50.4,"weather_code":3,"is_day":1},
              "daily":{"time":["2026-11-12","2026-11-13"],"temperature_2m_max":[58.7,56.1],"weather_code":[3,61]}}]
        """.trimIndent()
        val parsed = Jsonx.json.decodeFromString<List<OmResponse>>(json)
        assertEquals(2, parsed.size)
        assertEquals(37, parsed[0].current.temperature2m.roundToInt())
        assertEquals(73, parsed[0].current.weatherCode)
        assertEquals(1, parsed[0].current.isDay)
        assertEquals(listOf("2026-11-12", "2026-11-13"), parsed[0].daily.time)
    }

    @Test
    fun coinGeckoSimplePriceParses() {
        val json = """{"helium":{"usd":0.242394,"usd_24h_change":-3.048}}"""
        val root = Jsonx.json.decodeFromString<JsonObject>(json)
        val entry = root["helium"]!!.jsonObject
        assertEquals(0.242394, entry["usd"]!!.jsonPrimitive.doubleOrNull!!, 1e-9)
        assertTrue(entry["usd_24h_change"]!!.jsonPrimitive.doubleOrNull!! < 0)
    }

    @Test
    fun finnhubQuoteParses() {
        val json = """{"c":314.33,"d":1.2,"dp":1.10,"h":320.0,"l":310.0,"o":312.0,"pc":313.0,"t":1}"""
        val parsed = Jsonx.json.decodeFromString<FhQuote>(json)
        assertEquals(314.33, parsed.c, 1e-9)
        assertEquals(1.10, parsed.dp, 1e-9)
    }

    @Test
    fun defaultCitiesMatchBrief() {
        val cities = listOf(
            City("NYC", 40.69, -73.92, "America/New_York", true),
            City("SEA", 47.61, -122.33, "America/Los_Angeles"),
            City("LON", 51.51, -0.13, "Europe/London"),
            City("BJS", 39.90, 116.41, "Asia/Shanghai"),
            City("TYO", 35.68, 139.69, "Asia/Tokyo"),
        )
        assertEquals(cities, io.github.snomu.termwidget.data.Defaults.cities)
    }

    @Serializable
    private data class OmResponse(val current: OmCurrent, val daily: OmDaily)

    @Serializable
    private data class OmCurrent(
        @SerialName("temperature_2m") val temperature2m: Double,
        @SerialName("weather_code") val weatherCode: Int,
        @SerialName("is_day") val isDay: Int,
    )

    @Serializable
    private data class OmDaily(
        val time: List<String>,
        @SerialName("temperature_2m_max") val temperature2mMax: List<Double>,
        @SerialName("weather_code") val weatherCode: List<Int>,
    )

    @Serializable
    private data class FhQuote(val c: Double, val dp: Double)
}
