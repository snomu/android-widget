package io.github.snomu.termwidget.net

import io.github.snomu.termwidget.data.Jsonx
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** A price and the percent change that decides its color. */
data class PriceChange(val price: Double, val changePercent: Double)

class FinnhubClient(
    private val client: OkHttpClient = HttpClient.okHttp,
) {
    fun quote(symbol: String, apiKey: String): PriceChange {
        val url = BASE.toHttpUrl().newBuilder()
            .addQueryParameter("symbol", symbol)
            .build()
        // The key goes in a header so it never appears in a URL.
        val request = Request.Builder()
            .url(url)
            .header("X-Finnhub-Token", apiKey)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Finnhub HTTP ${response.code} for $symbol")
            }
            return parseQuote(response.body.string())
                ?: error("Finnhub has no quote for $symbol")
        }
    }

    @Serializable
    private data class QuoteResponse(
        @SerialName("c") val currentPrice: Double? = null,
        @SerialName("dp") val percentChange: Double? = null,
    )

    companion object {
        private const val BASE = "https://finnhub.io/api/v1/quote"

        /**
         * Returns null when Finnhub does not know the symbol: it answers 200
         * with a zero price and a null change instead of an error.
         */
        fun parseQuote(body: String): PriceChange? {
            val parsed = Jsonx.json.decodeFromString<QuoteResponse>(body)
            val price = parsed.currentPrice ?: return null
            val change = parsed.percentChange ?: return null
            if (price == 0.0) return null
            return PriceChange(price, change)
        }
    }
}
