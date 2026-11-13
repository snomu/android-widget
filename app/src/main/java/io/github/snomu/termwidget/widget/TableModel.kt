package io.github.snomu.termwidget.widget

data class ColoredText(
    val text: String,
    val color: Int,
    val bold: Boolean = false,
)

data class CityRowModel(
    val code: String,
    val codeColor: Int,
    val timeZone: String,
    val clockColor: Int,
    val markerAndTemps: List<ColoredText>,
)

data class TickerItemModel(
    val label: String,
    val labelColor: Int,
    val value: String,
    val valueColor: Int,
)

data class TableModel(
    val header: String,
    val headerColor: Int,
    val rows: List<CityRowModel>,
    val tickers: List<TickerItemModel>,
    val rowCharCount: Int,
)
