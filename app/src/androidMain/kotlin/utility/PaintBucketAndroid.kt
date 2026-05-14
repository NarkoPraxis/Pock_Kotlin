package utility

import android.content.res.Resources
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.runoutzone.pockpock.R
import gameobjects.Settings

// ── Backward-compat Int color accessors ───────────────────────────────────────
// Existing callers in src/main/java/ reference PaintBucket.highBallColor etc. as Int.
// These computed properties bridge the Compose Color fields to Int for zero-churn migration.

val PaintBucket.highBallColor: Int get() = highBallFill.toArgb()
val PaintBucket.highBallStrokeColor: Int get() = highBallStroke.toArgb()
val PaintBucket.lowBallColor: Int get() = lowBallFill.toArgb()
val PaintBucket.lowBallStrokeColor: Int get() = lowBallStroke.toArgb()

// Resources handle — kept for TutorialBox which reads R.dimen values.
lateinit var paintBucketResources: Resources

// ── Android Paint storage ──────────────────────────────────────────────────────
// All Paint objects live in this internal object to avoid naming conflicts with
// the PaintBucket extension properties of the same names.

internal object AndroidPaints {
    lateinit var backgroundPaint: Paint
    lateinit var effectPaint: Paint
    lateinit var goalPaint: Paint
    lateinit var textPaint: Paint
    lateinit var alwaysBlackTextPaint: Paint
    lateinit var lowBallFillPaint: Paint
    lateinit var lowBallStrokePaint: Paint
    lateinit var highBallFillPaint: Paint
    lateinit var highBallStrokePaint: Paint
    lateinit var wallPaint: Paint
    lateinit var canScoreWallPaint: Paint
    lateinit var scoreFlashPaint: Paint
    lateinit var highPlayerHighlightPaint: Paint
    lateinit var lowPlayerHighlightPaint: Paint
    lateinit var menuHintPaint: Paint
    lateinit var chargeFillHighPaint: Paint
    lateinit var chargeFillLowPaint: Paint
    lateinit var tutorialTextPaint: Paint
    lateinit var tutorialStrokePaint: Paint
    lateinit var debugTextPaint: Paint

    /** Locked-ball silhouette placeholder — always dark grey. */
    val placeholderPaint: Paint = Paint().apply {
        color = AndroidColor.argb(200, 30, 30, 30)
        isAntiAlias = true
        style = Paint.Style.FILL
    }
}

// ── Extension properties delegating to AndroidPaints ──────────────────────────
// Allow callers to write PaintBucket.backgroundPaint, PaintBucket.wallPaint, etc.

var PaintBucket.resources: Resources
    get() = paintBucketResources
    set(value) { paintBucketResources = value }

var PaintBucket.backgroundPaint: Paint
    get() = AndroidPaints.backgroundPaint
    set(value) { AndroidPaints.backgroundPaint = value }

var PaintBucket.effectPaint: Paint
    get() = AndroidPaints.effectPaint
    set(value) { AndroidPaints.effectPaint = value }

var PaintBucket.goalPaint: Paint
    get() = AndroidPaints.goalPaint
    set(value) { AndroidPaints.goalPaint = value }

var PaintBucket.textPaint: Paint
    get() = AndroidPaints.textPaint
    set(value) { AndroidPaints.textPaint = value }

var PaintBucket.alwaysBlackTextPaint: Paint
    get() = AndroidPaints.alwaysBlackTextPaint
    set(value) { AndroidPaints.alwaysBlackTextPaint = value }

var PaintBucket.lowBallFillPaint: Paint
    get() = AndroidPaints.lowBallFillPaint
    set(value) { AndroidPaints.lowBallFillPaint = value }

var PaintBucket.lowBallStrokePaint: Paint
    get() = AndroidPaints.lowBallStrokePaint
    set(value) { AndroidPaints.lowBallStrokePaint = value }

var PaintBucket.highBallFillPaint: Paint
    get() = AndroidPaints.highBallFillPaint
    set(value) { AndroidPaints.highBallFillPaint = value }

var PaintBucket.highBallStrokePaint: Paint
    get() = AndroidPaints.highBallStrokePaint
    set(value) { AndroidPaints.highBallStrokePaint = value }

var PaintBucket.wallPaint: Paint
    get() = AndroidPaints.wallPaint
    set(value) { AndroidPaints.wallPaint = value }

var PaintBucket.canScoreWallPaint: Paint
    get() = AndroidPaints.canScoreWallPaint
    set(value) { AndroidPaints.canScoreWallPaint = value }

var PaintBucket.scoreFlashPaint: Paint
    get() = AndroidPaints.scoreFlashPaint
    set(value) { AndroidPaints.scoreFlashPaint = value }

var PaintBucket.highPlayerHighlightPaint: Paint
    get() = AndroidPaints.highPlayerHighlightPaint
    set(value) { AndroidPaints.highPlayerHighlightPaint = value }

var PaintBucket.lowPlayerHighlightPaint: Paint
    get() = AndroidPaints.lowPlayerHighlightPaint
    set(value) { AndroidPaints.lowPlayerHighlightPaint = value }

var PaintBucket.menuHintPaint: Paint
    get() = AndroidPaints.menuHintPaint
    set(value) { AndroidPaints.menuHintPaint = value }

var PaintBucket.chargeFillHighPaint: Paint
    get() = AndroidPaints.chargeFillHighPaint
    set(value) { AndroidPaints.chargeFillHighPaint = value }

var PaintBucket.chargeFillLowPaint: Paint
    get() = AndroidPaints.chargeFillLowPaint
    set(value) { AndroidPaints.chargeFillLowPaint = value }

var PaintBucket.tutorialTextPaint: Paint
    get() = AndroidPaints.tutorialTextPaint
    set(value) { AndroidPaints.tutorialTextPaint = value }

var PaintBucket.tutorialStrokePaint: Paint
    get() = AndroidPaints.tutorialStrokePaint
    set(value) { AndroidPaints.tutorialStrokePaint = value }

var PaintBucket.debugTextPaint: Paint
    get() = AndroidPaints.debugTextPaint
    set(value) { AndroidPaints.debugTextPaint = value }

var PaintBucket.placeholderPaint: Paint
    get() = AndroidPaints.placeholderPaint
    set(value) { /* read-only */ }

// ── Color loading from R.color ─────────────────────────────────────────────────

/**
 * Load R.color values into PaintBucket's Compose Color fields.
 * Call this before [PaintBucket.initialize] (i.e. after screen dimensions are set).
 */
fun PaintBucket.initializeColors(resources: Resources) {
    paintBucketResources = resources

    backgroundColor = Color(ResourcesCompat.getColor(resources, R.color.background, null))
    goalColor       = Color(ResourcesCompat.getColor(resources, R.color.goalColor, null))
    wallColor       = Color(ResourcesCompat.getColor(resources, R.color.effectColor, null))
    effectColor     = wallColor  // keep alias in sync
    highBallFill    = Color(ResourcesCompat.getColor(resources, R.color.highPlayerLight, null))
    highBallStroke  = Color(ResourcesCompat.getColor(resources, R.color.highPlayerDark, null))
    lowBallFill     = Color(ResourcesCompat.getColor(resources, R.color.lowPlayerLight, null))
    lowBallStroke   = Color(ResourcesCompat.getColor(resources, R.color.lowPlayerDark, null))
    bonusColor      = Color(ResourcesCompat.getColor(resources, R.color.bonus, null))
    inertPrimaryColor   = Color(ResourcesCompat.getColor(resources, R.color.inertPrimary, null))
    inertSecondaryColor = Color(ResourcesCompat.getColor(resources, R.color.inertSecondary, null))

    // canScoreWallColor: saturate goalColor HSV[1] * 2, clamped to 1f
    val goalHsv = FloatArray(3)
    AndroidColor.colorToHSV(goalColor.toArgb(), goalHsv)
    goalHsv[1] = (goalHsv[1] * 2f).coerceAtMost(1f)
    canScoreWallColor = Color(AndroidColor.HSVToColor(goalHsv))

    // effectSecondaryColor: saturate effectColor HSV[1] * 2
    val effectHsv = FloatArray(3)
    AndroidColor.colorToHSV(effectColor.toArgb(), effectHsv)
    effectSecondaryColor = Color(
        AndroidColor.HSVToColor(
            floatArrayOf(effectHsv[0], (effectHsv[1] * 2f).coerceAtMost(1f), effectHsv[2])
        )
    )

    highPlayerHighlightColor = Color(highBallFill.red, highBallFill.green, highBallFill.blue, 50f / 255f)
    lowPlayerHighlightColor  = Color(lowBallFill.red,  lowBallFill.green,  lowBallFill.blue,  50f / 255f)
}

/**
 * Build (or rebuild) all Android [Paint] objects from the current Color fields.
 * Call after [initializeColors] and [PaintBucket.initialize].
 */
fun PaintBucket.buildPaints(resources: Resources) {
    val darkMode = Storage.darkMode
    val p = AndroidPaints  // shorthand

    p.tutorialTextPaint = Paint().apply {
        color = if (darkMode) backgroundColor.toArgb() else AndroidColor.BLACK
        textSize = resources.getDimensionPixelSize(R.dimen.tutorial_text_size).toFloat()
        style = Paint.Style.FILL
    }

    p.tutorialStrokePaint = Paint().apply {
        color = effectColor.toArgb()
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = STROKE_WIDTH
    }

    p.debugTextPaint = Paint().apply {
        textSize = 40f
        color = AndroidColor.BLACK
        style = Paint.Style.FILL
    }

    p.backgroundPaint = Paint().apply {
        color = if (darkMode) AndroidColor.BLACK else backgroundColor.toArgb()
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = STROKE_WIDTH
    }

    p.effectPaint = Paint().apply {
        color = effectColor.toArgb()
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = STROKE_WIDTH
    }

    p.goalPaint = Paint().apply {
        color = goalColor.toArgb()
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = STROKE_WIDTH
    }

    p.textPaint = Paint().apply {
        color = if (darkMode) backgroundColor.toArgb() else AndroidColor.BLACK
        textSize = scoreFontSize
        style = Paint.Style.FILL
    }

    p.alwaysBlackTextPaint = Paint().apply {
        color = AndroidColor.BLACK
        textSize = scoreFontSize
        style = Paint.Style.FILL
    }

    p.lowBallFillPaint = Paint().apply {
        color = lowBallFill.toArgb()
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = Settings.strokeWidth
    }

    p.lowBallStrokePaint = Paint().apply {
        color = lowBallStroke.toArgb()
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = Settings.strokeWidth
    }

    p.highBallFillPaint = Paint().apply {
        color = highBallFill.toArgb()
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = Settings.strokeWidth
    }

    p.highBallStrokePaint = Paint().apply {
        color = highBallStroke.toArgb()
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = Settings.strokeWidth
    }

    p.wallPaint = Paint().apply {
        color = effectColor.toArgb()
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = STROKE_WIDTH
    }

    p.canScoreWallPaint = Paint().apply {
        color = canScoreWallColor.toArgb()
        isAntiAlias = true
        isDither = true
        style = Paint.Style.FILL
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = STROKE_WIDTH
    }

    p.scoreFlashPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    p.highPlayerHighlightPaint = Paint().apply {
        color = highBallFill.toArgb()
        alpha = 50
        style = Paint.Style.FILL
    }

    p.lowPlayerHighlightPaint = Paint().apply {
        color = lowBallFill.toArgb()
        alpha = 50
        style = Paint.Style.FILL
    }

    p.menuHintPaint = Paint().apply {
        color = AndroidColor.WHITE
        alpha = 60
        textSize = Settings.screenRatio * 1.2f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    p.chargeFillHighPaint = Paint().apply {
        style = Paint.Style.FILL
    }

    p.chargeFillLowPaint = Paint().apply {
        style = Paint.Style.FILL
    }
}

/**
 * Combined entry point — replaces the old [PaintBucket.initialize(Resources)] call.
 * Loads R.color values, computes size-dependent values, then builds all Paint objects.
 */
fun PaintBucket.initialize(resources: Resources) {
    initializeColors(resources)
    initialize(Settings.screenRatio)
    buildPaints(resources)
}
