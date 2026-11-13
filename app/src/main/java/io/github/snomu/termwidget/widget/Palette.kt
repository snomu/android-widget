package io.github.snomu.termwidget.widget

object Palette {
    const val BACKGROUND = 0xFF000000.toInt()
    const val DEFAULT_TEXT = 0xFFE8E8E8.toInt()
    const val HOME_CODE = 0xFFFFFFFF.toInt()
    const val DIM = 0xFF6B7480.toInt()
    const val CLOCK = 0xFF6EE7D8.toInt()
    const val SUN = 0xFFFFC94A.toInt()
    const val CLEAR_NIGHT = 0xFFB8A6FF.toInt()
    const val PARTLY_CLOUDY = 0xFFC8B27A.toInt()
    const val CLOUDY = 0xFF8C96A6.toInt()
    const val RAIN = 0xFF4FA8FF.toInt()
    const val SNOW = 0xFFFFFFFF.toInt()
    const val THUNDER = 0xFFFF6B6B.toInt()
    const val MARKET_UP = 0xFF9BE564.toInt()
    const val MARKET_DOWN = 0xFFFF6B6B.toInt()
    const val SEPARATOR = 0xFF1E232A.toInt()

    const val NON_HOME_ALPHA = 0x80

    fun withAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or ((alpha and 0xFF) shl 24)
    }

    fun weatherColor(code: Int, isDay: Boolean, currentColumn: Boolean): Int {
        return when (code) {
            0, 1 -> {
                if (currentColumn && !isDay) CLEAR_NIGHT else SUN
            }
            2 -> PARTLY_CLOUDY
            3, 45, 48 -> CLOUDY
            in 51..67, in 80..82 -> RAIN
            in 71..77, in 85..86 -> SNOW
            in 95..99 -> THUNDER
            else -> CLOUDY
        }
    }
}
