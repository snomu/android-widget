package io.github.snomu.termwidget.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.snomu.termwidget.data.KeyVault
import io.github.snomu.termwidget.data.Store
import io.github.snomu.termwidget.net.MarketFetcher
import io.github.snomu.termwidget.net.OpenMeteoClient
import io.github.snomu.termwidget.widget.TermWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Refreshes weather and markets independently, keeps the cached values for
 * whatever failed, then re-renders every widget. It always succeeds: a failed
 * fetch is retried by the next periodic run, and re-rendering is what applies
 * the staleness dimming while offline.
 */
class RefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = Store(applicationContext)
        val config = store.getConfig()
        val now = System.currentTimeMillis()

        try {
            store.saveWeather(OpenMeteoClient().fetch(config.cities, now))
        } catch (_: Exception) {
            // Keep cached weather.
        }

        try {
            val market = MarketFetcher().fetch(
                tickers = config.tickers,
                finnhubApiKey = KeyVault(applicationContext).load(),
                previous = store.getMarket(),
                nowEpochMs = now,
            )
            store.saveMarket(market)
        } catch (_: Exception) {
            // Keep cached market data.
        }

        TermWidgetProvider.updateAll(applicationContext)
        Result.success()
    }
}
