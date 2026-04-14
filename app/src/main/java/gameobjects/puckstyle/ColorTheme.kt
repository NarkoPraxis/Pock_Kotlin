package gameobjects.puckstyle

import utility.PaintBucket

data class ColorTheme(
    val primary: Int,
    val secondary: Int,
    val accent: Int,
    val isWarm: Boolean
) {
    companion object {
        val Warm get() = ColorTheme(
            primary = PaintBucket.highBallColor,
            secondary = PaintBucket.highBallStrokeColor,
            accent = PaintBucket.effectColor,
            isWarm = true
        )
        val Cold get() = ColorTheme(
            primary = PaintBucket.lowBallColor,
            secondary = PaintBucket.lowBallStrokeColor,
            accent = PaintBucket.effectColor,
            isWarm = false
        )
    }
}
