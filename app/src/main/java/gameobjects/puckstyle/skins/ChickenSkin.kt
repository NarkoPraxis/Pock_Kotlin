package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorGroup
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import gameobjects.puckstyle.paddles.ChickenLaunch
import physics.Point
import gameobjects.puckstyle.paddles.ChickenLaunch.Companion.spawnFeatherExplosion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import gameobjects.puckstyle.Palette
import utility.Effects

class ChickenSkin( override val renderer: PuckRenderer) : PuckSkin {

    private val paint         = Paint().apply { isAntiAlias = true }
    private val beakPath      = Path()
    private val wingSecondary = Path()
    private val wingPrimary   = Path()
    private val mouthRect     = RectF()
    // Avoids List allocation inside eye-drawing loops
    private val eyeSides      = floatArrayOf(0f, 0f)

    // Cached radius-derived values
    private var cachedRadius = -1f
    private var cachedStrokeWidth = 0f
    private var cachedEyeR   = 0f
    private var cachedEyeX   = 0f
    private var cachedEyeY   = 0f
    private var cachedBeakTopY = 0f

    private fun ensureCache() {
        val newR = renderer.radius
        if (cachedRadius != newR) {
            cachedRadius    = newR
            cachedStrokeWidth = renderer.strokePaint.strokeWidth
            cachedEyeR      = newR * 0.25f
            cachedEyeX      = newR * 0.30f
            cachedEyeY      = -newR * 0.28f
            cachedBeakTopY  = newR * 0.14f
        }
    }

    // --- wing flap ---
    private var wingPhase = 0f
    private var displayedWingAngle = 0f
    private var lastX = Float.NaN
    private var lastY = Float.NaN

    // --- blink ---
    private var blinkCountdown = Random.nextInt(60, 181)
    private var blinkFrame = 0
    private val BLINK_DURATION = 4

    // --- animation state machine ---
    private enum class ChickenAnim { Default, AlmostHit, JustHit, Celebration, Taunt }

    private var currentAnim = ChickenAnim.Default
    private var animFrame   = 0
    private var animLoop    = false
    private var dangerFromSweetSpot = false
    private var lastPhase   = ChargePhase.Idle
    private var winkRight   = true

    // Threat coords in world space; NaN when no active danger
    private var threatX = Float.NaN
    private var threatY = Float.NaN

    // Animation durations (frames)
    private val ANIM_ALMOST_HIT   = 35
    private val ANIM_JUST_HIT     = 30
    private val ANIM_CELEBRATION  = 50
    private val ANIM_TAUNT        = 40

    // Body-part stagger offsets within animations
    private val DELAY_BEAK    = 3
    private val DELAY_WINGS   = 10
    private val DELAY_FEATHERS = 10

    // Per-frame state hoisted from drawBody so sub-draw methods can read them
    private var frameColors: ColorGroup = theme.main
    private var r       = 0f
    private var eyeR    = 0f
    private var eyeX    = 0f
    private var eyeY    = 0f
    private var beakTopY = 0f
    private var irisOffX = 0f
    private var irisOffY = 0f
    private var wingAngle = 0f
    private var eyeOpen   = true

    override fun drawBody(canvas: Canvas) {
        ensureCache()
        frameColors = responsiveGroup
        r        = cachedRadius
        val sw   = cachedStrokeWidth
        eyeR     = cachedEyeR
        eyeX     = cachedEyeX
        eyeY     = cachedEyeY
        beakTopY = cachedBeakTopY

        // Movement tracking for wing flap
        if (lastX.isNaN()) { lastX = renderer.x; lastY = renderer.y }
        val speed = hypot(renderer.x - lastX, renderer.y - lastY)
        lastX = renderer.x; lastY = renderer.y
        wingPhase += speed * 0.012f
        wingAngle = sin(wingPhase) * 22f
        val targetWingAngle = when (currentAnim) {
            ChickenAnim.AlmostHit, ChickenAnim.JustHit -> 0f
            ChickenAnim.Celebration -> 22f
            else -> wingAngle
        }
        displayedWingAngle = lerp(displayedWingAngle, targetWingAngle, 0.18f)

        // Blink
        blinkCountdown--
        if (blinkCountdown <= 0) {
            blinkFrame = BLINK_DURATION
            blinkCountdown = Random.nextInt(60, 181)
        }
        eyeOpen = blinkFrame == 0
        if (blinkFrame > 0) blinkFrame--

        // Iris direction — tracks paddle by default; tracks threat during proximity AlmostHit
        computeIrisOffset()

        // Advance animation frame and handle expiry
        if (currentAnim != ChickenAnim.Default) {
            animFrame++
            val duration = when (currentAnim) {
                ChickenAnim.AlmostHit   -> ANIM_ALMOST_HIT
                ChickenAnim.JustHit     -> ANIM_JUST_HIT
                ChickenAnim.Celebration -> ANIM_CELEBRATION
                ChickenAnim.Taunt       -> ANIM_TAUNT
                else                    -> 0
            }
            if (animFrame >= duration) {
                if (animLoop && currentAnim == ChickenAnim.Celebration && Settings.gameOver) {
                    animFrame = 0
                } else {
                    currentAnim = ChickenAnim.Default
                    animFrame   = 0
                    animLoop    = false
                }
            }
        }

        canvas.withTranslation(renderer.x, renderer.y) {
            if (renderer.isHigh) rotate(180f)

            // Body fill
            paint.style = Paint.Style.FILL
            paint.color = frameColors.primary
            drawCircle(0f, 0f, r, paint)

            // Secondary stroke highlight
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = sw
            paint.color = frameColors.secondary
            drawCircle(0f, 0f, r, paint)

            drawWingsForState(this)
            drawFeathersForState(this)
            drawEyesForState(this)
            drawBeakForState(this)
        }
    }

    // ── iris direction ─────────────────────────────────────────────────────────

    private fun computeIrisOffset() {
        val useThreat = currentAnim == ChickenAnim.AlmostHit &&
            !dangerFromSweetSpot && !threatX.isNaN() && !threatY.isNaN()

        val wx: Float
        val wy: Float
        if (useThreat) {
            wx = threatX - renderer.x
            wy = threatY - renderer.y
        } else {
            val paddle = renderer.effect as? ChickenLaunch
            if (paddle != null) {
                wx = paddle.exposedPaddleX - renderer.x
                wy = paddle.exposedPaddleY - renderer.y
            } else {
                wx = 0f; wy = r
            }
        }

        val dist = hypot(wx, wy).coerceAtLeast(0.001f)
        val nX = wx / dist; val nY = wy / dist
        irisOffX = if (renderer.isHigh) -nX else nX
        irisOffY = if (renderer.isHigh) -nY else nY
    }

    // ── state dispatch ─────────────────────────────────────────────────────────

    private fun drawWingsForState(canvas: Canvas) {
        when (currentAnim) {
            ChickenAnim.AlmostHit, ChickenAnim.JustHit -> {
                val t = if (animFrame >= DELAY_WINGS) easeIn((animFrame - DELAY_WINGS).toFloat(), 3f) else 0f
                drawWing(canvas, left = true,  angle = displayedWingAngle, scale = lerp(1f, 0.82f, t))
                drawWing(canvas, left = false, angle = displayedWingAngle, scale = lerp(1f, 0.82f, t))
            }
            ChickenAnim.Celebration -> {
                val t = if (animFrame >= DELAY_WINGS) easeIn((animFrame - DELAY_WINGS).toFloat(), 8f) else 0f
                drawWing(canvas, left = true,  angle = displayedWingAngle, scale = lerp(1f, 1.1f, t))
                drawWing(canvas, left = false, angle = displayedWingAngle, scale = lerp(1f, 1.1f, t))
            }
            else -> {
                drawWing(canvas, left = true,  angle = displayedWingAngle)
                drawWing(canvas, left = false, angle = displayedWingAngle)
            }
        }
    }

    private fun drawFeathersForState(canvas: Canvas) {
        val t = when {
            currentAnim == ChickenAnim.AlmostHit || currentAnim == ChickenAnim.JustHit ->
                if (animFrame >= DELAY_FEATHERS) easeIn((animFrame - DELAY_FEATHERS).toFloat(), 8f) else 0f
            currentAnim == ChickenAnim.Celebration ->
                if (animFrame >= DELAY_FEATHERS) easeIn((animFrame - DELAY_FEATHERS).toFloat(), 8f) else 0f
            else -> -1f
        }

        if (t < 0f) {
            drawHeadFeather(canvas, 0f,        -r * 0.88f, 0f,   1.5f)
            drawHeadFeather(canvas, -r * 0.2f, -r * 0.85f, -28f, 1.1f)
            drawHeadFeather(canvas,  r * 0.2f, -r * 0.85f,  28f, 1.1f)
            return
        }

        val droopy = currentAnim == ChickenAnim.AlmostHit || currentAnim == ChickenAnim.JustHit
        if (droopy) {
            drawHeadFeatherDroopy(canvas, 0f,        -r * 0.88f, 0f,   1.5f, t)
            drawHeadFeatherDroopy(canvas, -r * 0.2f, -r * 0.85f, -28f, 1.1f, t)
            drawHeadFeatherDroopy(canvas,  r * 0.2f, -r * 0.85f,  28f, 1.1f, t)
        } else {
            drawHeadFeatherFlared(canvas, 0f,        -r * 0.88f, 0f,   1.5f, t)
            drawHeadFeatherFlared(canvas, -r * 0.2f, -r * 0.85f, -28f, 1.1f, t)
            drawHeadFeatherFlared(canvas,  r * 0.2f, -r * 0.85f,  28f, 1.1f, t)
        }
    }

    private fun drawEyesForState(canvas: Canvas) {
        when (currentAnim) {
            ChickenAnim.Taunt       -> drawWinkingEye(canvas)
            ChickenAnim.AlmostHit   -> drawWideEye(canvas)
            ChickenAnim.JustHit     -> drawWinceEye(canvas)
            ChickenAnim.Celebration -> drawHappyEye(canvas)
            else                    -> drawEye(canvas, eyeOpen)
        }
    }

    private fun drawBeakForState(canvas: Canvas) {
        when {
            currentAnim == ChickenAnim.AlmostHit   && animFrame >= DELAY_BEAK -> drawBeakSnap(canvas)
            currentAnim == ChickenAnim.JustHit                                -> drawBeakGrimace(canvas)
            currentAnim == ChickenAnim.Celebration && animFrame >= DELAY_BEAK -> drawBeakGape(canvas)
            currentAnim == ChickenAnim.Taunt       && animFrame >= DELAY_BEAK -> drawBeakYawn(canvas)
            else                                                               -> drawBeak(canvas)
        }
    }

    // ── eyes ───────────────────────────────────────────────────────────────────

    private fun drawEye(canvas: Canvas, open: Boolean) {
        if (open) {
            val maxOff = eyeR * 0.45f
            eyeSides[0] = -eyeX; eyeSides[1] = eyeX
            for (side in eyeSides) {
                val cx = side + irisOffX * maxOff
                val cy = eyeY + irisOffY * maxOff
                paint.style = Paint.Style.FILL
                paint.color = Color.WHITE
                canvas.drawCircle(side, eyeY, eyeR, paint)
                paint.color = frameColors.secondary
                canvas.drawCircle(cx, cy, eyeR * 0.72f, paint)
                paint.color = Color.BLACK
                canvas.drawCircle(cx, cy, eyeR * 0.4f, paint)
                paint.color = Color.WHITE
                canvas.drawCircle(cx - eyeR * 0.27f, cy - eyeR * 0.3f, eyeR * 0.21f, paint)
            }
        } else {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = r * 0.08f
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = frameColors.secondary
            canvas.drawLine(-eyeX - eyeR * 0.65f, eyeY, -eyeX + eyeR * 0.65f, eyeY, paint)
            canvas.drawLine( eyeX - eyeR * 0.65f, eyeY,  eyeX + eyeR * 0.65f, eyeY, paint)
        }
    }

    private fun drawWinkingEye(canvas: Canvas) {
        val maxOff    = eyeR * 0.45f
        val openSide  = if (winkRight)  eyeX else -eyeX
        val closedSide = if (winkRight) -eyeX else  eyeX
        val cx = openSide + irisOffX * maxOff
        val cy = eyeY     + irisOffY * maxOff

        // Open eye
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(openSide, eyeY, eyeR, paint)
        paint.color = frameColors.secondary
        canvas.drawCircle(cx, cy, eyeR * 0.72f, paint)
        paint.color = Color.BLACK
        canvas.drawCircle(cx, cy, eyeR * 0.4f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(cx - eyeR * 0.27f, cy - eyeR * 0.3f, eyeR * 0.21f, paint)

        // Winking eye — shut for frames 0–25, reopens after
        if (animFrame <= 25) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = r * 0.08f
            paint.strokeCap = Paint.Cap.ROUND
            paint.color = frameColors.secondary
            canvas.drawLine(closedSide - eyeR * 0.65f, eyeY, closedSide + eyeR * 0.65f, eyeY, paint)
        } else {
            val cx2 = closedSide + irisOffX * maxOff
            val cy2 = eyeY       + irisOffY * maxOff
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawCircle(closedSide, eyeY, eyeR, paint)
            paint.color = frameColors.secondary
            canvas.drawCircle(cx2, cy2, eyeR * 0.72f, paint)
            paint.color = Color.BLACK
            canvas.drawCircle(cx2, cy2, eyeR * 0.4f, paint)
        }
    }

    private fun drawWideEye(canvas: Canvas) {
        val t = easeIn(animFrame.toFloat(), 8f)
        val scaleX = lerp(1f, 0.75f, t)
        val scaleY = lerp(1f, 1.35f, t)
        val maxOff  = eyeR * 0.45f
        eyeSides[0] = -eyeX; eyeSides[1] = eyeX
        for (side in eyeSides) {
            val cx = side + irisOffX * maxOff
            val cy = eyeY + irisOffY * maxOff
            canvas.save()
            canvas.scale(scaleX, scaleY, side, eyeY)
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawCircle(side, eyeY, eyeR, paint)
            paint.color = frameColors.secondary
            canvas.drawCircle(cx, cy, eyeR * 0.72f, paint)
            paint.color = Color.BLACK
            canvas.drawCircle(cx, cy, eyeR * 0.4f, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(cx - eyeR * 0.27f, cy - eyeR * 0.3f, eyeR * 0.21f, paint)
            canvas.restore()
        }
    }

    private fun drawWinceEye(canvas: Canvas) {
        val decay   = 1f - animFrame.toFloat() / ANIM_JUST_HIT
        val shudder = sin(animFrame * 1.5f) * r * 0.02f * decay
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.08f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = frameColors.secondary
        canvas.drawLine(-eyeX - eyeR * 0.65f, eyeY + shudder, -eyeX + eyeR * 0.65f, eyeY + shudder, paint)
        canvas.drawLine( eyeX - eyeR * 0.65f, eyeY + shudder,  eyeX + eyeR * 0.65f, eyeY + shudder, paint)
    }

    private fun drawHappyEye(canvas: Canvas) {
        val t = easeIn(animFrame.toFloat(), 8f)
        val scaleX = lerp(1f, 1.3f,  t)
        val scaleY = lerp(1f, 0.75f, t)
        val maxOff  = eyeR * 0.45f
        eyeSides[0] = -eyeX; eyeSides[1] = eyeX
        for (side in eyeSides) {
            val cx = side + irisOffX * maxOff
            val cy = eyeY + irisOffY * maxOff
            canvas.save()
            canvas.scale(scaleX, scaleY, side, eyeY)
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawCircle(side, eyeY, eyeR, paint)
            paint.color = frameColors.secondary
            canvas.drawCircle(cx, cy, eyeR * 0.72f, paint)
            paint.color = Color.BLACK
            canvas.drawCircle(cx, cy, eyeR * 0.4f, paint)
            paint.color = Color.WHITE
            canvas.drawCircle(cx - eyeR * 0.27f, cy - eyeR * 0.3f, eyeR * 0.21f, paint)
            // Happy bottom: overdraw lower portion with body color to create grin-bottom shape
            paint.color = frameColors.primary
            canvas.drawRect(side - eyeR, eyeY + eyeR * 0.3f, side + eyeR, eyeY + eyeR + 2f, paint)
            canvas.restore()
        }
    }

    // ── beak ───────────────────────────────────────────────────────────────────

    private fun drawBeak(canvas: Canvas) {
        beakPath.reset()
        beakPath.moveTo(-r * 0.24f, beakTopY)
        beakPath.lineTo( r * 0.24f, beakTopY)
        beakPath.lineTo(0f,          r * 0.58f)
        beakPath.close()
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
        paint.color = frameColors.secondary
        canvas.drawPath(beakPath, paint)
        drawNostrils(canvas)
    }

    private fun drawNostrils(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = frameColors.primary
       // canvas.drawCircle(-r * 0.08f, beakTopY + r * 0.08f, r * 0.05f, paint)
       // canvas.drawCircle( r * 0.08f, beakTopY + r * 0.08f, r * 0.05f, paint)
    }

    private fun drawBeakYawn(canvas: Canvas) {
        val lf = (animFrame - DELAY_BEAK).coerceAtLeast(0)
        val tipY = when {
            lf < 15 -> lerp(r * 0.58f, r * 0.82f, lf / 15f)
            lf < 35 -> r * 0.82f
            else    -> lerp(r * 0.82f, r * 0.58f, (lf - 35f) / 5f)
        }
        beakPath.reset()
        beakPath.moveTo(-r * 0.24f, beakTopY)
        beakPath.lineTo( r * 0.24f, beakTopY)
        beakPath.lineTo(0f,          tipY)
        beakPath.close()
        paint.style = Paint.Style.FILL
        paint.color = frameColors.secondary
        canvas.drawPath(beakPath, paint)
        drawNostrils(canvas)
    }

    private fun drawBeakSnap(canvas: Canvas) {
        val lf = (animFrame - DELAY_BEAK).coerceAtLeast(0)
        val tipY = when (lf) {
            in 0..3   -> lerp(r * 0.58f, r * 0.78f,  lf / 3f)
            in 4..6   -> lerp(r * 0.78f, r * 0.58f, (lf - 4f) / 3f)
            in 10..13 -> lerp(r * 0.58f, r * 0.78f, (lf - 10f) / 3f)
            in 14..16 -> lerp(r * 0.78f, r * 0.58f, (lf - 14f) / 3f)
            else      -> r * 0.58f
        }
        beakPath.reset()
        beakPath.moveTo(-r * 0.24f, beakTopY)
        beakPath.lineTo( r * 0.24f, beakTopY)
        beakPath.lineTo(0f,          tipY)
        beakPath.close()
        paint.style = Paint.Style.FILL
        paint.color = frameColors.secondary
        canvas.drawPath(beakPath, paint)
        drawNostrils(canvas)
    }

    private fun drawBeakGape(canvas: Canvas) {
        val lf   = (animFrame - DELAY_BEAK).coerceAtLeast(0)
        val gapeT = easeIn(lf.toFloat(), 5f)
        val tipY  = lerp(r * 0.58f, r * 0.72f, gapeT)
        beakPath.reset()
        beakPath.moveTo(-r * 0.24f, beakTopY)
        beakPath.lineTo( r * 0.24f, beakTopY)
        beakPath.lineTo(0f,          tipY)
        beakPath.close()
        paint.style = Paint.Style.FILL
        paint.color = frameColors.secondary
        canvas.drawPath(beakPath, paint)
        // Interior mouth void
        val mouthH = r * 0.12f * gapeT
        mouthRect.set(-r * 0.14f, beakTopY + r * 0.04f, r * 0.14f, beakTopY + r * 0.04f + mouthH)
        paint.color = Color.BLACK
        canvas.drawOval(mouthRect, paint)
        drawNostrils(canvas)
    }

    private fun drawBeakGrimace(canvas: Canvas) {
        val t = easeIn(animFrame.toFloat(), 6f)
        val beakCenterY = beakTopY + (r * 0.58f - beakTopY) / 2f
        canvas.save()
        canvas.scale(lerp(1f, 0.6f, t), lerp(1f, 1.4f, t), 0f, beakCenterY)
        drawBeak(canvas)
        canvas.restore()
    }

    // ── wings ──────────────────────────────────────────────────────────────────

    private fun drawWing(canvas: Canvas, left: Boolean, angle: Float, scale: Float = 1f) {
        val s     = if (left) -1f else 1f
        val pivot = s * r * 0.72f
        canvas.save()
        canvas.rotate(s * -angle, pivot, 0f)
        if (scale != 1f) canvas.scale(scale, scale, pivot, 0f)

        paint.style = Paint.Style.FILL
        wingSecondary.reset()
        wingSecondary.moveTo(s * r * 0.72f, -r * 0.13f)
        wingSecondary.quadTo(s * r * 1.22f, -r * 0.55f, s * r * 1.65f, -r * 0.27f)
        wingSecondary.quadTo(s * r * 1.58f,  r * 0.06f, s * r * 1.22f,  r * 0.33f)
        wingSecondary.quadTo(s * r * 0.92f,  r * 0.38f, s * r * 0.72f,  r * 0.13f)
        wingSecondary.close()
        paint.color = frameColors.secondary
        canvas.drawPath(wingSecondary, paint)

        wingPrimary.reset()
        wingPrimary.moveTo(s * r * 0.50f, -r * 0.10f)
        wingPrimary.quadTo(s * r * 1.18f, -r * 0.48f, s * r * 1.57f, -r * 0.27f)
        wingPrimary.quadTo(s * r * 1.50f,  r * 0.04f, s * r * 1.16f,  r * 0.27f)
        wingPrimary.quadTo(s * r * 0.90f,  r * 0.33f, s * r * 0.50f,  r * 0.10f)
        wingPrimary.close()
        paint.color = frameColors.primary
        canvas.drawPath(wingPrimary, paint)

        canvas.restore()
    }

    // ── head feathers ──────────────────────────────────────────────────────────

    private fun drawHeadFeather(canvas: Canvas, attachX: Float, attachY: Float, rotDeg: Float, scale: Float) {
        val fw = r * 0.18f * scale
        val fh = r * 0.46f * scale
        canvas.save()
        canvas.rotate(rotDeg, attachX, attachY)
        wingSecondary.reset()
        wingSecondary.moveTo(attachX - fw * 0.7f,  attachY)
        wingSecondary.quadTo(attachX - fw,          attachY - fh * 0.55f, attachX,             attachY - fh)
        wingSecondary.quadTo(attachX + fw,          attachY - fh * 0.55f, attachX + fw * 0.7f, attachY)
        wingSecondary.close()
        paint.style = Paint.Style.FILL
        paint.color = frameColors.secondary
        canvas.drawPath(wingSecondary, paint)
        wingPrimary.reset()
        wingPrimary.moveTo(attachX - fw * 0.42f, attachY + fh * 0.18f)
        wingPrimary.quadTo(attachX - fw * 0.75f, attachY - fh * 0.48f, attachX,              attachY - fh * 0.88f)
        wingPrimary.quadTo(attachX + fw * 0.75f, attachY - fh * 0.48f, attachX + fw * 0.42f, attachY + fh * 0.18f)
        wingPrimary.close()
        paint.color = frameColors.primary
        canvas.drawPath(wingPrimary, paint)
        canvas.restore()
    }

    private fun drawHeadFeatherFlared(canvas: Canvas, attachX: Float, attachY: Float, rotDeg: Float, scale: Float, t: Float) {
        canvas.save()
        canvas.scale(1f, lerp(1f, 1.25f, t), attachX, attachY)
        drawHeadFeather(canvas, attachX, attachY, rotDeg, scale + 0.15f * t)
        canvas.restore()
    }

    private fun drawHeadFeatherDroopy(canvas: Canvas, attachX: Float, attachY: Float, rotDeg: Float, scale: Float, t: Float) {
        val spreadAngle = lerp(rotDeg, rotDeg * 1.36f, t)
        canvas.save()
        canvas.scale(1f, lerp(1f, 0.75f, t), attachX, attachY)
        drawHeadFeather(canvas, attachX, attachY, spreadAngle, scale)
        canvas.restore()
    }

    // ── PuckSkin hooks ─────────────────────────────────────────────────────────

    override fun onCollisionWin(position: Point, speed: Float) {
        spawnFeatherExplosion(position.x, position.y, renderer.radius, responsivePrimary, responsiveSecondary)
    }

    override fun onShieldedCollision(position: Point) {
        spawnFeatherExplosion(position.x, position.y, renderer.radius, responsivePrimary, responsiveSecondary)
    }

    override fun onPhaseChanged(phase: ChargePhase) {
        when (phase) {
            ChargePhase.SweetSpot -> {
                dangerFromSweetSpot = true
                startAnim(ChickenAnim.AlmostHit)
            }
            ChargePhase.Inert -> {
                winkRight = !winkRight
                startAnim(ChickenAnim.Taunt)
            }
            ChargePhase.Idle -> {
                if (lastPhase == ChargePhase.SweetSpot) {
                    startAnim(ChickenAnim.JustHit)
                } else if (currentAnim == ChickenAnim.Default || currentAnim == ChickenAnim.AlmostHit) {
                    currentAnim = ChickenAnim.Default; animFrame = 0
                }
            }
            else -> {}
        }
        lastPhase = phase
    }

    override fun onDangerNear(threatX: Float, threatY: Float) {
        if (renderer.shielded) return
        if (currentAnim == ChickenAnim.JustHit) return
        this.threatX = threatX
        this.threatY = threatY
        dangerFromSweetSpot = false
        startAnim(ChickenAnim.AlmostHit)
    }

    override fun onDangerClear() {
        threatX = Float.NaN; threatY = Float.NaN
        if (currentAnim == ChickenAnim.AlmostHit && !dangerFromSweetSpot) {
            currentAnim = ChickenAnim.Default; animFrame = 0
        }
    }

    override fun onHit() {
        if (renderer.shielded) return
        startAnim(ChickenAnim.JustHit)
    }

    override fun onScore(otherColor: Int, position: Point, highGoal: Boolean) {
        val won = (renderer.isHigh && !highGoal) || (!renderer.isHigh && highGoal)
        if (won) {
            animLoop = Settings.gameOver
            startAnim(ChickenAnim.Celebration)
        } else {
            startAnim(ChickenAnim.JustHit)
        }
        Effects.addPersistentEffect(FeatherCelebration(position.x, position.y, renderer.radius, highGoal, responsivePrimary, responsiveSecondary, fullCircle = false))
    }

    private class FeatherCelebration(
        private val cx: Float, private val cy: Float,
        private val radius: Float,
        highGoal: Boolean,
        private val primary: Int,
        private val secondary: Int,
        fullCircle: Boolean
    ) : Effects.PersistentEffect {
        private class Feather(
            var x: Float, var y: Float,
            val vx: Float, val vy: Float,
            var angle: Float, val spin: Float,
            val colorMix: Float
        )
        private val feathers: List<Feather>
        private val paint = Paint().apply { isAntiAlias = true }
        private val fw = radius * 0.4f
        private val fh = radius * 1.1f
        private var frame = 0
        override var isDone = false

        init {
            val count = if (fullCircle) 24 else 14
            val angleRange = if (fullCircle) 2f * PI.toFloat() else PI.toFloat()
            val angleOffset = if (fullCircle || highGoal) 0f else PI.toFloat()
            feathers = List(count) { i ->
                val a = (i.toFloat() / count) * angleRange + angleOffset + Random.nextFloat() * 0.3f
                val speed = radius * (0.045f + Random.nextFloat() * 0.045f)
                Feather(cx, cy, cos(a) * speed, sin(a) * speed, Random.nextFloat() * 360f, (Random.nextFloat() - 0.5f) * 6f, Random.nextFloat())
            }
        }

        override fun step() { frame++; if (frame > 60) isDone = true }

        override fun draw(canvas: Canvas) {
            for (f in feathers) {
                f.x += f.vx; f.y += f.vy; f.angle += f.spin
                val alpha = (220f * (1f - frame / 60f).coerceAtLeast(0f)).toInt()
                if (alpha <= 0) continue
                val c = Palette.lerpColor(primary, secondary, f.colorMix)
                paint.style = Paint.Style.FILL
                paint.color = Palette.withAlpha(c, alpha)
                canvas.withRotation(f.angle, f.x, f.y) {
                    drawOval(f.x - fw, f.y - fh, f.x + fw, f.y + fh, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = fw * 0.35f
                    paint.strokeCap = Paint.Cap.ROUND
                    paint.color = Palette.withAlpha(secondary, alpha)
                    drawLine(f.x, f.y + fh * 1.3f, f.x, f.y - fh * 0.7f, paint)
                }
            }
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun startAnim(anim: ChickenAnim) { currentAnim = anim; animFrame = 0 }

    private fun easeIn(frame: Float, duration: Float): Float =
        sin(min(frame, duration) / duration * HALF_PI)

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    companion object {
        private val HALF_PI = (Math.PI / 2.0).toFloat()
    }
}
