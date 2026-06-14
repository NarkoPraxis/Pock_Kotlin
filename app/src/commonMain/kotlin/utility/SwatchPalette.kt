package utility

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * Central hue → swatch-colour conversion for the colour picker and the gameplay palette.
 *
 * Almost every preset colour is a single HUE rendered at a fixed pastel saturation/value:
 *   primary = the light fill   (HSV sat [PRIMARY_SAT], val [SWATCH_VALUE])
 *   secondary = the richer outline (HSV sat [SECONDARY_SAT], val [SWATCH_VALUE])
 *   pale = a near-white tint   (HSV sat [PALE_SAT], val [PALE_VALUE])
 *
 * A couple of colours can't exist at that uniform pastel S/V — "brown" is a dark, low-value orange
 * and "forest green" is a dark green — so they carry an explicit true-colour override keyed by their
 * identity hue (see ColorCarousel.PRESETS). The hue stays the colour's unique id in storage; only the
 * rendered tones deviate from the formula. Match is by a tight epsilon so a custom-slider hue that
 * merely passes near 25°/140° isn't captured, while the exact preset hues always are.
 */
object SwatchPalette {
    const val PRIMARY_SAT = 0.359f
    const val SECONDARY_SAT = 0.661f
    const val SWATCH_VALUE = 0.961f
    const val PALE_SAT = 0.10f
    const val PALE_VALUE = 0.99f

    // Identity hues that render as a fixed true colour instead of the uniform pastel pair.
    const val BROWN_HUE = 25f
    const val FOREST_GREEN_HUE = 140f
    private const val MATCH_EPS = 0.25f

    private class Tones(val primary: Color, val secondary: Color, val pale: Color)

    private val BROWN = Tones(
        primary = Color(0xFF8B4513),    // saddle brown — the classic "brown"
        secondary = Color(0xFF5E2F0D),  // darker brown outline
        pale = Color(0xFFEAD7C4)        // light tan tint
    )
    private val FOREST_GREEN = Tones(
        primary = Color(0xFF228B22),    // classic forest green
        secondary = Color(0xFF155A15),  // darker green outline
        pale = Color(0xFFCDE8CD)        // light green tint
    )

    private fun overrideFor(hue: Float): Tones? = when {
        abs(hue - BROWN_HUE) < MATCH_EPS -> BROWN
        abs(hue - FOREST_GREEN_HUE) < MATCH_EPS -> FOREST_GREEN
        else -> null
    }

    fun primary(hue: Float): Color = overrideFor(hue)?.primary ?: Color.hsv(hue, PRIMARY_SAT, SWATCH_VALUE)
    fun secondary(hue: Float): Color = overrideFor(hue)?.secondary ?: Color.hsv(hue, SECONDARY_SAT, SWATCH_VALUE)
    fun pale(hue: Float): Color = overrideFor(hue)?.pale ?: Color.hsv(hue, PALE_SAT, PALE_VALUE)
}
