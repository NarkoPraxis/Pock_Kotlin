package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class ChickenSkin(override val theme: ColorTheme) : PuckSkin {

    private val paint         = Paint().apply { isAntiAlias = true }
    private val beakPath      = Path()
    private val wingSecondary = Path()
    private val wingPrimary   = Path()

    private var wingPhase = 0f
    private var lastX = Float.NaN
    private var lastY = Float.NaN

    private var blinkCountdown = Random.nextInt(60, 181)
    private var blinkFrame = 0
    private val BLINK_DURATION = 4

    override fun drawBody(canvas: Canvas, renderer: PuckRenderer) {
        val r  = renderer.radius
        val sw = renderer.strokePaint.strokeWidth

        if (lastX.isNaN()) { lastX = renderer.x; lastY = renderer.y }
        val speed = hypot(renderer.x - lastX, renderer.y - lastY)
        lastX = renderer.x
        lastY = renderer.y
        wingPhase += speed * 0.012f
        val wingAngle = sin(wingPhase) * 22f

        blinkCountdown--
        if (blinkCountdown <= 0) {
            blinkFrame = BLINK_DURATION
            blinkCountdown = Random.nextInt(60, 181)
        }
        val eyeOpen = blinkFrame == 0
        if (blinkFrame > 0) blinkFrame--

        canvas.save()
        canvas.translate(renderer.x, renderer.y)
        if (renderer.isHigh) canvas.rotate(180f)

        // 1. Body fill
        paint.style = Paint.Style.FILL
        paint.color = theme.primary
        canvas.drawCircle(0f, 0f, r, paint)

        // 2. Secondary stroke highlight
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = sw
        paint.color = theme.secondary
        canvas.drawCircle(0f, 0f, r, paint)

        // 3. Wings
        drawWing(canvas, r, wingAngle, left = true)
        drawWing(canvas, r, wingAngle, left = false)

        // 3b. Head feathers — drawn after wings so they sit on top of body highlight
        drawHeadFeather(canvas, r,  0f,          -r * 0.88f,   0f, 1.5f)
        drawHeadFeather(canvas, r, -r * 0.2f,   -r * 0.85f, -28f, 1.1f)
        drawHeadFeather(canvas, r,  r * 0.2f,   -r * 0.85f,  28f, 1.1f)


        // 4. Eyes — ~1/3 larger than previous iteration
        val eyeR = r * 0.25f
        val eyeX = r * 0.30f
        val eyeY = -r * 0.28f

        if (eyeOpen) {
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawCircle(-eyeX, eyeY, eyeR, paint)
            canvas.drawCircle( eyeX, eyeY, eyeR, paint)
            paint.color = theme.secondary
            canvas.drawCircle(-eyeX, eyeY, eyeR * 0.72f, paint)
            canvas.drawCircle( eyeX, eyeY, eyeR * 0.72f, paint)
            paint.color = Color.BLACK
            canvas.drawCircle(-eyeX, eyeY, eyeR * 0.4f, paint)
            canvas.drawCircle( eyeX, eyeY, eyeR * 0.4f, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(-eyeX - eyeR * 0.27f, eyeY - eyeR * 0.3f, eyeR * 0.21f, paint)
            canvas.drawCircle( eyeX - eyeR * 0.27f, eyeY - eyeR * 0.3f, eyeR * 0.21f, paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = r * 0.08f
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = theme.secondary
            canvas.drawLine(-eyeX - eyeR * 0.65f, eyeY, -eyeX + eyeR * 0.65f, eyeY, paint)
            canvas.drawLine( eyeX - eyeR * 0.65f, eyeY,  eyeX + eyeR * 0.65f, eyeY, paint)
        }

        // 5. Beak — ~1/3 larger than previous iteration
        beakPath.reset()
        beakPath.moveTo(-r * 0.24f, r * 0.14f)
        beakPath.lineTo( r * 0.24f, r * 0.14f)
        beakPath.lineTo( 0f,        r * 0.58f)
        beakPath.close()
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
        paint.color = theme.secondary
        canvas.drawPath(beakPath, paint)

        canvas.restore()
    }

    private fun drawHeadFeather(canvas: Canvas, r: Float, attachX: Float, attachY: Float, rotDeg: Float, scale: Float) {
        val fw = r * 0.18f * scale
        val fh = r * 0.46f * scale
        canvas.save()
        canvas.rotate(rotDeg, attachX, attachY)
        // Secondary outer shape
        wingSecondary.reset()
        wingSecondary.moveTo(attachX - fw * 0.7f,  attachY)
        wingSecondary.quadTo(attachX - fw,          attachY - fh * 0.55f, attachX,         attachY - fh)
        wingSecondary.quadTo(attachX + fw,          attachY - fh * 0.55f, attachX + fw * 0.7f, attachY)
        wingSecondary.close()
        paint.style = Paint.Style.FILL
        paint.color = theme.secondary
        canvas.drawPath(wingSecondary, paint)
        // Primary inner shape — base extends below attachment to merge into body
        wingPrimary.reset()
        wingPrimary.moveTo(attachX - fw * 0.42f, attachY + fh * 0.18f)
        wingPrimary.quadTo(attachX - fw * 0.75f, attachY - fh * 0.48f, attachX,         attachY - fh * 0.88f)
        wingPrimary.quadTo(attachX + fw * 0.75f, attachY - fh * 0.48f, attachX + fw * 0.42f, attachY + fh * 0.18f)
        wingPrimary.close()
        paint.color = theme.primary
        canvas.drawPath(wingPrimary, paint)
        canvas.restore()
    }

    private fun drawWing(canvas: Canvas, r: Float, wingAngle: Float, left: Boolean) {
        val s     = if (left) -1f else 1f
        val pivot = s * r * 0.72f

        canvas.save()
        canvas.rotate(s * -wingAngle, pivot, 0f)

        paint.style = Paint.Style.FILL

        // Secondary — outer/larger shape, provides visible outline at tip and edges
        wingSecondary.reset()
        wingSecondary.moveTo(s * r * 0.72f, -r * 0.13f)
        wingSecondary.quadTo(s * r * 1.22f, -r * 0.55f, s * r * 1.65f, -r * 0.27f)  // upper → tip
        wingSecondary.quadTo(s * r * 1.58f,  r * 0.06f, s * r * 1.22f,  r * 0.33f)  // tip → lower
        wingSecondary.quadTo(s * r * 0.92f,  r * 0.38f, s * r * 0.72f,  r * 0.13f)  // lower → attachment
        wingSecondary.close()
        paint.color = theme.secondary
        canvas.drawPath(wingSecondary, paint)

        // Primary — smaller inner shape; attachment pulled into body to merge with body fill
        wingPrimary.reset()
        wingPrimary.moveTo(s * r * 0.50f, -r * 0.10f)
        wingPrimary.quadTo(s * r * 1.18f, -r * 0.48f, s * r * 1.57f, -r * 0.27f)   // upper → tip
        wingPrimary.quadTo(s * r * 1.50f,  r * 0.04f, s * r * 1.16f,  r * 0.27f)   // tip → lower
        wingPrimary.quadTo(s * r * 0.90f,  r * 0.33f, s * r * 0.50f,  r * 0.10f)   // lower → inner attachment
        wingPrimary.close()
        paint.color = theme.primary
        canvas.drawPath(wingPrimary, paint)

        canvas.restore()
    }
}
