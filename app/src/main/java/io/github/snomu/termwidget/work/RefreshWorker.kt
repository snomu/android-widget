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

class RefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = Store(applicationContext)
        val keyVault = KeyVault(applicationContext)
        val config = store.getConfig()
        val now = System.currentTimeMillis()

        try {
            val weather = OpenMeteoClient().fetch(config.cities, now)
            store.saveWeather(weather)
        } catch (_: Exception) {
            // Keep cached weather.
        }

        try {
            val market = MarketFetcher().fetch(
                tickers = config.tickers,
                finnhubApiKey = keyVault.load(),
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
