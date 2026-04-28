package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import gameobjects.puckstyle.ColorGroup
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import androidx.core.graphics.withTranslation
import androidx.core.graphics.withRotation

class ChickenSkin(override val theme: ColorTheme, override val renderer: PuckRenderer) : PuckSkin {

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

    private data class Keyframe(val trigger: Int, val animation: (canvas: Canvas) -> Unit)

    private val celebrationKeyFrames = listOf<Keyframe>(
        Keyframe(0, ::drawHappyEye),
        Keyframe(3, ::drawBeakOpen),
        Keyframe(10, ::drawWingsFlared),
        Keyframe(10, ::drawHeadFeathersFlared)
    )

    private val almostHitKeyFrames = listOf<Keyframe>(
        Keyframe(0, ::drawWideEye),
        Keyframe(3, ::drawBeakOpen),
        Keyframe(10, ::drawWingsTucked),
        Keyframe(10, ::drawHeadFeathersDroopy)
    )

    private val justHitKeyFrames = listOf<Keyframe>(
        Keyframe(0, ::drawWinceEye),
        Keyframe(3, ::drawBeakGrimace),
        Keyframe(10, ::drawWingsTucked),
        Keyframe(10, ::drawHeadFeathersDroopy)
    )

    // Updated once per drawBody call; used by private sub-draw methods
    private var frameColors: ColorGroup = theme.main

    override fun drawBody(canvas: Canvas) {
        frameColors = resolvedColors()
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

        canvas.withTranslation(renderer.x, renderer.y) {
            if (renderer.isHigh) rotate(180f)

            // 1. Body fill
            paint.style = Paint.Style.FILL
            paint.color = frameColors.primary
            drawCircle(0f, 0f, r, paint)

            // 2. Secondary stroke highlight
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = sw
            paint.color = frameColors.secondary
            drawCircle(0f, 0f, r, paint)

            // 3. Wings
            drawWing(this, r, wingAngle, left = true)
            drawWing(this, r, wingAngle, left = false)

            // 3b. Head feathers — drawn after wings so they sit on top of body highlight
            drawHeadFeather(this, r, 0f, -r * 0.88f, 0f, 1.5f)
            drawHeadFeather(this, r, -r * 0.2f, -r * 0.85f, -28f, 1.1f)
            drawHeadFeather(this, r, r * 0.2f, -r * 0.85f, 28f, 1.1f)

            // 4. Eyes — ~1/3 larger than previous iteration
            val eyeR = r * 0.25f
            val eyeX = r * 0.30f
            val eyeY = -r * 0.28f

            drawEye(canvas,eyeOpen, eyeX, eyeY, eyeR, r)

            // 5. Beak
            beakPath.reset()
            beakPath.moveTo(-r * 0.24f, r * 0.14f)
            beakPath.lineTo(r * 0.24f, r * 0.14f)
            beakPath.lineTo(0f, r * 0.58f)
            beakPath.close()
            paint.style = Paint.Style.FILL
            paint.strokeCap = Paint.Cap.BUTT
            paint.color = frameColors.secondary
            drawPath(beakPath, paint)

        }
    }

    // draws normal eye. UPDATE: Should look like the eye is tracking the paddle
    private fun drawEye(canvas: Canvas, eyeOpen: Boolean, eyeX: Float, eyeY: Float, eyeR: Float, r: Float
    ) {
        if (eyeOpen) {
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawCircle(-eyeX, eyeY, eyeR, paint)
            canvas.drawCircle(eyeX, eyeY, eyeR, paint)
            paint.color = frameColors.secondary
            canvas.drawCircle(-eyeX, eyeY, eyeR * 0.72f, paint)
            canvas.drawCircle(eyeX, eyeY, eyeR * 0.72f, paint)
            paint.color = Color.BLACK
            canvas.drawCircle(-eyeX, eyeY, eyeR * 0.4f, paint)
            canvas.drawCircle(eyeX, eyeY, eyeR * 0.4f, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(-eyeX - eyeR * 0.27f, eyeY - eyeR * 0.3f, eyeR * 0.21f, paint)
            canvas.drawCircle(eyeX - eyeR * 0.27f, eyeY - eyeR * 0.3f, eyeR * 0.21f, paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = r * 0.08f
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = frameColors.secondary
            canvas.drawLine(-eyeX - eyeR * 0.65f, eyeY, -eyeX + eyeR * 0.65f, eyeY, paint)
            canvas.drawLine(eyeX - eyeR * 0.65f, eyeY, eyeX + eyeR * 0.65f, eyeY, paint)
        }
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
        paint.color = frameColors.secondary
        canvas.drawPath(wingSecondary, paint)
        // Primary inner shape — base extends below attachment to merge into body
        wingPrimary.reset()
        wingPrimary.moveTo(attachX - fw * 0.42f, attachY + fh * 0.18f)
        wingPrimary.quadTo(attachX - fw * 0.75f, attachY - fh * 0.48f, attachX,         attachY - fh * 0.88f)
        wingPrimary.quadTo(attachX + fw * 0.75f, attachY - fh * 0.48f, attachX + fw * 0.42f, attachY + fh * 0.18f)
        wingPrimary.close()
        paint.color = frameColors.primary
        canvas.drawPath(wingPrimary, paint)
        canvas.restore()
    }

    private fun drawWing(canvas: Canvas, r: Float, wingAngle: Float, left: Boolean) {
        val s     = if (left) -1f else 1f
        val pivot = s * r * 0.72f

        canvas.withRotation(s * -wingAngle, pivot, 0f) {
            paint.style = Paint.Style.FILL

            // Secondary — outer/larger shape, provides visible outline at tip and edges
            wingSecondary.reset()
            wingSecondary.moveTo(s * r * 0.72f, -r * 0.13f)
            wingSecondary.quadTo(
                s * r * 1.22f,
                -r * 0.55f,
                s * r * 1.65f,
                -r * 0.27f
            )  // upper → tip
            wingSecondary.quadTo(s * r * 1.58f, r * 0.06f, s * r * 1.22f, r * 0.33f)  // tip → lower
            wingSecondary.quadTo(
                s * r * 0.92f,
                r * 0.38f,
                s * r * 0.72f,
                r * 0.13f
            )  // lower → attachment
            wingSecondary.close()
            paint.color = frameColors.secondary
            drawPath(wingSecondary, paint)

            // Primary — smaller inner shape; attachment pulled into body to merge with body fill
            wingPrimary.reset()
            wingPrimary.moveTo(s * r * 0.50f, -r * 0.10f)
            wingPrimary.quadTo(
                s * r * 1.18f,
                -r * 0.48f,
                s * r * 1.57f,
                -r * 0.27f
            )   // upper → tip
            wingPrimary.quadTo(s * r * 1.50f, r * 0.04f, s * r * 1.16f, r * 0.27f)   // tip → lower
            wingPrimary.quadTo(
                s * r * 0.90f,
                r * 0.33f,
                s * r * 0.50f,
                r * 0.10f
            )   // lower → inner attachment
            wingPrimary.close()
            paint.color = frameColors.primary
            drawPath(wingPrimary, paint)

        }
    }

    // cheeky taunt
    private fun drawWinkingEye(canvas: Canvas) {

    }

    // scared reaction to almost being hit
    private fun drawWideEye(canvas: Canvas) {

    }

    // reaction to being hit
    private fun drawWinceEye (canvas: Canvas) {

    }

    // celebration on score and victory
    private fun drawHappyEye (canvas: Canvas) {

    }

    // to show anticipation or excitement
    private fun drawBeakOpen( canvas: Canvas) {

    }

    // to show pain, like squeezing lips together
    private fun drawBeakGrimace( canvas: Canvas) {

    }

    // to celebrate and show off
    private fun drawWingsFlared(canvas: Canvas) {

    }

    // to show pain or reaction to pain
    private fun drawWingsTucked(canvas: Canvas) {

    }

    // to celebrate
    private fun drawHeadFeathersFlared(canvas: Canvas) {

    }

    // to show pain or anticipation to pain
    private fun drawHeadFeathersDroopy(canvas: Canvas) {

    }

}
