package io.github.snomu.termwidget.debug

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import io.github.snomu.termwidget.data.Store
import io.github.snomu.termwidget.widget.TermWidgetProvider
import io.github.snomu.termwidget.widget.WidgetRenderer
import io.github.snomu.termwidget.work.RefreshScheduler
import kotlinx.coroutines.runBlocking

/**
 * Debug-only host that applies the same RemoteViews the launcher uses, so the
 * widget can be checked without pinning it to a home screen. Not in release builds.
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
        val refreshIntent = TermWidgetProvider.refreshIntent(this, 0)
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
