package io.github.snomu.termwidget.debug

import android.app.PendingIntent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import io.github.snomu.termwidget.data.Store
import io.github.snomu.termwidget.widget.TermWidgetProvider
import io.github.snomu.termwidget.widget.WidgetRenderer
import io.github.snomu.termwidget.work.RefreshScheduler
import kotlinx.coroutines.runBlocking

/**
 * Debug-only host that applies the same RemoteViews the launcher uses,
 * so we can screenshot the widget without pinning it to the home screen.
 */
class WidgetPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RefreshScheduler.refreshNow(this)

        val density = resources.displayMetrics.density
        val widthPx = (250 * density).toInt()
        val heightPx = (120 * density).toInt()

        val container = FrameLayout(this).apply {
            setBackgroundColor(0xFF222222.toInt())
            setPadding(
                (16 * density).toInt(),
                (64 * density).toInt(),
                (16 * density).toInt(),
                (16 * density).toInt(),
            )
        }

        val store = Store(this)
        val (config, weather, market) = runBlocking {
            Triple(store.getConfig(), store.getWeather(), store.getMarket())
        }
        val refreshIntent = PendingIntent.getBroadcast(
            this,
            0,
            android.content.Intent(this, TermWidgetProvider::class.java)
                .setAction(TermWidgetProvider.ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val options = Bundle().apply {
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 250)
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 120)
            putInt(android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 120)
        }
        val remoteViews = WidgetRenderer.render(
            context = this,
            appWidgetId = 1,
            options = options,
            config = config,
            weather = weather,
            market = market,
            refreshIntent = refreshIntent,
        )
        val widgetView = remoteViews.apply(this, container)
        container.addView(
            widgetView,
            FrameLayout.LayoutParams(widthPx, heightPx),
        )
        setContentView(container)
    }
}
