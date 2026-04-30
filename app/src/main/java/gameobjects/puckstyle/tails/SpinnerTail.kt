package gameobjects.puckstyle.tails

import android.graphics.Canvas
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import kotlin.math.cos
import kotlin.math.sin


class SpinnerTail(override val theme: ColorTheme, override val renderer: PuckRenderer) : TailRenderer {

    private data class Pos(var x: Float = 0f, var y: Float = 0f)

    private var history: MutableList<Pos>? = null
    private var tailRotation = 0f
    private val spinDir = if (theme.isWarm) -1f else 1f

    private val centerPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val tipPaint    = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    // Cached constant: tail length is fixed — tailLengthMultiplier never changes after setup
    private val tailLen = (40 * Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)

    // Cached constants: convert degrees to radians once
    private val piF        = Math.PI.toFloat()
    private val angleStep  = 18f * piF / 180f   // per-segment rotation step in radians
    private val degToRad   = piF / 180f

    override fun render(canvas: Canvas) {
        if (history == null || history!!.size != tailLen) history = MutableList(tailLen) { Pos(renderer.x, renderer.y) }
        val history = history!!

        tailRotation += 10f

        for (i in history.size - 1 downTo 0) {
            if (i - 1 >= 0) { history[i].x = history[i - 1].x; history[i].y = history[i - 1].y }
            else             { history[i].x = renderer.x;       history[i].y = renderer.y       }
        }

        val lineLen   = renderer.radius * 1.3f
        val halfLen   = lineLen / 2f
        val tipLen    = lineLen / 6f
        val sw        = renderer.strokePaint.strokeWidth

        val colors = resolvedColors()
        val color  = colors.primary
        val hilite = colors.secondary

        tipPaint.strokeWidth    = sw * 3f
        centerPaint.strokeWidth = sw * 2f

        val holdCount = 5
        // Hoist the fade denominator — constant for the entire render call
        val fadeDenom = (history.size - 1 - holdCount).coerceAtLeast(1).toFloat()
        // Hoist the base angle in radians once
        val baseAngRad = tailRotation * degToRad

        for (i in 0 until history.size) {
            val alpha = if (i < holdCount) {
                255
            } else {
                val fadeRatio = (i - holdCount).toFloat() / fadeDenom
                (255f * (1f - fadeRatio)).toInt()
            }
            if (alpha <= 0) continue

            val ang = (baseAngRad - i * angleStep) * spinDir
            val cx  = history[i].x
            val cy  = history[i].y
            val ca  = cos(ang)
            val sa  = sin(ang)
            val caHalf    = ca * halfLen
            val saHalf    = sa * halfLen
            val caTip     = ca * (halfLen - tipLen)
            val saTip     = sa * (halfLen - tipLen)

            tipPaint.color    = Palette.withAlpha(hilite, alpha)
            canvas.drawLine(cx - caHalf, cy - saHalf, cx - caTip, cy - saTip, tipPaint)
            canvas.drawLine(cx + caHalf, cy + saHalf, cx + caTip, cy + saTip, tipPaint)

            centerPaint.color = Palette.withAlpha(color, alpha)
            canvas.drawLine(cx - caHalf, cy - saHalf, cx + caHalf, cy + saHalf, centerPaint)
        }
    }

    override fun clear() { history = null; tailRotation = 0f }

    override fun fillTo(x: Float, y: Float) {
        history?.forEach { it.x = x; it.y = y }
    }
}
