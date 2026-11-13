package io.github.snomu.termwidget.net

import io.github.snomu.termwidget.data.Jsonx
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class CoinGeckoClient(
    private val client: OkHttpClient = HttpClient.okHttp,
) {
    /** Returns the ids that CoinGecko knows; unknown ids are simply absent. */
    fun prices(ids: List<String>): Map<String, PriceChange> {
        if (ids.isEmpty()) return emptyMap()

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
            return parsePrices(response.body.string(), ids)
        }
    }

    companion object {
        private const val BASE = "https://api.coingecko.com/api/v3/simple/price"

        fun parsePrices(body: String, ids: List<String>): Map<String, PriceChange> {
            val root = Jsonx.json.decodeFromString<JsonObject>(body)
            return ids.mapNotNull { id ->
                val entry = root[id] as? JsonObject ?: return@mapNotNull null
                val price = entry["usd"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val change = entry["usd_24h_change"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                id to PriceChange(price, change)
            }.toMap()
        }
    }
}
