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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TermWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        RefreshScheduler.ensurePeriodic(context)
        renderAsync { appWidgetIds.forEach { update(context, appWidgetManager, it, null) } }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        renderAsync { update(context, appWidgetManager, appWidgetId, newOptions) }
    }

    override fun onEnabled(context: Context) {
        RefreshScheduler.ensurePeriodic(context)
        RefreshScheduler.refreshNow(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            RefreshScheduler.refreshNow(context)
        }
    }

    /** Reads the cache off the main thread while keeping the broadcast alive. */
    private fun renderAsync(block: suspend () -> Unit) {
        val pending = goAsync()
        scope.launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "io.github.snomu.termwidget.action.REFRESH"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Re-renders every placed widget from the cache. */
        suspend fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TermWidgetProvider::class.java))
            ids.forEach { update(context, manager, it, null) }
        }

        /** Re-renders one widget from the cache; null [options] means "ask the host". */
        suspend fun update(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
            options: Bundle?,
        ) {
            val store = Store(context)
            val views = WidgetRenderer.render(
                context = context,
                appWidgetId = appWidgetId,
                options = options,
                config = store.getConfig(),
                weather = store.getWeather(),
                market = store.getMarket(),
                refreshIntent = refreshIntent(context, appWidgetId),
            )
            manager.updateAppWidget(appWidgetId, views)
        }

        fun refreshIntent(context: Context, requestCode: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                requestCode,
                Intent(context, TermWidgetProvider::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}
