package gameobjects.puckstyle

import utility.PaintBucket

data class ColorGroup(val primary: Int, val secondary: Int)

data class ColorTheme(
    val main: ColorGroup,
    val shield: ColorGroup,
    val inert: ColorGroup,
    val isWarm: Boolean
) {
    companion object {
        val Warm get() = ColorTheme(
            main = ColorGroup(PaintBucket.highBallColor, PaintBucket.highBallStrokeColor),
            shield = ColorGroup(PaintBucket.effectColor, PaintBucket.effectSecondaryColor),
            inert = ColorGroup(PaintBucket.inertPrimaryColor, PaintBucket.inertSecondaryColor),
            isWarm = true
        )
        val Cold get() = ColorTheme(
            main = ColorGroup(PaintBucket.lowBallColor, PaintBucket.lowBallStrokeColor),
            shield = ColorGroup(PaintBucket.effectColor, PaintBucket.effectSecondaryColor),
            inert = ColorGroup(PaintBucket.inertPrimaryColor, PaintBucket.inertSecondaryColor),
            isWarm = false
        )

        fun getTheme(isHigh: Boolean) : ColorTheme {
            return if (isHigh) ColorTheme.Warm else ColorTheme.Cold
        }
    }
}
