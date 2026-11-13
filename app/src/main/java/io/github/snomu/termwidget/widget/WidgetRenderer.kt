package io.github.snomu.termwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ScaleXSpan
import android.text.style.StyleSpan
import android.text.style.SuperscriptSpan
import android.util.SizeF
import android.util.TypedValue
import android.widget.RemoteViews
import io.github.snomu.termwidget.R
import io.github.snomu.termwidget.model.AppConfig
import io.github.snomu.termwidget.model.MarketCache
import io.github.snomu.termwidget.model.WeatherCache
import kotlin.math.max
import kotlin.math.min

object WidgetRenderer {
    private const val HORIZONTAL_PADDING_DP = 10f
    private const val VERTICAL_PADDING_DP = 8f
    private const val SAFETY = 0.96f
    private const val TICKER_SCALE = 0.80f
    private const val MIN_FONT_PX = 8f

    fun render(
        context: Context,
        appWidgetId: Int,
        options: Bundle?,
        config: AppConfig,
        weather: WeatherCache?,
        market: MarketCache?,
        nowEpochMs: Long = System.currentTimeMillis(),
        refreshIntent: PendingIntent,
    ): RemoteViews {
        val model = TableFormatter.format(config, weather, market, nowEpochMs)
        val manager = AppWidgetManager.getInstance(context)
        val opts = options ?: manager.getAppWidgetOptions(appWidgetId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sizes = opts.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            if (!sizes.isNullOrEmpty()) {
                val sized = sizes.associateWith { size ->
                    buildViews(
                        context = context,
                        model = model,
                        widthDp = size.width,
                        heightDp = size.height,
                        refreshIntent = refreshIntent,
                    )
                }
                return RemoteViews(sized)
            }
        }

        val minWidth = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat()
        val maxWidth = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).toFloat()
        val minHeight = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).toFloat()
        val maxHeight = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat()

        val portrait = buildViews(
            context = context,
            model = model,
            widthDp = minWidth.coerceAtLeast(1f),
            heightDp = maxHeight.coerceAtLeast(1f),
            refreshIntent = refreshIntent,
        )
        val landscape = buildViews(
            context = context,
            model = model,
            widthDp = maxWidth.coerceAtLeast(minWidth).coerceAtLeast(1f),
            heightDp = minHeight.coerceAtLeast(1f),
            refreshIntent = refreshIntent,
        )
        return RemoteViews(portrait, landscape)
    }

    private fun buildViews(
        context: Context,
        model: TableModel,
        widthDp: Float,
        heightDp: Float,
        refreshIntent: PendingIntent,
    ): RemoteViews {
        val density = context.resources.displayMetrics.density
        val widthPx = widthDp * density
        val heightPx = heightDp * density
        val usableWidth = max(1f, widthPx - HORIZONTAL_PADDING_DP * 2f * density)
        val usableHeight = max(1f, heightPx - VERTICAL_PADDING_DP * 2f * density)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = 100f
        }
        val advance = paint.measureText("0") / 100f
        val rowChars = model.rowCharCount.coerceAtLeast(1)
        var fontPx = (usableWidth / (rowChars * advance)) * SAFETY

        val tableLines = 1 + model.rows.size
        val tickerLineFactor = TICKER_SCALE
        val totalLineUnits = tableLines + tickerLineFactor + 0.35f
        val maxByHeight = usableHeight / totalLineUnits
        fontPx = min(fontPx, maxByHeight * SAFETY)
        fontPx = max(fontPx, MIN_FONT_PX)

        var tickerPx = fontPx * TICKER_SCALE
        tickerPx = fitTickerFont(model.tickers, tickerPx, usableWidth)

        val markerAdvanceOk = measureMarkerAdvanceOk(paint, fontPx)

        val views = RemoteViews(context.packageName, R.layout.widget)
        views.setOnClickPendingIntent(R.id.widget_root, refreshIntent)

        views.setTextViewText(R.id.header, model.header)
        views.setTextColor(R.id.header, model.headerColor)
        views.setTextViewTextSize(R.id.header, TypedValue.COMPLEX_UNIT_PX, fontPx)

        views.removeAllViews(R.id.rows_container)
        for (row in model.rows) {
            val rowViews = RemoteViews(context.packageName, R.layout.widget_row)
            val codeText = SpannableStringBuilder(row.code)
            codeText.setSpan(
                ForegroundColorSpan(row.codeColor),
                0,
                codeText.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            if ((row.codeColor and 0x00FFFFFF) == (Palette.HOME_CODE and 0x00FFFFFF) &&
                (row.codeColor ushr 24) == 0xFF
            ) {
                codeText.setSpan(
                    StyleSpan(Typeface.BOLD),
                    0,
                    codeText.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            rowViews.setTextViewText(R.id.city_code, codeText)
            rowViews.setTextViewTextSize(R.id.city_code, TypedValue.COMPLEX_UNIT_PX, fontPx)

            rowViews.setTextViewTextSize(R.id.city_clock, TypedValue.COMPLEX_UNIT_PX, fontPx)
            rowViews.setTextColor(R.id.city_clock, row.clockColor)
            rowViews.setCharSequence(R.id.city_clock, "setFormat12Hour", "HH:mm")
            rowViews.setCharSequence(R.id.city_clock, "setFormat24Hour", "HH:mm")
            rowViews.setString(R.id.city_clock, "setTimeZone", row.timeZone)

            val temps = buildTempsSpannable(row.markerAndTemps, markerAdvanceOk)
            rowViews.setTextViewText(R.id.city_temps, temps)
            rowViews.setTextViewTextSize(R.id.city_temps, TypedValue.COMPLEX_UNIT_PX, fontPx)

            views.addView(R.id.rows_container, rowViews)
        }

        views.removeAllViews(R.id.ticker_container)
        model.tickers.forEachIndexed { index, item ->
            if (index > 0) {
                val spacer = RemoteViews(context.packageName, R.layout.widget_ticker_spacer)
                views.addView(R.id.ticker_container, spacer)
            }
            val itemViews = RemoteViews(context.packageName, R.layout.widget_ticker_item)
            val labelAndValue = SpannableStringBuilder()
            val labelStart = labelAndValue.length
            labelAndValue.append(item.label)
            labelAndValue.setSpan(
                ForegroundColorSpan(item.labelColor),
                labelStart,
                labelAndValue.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            labelAndValue.append(' ')
            val valueStart = labelAndValue.length
            labelAndValue.append(item.value)
            labelAndValue.setSpan(
                ForegroundColorSpan(item.valueColor),
                valueStart,
                labelAndValue.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            itemViews.setTextViewText(R.id.ticker_text, labelAndValue)
            itemViews.setTextViewTextSize(R.id.ticker_text, TypedValue.COMPLEX_UNIT_PX, tickerPx)
            views.addView(R.id.ticker_container, itemViews)
        }

        return views
    }

    private fun buildTempsSpannable(
        parts: List<ColoredText>,
        markerAdvanceOk: Boolean,
    ): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        for (part in parts) {
            if (part.text == "⁺" && !markerAdvanceOk) {
                val start = builder.length
                builder.append('+')
                builder.setSpan(SuperscriptSpan(), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(RelativeSizeSpan(0.7f), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(ScaleXSpan(1.15f), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(
                    ForegroundColorSpan(part.color),
                    start,
                    start + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
                continue
            }

            val start = builder.length
            builder.append(part.text)
            builder.setSpan(
                ForegroundColorSpan(part.color),
                start,
                builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            if (part.bold) {
                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        return builder
    }

    private fun measureMarkerAdvanceOk(paint: Paint, fontPx: Float): Boolean {
        paint.textSize = fontPx
        paint.typeface = Typeface.MONOSPACE
        val zero = paint.measureText("0")
        val marker = paint.measureText("⁺")
        if (marker <= 0f) return false
        return absRatio(marker, zero) < 0.08f
    }

    private fun absRatio(a: Float, b: Float): Float {
        if (b == 0f) return 1f
        return kotlin.math.abs(a - b) / b
    }

    private fun fitTickerFont(
        tickers: List<TickerItemModel>,
        preferred: Float,
        usableWidth: Float,
    ): Float {
        if (tickers.isEmpty()) return preferred
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = preferred
        }
        val joined = tickers.joinToString("  ") { "${it.label} ${it.value}" }
        var size = preferred
        var width = paint.measureText(joined)
        while (width > usableWidth && size > MIN_FONT_PX) {
            size -= 0.5f
            paint.textSize = size
            width = paint.measureText(joined)
        }
        return size
    }

    fun defaultSizeDp(): Pair<Float, Float> = 250f to 120f
}
