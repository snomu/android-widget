package io.github.snomu.termwidget.net

import io.github.snomu.termwidget.model.MarketCache
import io.github.snomu.termwidget.model.Quote
import io.github.snomu.termwidget.model.Ticker
import io.github.snomu.termwidget.model.TickerSource

class MarketFetcher(
    private val finnhub: FinnhubClient = FinnhubClient(),
    private val coinGecko: CoinGeckoClient = CoinGeckoClient(),
) {
    fun fetch(
        tickers: List<Ticker>,
        finnhubApiKey: String?,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): MarketCache {
        val quotes = mutableListOf<Quote>()
        val errors = mutableListOf<String>()

        val finnhubTickers = tickers.filter { it.source == TickerSource.FINNHUB }
        if (finnhubTickers.isNotEmpty()) {
            val key = finnhubApiKey?.takeIf { it.isNotBlank() }
            if (key == null) {
                errors += "Finnhub API key missing"
            } else {
                for (ticker in finnhubTickers) {
                    try {
                        quotes += finnhub.quote(ticker.symbol, key)
                    } catch (e: Exception) {
                        errors += "${ticker.symbol}: ${e.message}"
                    }
                }
            }
        }

        val cryptoIds = tickers
            .filter { it.source == TickerSource.COINGECKO }
            .map { it.symbol }
            .distinct()
        if (cryptoIds.isNotEmpty()) {
            try {
                quotes += coinGecko.prices(cryptoIds)
            } catch (e: Exception) {
                errors += "CoinGecko: ${e.message}"
            }
        }

        if (quotes.isEmpty()) {
            error(errors.joinToString("; ").ifBlank { "No market quotes fetched" })
        }

        return MarketCache(fetchedAtEpochMs = nowEpochMs, quotes = quotes)
    }
}
