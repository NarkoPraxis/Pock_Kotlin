package gameobjects.puckstyle

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import utility.SwatchPalette

/**
 * The "rainbow colour override" — a per-player, per-state (main / shield) toggle that replaces a
 * puck's configured colour with a synchronised rainbow strobe while preserving its two-tone design.
 *
 * Design:
 * - There is exactly ONE colour funnel in the renderer: [PuckRenderer.responsiveColorGroup] (set
 *   once per [PuckRenderer.draw]). Every skin / tail / paddle reads its colours from it (via
 *   `responsivePrimary` / `responsiveSecondary` / `responsiveGroup`). So when an override is active,
 *   [PuckRenderer.draw] swaps that group for [group] and the whole puck strobes — including themed
 *   parts of Fire/Galaxy/etc. Hard-coded blacks/greys (Galaxy's void, Metal's grey gradient) never
 *   read the responsive group, so they're left alone, exactly as intended.
 * - The strobe colour is computed once per draw (one [group] call, cached in the renderer's group
 *   field) and then *looked up* by every component, so every overridden puck is in lockstep.
 * - Both levels of the two-tone design are preserved: the desaturated [SwatchPalette.primary] and
 *   the saturated [SwatchPalette.secondary] are taken at the same strobing hue, so the contrast that
 *   defines the ball's look is kept as the colours cycle. This is the same HSV pair the colour
 *   picker uses for every other colour.
 * - Sync vs. distinguishability: both players strobe off the same clock (the per-puck frame in play,
 *   [utility.UiStrobeClock] in static UI), but the Top player is phase-shifted by [PLAYER_OFFSET] so
 *   the two pucks never resolve to the same colour at the same instant.
 */
object RainbowOverride {
    /** Hue degrees advanced per strobe tick — matches RainbowSkin's `strobe * 4f` cycle. */
    const val SPEED = 4f

    /** Hue offset applied to the Top player so a both-rainbow match stays visually distinct. */
    const val PLAYER_OFFSET = 180f

    /** Base strobing hue (0–360) for a player at a given strobe tick. */
    fun hue(isHigh: Boolean, strobe: Int): Float =
        ((strobe * SPEED + if (isHigh) PLAYER_OFFSET else 0f) % 360f + 360f) % 360f

    // The two-tone pair at a hue, built directly from SwatchPalette's S/V constants (NOT the
    // `SwatchPalette.primary/secondary` helpers, which special-case 25°/140° to dark true-colours —
    // those exception colours have no place in a continuous spectral sweep). Shared by the ball
    // ([group]) and the Ball Designer's Rainbow carousel button so the two cycle identically.
    fun primaryColor(hue: Float): Color = Color.hsv(hue, SwatchPalette.PRIMARY_SAT, SwatchPalette.SWATCH_VALUE)
    fun secondaryColor(hue: Float): Color = Color.hsv(hue, SwatchPalette.SECONDARY_SAT, SwatchPalette.SWATCH_VALUE)
    fun paleColor(hue: Float): Color = Color.hsv(hue, SwatchPalette.PALE_SAT, SwatchPalette.PALE_VALUE)

    /**
     * A strobing [ColorGroup] at the given tick. Primary (desaturated) and secondary (saturated)
     * come from the same hue so the puck's two-tone contrast is preserved while it cycles.
     *
     * NB: this builds the HSV pair directly from [SwatchPalette]'s saturation/value constants rather
     * than calling `SwatchPalette.primary/secondary(hue)`. Those helpers special-case a few exact
     * hues (25° brown, 140° forest green) to dark true-colours for the picker's swatches. A smooth
     * sweep steps the hue in whole-degree multiples and lands *exactly* on 140° once per cycle, which
     * would snap the ball to a dark dot for a single frame (the "flash"). Bypassing the overrides
     * keeps the sweep continuous.
     */
    fun group(isHigh: Boolean, strobe: Int): ColorGroup {
        val h = hue(isHigh, strobe)
        return ColorGroup(
            primary = primaryColor(h).toArgb(),
            secondary = secondaryColor(h).toArgb(),
            pale = paleColor(h).toArgb()
        )
    }
}
