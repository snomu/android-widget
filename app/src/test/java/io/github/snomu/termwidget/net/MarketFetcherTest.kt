package io.github.snomu.termwidget.net

import io.github.snomu.termwidget.data.Defaults
import io.github.snomu.termwidget.model.MarketCache
import io.github.snomu.termwidget.model.Quote
import io.github.snomu.termwidget.model.TickerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketFetcherTest {
    private val tickers = Defaults.tickers

    private val previous = MarketCache(
        listOf(
            Quote(TickerSource.FINNHUB, "SPY", 500.0, 0.2, fetchedAtEpochMs = 1_000L),
            Quote(TickerSource.FINNHUB, "GOOG", 180.0, -0.4, fetchedAtEpochMs = 1_000L),
            Quote(TickerSource.COINGECKO, "helium", 0.25, -1.0, fetchedAtEpochMs = 1_000L),
            Quote(TickerSource.FINNHUB, "REMOVED", 1.0, 1.0, fetchedAtEpochMs = 1_000L),
        ),
    )

    @Test
    fun freshQuotesReplaceCachedOnes() {
        val fetcher = MarketFetcher(
            finnhub = { symbol, _ -> PriceChange(symbol.length.toDouble(), 1.5) },
            coinGecko = { ids -> ids.associateWith { PriceChange(0.3, 2.0) } },
        )

        val result = fetcher.fetch(tickers, "key", previous, nowEpochMs = 9_000L)

        assertEquals(3, result.quotes.size)
        assertTrue(result.quotes.all { it.fetchedAtEpochMs == 9_000L })
        assertEquals(4.0, result.quotes.first { it.symbol == "GOOG" }.price, 0.0)
        assertEquals(0.3, result.quotes.first { it.symbol == "helium" }.price, 0.0)
    }

    @Test
    fun aFailedSymbolKeepsItsCachedQuoteAndOriginalTimestamp() {
        val fetcher = MarketFetcher(
            finnhub = { symbol, _ ->
                if (symbol == "SPY") error("HTTP 429") else PriceChange(1.0, 1.0)
            },
            coinGecko = { error("offline") },
        )

        val result = fetcher.fetch(tickers, "key", previous, nowEpochMs = 9_000L)
        val bySymbol = result.quotes.associateBy { it.symbol }

        assertEquals(9_000L, bySymbol.getValue("GOOG").fetchedAtEpochMs)
        assertEquals(previous.quotes[0], bySymbol.getValue("SPY"))
        assertEquals(previous.quotes[2], bySymbol.getValue("helium"))
    }

    @Test
    fun withoutAnApiKeyStocksKeepCachedQuotesAndCryptoStillUpdates() {
        var finnhubCalls = 0
        val fetcher = MarketFetcher(
            finnhub = { _, _ ->
                finnhubCalls += 1
                PriceChange(1.0, 1.0)
            },
            coinGecko = { ids -> ids.associateWith { PriceChange(0.3, 2.0) } },
        )

        val result = fetcher.fetch(tickers, finnhubApiKey = null, previous, nowEpochMs = 9_000L)
        val bySymbol = result.quotes.associateBy { it.symbol }

        assertEquals(0, finnhubCalls)
        assertEquals(previous.quotes[1], bySymbol.getValue("GOOG"))
        assertEquals(9_000L, bySymbol.getValue("helium").fetchedAtEpochMs)
    }

    @Test
    fun quotesForRemovedTickersAreDropped() {
        val fetcher = MarketFetcher(
            finnhub = { _, _ -> error("offline") },
            coinGecko = { error("offline") },
        )

        val result = fetcher.fetch(tickers, "key", previous, nowEpochMs = 9_000L)

        assertEquals(setOf("SPY", "GOOG", "helium"), result.quotes.map { it.symbol }.toSet())
    }

    @Test
    fun nothingCachedAndEverythingFailingYieldsNoQuotes() {
        val fetcher = MarketFetcher(
            finnhub = { _, _ -> error("offline") },
            coinGecko = { error("offline") },
        )

        val result = fetcher.fetch(tickers, "key", previous = null, nowEpochMs = 9_000L)

        assertTrue(result.quotes.isEmpty())
    }
}
