package gameobjects.puckstyle

import androidx.compose.ui.graphics.toArgb
import utility.PaintBucket
import utility.highBallColor
import utility.highBallStrokeColor
import utility.lowBallColor
import utility.lowBallStrokeColor

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
            shield = ColorGroup(PaintBucket.effectColor.toArgb(), PaintBucket.effectSecondaryColor.toArgb()),
            inert = ColorGroup(PaintBucket.inertPrimaryColor.toArgb(), PaintBucket.inertSecondaryColor.toArgb()),
            isWarm = true
        )
        val Cold get() = ColorTheme(
            main = ColorGroup(PaintBucket.lowBallColor, PaintBucket.lowBallStrokeColor),
            shield = ColorGroup(PaintBucket.effectColor.toArgb(), PaintBucket.effectSecondaryColor.toArgb()),
            inert = ColorGroup(PaintBucket.inertPrimaryColor.toArgb(), PaintBucket.inertSecondaryColor.toArgb()),
            isWarm = false
        )

        fun getTheme(isHigh: Boolean) : ColorTheme {
            return if (isHigh) ColorTheme.Warm else ColorTheme.Cold
        }
    }
}
