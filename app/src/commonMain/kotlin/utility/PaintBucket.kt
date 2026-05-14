package utility

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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

    // ── Colors ────────────────────────────────────────────────────────────────

    /** Background fill (dark arena). Overwritten by Android initializeColors(). */
    var backgroundColor: Color = Color(0xFF1A1A1A)

    /** Goal-zone fill color. */
    var goalColor: Color = Color(0xFF2A2A3A)

    /** Wall/particle color (alias: effectColor). */
    var wallColor: Color = Color(0xFF444466)

    /** Saturated wall color shown when a goal is open. */
    var canScoreWallColor: Color = Color(0xFF5555AA)

    /** High (warm) player puck fill. Mutable — Logic.setPuckColor() swaps this during Scored. */
    var highBallFill: Color = Color(0xfff59da0)

    /** High (warm) player puck stroke. Mutable. */
    var highBallStroke: Color = Color(0xfff25252)

    /** Low (cold) player puck fill. Mutable. */
    var lowBallFill: Color = Color(0xff9dd4f5)

    /** Low (cold) player puck stroke. Mutable. */
    var lowBallStroke: Color = Color(0xff52b6f2)

    /**
     * Primary effect/wall color alias — kept separately so code referencing `effectColor`
     * compiles without changes. Always kept in sync with [wallColor] after initialize().
     */
    var effectColor: Color = Color(0xFF444466)

    var inertPrimaryColor: Color = Color(0xFF555555)
    var inertSecondaryColor: Color = Color(0xFF333333)

    /** Saturated variant of [effectColor] used for shield/secondary effects. */
    var effectSecondaryColor: Color = Color(0xFF666688)

    var bonusColor: Color = Color(0xFFFFFF00)

    var highPlayerHighlightColor: Color = Color(0x32FF6B6B)
    var lowPlayerHighlightColor: Color = Color(0x326B6BFF)

    /** Score-flash overlay color — set at runtime by Logic when a goal is scored. */
    var scoreFlashColor: Color = Color(0x00FFFFFF)

    /** Score-flash overlay alpha (0f–255f). Decremented each frame by Drawing.drawScoreFlash(). */
    var scoreFlashAlpha: Float = 0f

    var menuHintColor: Color = Color(0x3CFFFFFF)

    val timerColor: Color get() = if (utility.Storage.darkMode) Color(0xFF999999) else Color(0xFFD9D9D9)

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
