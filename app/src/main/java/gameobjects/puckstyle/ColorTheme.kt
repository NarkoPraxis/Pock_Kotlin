package gameobjects.puckstyle

import utility.PaintBucket

data class ColorGroup(val primary: Int, val secondary: Int)

data class ColorTheme(
    val main: ColorGroup,
    val effect: ColorGroup,
    val inert: ColorGroup,
    val isWarm: Boolean
) {
    companion object {
        val Warm get() = ColorTheme(
            main = ColorGroup(PaintBucket.highBallColor, PaintBucket.highBallStrokeColor),
            effect = ColorGroup(PaintBucket.effectColor, PaintBucket.effectSecondaryColor),
            inert = ColorGroup(PaintBucket.inertPrimaryColor, PaintBucket.inertSecondaryColor),
            isWarm = true
        )
        val Cold get() = ColorTheme(
            main = ColorGroup(PaintBucket.lowBallColor, PaintBucket.lowBallStrokeColor),
            effect = ColorGroup(PaintBucket.effectColor, PaintBucket.effectSecondaryColor),
            inert = ColorGroup(PaintBucket.inertPrimaryColor, PaintBucket.inertSecondaryColor),
            isWarm = false
        )
    }
}
