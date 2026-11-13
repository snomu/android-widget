package io.github.snomu.termwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.github.snomu.termwidget.data.Store
import io.github.snomu.termwidget.work.RefreshScheduler
import kotlinx.coroutines.runBlocking

class TermWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        RefreshScheduler.ensurePeriodic(context)
        for (id in appWidgetIds) {
            updateOne(context, appWidgetManager, id, null)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        updateOne(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onEnabled(context: Context) {
        RefreshScheduler.ensurePeriodic(context)
        RefreshScheduler.refreshNow(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> RefreshScheduler.refreshNow(context)
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                // Handled by onUpdate; keep periodic scheduled.
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "io.github.snomu.termwidget.action.REFRESH"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TermWidgetProvider::class.java))
            for (id in ids) {
                updateOne(context, manager, id, null)
            }
        }

        fun updateOne(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
            options: Bundle?,
        ) {
            val store = Store(context)
            val (config, weather, market) = runBlocking {
                Triple(store.getConfig(), store.getWeather(), store.getMarket())
            }
            val refreshIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                Intent(context, TermWidgetProvider::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val views = WidgetRenderer.render(
                context = context,
                appWidgetId = appWidgetId,
                options = options,
                config = config,
                weather = weather,
                market = market,
                refreshIntent = refreshIntent,
            )
            manager.updateAppWidget(appWidgetId, views)
        }
    }
}
