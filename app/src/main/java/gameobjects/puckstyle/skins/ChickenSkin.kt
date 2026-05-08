package gameobjects.puckstyle.skins

import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.core.graphics.withRotation
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorGroup
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
import gameobjects.puckstyle.Palette
import utility.Effects

class ChickenSkin(override val renderer: PuckRenderer) : PuckSkin {

    private val beakPath      = Path()
    private val wingSecondary = Path()
    private val wingPrimary   = Path()
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

    override fun DrawScope.drawBody() {
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

        withTransform({ translate(renderer.x, renderer.y); if (renderer.isHigh) rotate(180f) }) {
            drawCircle(Color(frameColors.primary), r, Offset.Zero)
            drawCircle(Color(frameColors.secondary), r, Offset.Zero, style = Stroke(width = sw))
            drawWingsForState()
            drawFeathersForState()
            drawEyesForState()
            drawBeakForState()
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

    private fun DrawScope.drawWingsForState() {
        when (currentAnim) {
            ChickenAnim.AlmostHit, ChickenAnim.JustHit -> {
                val t = if (animFrame >= DELAY_WINGS) easeIn((animFrame - DELAY_WINGS).toFloat(), 3f) else 0f
                drawWing(left = true,  angle = displayedWingAngle, scale = lerp(1f, 0.82f, t))
                drawWing(left = false, angle = displayedWingAngle, scale = lerp(1f, 0.82f, t))
            }
            ChickenAnim.Celebration -> {
                val t = if (animFrame >= DELAY_WINGS) easeIn((animFrame - DELAY_WINGS).toFloat(), 8f) else 0f
                drawWing(left = true,  angle = displayedWingAngle, scale = lerp(1f, 1.1f, t))
                drawWing(left = false, angle = displayedWingAngle, scale = lerp(1f, 1.1f, t))
            }
            else -> {
                drawWing(left = true,  angle = displayedWingAngle)
                drawWing(left = false, angle = displayedWingAngle)
            }
        }
    }

    private fun DrawScope.drawFeathersForState() {
        val t = when {
            currentAnim == ChickenAnim.AlmostHit || currentAnim == ChickenAnim.JustHit ->
                if (animFrame >= DELAY_FEATHERS) easeIn((animFrame - DELAY_FEATHERS).toFloat(), 8f) else 0f
            currentAnim == ChickenAnim.Celebration ->
                if (animFrame >= DELAY_FEATHERS) easeIn((animFrame - DELAY_FEATHERS).toFloat(), 8f) else 0f
            else -> -1f
        }

        if (t < 0f) {
            drawHeadFeather(0f,        -r * 0.88f, 0f,   1.5f)
            drawHeadFeather(-r * 0.2f, -r * 0.85f, -28f, 1.1f)
            drawHeadFeather( r * 0.2f, -r * 0.85f,  28f, 1.1f)
            return
        }

        val droopy = currentAnim == ChickenAnim.AlmostHit || currentAnim == ChickenAnim.JustHit
        if (droopy) {
            drawHeadFeatherDroopy(0f,        -r * 0.88f, 0f,   1.5f, t)
            drawHeadFeatherDroopy(-r * 0.2f, -r * 0.85f, -28f, 1.1f, t)
            drawHeadFeatherDroopy( r * 0.2f, -r * 0.85f,  28f, 1.1f, t)
        } else {
            drawHeadFeatherFlared(0f,        -r * 0.88f, 0f,   1.5f, t)
            drawHeadFeatherFlared(-r * 0.2f, -r * 0.85f, -28f, 1.1f, t)
            drawHeadFeatherFlared( r * 0.2f, -r * 0.85f,  28f, 1.1f, t)
        }
    }

    private fun DrawScope.drawEyesForState() {
        when (currentAnim) {
            ChickenAnim.Taunt       -> drawWinkingEye()
            ChickenAnim.AlmostHit   -> drawWideEye()
            ChickenAnim.JustHit     -> drawWinceEye()
            ChickenAnim.Celebration -> drawHappyEye()
            else                    -> drawEye(eyeOpen)
        }
    }

    private fun DrawScope.drawBeakForState() {
        when {
            currentAnim == ChickenAnim.AlmostHit   && animFrame >= DELAY_BEAK -> drawBeakGape()
            currentAnim == ChickenAnim.JustHit                                -> drawBeakGrimace()
            currentAnim == ChickenAnim.Celebration && animFrame >= DELAY_BEAK -> drawBeakGape()
            currentAnim == ChickenAnim.Taunt       && animFrame >= DELAY_BEAK -> drawBeakGape()
            else                                                               -> drawBeak()
        }
    }

    // ── eyes ───────────────────────────────────────────────────────────────────

    private fun DrawScope.drawEye(open: Boolean) {
        if (open) {
            val maxOff = eyeR * 0.45f
            eyeSides[0] = -eyeX; eyeSides[1] = eyeX
            for (side in eyeSides) {
                val cx = side + irisOffX * maxOff
                val cy = eyeY + irisOffY * maxOff
                drawCircle(Color.White, eyeR, Offset(side, eyeY))
                drawCircle(Color(frameColors.secondary), eyeR * 0.72f, Offset(cx, cy))
                drawCircle(Color.Black, eyeR * 0.4f, Offset(cx, cy))
                drawCircle(Color.White, eyeR * 0.21f, Offset(cx - eyeR * 0.27f, cy - eyeR * 0.3f))
            }
        } else {
            val lineColor = Color(frameColors.secondary)
            val sw = r * 0.08f
            drawLine(lineColor, Offset(-eyeX - eyeR * 0.65f, eyeY), Offset(-eyeX + eyeR * 0.65f, eyeY), sw, cap = StrokeCap.Round)
            drawLine(lineColor, Offset( eyeX - eyeR * 0.65f, eyeY), Offset( eyeX + eyeR * 0.65f, eyeY), sw, cap = StrokeCap.Round)
        }
    }

    private fun DrawScope.drawWinkingEye() {
        val maxOff    = eyeR * 0.45f
        val openSide  = if (winkRight)  eyeX else -eyeX
        val closedSide = if (winkRight) -eyeX else  eyeX
        val cx = openSide + irisOffX * maxOff
        val cy = eyeY     + irisOffY * maxOff

        drawCircle(Color.White, eyeR, Offset(openSide, eyeY))
        drawCircle(Color(frameColors.secondary), eyeR * 0.72f, Offset(cx, cy))
        drawCircle(Color.Black, eyeR * 0.4f, Offset(cx, cy))
        drawCircle(Color.White, eyeR * 0.21f, Offset(cx - eyeR * 0.27f, cy - eyeR * 0.3f))

        if (animFrame <= 25) {
            drawLine(Color(frameColors.secondary), Offset(closedSide - eyeR * 0.65f, eyeY), Offset(closedSide + eyeR * 0.65f, eyeY), r * 0.08f, cap = StrokeCap.Round)
        } else {
            val cx2 = closedSide + irisOffX * maxOff
            val cy2 = eyeY       + irisOffY * maxOff
            drawCircle(Color.White, eyeR, Offset(closedSide, eyeY))
            drawCircle(Color(frameColors.secondary), eyeR * 0.72f, Offset(cx2, cy2))
            drawCircle(Color.Black, eyeR * 0.4f, Offset(cx2, cy2))
        }
    }

    private fun DrawScope.drawWideEye() {
        val t = easeIn(animFrame.toFloat(), 8f)
        val scaleX = lerp(1f, 0.75f, t)
        val scaleY = lerp(1f, 1.35f, t)
        val maxOff  = eyeR * 0.45f
        eyeSides[0] = -eyeX; eyeSides[1] = eyeX
        for (side in eyeSides) {
            val cx = side + irisOffX * maxOff
            val cy = eyeY + irisOffY * maxOff
            withTransform({ scale(scaleX, scaleY, pivot = Offset(side, eyeY)) }) {
                drawCircle(Color.White, eyeR, Offset(side, eyeY))
                drawCircle(Color(frameColors.secondary), eyeR * 0.72f, Offset(cx, cy))
                drawCircle(Color.Black, eyeR * 0.4f, Offset(cx, cy))
                drawCircle(Color.White, eyeR * 0.21f, Offset(cx - eyeR * 0.27f, cy - eyeR * 0.3f))
            }
        }
    }

    private fun DrawScope.drawWinceEye() {
        val decay   = 1f - animFrame.toFloat() / ANIM_JUST_HIT
        val shudder = sin(animFrame * 1.5f) * r * 0.02f * decay
        val lineColor = Color(frameColors.secondary)
        val sw = r * 0.08f
        drawLine(lineColor, Offset(-eyeX - eyeR * 0.65f, eyeY + shudder), Offset(-eyeX + eyeR * 0.65f, eyeY + shudder), sw, cap = StrokeCap.Round)
        drawLine(lineColor, Offset( eyeX - eyeR * 0.65f, eyeY + shudder), Offset( eyeX + eyeR * 0.65f, eyeY + shudder), sw, cap = StrokeCap.Round)
    }

    private fun DrawScope.drawHappyEye() {
        val t = easeIn(animFrame.toFloat(), 8f)
        val scaleX = lerp(1f, 1.3f,  t)
        val scaleY = lerp(1f, 0.75f, t)
        val maxOff  = eyeR * 0.45f
        eyeSides[0] = -eyeX; eyeSides[1] = eyeX
        for (side in eyeSides) {
            val cx = side + irisOffX * maxOff
            val cy = eyeY + irisOffY * maxOff
            withTransform({ scale(scaleX, scaleY, pivot = Offset(side, eyeY)) }) {
                drawCircle(Color.White, eyeR, Offset(side, eyeY))
                drawCircle(Color(frameColors.secondary), eyeR * 0.72f, Offset(cx, cy))
                drawCircle(Color.Black, eyeR * 0.4f, Offset(cx, cy))
                drawCircle(Color.White, eyeR * 0.21f, Offset(cx - eyeR * 0.27f, cy - eyeR * 0.3f))
                // Overdraw lower half with body color to create grin-bottom shape
                drawRect(Color(frameColors.primary), topLeft = Offset(side - eyeR, eyeY + eyeR * 0.3f), size = Size(2 * eyeR, eyeR + 2f))
            }
        }
    }

    // ── beak ───────────────────────────────────────────────────────────────────

    private fun DrawScope.drawBeak() {
        beakPath.reset()
        beakPath.moveTo(-r * 0.24f, beakTopY)
        beakPath.lineTo( r * 0.24f, beakTopY)
        beakPath.lineTo(0f,          r * 0.58f)
        beakPath.close()
        drawPath(beakPath, Color(frameColors.secondary))
        drawNostrils()
    }

    @Suppress("EmptyFunctionBlock")
    private fun DrawScope.drawNostrils() {}

    private fun DrawScope.drawBeakYawn() {
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
        drawPath(beakPath, Color(frameColors.secondary))
        drawNostrils()
    }

    private fun DrawScope.drawBeakSnap() {
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
        drawPath(beakPath, Color(frameColors.secondary))
        drawNostrils()
    }

    private fun DrawScope.drawBeakGape() {
        val lf    = (animFrame - DELAY_BEAK).coerceAtLeast(0)
        val gapeT = easeIn(lf.toFloat(), 5f)

        val closedTipY = r * 0.58f
        val growth     = r * 0.10f * gapeT
        val lowerTipY  = closedTipY + growth
        val mouthTipY  = closedTipY + growth
        val upperTipY  = lerp(closedTipY, beakTopY + (closedTipY - beakTopY) * 0.40f, gapeT)

        // Layer 1 (behind): lower jaw
        beakPath.reset()
        beakPath.moveTo(-r * 0.24f, beakTopY)
        beakPath.lineTo( r * 0.24f, beakTopY)
        beakPath.lineTo(0f, lowerTipY)
        beakPath.close()
        drawPath(beakPath, Color(frameColors.secondary))

        // Layer 2 (middle): mouth interior
        beakPath.reset()
        beakPath.moveTo(-r * 0.14f, beakTopY)
        beakPath.lineTo( r * 0.14f, beakTopY)
        beakPath.lineTo(0f, mouthTipY)
        beakPath.close()
        drawPath(beakPath, Color.Black)

        // Layer 3 (front): upper jaw
        beakPath.reset()
        beakPath.moveTo(-r * 0.24f, beakTopY)
        beakPath.lineTo( r * 0.24f, beakTopY)
        beakPath.lineTo(0f, upperTipY)
        beakPath.close()
        drawPath(beakPath, Color(frameColors.secondary))

        drawNostrils()
    }

    private fun DrawScope.drawBeakGrimace() {
        val t = easeIn(animFrame.toFloat(), 6f)
        val beakCenterY = beakTopY + (r * 0.58f - beakTopY) / 2f
        withTransform({ scale(lerp(1f, 0.8f, t), lerp(1f, 1.2f, t), pivot = Offset(0f, beakCenterY)) }) {
            drawBeak()
        }
    }

    // ── wings ──────────────────────────────────────────────────────────────────

    private fun DrawScope.drawWing(left: Boolean, angle: Float, scale: Float = 1f) {
        val s     = if (left) -1f else 1f
        val pivot = s * r * 0.72f
        withTransform({
            rotate(s * -angle, pivot = Offset(pivot, 0f))
            if (scale != 1f) scale(scale, scale, pivot = Offset(pivot, 0f))
        }) {
            wingSecondary.reset()
            wingSecondary.moveTo(s * r * 0.72f, -r * 0.13f)
            wingSecondary.quadraticTo(s * r * 1.22f, -r * 0.55f, s * r * 1.65f, -r * 0.27f)
            wingSecondary.quadraticTo(s * r * 1.58f, r * 0.06f, s * r * 1.22f, r * 0.33f)
            wingSecondary.quadraticTo(s * r * 0.92f, r * 0.38f, s * r * 0.72f, r * 0.13f)
            wingSecondary.close()
            drawPath(wingSecondary, Color(frameColors.secondary))

            wingPrimary.reset()
            wingPrimary.moveTo(s * r * 0.50f, -r * 0.10f)
            wingPrimary.quadraticTo(s * r * 1.18f, -r * 0.48f, s * r * 1.57f, -r * 0.27f)
            wingPrimary.quadraticTo(s * r * 1.50f, r * 0.04f, s * r * 1.16f, r * 0.27f)
            wingPrimary.quadraticTo(s * r * 0.90f, r * 0.33f, s * r * 0.50f, r * 0.10f)
            wingPrimary.close()
            drawPath(wingPrimary, Color(frameColors.primary))
        }
    }

    // ── head feathers ──────────────────────────────────────────────────────────

    private fun DrawScope.drawHeadFeather(attachX: Float, attachY: Float, rotDeg: Float, scale: Float) {
        val fw = r * 0.18f * scale
        val fh = r * 0.46f * scale
        withTransform({ rotate(rotDeg, pivot = Offset(attachX, attachY)) }) {
            wingSecondary.reset()
            wingSecondary.moveTo(attachX - fw * 0.7f, attachY)
            wingSecondary.quadraticTo(attachX - fw, attachY - fh * 0.55f, attachX, attachY - fh)
            wingSecondary.quadraticTo(attachX + fw, attachY - fh * 0.55f, attachX + fw * 0.7f, attachY)
            wingSecondary.close()
            drawPath(wingSecondary, Color(frameColors.secondary))

            wingPrimary.reset()
            wingPrimary.moveTo(attachX - fw * 0.42f, attachY + fh * 0.18f)
            wingPrimary.quadraticTo(attachX - fw * 0.75f, attachY - fh * 0.48f, attachX, attachY - fh * 0.88f)
            wingPrimary.quadraticTo(attachX + fw * 0.75f, attachY - fh * 0.48f, attachX + fw * 0.42f, attachY + fh * 0.18f)
            wingPrimary.close()
            drawPath(wingPrimary, Color(frameColors.primary))
        }
    }

    private fun DrawScope.drawHeadFeatherFlared(attachX: Float, attachY: Float, rotDeg: Float, scale: Float, t: Float) {
        withTransform({ scale(1f, lerp(1f, 1.25f, t), pivot = Offset(attachX, attachY)) }) {
            drawHeadFeather(attachX, attachY, rotDeg, scale + 0.15f * t)
        }
    }

    private fun DrawScope.drawHeadFeatherDroopy(attachX: Float, attachY: Float, rotDeg: Float, scale: Float, t: Float) {
        val spreadAngle = lerp(rotDeg, rotDeg * 1.36f, t)
        withTransform({ scale(1f, lerp(1f, 0.75f, t), pivot = Offset(attachX, attachY)) }) {
            drawHeadFeather(attachX, attachY, spreadAngle, scale)
        }
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

    override val explosionFrequency get() = 20

    override fun onUsedToScore(otherColor: Int, position: Point, highGoal: Boolean) {
        startAnim(ChickenAnim.Taunt)
        Effects.addPersistentEffect(FeatherCelebration(position.x, position.y, renderer.radius, highGoal, theme.main.primary, theme.main.secondary, fullCircle = false))
    }

    override fun onScored() {
        startAnim(ChickenAnim.Celebration)
    }

    override fun onVictory(x: Float, y: Float) {
        animLoop = true
        startAnim(ChickenAnim.Celebration)
        Effects.addPersistentEffect(FeatherCelebration(x, y, renderer.radius, highGoal = true, theme.main.primary, theme.main.secondary, fullCircle = true))
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
                val speed = radius * (0.045f + Random.nextFloat() * 0.1f)
                Feather(cx, cy, cos(a) * speed, sin(a) * speed, Random.nextFloat() * 360f, (Random.nextFloat() - 0.5f) * 6f, Random.nextFloat())
            }
        }

        override fun step() { frame++; if (frame > 60) isDone = true }

        override fun draw(canvas: Canvas) {
            for (f in feathers) {
                f.x += f.vx
                f.y += f.vy
                f.angle += f.spin
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
