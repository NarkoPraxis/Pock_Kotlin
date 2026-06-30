package utility

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import gameobjects.Settings

/**
 * Cross-platform paint/color registry.
 *
 * Holds all rendering colors as Compose [Color] values and stroke descriptors as [Stroke] objects.
 * Android-specific [android.graphics.Paint] objects and legacy Int color accessors live in
 * PaintBucketAndroid.kt (androidMain source set) via extension properties/functions.
 *
 * Call [initialize] once after screen dimensions are known (after Settings is populated).
 * The Android side additionally calls [initializeColors] to load R.color values from Resources.
 */
object PaintBucket {

    // ── Canonical palette (single source of truth for all UI and game colors) ──

    val black: Color = Color(0xFF000000)
    val white: Color = Color(0xFFFFFFFF)

    /** High (warm) player puck fill. */
    var highPlayerPrimary: Color = Color(0xFFf59da0)
    /** High (warm) player puck stroke. */
    var highPlayerSecondary: Color = Color(0xFFf25252)

    /** Low (cold) player puck fill. */
    var lowPlayerPrimary: Color = Color(0xFF9dd4f5)
    /** Low (cold) player puck stroke. */
    var lowPlayerSecondary: Color = Color(0xFF52b6f2)

    /** Shield/effect primary color (arena walls / goal zones). */
    var shieldPrimary: Color = Color(0xFFc09df5)
    /** Shield/effect secondary color (arena walls / goal zones). */
    var shieldSecondary: Color = Color(0xFF9356ee)

    /** Per-player shield colors — driven by Storage hue settings. */
    var highShieldPrimary: Color = Color(0xFFc09df5)
    var highShieldSecondary: Color = Color(0xFF9356ee)
    var lowShieldPrimary: Color = Color(0xFFc09df5)
    var lowShieldSecondary: Color = Color(0xFF9356ee)

    /** Pale tier — desaturated version of secondary hue. Driven by Storage hue settings. */
    var highPlayerPale: Color = Color.hsv(0f, 0.10f, 0.99f)
    var lowPlayerPale: Color = Color.hsv(220f, 0.10f, 0.99f)
    var highShieldPale: Color = Color.hsv(264f, 0.10f, 0.99f)
    var lowShieldPale: Color = Color.hsv(264f, 0.10f, 0.99f)

    /** Inert/neutral puck primary (light grey). */
    var inertPrimary: Color = Color(0xFFD9D9D9)
    /** Inert/neutral puck secondary (mid grey). */
    var inertSecondary: Color = Color(0xFF999999)
    /** Inert/neutral pale (very light grey — no hue). */
    var inertPale: Color = Color(0xFFEFEFEF)

    /** Dark-mode menu/arena background. */
    var menuBackgroundDark: Color = Color(0xFF131221)
    /** Dark-mode menu button fill. */
    var menuButtonDark: Color = Color(0xFF2e2c50)
    /** Light-mode menu background. */
    var menuBackgroundLight: Color = Color(0xFFFFFFFF)
    /** Light-mode menu button fill. */
    var menuButtonLight: Color = Color(0xFFe2d1fa)

    // ── Menu-chrome brand accents (fixed; never themed, never hue-shifted) ──
    // These mirror the *default* player primary/secondary literals but live as
    // immutable vals so applyPlayerHues() and the dark-mode toggle never touch
    // them. Used by the redesigned MainMenuScreen (Main.svg) chrome.

    /** Main-menu button fill / left icon pill (brand blue). */
    val menuAccentBlue: Color = Color(0xFF52B6F2)
    /** Softer brand blue (highlights / accents). */
    val menuAccentBlueSoft: Color = Color(0xFF9DD3F5)
    /** Main-menu progress/customize pill (brand red). */
    val menuAccentRed: Color = Color(0xFFF25252)
    /** Softer brand red (highlights / accents). */
    val menuAccentRedSoft: Color = Color(0xFFF59DA0)
    /** Dashed indicator stroke (hashed score-placement ring + drag connector) — pale lavender. */
    val menuHashStroke: Color = Color(0xFFE2E2FF)

    // ── UI accent colors ───────────────────────────────────────────────────────

    /** Error/danger red — reset button, muted-on indicator. */
    val dangerRed: Color = Color(0xFFFF6666)
    /** Volume slider unmuted-state label. */
    val muteInactive: Color = Color(0xFF8888AA)
    /** Segment selector active button fill. */
    val segmentActive: Color = Color(0xFF6666AA)
    /** Segment selector inactive button fill (dark mode). */
    val segmentInactiveDark: Color = Color(0xFF333344)

    // ── Text hierarchy ─────────────────────────────────────────────────────────

    /** Secondary text / section labels (dark mode). */
    val textSecondaryDark: Color = Color(0xFFAAAAAA)
    /** Secondary text / section labels (light mode). */
    val textSecondaryLight: Color = Color(0xFF555566)
    /** Tertiary / muted labels and volume values (dark mode). */
    val textMutedDark: Color = Color(0xFFCCCCCC)
    /** Tertiary / muted labels and volume values (light mode). */
    val textMutedLight: Color = Color(0xFF333344)

    // ── Component backgrounds ──────────────────────────────────────────────────

    /** Ball-unlock card background (light mode). */
    val cardBackgroundLight: Color = Color(0xFFE8E8F8)

    // ── Dividers ───────────────────────────────────────────────────────────────

    /** Section divider line (dark mode). */
    val dividerDark: Color = Color(0xFF444466)
    /** Section divider line (light mode). */
    val dividerLight: Color = Color(0xFFCCCCDD)

    // ── Legacy aliases (kept for backward compatibility — point to canonical fields above) ──

    /** Background fill (dark arena). Overwritten by Android initializeColors(). */
    var backgroundColor: Color = Color(0xFF1A1A1A)

    /** Goal-zone fill color. */
    var goalColor: Color = Color(0xFF2A2A3A)

    /** Wall/particle color (alias: effectColor). */
    var wallColor: Color = Color(0xFFC09DF5)

    /** Saturated wall color shown when a goal is open. */
    var canScoreWallColor: Color = Color(0xFF5555AA)

    /** High (warm) player puck fill. Mutable — Logic.setPuckColor() swaps this during Scored. */
    var highBallFill: Color get() = highPlayerPrimary; set(v) { highPlayerPrimary = v }

    /** High (warm) player puck stroke. Mutable. */
    var highBallStroke: Color get() = highPlayerSecondary; set(v) { highPlayerSecondary = v }

    /** Low (cold) player puck fill. Mutable. */
    var lowBallFill: Color get() = lowPlayerPrimary; set(v) { lowPlayerPrimary = v }

    /** Low (cold) player puck stroke. Mutable. */
    var lowBallStroke: Color get() = lowPlayerSecondary; set(v) { lowPlayerSecondary = v }

    /** Primary shield/effect color alias. */
    var effectColor: Color get() = shieldPrimary; set(v) { shieldPrimary = v }

    var inertPrimaryColor: Color get() = inertPrimary; set(v) { inertPrimary = v }
    var inertSecondaryColor: Color get() = inertSecondary; set(v) { inertSecondary = v }

    /** Secondary shield/effect color alias. */
    var effectSecondaryColor: Color get() = shieldSecondary; set(v) { shieldSecondary = v }

    var bonusColor: Color = Color(0xFFFFFF00)

    /** Legacy alias — touch-zone highlight is the player's main pale color. */
    var highPlayerHighlightColor: Color
        get() = highPlayerPale
        set(v) { highPlayerPale = v }
    var lowPlayerHighlightColor: Color
        get() = lowPlayerPale
        set(v) { lowPlayerPale = v }

    var menuHintColor: Color = Color(0x3CFFFFFF)

    val timerColor: Color get() = if (utility.Storage.darkMode) Color(0xFF999999) else Color(0xFFD9D9D9)

    // ── Score / Time dial section colours ───────────────────────────────────────
    // Light mode keeps the player's theme.main: primary at rest, secondary while a number updates.
    // In dark mode that scheme is too contrasty against the arena, so both states instead derive from
    // the faint touch-highlight tint behind the dial (the player's primary composited at 0.2 alpha
    // over the background) lifted slightly toward white — ~10% for the resting fill, ~20% while a
    // number is updating. Shared by ScoreDial and TimeDial so the two panels always match.
    private fun dialTint(isHigh: Boolean): Color =
        lerp(backgroundColor, if (isHigh) highBallFill else lowBallFill, 0.2f)

    /** Resting fill of a score/time dial section. */
    fun dialRestColor(isHigh: Boolean): Color =
        if (utility.Storage.darkMode) lerp(dialTint(isHigh), white, 0.10f)
        else if (isHigh) highBallFill else lowBallFill

    /** Fill of a score/time dial section while its number is updating. */
    fun dialActiveColor(isHigh: Boolean): Color =
        if (utility.Storage.darkMode) lerp(dialTint(isHigh), white, 0.20f)
        else if (isHigh) highBallStroke else lowBallStroke

    // ── Stroke descriptors ─────────────────────────────────────────────────────

    /** Puck outline stroke — width set in [initialize]. */
    var ballStroke: Stroke = Stroke(width = 4f)

    /** Arena-wall segment stroke. */
    var wallStroke: Stroke = Stroke(width = 3f)

    /** Aim arrow line stroke (round caps + join). */
    var aimArrowStroke: Stroke = Stroke(
        width = 6f,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round
    )

    /** Long wall particle stroke (round cap). */
    var longParticleStroke: Stroke = Stroke(width = 3f, cap = StrokeCap.Round)

    // ── Font sizes ─────────────────────────────────────────────────────────────

    /**
     * Score text size in px.
     * Set in [initialize] to `Settings.topGoalBottom * 0.85f`.
     * (Fixes CLAUDE.md bug: was hardcoded 120f, should be screenRatio-based.)
     */
    var scoreFontSize: Float = 120f

    var tutorialFontSize: Float = 40f
    var menuHintFontSize: Float = 20f

    // ── Misc ──────────────────────────────────────────────────────────────────

    /**
     * Stroke width scalar derived from screenRatio.
     * Referenced by many callers by this exact name — kept for compatibility.
     */
    var STROKE_WIDTH: Float = 0f

    /** Greyscale color filter used for locked/placeholder ball previews. */
    val greyscaleColorFilter: ColorFilter =
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

    /** Charge ring alpha (deprecated UI; kept alive so callers don't break). */
    var chargeAlpha: Float = 1f

    // ── Initialization ────────────────────────────────────────────────────────

    /**
     * Apply stored player hue selections to all player/shield color fields.
     * Tones come from [SwatchPalette] (the standard pastel pair for most hues, or a true-colour
     * override for Brown/Forest Green). Call after [initialize] (and [initializePlatformColors] on iOS).
     */
    fun applyPlayerHues() {
        val highHue = Storage.highPlayerColorHue
        val lowHue = Storage.lowPlayerColorHue
        val highShieldHue = Storage.highShieldColorHue
        val lowShieldHue = Storage.lowShieldColorHue
        highPlayerSecondary = SwatchPalette.secondary(highHue)
        highPlayerPrimary = SwatchPalette.primary(highHue)
        highPlayerPale = SwatchPalette.pale(highHue)
        lowPlayerSecondary = SwatchPalette.secondary(lowHue)
        lowPlayerPrimary = SwatchPalette.primary(lowHue)
        lowPlayerPale = SwatchPalette.pale(lowHue)
        highShieldSecondary = SwatchPalette.secondary(highShieldHue)
        highShieldPrimary = SwatchPalette.primary(highShieldHue)
        highShieldPale = SwatchPalette.pale(highShieldHue)
        lowShieldSecondary = SwatchPalette.secondary(lowShieldHue)
        lowShieldPrimary = SwatchPalette.primary(lowShieldHue)
        lowShieldPale = SwatchPalette.pale(lowShieldHue)
    }

    /**
     * Set arena colors for iOS based on dark mode. Call after [initialize].
     * Android does not call this — it uses initializeColors(resources) instead.
     */
    fun initializePlatformColors(isDark: Boolean) {
        backgroundColor = if (isDark) Color(0xFF1A1A1A) else Color(0xFFFFFFFF)
        goalColor = if (isDark) Color(0xFF2A2A3A) else shieldPrimary
        canScoreWallColor = if (isDark) Color(0xFF5555AA) else shieldSecondary
    }

    /**
     * Recompute size-dependent values. Call once after [Settings] screen fields are populated.
     * On Android, call [initializeColors] first to load R.color values, then this.
     */
    fun initialize(screenRatio: Float) {
        STROKE_WIDTH = screenRatio / 12f

        scoreFontSize = Settings.topGoalBottom * 0.85f
        tutorialFontSize = screenRatio * 2.5f
        menuHintFontSize = screenRatio * 1.2f

        // Rebuild Stroke objects with updated widths
        ballStroke = Stroke(
            width = STROKE_WIDTH * 2f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        wallStroke = Stroke(width = STROKE_WIDTH)
        aimArrowStroke = Stroke(
            width = screenRatio * 0.4f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        longParticleStroke = Stroke(width = STROKE_WIDTH, cap = StrokeCap.Round)
    }
}
