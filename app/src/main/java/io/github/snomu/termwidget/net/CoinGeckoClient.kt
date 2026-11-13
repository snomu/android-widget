package io.github.snomu.termwidget.net

import io.github.snomu.termwidget.data.Jsonx
import io.github.snomu.termwidget.model.Quote
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

class CoinGeckoClient(
    private val client: okhttp3.OkHttpClient = HttpClient.okHttp,
) {
    fun prices(ids: List<String>): List<Quote> {
        if (ids.isEmpty()) return emptyList()

        val url = BASE.toHttpUrl().newBuilder()
            .addQueryParameter("ids", ids.joinToString(","))
            .addQueryParameter("vs_currencies", "usd")
            .addQueryParameter("include_24hr_change", "true")
            .build()
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("CoinGecko HTTP ${response.code}")
            }
            val body = response.body.string()
            val root = Jsonx.json.decodeFromString<JsonObject>(body)
            return ids.map { id ->
                val entry = root[id]?.jsonObject
                    ?: error("CoinGecko missing id $id")
                val price = entry["usd"]?.jsonPrimitive?.doubleOrNull
                    ?: error("CoinGecko missing usd for $id")
                val change = entry["usd_24h_change"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                Quote(symbol = id, price = price, changePercent = change)
            }
        }
    }

    companion object {
        private const val BASE = "https://api.coingecko.com/api/v3/simple/price"
    }
}
