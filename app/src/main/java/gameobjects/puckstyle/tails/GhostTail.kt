package gameobjects.puckstyle.tails

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import gameobjects.puckstyle.skins.GhostSkin.Companion.radiusOffset

class GhostTail( override val renderer: PuckRenderer) : TailRenderer {

    // Parallel float arrays instead of a list of data-class objects — zero per-frame allocation.
    private var xs: FloatArray? = null
    private var ys: FloatArray? = null

    // Cached tail length so we don't recompute Settings.tailLengthMultiplier every frame.
    private var cachedGhostLen = -1
    private val baseCount = 30

    private val whitePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val glowPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.STROKE }

    override val zIndex: Int
        get() = 2

    override fun render(canvas: Canvas) {
        val ghostLen = (baseCount * Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)

        // Reallocate arrays only when length changes (rare).
        if (ghostLen != cachedGhostLen) {
            xs = FloatArray(ghostLen) { renderer.x }
            ys = FloatArray(ghostLen) { renderer.y }
            cachedGhostLen = ghostLen
        }
        val xs = xs!!
        val ys = ys!!

        val glowColor = responsivePrimary
        val radiusOffset = radiusOffset(renderer)
        val r = renderer.radius
        val sw = renderer.strokePaint.strokeWidth * 1.2f
        val lastIdx = ghostLen - 1

        // Single shift pass: oldest entry (index 0) is dropped, newest puck position goes to index 0.
        for (i in lastIdx downTo 1) {
            xs[i] = xs[i - 1]
            ys[i] = ys[i - 1]
        }
        xs[0] = renderer.x
        ys[0] = renderer.y

        // Pass 1 — glow rings (drawn first so white fill sits on top)
        glowPaint.strokeWidth = sw
        for (i in lastIdx downTo 0) {
            val ratio = i.toFloat() / lastIdx.coerceAtLeast(1)
            val size = r - Settings.strokeWidth - r * (i.coerceAtLeast(1).toFloat() / lastIdx)
            val alpha = (255f * (1f - ratio)).toInt()
            glowPaint.color = Palette.withAlpha(glowColor, (alpha * 0.45f).toInt())
            canvas.drawCircle(xs[i], ys[i], size * 1.15f * radiusOffset, glowPaint)
        }

        // Pass 2 — white fill discs
        for (i in lastIdx downTo 0) {
            val ratio = i.toFloat() / lastIdx.coerceAtLeast(1)
            val size = r * 1.1f - Settings.strokeWidth - r * (i.coerceAtLeast(1).toFloat() / lastIdx)
            val alpha = (255f * (1f - ratio)).toInt()
            whitePaint.color = Color.argb((alpha * 0.75f).toInt(), 255, 255, 255)
            canvas.drawCircle(xs[i], ys[i], size * radiusOffset, whitePaint)
        }
    }

    override fun clear() {
        xs = null
        ys = null
        cachedGhostLen = -1
    }

    override fun fillTo(x: Float, y: Float) {
        xs?.fill(x)
        ys?.fill(y)
    }
}
