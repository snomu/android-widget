package io.github.snomu.termwidget.net

import io.github.snomu.termwidget.data.Jsonx
import io.github.snomu.termwidget.model.City
import io.github.snomu.termwidget.model.CityWeather
import io.github.snomu.termwidget.model.DailyForecast
import io.github.snomu.termwidget.model.WeatherCache
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.roundToInt

class OpenMeteoClient(
    private val client: OkHttpClient = HttpClient.okHttp,
) {
    fun fetch(cities: List<City>, nowEpochMs: Long = System.currentTimeMillis()): WeatherCache {
        require(cities.isNotEmpty()) { "cities must not be empty" }

        val url = BASE.toHttpUrl().newBuilder()
            .addQueryParameter("latitude", cities.joinToString(",") { it.latitude.toString() })
            .addQueryParameter("longitude", cities.joinToString(",") { it.longitude.toString() })
            .addQueryParameter("current", "temperature_2m,weather_code,is_day")
            .addQueryParameter("daily", "temperature_2m_max,weather_code")
            .addQueryParameter("temperature_unit", "fahrenheit")
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("past_days", "1")
            // One more than the six columns shown: a city whose local date is
            // behind the home city's would otherwise lose the last column.
            .addQueryParameter("forecast_days", "7")
            .build()

        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Open-Meteo HTTP ${response.code}")
            }
            return parse(response.body.string(), cities, nowEpochMs)
        }
    }

    @Serializable
    private data class ForecastResponse(
        val current: Current,
        val daily: Daily,
    )

    @Serializable
    private data class Current(
        @SerialName("temperature_2m") val temperature2m: Double,
        @SerialName("weather_code") val weatherCode: Int,
        @SerialName("is_day") val isDay: Int,
    )

    @Serializable
    private data class Daily(
        val time: List<String>,
        @SerialName("temperature_2m_max") val temperature2mMax: List<Double?>,
        @SerialName("weather_code") val weatherCode: List<Int?>,
    )

    companion object {
        private const val BASE = "https://api.open-meteo.com/v1/forecast"

        /**
         * Parses a forecast response. The API returns a JSON array when several
         * locations are requested and a single object when only one is.
         */
        fun parse(body: String, cities: List<City>, nowEpochMs: Long): WeatherCache {
            val root = Jsonx.json.decodeFromString<JsonElement>(body)
            val forecasts = if (root is JsonArray) {
                root.map { Jsonx.json.decodeFromJsonElement<ForecastResponse>(it) }
            } else {
                listOf(Jsonx.json.decodeFromJsonElement<ForecastResponse>(root))
            }
            require(forecasts.size == cities.size) {
                "Open-Meteo returned ${forecasts.size} locations for ${cities.size} cities"
            }

            val cityWeather = cities.zip(forecasts).map { (city, forecast) ->
                val daily = forecast.daily
                CityWeather(
                    code = city.code,
                    currentTempF = forecast.current.temperature2m.roundToInt(),
                    currentWeatherCode = forecast.current.weatherCode,
                    isDay = forecast.current.isDay == 1,
                    // A day with a missing value is skipped and renders as a blank cell.
                    daily = daily.time.indices.mapNotNull { index ->
                        val high = daily.temperature2mMax.getOrNull(index) ?: return@mapNotNull null
                        val code = daily.weatherCode.getOrNull(index) ?: return@mapNotNull null
                        DailyForecast(
                            date = daily.time[index],
                            highF = high.roundToInt(),
                            weatherCode = code,
                        )
                    },
                )
            }
            return WeatherCache(fetchedAtEpochMs = nowEpochMs, cities = cityWeather)
        }
    }
}
