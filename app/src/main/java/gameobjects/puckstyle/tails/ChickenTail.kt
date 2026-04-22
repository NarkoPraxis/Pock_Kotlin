package gameobjects.puckstyle.tails

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import gameobjects.Settings
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.TailRenderer
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class ChickenTail(override val theme: ColorTheme) : TailRenderer {

    // ---- Layer 1: footsteps ----
    private class Footprint(val x: Float, val y: Float, val angle: Float, var alpha: Int = 180)
    private val footprints = mutableListOf<Footprint>()
    private var stepTimer = 0
    private var stepSide = 1f

    // ---- Layer 2: feather particles ----
    private class Feather(
        var x: Float, var y: Float,
        val driftX: Float, val driftY: Float,
        var angle: Float, val spin: Float,
        var alpha: Int,
        val colorMix: Float
    )
    private val feathers = mutableListOf<Feather>()
    private var featherSpawnTimer = 0

    // ---- Movement tracking ----
    private var prevX = Float.NaN
    private var prevY = Float.NaN
    private var travelAngle = 0f

    private val paint    = Paint().apply { isAntiAlias = true }
    private val footPath = Path()

    override val zIndex: Int get() = -1

    override fun render(canvas: Canvas, renderer: PuckRenderer) {
        val r = renderer.radius

        var speed = 0f
        if (!prevX.isNaN()) {
            val dx = renderer.x - prevX
            val dy = renderer.y - prevY
            speed = hypot(dx, dy)
            if (speed > 0.001f) travelAngle = atan2(dy, dx)
        }
        prevX = renderer.x
        prevY = renderer.y

        // Layer 1: footsteps
        stepTimer++
        if (stepTimer >= 14 && speed > r * 0.01f) {
            stepTimer = 0
            val perpX = -sin(travelAngle) * r * 0.4f * stepSide
            val perpY =  cos(travelAngle) * r * 0.4f * stepSide
            footprints += Footprint(renderer.x + perpX, renderer.y + perpY, travelAngle)
            stepSide = -stepSide
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.13f
        paint.strokeCap = Paint.Cap.ROUND
        val fpIter = footprints.iterator()
        while (fpIter.hasNext()) {
            val f = fpIter.next()
            f.alpha -= (3f / Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)
            if (f.alpha <= 0) { fpIter.remove(); continue }
            paint.color = Palette.withAlpha(theme.secondary, f.alpha)
            drawFoot(canvas, f, r)
        }

        // Layer 2: feather particles
        featherSpawnTimer++
        if (featherSpawnTimer >= 2 && speed > r * 0.005f && feathers.size < (55 * Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)) {
            featherSpawnTimer = 0
            feathers += Feather(
                x = renderer.x + (Random.nextFloat() - 0.5f) * r * 2.0f,
                y = renderer.y + (Random.nextFloat() - 0.5f) * r * 2.0f,
                driftX = (Random.nextFloat() - 0.5f) * 0.8f,
                driftY = -Random.nextFloat() * 0.6f - 0.1f,
                angle = Random.nextFloat() * 360f,
                spin  = (Random.nextFloat() - 0.5f) * 4f,
                alpha = 220,
                colorMix = Random.nextFloat()
            )
        }
        val fw = r * 0.2f
        val fh = r * 0.55f
        val featherIter = feathers.iterator()
        while (featherIter.hasNext()) {
            val f = featherIter.next()
            f.x += f.driftX
            f.y += f.driftY
            f.angle += f.spin
            f.alpha -= (5f / Settings.tailLengthMultiplier).toInt().coerceAtLeast(1)
            if (f.alpha <= 0) { featherIter.remove(); continue }
            val c = Palette.lerpColor(theme.primary, theme.secondary, f.colorMix)
            paint.style = Paint.Style.FILL
            paint.color = Palette.withAlpha(c, f.alpha)
            canvas.save()
            canvas.rotate(f.angle, f.x, f.y)
            canvas.drawOval(f.x - fw, f.y - fh, f.x + fw, f.y + fh, paint)
            // Quill stem along long axis
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = fw * 0.35f
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = Palette.withAlpha(theme.secondary, f.alpha)
            canvas.drawLine(f.x, f.y + fh * 1.3f, f.x, f.y - fh * 0.7f, paint)
            canvas.restore()
        }
    }

    private fun drawFoot(canvas: Canvas, f: Footprint, r: Float) {
        val toeLen = r * 0.55f
        val faceAngle = f.angle
        val deg35 = Math.toRadians(35.0).toFloat()
        val rearAngle = faceAngle + Math.PI.toFloat()

        // Single path for all toes — no alpha accumulation at the shared center point
        footPath.reset()
        for (offset in floatArrayOf(0f, deg35, -deg35)) {
            val ang = faceAngle + offset
            footPath.moveTo(f.x, f.y)
            footPath.lineTo(f.x + cos(ang) * toeLen, f.y + sin(ang) * toeLen)
        }
        footPath.moveTo(f.x, f.y)
        footPath.lineTo(f.x + cos(rearAngle) * toeLen * 0.6f, f.y + sin(rearAngle) * toeLen * 0.6f)
        canvas.drawPath(footPath, paint)
    }

    override fun clear() {
        footprints.clear()
        feathers.clear()
        prevX = Float.NaN
        prevY = Float.NaN
        stepTimer = 0
        featherSpawnTimer = 0
    }

    override fun fillTo(x: Float, y: Float) {
        prevX = x
        prevY = y
    }
}
