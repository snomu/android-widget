package io.github.snomu.termwidget.net

import io.github.snomu.termwidget.data.Jsonx
import io.github.snomu.termwidget.model.City
import io.github.snomu.termwidget.model.CityWeather
import io.github.snomu.termwidget.model.DailyForecast
import io.github.snomu.termwidget.model.WeatherCache
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import kotlin.math.roundToInt

class OpenMeteoClient(
    private val client: okhttp3.OkHttpClient = HttpClient.okHttp,
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
            .addQueryParameter("forecast_days", "7")
            .build()

        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Open-Meteo HTTP ${response.code}")
            }
            val body = response.body.string()
            val forecasts = if (cities.size == 1) {
                listOf(Jsonx.json.decodeFromString<ForecastResponse>(body))
            } else {
                Jsonx.json.decodeFromString<List<ForecastResponse>>(body)
            }
            require(forecasts.size == cities.size) {
                "Open-Meteo returned ${forecasts.size} locations for ${cities.size} cities"
            }

            val cityWeather = cities.zip(forecasts).map { (city, forecast) ->
                CityWeather(
                    code = city.code,
                    currentTempF = forecast.current.temperature2m.roundToInt(),
                    currentWeatherCode = forecast.current.weatherCode,
                    isDay = forecast.current.isDay == 1,
                    daily = forecast.daily.time.indices.map { index ->
                        DailyForecast(
                            date = forecast.daily.time[index],
                            highF = forecast.daily.temperature2mMax[index].roundToInt(),
                            weatherCode = forecast.daily.weatherCode[index],
                        )
                    },
                )
            }
            return WeatherCache(fetchedAtEpochMs = nowEpochMs, cities = cityWeather)
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
        @SerialName("temperature_2m_max") val temperature2mMax: List<Double>,
        @SerialName("weather_code") val weatherCode: List<Int>,
    )

    companion object {
        private const val BASE = "https://api.open-meteo.com/v1/forecast"
    }
}
