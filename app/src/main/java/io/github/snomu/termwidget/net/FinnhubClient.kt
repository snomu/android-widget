package io.github.snomu.termwidget.net

import io.github.snomu.termwidget.data.Jsonx
import io.github.snomu.termwidget.model.Quote
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class FinnhubClient(
    private val client: okhttp3.OkHttpClient = HttpClient.okHttp,
) {
    fun quote(symbol: String, apiKey: String): Quote {
        val url = BASE.toHttpUrl().newBuilder()
            .addQueryParameter("symbol", symbol)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("X-Finnhub-Token", apiKey)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Finnhub HTTP ${response.code} for $symbol")
            }
            val body = response.body.string()
            val parsed = Jsonx.json.decodeFromString<QuoteResponse>(body)
            if (parsed.currentPrice == 0.0 && parsed.percentChange == 0.0) {
                error("Finnhub returned empty quote for $symbol")
            }
            return Quote(
                symbol = symbol,
                price = parsed.currentPrice,
                changePercent = parsed.percentChange,
            )
        }
    }

    @Serializable
    private data class QuoteResponse(
        @SerialName("c") val currentPrice: Double,
        @SerialName("dp") val percentChange: Double,
    )

    companion object {
        private const val BASE = "https://finnhub.io/api/v1/quote"
    }
}
