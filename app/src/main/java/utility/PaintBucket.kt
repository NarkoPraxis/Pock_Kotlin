package utility

import android.content.res.Resources
import android.graphics.Color
import android.graphics.Paint
import androidx.core.content.res.ResourcesCompat
import com.example.puck.R
import gameobjects.Settings

object PaintBucket {
    var highBallStrokeColor = 0
    var highBallColor = 0
    var lowBallStrokeColor = 0
    var lowBallColor = 0
    var backgroundColor = 0
    var effectColor = 0
    var black = 0
    var bonusColor = 0
    var goalColor = 0

    lateinit var resources: Resources

    lateinit var debugTextPaint: Paint
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
    var canScoreWallColor = 0
    lateinit var tutorialTextPaint: Paint
    lateinit var tutorialStrokePaint: Paint
    lateinit var scoreFlashPaint: Paint
    lateinit var highPlayerHighlightPaint: Paint
    lateinit var lowPlayerHighlightPaint: Paint
    lateinit var menuHintPaint: Paint
    lateinit var rulesTextPaint: Paint
    lateinit var chargeFillHighPaint: Paint
    lateinit var chargeFillLowPaint: Paint

    // Plan 03: shared paint for locked-ball silhouette placeholder — always dark grey, no initialization needed
    val placeholderPaint: Paint = Paint().apply {
        color = Color.argb(200, 30, 30, 30)
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    var STROKE_WIDTH = 0f

    fun initialize(resources: Resources) {
        this.resources = resources
        black = ResourcesCompat.getColor(resources, R.color.black, null)
        bonusColor = ResourcesCompat.getColor(resources, R.color.bonus, null)
        highBallColor = ResourcesCompat.getColor(resources, R.color.highPlayerLight, null)
        highBallStrokeColor = ResourcesCompat.getColor(resources, R.color.highPlayerDark, null)
        lowBallColor = ResourcesCompat.getColor(resources, R.color.lowPlayerLight, null)
        lowBallStrokeColor = ResourcesCompat.getColor(resources, R.color.lowPlayerDark, null)
        goalColor = ResourcesCompat.getColor(resources, R.color.goalColor, null)
        backgroundColor = ResourcesCompat.getColor(resources, R.color.background, null)
        effectColor = ResourcesCompat.getColor(resources, R.color.effectColor, null)

        val goalHsv = FloatArray(3)
        Color.colorToHSV(goalColor, goalHsv)
        goalHsv[1] = (goalHsv[1] * 2f).coerceAtMost(1f)
        canScoreWallColor = Color.HSVToColor(goalHsv)


        STROKE_WIDTH = Settings.screenRatio / 12f


        tutorialTextPaint = Paint().apply{
            color = if (Storage.darkMode) backgroundColor else Color.BLACK
            textSize = resources.getDimensionPixelSize(R.dimen.tutorial_text_size).toFloat()
            style = Paint.Style.FILL
        }

        tutorialStrokePaint = Paint().apply {
            color = effectColor
            isAntiAlias = true
            isDither = true
            style = Paint.Style.FILL
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = STROKE_WIDTH
        }

        debugTextPaint = Paint().apply {
            textSize = 40f
            color = black
            style = Paint.Style.FILL
        }

        backgroundPaint = Paint().apply {
            color = if (Storage.darkMode) Color.BLACK else backgroundColor
            isAntiAlias = true
            isDither = true
            style = Paint.Style.FILL
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = STROKE_WIDTH
        }

        effectPaint = Paint().apply {
            color = effectColor
            isAntiAlias = true
            isDither = true
            style = Paint.Style.FILL
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = STROKE_WIDTH
        }

        goalPaint = Paint().apply {
            color = goalColor
            isAntiAlias = true
            isDither = true
            style = Paint.Style.FILL
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = STROKE_WIDTH
        }

        textPaint = Paint().apply {
            color = if (Storage.darkMode) backgroundColor else Color.BLACK
            textSize = 120f
            style = Paint.Style.FILL
        }

        alwaysBlackTextPaint = Paint().apply {
            color = black
            textSize = 120f
            style = Paint.Style.FILL
        }

        lowBallFillPaint = Paint().apply {
            color = lowBallColor
            isAntiAlias = true
            isDither = true
            style = Paint.Style.FILL
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = Settings.strokeWidth
        }

        lowBallStrokePaint = Paint().apply {
            color = lowBallStrokeColor
            isAntiAlias = true
            isDither = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = Settings.strokeWidth
        }

        highBallFillPaint = Paint().apply {
            color = highBallColor
            isAntiAlias = true
            isDither = true
            style = Paint.Style.FILL
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = Settings.strokeWidth
        }

        highBallStrokePaint = Paint().apply {
            color = highBallStrokeColor
            isAntiAlias = true
            isDither = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = Settings.strokeWidth
        }

        wallPaint = Paint().apply {
            color = effectColor
            isAntiAlias = true
            isDither = true
            style = Paint.Style.FILL
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = STROKE_WIDTH
        }

        canScoreWallPaint = Paint().apply {
            color = canScoreWallColor
            isAntiAlias = true
            isDither = true
            style = Paint.Style.FILL
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = STROKE_WIDTH
        }

        scoreFlashPaint = Paint().apply {
            style = Paint.Style.FILL
        }

        highPlayerHighlightPaint = Paint().apply {
            color = highBallColor
            alpha = 50
            style = Paint.Style.FILL
        }

        lowPlayerHighlightPaint = Paint().apply {
            color = lowBallColor
            alpha = 50
            style = Paint.Style.FILL
        }

        menuHintPaint = Paint().apply {
            color = Color.WHITE
            alpha = 60
            textSize = Settings.screenRatio * 1.2f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        rulesTextPaint = Paint().apply {
            color = if (Storage.darkMode) backgroundColor else Color.BLACK
            textSize = Settings.screenRatio * 1.4f
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        chargeFillHighPaint = Paint().apply {
            style = Paint.Style.FILL
        }
        chargeFillLowPaint = Paint().apply {
            style = Paint.Style.FILL
        }

    }

}