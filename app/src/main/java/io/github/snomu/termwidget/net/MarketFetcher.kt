package io.github.snomu.termwidget.net

import io.github.snomu.termwidget.model.MarketCache
import io.github.snomu.termwidget.model.Quote
import io.github.snomu.termwidget.model.Ticker
import io.github.snomu.termwidget.model.TickerSource

class MarketFetcher(
    private val finnhub: (symbol: String, apiKey: String) -> PriceChange = FinnhubClient()::quote,
    private val coinGecko: (ids: List<String>) -> Map<String, PriceChange> = CoinGeckoClient()::prices,
) {
    /**
     * Fetches every configured ticker and merges the result into [previous].
     * A ticker whose fetch fails keeps its last good quote (and that quote's
     * original timestamp, so it still dims once it is too old). Quotes for
     * tickers that are no longer configured are dropped.
     */
    fun fetch(
        tickers: List<Ticker>,
        finnhubApiKey: String?,
        previous: MarketCache?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): MarketCache {
        val fresh = mutableMapOf<Pair<TickerSource, String>, PriceChange>()

        val key = finnhubApiKey?.takeIf { it.isNotBlank() }
        if (key != null) {
            tickers.filter { it.source == TickerSource.FINNHUB }
                .map { it.symbol }
                .distinct()
                .forEach { symbol ->
                    try {
                        fresh[TickerSource.FINNHUB to symbol] = finnhub(symbol, key)
                    } catch (_: Exception) {
                        // Keep the cached quote for this symbol.
                    }
                }
        }

        val cryptoIds = tickers.filter { it.source == TickerSource.COINGECKO }
            .map { it.symbol }
            .distinct()
        if (cryptoIds.isNotEmpty()) {
            try {
                coinGecko(cryptoIds).forEach { (id, value) ->
                    fresh[TickerSource.COINGECKO to id] = value
                }
            } catch (_: Exception) {
                // Keep the cached quotes for these ids.
            }
        }

        val cached = previous?.quotes.orEmpty().associateBy { it.source to it.symbol }
        val quotes = tickers.map { it.source to it.symbol }.distinct().mapNotNull { id ->
            val value = fresh[id]
            if (value != null) {
                Quote(
                    source = id.first,
                    symbol = id.second,
                    price = value.price,
                    changePercent = value.changePercent,
                    fetchedAtEpochMs = nowEpochMs,
                )
            } else {
                cached[id]
            }
        }
        return MarketCache(quotes)
    }
}
