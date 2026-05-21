package gameobjects.puckstyle

import androidx.compose.ui.graphics.toArgb
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
            main = ColorGroup(PaintBucket.highBallFill.toArgb(), PaintBucket.highBallStroke.toArgb()),
            shield = ColorGroup(PaintBucket.highShieldPrimary.toArgb(), PaintBucket.highShieldSecondary.toArgb()),
            inert = ColorGroup(PaintBucket.inertPrimaryColor.toArgb(), PaintBucket.inertSecondaryColor.toArgb()),
            isWarm = true
        )
        val Cold get() = ColorTheme(
            main = ColorGroup(PaintBucket.lowBallFill.toArgb(), PaintBucket.lowBallStroke.toArgb()),
            shield = ColorGroup(PaintBucket.lowShieldPrimary.toArgb(), PaintBucket.lowShieldSecondary.toArgb()),
            inert = ColorGroup(PaintBucket.inertPrimaryColor.toArgb(), PaintBucket.inertSecondaryColor.toArgb()),
            isWarm = false
        )

        fun getTheme(isHigh: Boolean): ColorTheme {
            return if (isHigh) Warm else Cold
        }
    }
}
