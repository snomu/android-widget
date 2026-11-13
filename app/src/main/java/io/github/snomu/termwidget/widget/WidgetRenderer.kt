package io.github.snomu.termwidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ScaleXSpan
import android.text.style.StyleSpan
import android.util.SizeF
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.os.BundleCompat
import io.github.snomu.termwidget.R
import io.github.snomu.termwidget.model.AppConfig
import io.github.snomu.termwidget.model.MarketCache
import io.github.snomu.termwidget.model.WeatherCache
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

object WidgetRenderer {
    // Fallback size, matching res/xml/widget_info.xml, for hosts that report no size.
    private const val DEFAULT_WIDTH_DP = 250f
    private const val DEFAULT_HEIGHT_DP = 110f

    private const val MIN_SIDE_PADDING_DP = 8f
    private const val VERTICAL_PADDING_DP = 8f

    // Must match the separator in res/layout/widget.xml: 1px line with 4dp margins.
    private const val SEPARATOR_MARGIN_DP = 4f
    private const val SEPARATOR_LINE_PX = 1f

    private const val TICKER_SCALE = 0.80f
    private const val MIN_CELL_PX = 4
    private const val MIN_TICKER_FONT_PX = 6f
    private const val FIT_TOLERANCE_PX = 0.01f
    private const val SEARCH_STEPS = 20
    private const val MARKER = "⁺"
    private const val CODE_GAP = "  "

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
        val opts = options ?: AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val sizes = BundleCompat.getParcelableArrayList(
                opts,
                AppWidgetManager.OPTION_APPWIDGET_SIZES,
                SizeF::class.java,
            )
            if (!sizes.isNullOrEmpty()) {
                return RemoteViews(
                    sizes.associateWith { size ->
                        buildViews(context, model, size.width, size.height, refreshIntent)
                    },
                )
            }
        }

        val minWidth = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).toFloat()
        val maxWidth = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).toFloat()
        val minHeight = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).toFloat()
        val maxHeight = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat()

        // Portrait is the narrow, tall size; landscape is the wide, short one.
        val portraitWidth = if (minWidth > 0f) minWidth else DEFAULT_WIDTH_DP
        val portraitHeight = if (maxHeight > 0f) maxHeight else DEFAULT_HEIGHT_DP
        val landscapeWidth = if (maxWidth > 0f) maxWidth else portraitWidth
        val landscapeHeight = if (minHeight > 0f) minHeight else portraitHeight

        val portrait = buildViews(context, model, portraitWidth, portraitHeight, refreshIntent)
        val landscape = buildViews(context, model, landscapeWidth, landscapeHeight, refreshIntent)
        return RemoteViews(landscape, portrait)
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
        val verticalPaddingPx = (VERTICAL_PADDING_DP * density).toInt()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE }
        val rowChars = model.rowCharCount.coerceAtLeast(1)
        val tableLines = 1 + model.rows.size
        val separatorPx = SEPARATOR_LINE_PX + 2f * SEPARATOR_MARGIN_DP * density

        // Every character sits in a cell that is a whole number of pixels wide.
        // The header is one TextView and each row is three, so a fractional
        // advance would round differently per view and the columns would drift.
        val availableWidth = widthPx - 2f * MIN_SIDE_PADDING_DP * density
        val availableHeight = heightPx - 2f * verticalPaddingPx
        var cellPx = max(MIN_CELL_PX, floor(availableWidth / rowChars).toInt())
        var fontPx = fontForCell(paint, cellPx, rowChars)
        while (cellPx > MIN_CELL_PX &&
            contentHeight(paint, fontPx, tableLines, separatorPx) > availableHeight
        ) {
            cellPx -= 1
            fontPx = fontForCell(paint, cellPx, rowChars)
        }

        // Measured rather than assumed: the fitted size can be slightly narrower
        // than the cells it was aiming for.
        paint.textSize = fontPx
        val tableWidthPx = ceil(advance(paint, "0".repeat(rowChars)) - FIT_TOLERANCE_PX).toInt()
        val tickerPx = fitTickerFont(paint, model.tickers, fontPx * TICKER_SCALE, tableWidthPx.toFloat())

        // Centre the table; the separator and ticker line then span exactly its width.
        val spare = max(0, widthPx.toInt() - tableWidthPx)
        val paddingLeft = spare / 2
        val paddingRight = spare - paddingLeft

        val markerGapScale = markerGapScale(paint, fontPx)

        val views = RemoteViews(context.packageName, R.layout.widget)
        views.setOnClickPendingIntent(R.id.widget_root, refreshIntent)
        views.setViewPadding(
            R.id.widget_root,
            paddingLeft,
            verticalPaddingPx,
            paddingRight,
            verticalPaddingPx,
        )

        views.setTextViewText(R.id.header, model.header)
        views.setTextColor(R.id.header, model.headerColor)
        views.setTextViewTextSize(R.id.header, TypedValue.COMPLEX_UNIT_PX, fontPx)

        views.removeAllViews(R.id.rows_container)
        for (row in model.rows) {
            val rowViews = RemoteViews(context.packageName, R.layout.widget_row)

            val code = SpannableStringBuilder(row.code)
            code.setSpan(ForegroundColorSpan(row.codeColor), 0, code.length, SPAN_FLAGS)
            if (row.home) {
                code.setSpan(StyleSpan(Typeface.BOLD), 0, code.length, SPAN_FLAGS)
            }
            // The gap before the clock lives in the same view, so a bold code
            // cannot push the clock off the grid.
            code.append(CODE_GAP)
            rowViews.setTextViewText(R.id.city_code, code)
            rowViews.setTextViewTextSize(R.id.city_code, TypedValue.COMPLEX_UNIT_PX, fontPx)

            // TextClock ticks inside the launcher process, so the minute changes
            // without waking this app.
            rowViews.setTextViewTextSize(R.id.city_clock, TypedValue.COMPLEX_UNIT_PX, fontPx)
            rowViews.setTextColor(R.id.city_clock, row.clockColor)
            rowViews.setCharSequence(R.id.city_clock, "setFormat12Hour", "HH:mm")
            rowViews.setCharSequence(R.id.city_clock, "setFormat24Hour", "HH:mm")
            rowViews.setString(R.id.city_clock, "setTimeZone", row.timeZone)

            rowViews.setTextViewText(R.id.city_temps, buildTemps(row.markerAndTemps, markerGapScale))
            rowViews.setTextViewTextSize(R.id.city_temps, TypedValue.COMPLEX_UNIT_PX, fontPx)

            views.addView(R.id.rows_container, rowViews)
        }

        views.removeAllViews(R.id.ticker_container)
        model.tickers.forEachIndexed { index, item ->
            if (index > 0) {
                views.addView(
                    R.id.ticker_container,
                    RemoteViews(context.packageName, R.layout.widget_ticker_spacer),
                )
            }
            val itemViews = RemoteViews(context.packageName, R.layout.widget_ticker_item)
            val text = SpannableStringBuilder(item.label)
            text.setSpan(ForegroundColorSpan(item.labelColor), 0, text.length, SPAN_FLAGS)
            text.append(' ')
            val valueStart = text.length
            text.append(item.value)
            text.setSpan(ForegroundColorSpan(item.valueColor), valueStart, text.length, SPAN_FLAGS)
            itemViews.setTextViewText(R.id.ticker_text, text)
            itemViews.setTextViewTextSize(R.id.ticker_text, TypedValue.COMPLEX_UNIT_PX, tickerPx)
            views.addView(R.id.ticker_container, itemViews)
        }

        return views
    }

    /**
     * The text size at which one character advances by [cellPx], so that every
     * run of n characters measures exactly n cells in whichever view holds it.
     *
     * Advances are not always proportional to the text size: with font hinting
     * they snap to whole pixels. So the size is found by measuring. If no size
     * gives exactly [cellPx], the result is the nearest size that is narrower.
     */
    private fun fontForCell(paint: Paint, cellPx: Int, rowChars: Int): Float {
        paint.textSize = 100f
        val advancePerPx = advance(paint, "0") / 100f
        if (advancePerPx <= 0f) return cellPx.toFloat()

        val sample = "0".repeat(rowChars)
        val target = (cellPx * rowChars).toFloat()
        fun width(size: Float): Float {
            paint.textSize = size
            return advance(paint, sample)
        }

        val nominal = cellPx / advancePerPx
        val nominalWidth = width(nominal)
        return when {
            abs(nominalWidth - target) <= FIT_TOLERANCE_PX -> nominal
            nominalWidth > target -> {
                // Too wide: the largest size below nominal that fits.
                var fits = nominal * 0.75f
                var tooWide = nominal
                repeat(SEARCH_STEPS) {
                    val mid = (fits + tooWide) / 2f
                    if (width(mid) <= target) fits = mid else tooWide = mid
                }
                fits
            }
            else -> {
                // Too narrow: the smallest size above nominal that fills the cells.
                var tooNarrow = nominal
                var fills = nominal * 1.25f
                if (width(fills) < target) return nominal
                repeat(SEARCH_STEPS) {
                    val mid = (tooNarrow + fills) / 2f
                    if (width(mid) >= target) fills = mid else tooNarrow = mid
                }
                if (width(fills) <= target + FIT_TOLERANCE_PX) fills else nominal
            }
        }
    }

    /** The exact, unrounded advance of [text]. */
    private fun advance(paint: Paint, text: String): Float =
        paint.getRunAdvance(text, 0, text.length, 0, text.length, false, text.length)

    /**
     * The superscript plus usually comes from a fallback font and is narrower
     * than a cell. Rather than distort the glyph, the space that follows it is
     * stretched so that the pair still takes exactly two cells. Returns the
     * scale for that space, or null when no adjustment is needed.
     */
    private fun markerGapScale(paint: Paint, fontPx: Float): Float? {
        paint.textSize = fontPx
        val cell = advance(paint, "0")
        val marker = advance(paint, MARKER)
        val wanted = 2f * cell - marker
        if (abs(marker - cell) < 0.01f || cell <= 0f || wanted <= 0f) return null

        // Advances can be rounded to whole pixels, so home in on the scale by
        // measuring rather than trusting the arithmetic.
        var scale = wanted / cell
        val step = 0.2f / cell
        for (attempt in 0 until 60) {
            paint.textScaleX = scale
            val actual = advance(paint, " ")
            when {
                actual > wanted + 0.01f -> scale -= step
                actual < wanted - 0.01f -> scale += step
                else -> break
            }
        }
        paint.textScaleX = 1f
        return scale
    }

    private fun contentHeight(paint: Paint, fontPx: Float, tableLines: Int, separatorPx: Float): Float =
        tableLines * lineHeight(paint, fontPx) + separatorPx + lineHeight(paint, fontPx * TICKER_SCALE)

    /** Height of one line in a TextView with includeFontPadding="false". */
    private fun lineHeight(paint: Paint, fontPx: Float): Float {
        paint.textSize = fontPx
        val metrics = paint.fontMetrics
        return ceil(metrics.descent - metrics.ascent) + 1f
    }

    /** Shrinks the ticker line until its items fit with at least one space between them. */
    private fun fitTickerFont(
        paint: Paint,
        tickers: List<TickerItemModel>,
        preferred: Float,
        tableWidthPx: Float,
    ): Float {
        if (tickers.isEmpty()) return preferred
        val joined = tickers.joinToString(" ") { "${it.label} ${it.value}" }
        var size = preferred
        paint.textSize = size
        while (size > MIN_TICKER_FONT_PX && paint.measureText(joined) + tickers.size > tableWidthPx) {
            size -= 0.25f
            paint.textSize = size
        }
        return size
    }

    private fun buildTemps(parts: List<ColoredText>, markerGapScale: Float?): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        parts.forEachIndexed { index, part ->
            val start = builder.length
            builder.append(part.text)
            builder.setSpan(ForegroundColorSpan(part.color), start, builder.length, SPAN_FLAGS)
            if (part.bold) {
                builder.setSpan(StyleSpan(Typeface.BOLD), start, builder.length, SPAN_FLAGS)
            }
            val followsMarker = index > 0 && parts[index - 1].text == MARKER
            if (followsMarker && markerGapScale != null && part.text == " ") {
                builder.setSpan(ScaleXSpan(markerGapScale), start, builder.length, SPAN_FLAGS)
            }
        }
        return builder
    }

    private const val SPAN_FLAGS = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
}
