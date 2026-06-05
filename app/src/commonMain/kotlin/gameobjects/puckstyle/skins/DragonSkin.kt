package gameobjects.puckstyle.skins

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorGroup
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import gameobjects.Settings
import physics.Point
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class DragonSkin(override val renderer: PuckRenderer) : PuckSkin {

    // Body: viewBox 111.72 x 105.96, body radius ~55.86
    // Scale factor = r / 55.86
    private val BODY_W_K = 111.72f / 55.86f  // ~2.0
    private val BODY_H_K = 105.96f / 55.86f  // ~1.896

    // Eyes: separate L/R, diamond shaped
    // Eye sclera (L1/R1): viewBox 28.9 x 28.9
    private val EYE_SCLERA_W_K = 28.9f / 55.86f   // ~0.517
    private val EYE_SCLERA_H_K = 28.9f / 55.86f   // ~0.517
    // Eye pupil (L2/R2): viewBox 22 x 20.24
    private val EYE_PUPIL_W_K = 22f / 55.86f       // ~0.394
    private val EYE_PUPIL_H_K = 20.24f / 55.86f    // ~0.362

    // Eye positions from Dragon.svg composite:
    // Left eye: in composite at approx (57.58, 62.16) center of viewBox placed at (43.14, 48.71)
    // Right eye: in composite at approx (123.28, 62.16) center of viewBox placed at (109.49+14.45, 48.71+14.45)
    // Composition center x = 90.43, body center y ~= 55.79
    // Left eye offset from body center: (57.58 - 90.43) / 55.86 = -0.588, (62.16 - 55.79) / 55.86 = 0.114
    // Right eye offset: (123.28 - 90.43) / 55.86 = 0.588, same y
    private val EYE_OFFSET_X_K = 0.588f
    private val EYE_OFFSET_Y_K = -0.10f
    // Pupil offset within eye (pupil is smaller, centered slightly differently)
    private val PUPIL_OFFSET_Y_K = 0.05f

    // Dynamic pupil tracking
    private val PUPIL_R_K = 0.14f
    private val PUPIL_COVER_R_K = 0.20f
    private val PUPIL_HIGHLIGHT_R_K = 0.09f
    private val PUPIL_MAX_FOLLOW_K = 0.16f

    // Closed eye: viewBox 30.26 x 28.4
    private val EYE_CLOSED_W_K = 30.26f / 55.86f  // ~0.542
    private val EYE_CLOSED_H_K = 28.4f / 55.86f   // ~0.508

    // Mouth open 1 (lips): viewBox 95.92 x 28.45
    private val MOUTH_OPEN_W_K = 95.92f / 55.86f   // ~1.717
    private val MOUTH_OPEN_H_K = 28.45f / 55.86f   // ~0.509
    // Mouth open 2 (teeth): viewBox 76.3 x 13.83
    private val MOUTH_TEETH_W_K = 76.3f / 55.86f   // ~1.366
    private val MOUTH_TEETH_H_K = 13.83f / 55.86f  // ~0.248
    // Mouth closed 1 (fangs): viewBox 62.24 x 14.19
    private val MOUTH_CFANG_W_K = 62.24f / 55.86f  // ~1.114
    private val MOUTH_CFANG_H_K = 14.19f / 55.86f  // ~0.254
    // Mouth closed 2 (lips): viewBox 88.02 x 26.68
    private val MOUTH_CLIP_W_K = 88.02f / 55.86f   // ~1.575
    private val MOUTH_CLIP_H_K = 26.68f / 55.86f   // ~0.478
    // Mouth position: from composite, mouth center is at approx y=80-90 relative to body center ~55.79
    // Offset Y: (83 - 55.79) / 55.86 = 0.487
    private val MOUTH_OFFSET_Y_K = 0.40f

    // Horns (map to PokPok feathers but subtler)
    // Horn Left/Right: viewBox 28.94 x 41.7
    private val HORN_SIDE_W_K = 28.94f / 55.86f    // ~0.518
    private val HORN_SIDE_H_K = 41.7f / 55.86f     // ~0.746
    // TUNE side horn spread: increase CX_K to push horns further apart (0.0=center, ~1.0=ball edge)
    private val HORN_SIDE_CX_K = 0.6f
    // TUNE side horn height: the first value is the bottom anchor; 0.0=ball center, -1.0=ball top
    private val HORN_SIDE_CY_K = -0.40f - HORN_SIDE_H_K / 2f

    // Three independent middle nubs (split from Dragon_Horn_Middle)
    // Sizes in body-radius units, heights from original painter nub extents
    private val NUB_TOP_W_K = 20.12f / 55.86f   // ~0.360
    private val NUB_TOP_H_K = 11.66f / 55.86f   // ~0.209
    private val NUB_MID_W_K = 20.12f / 55.86f   // ~0.360
    private val NUB_MID_H_K = 19.9f  / 55.86f   // ~0.356
    private val NUB_BOT_W_K = 20.12f / 55.86f   // ~0.360
    private val NUB_BOT_H_K = 12.93f / 55.86f   // ~0.231
    // TUNE nub heights: more negative = higher on the head; adjust all three to shift the stack
    private val NUB_TOP_CY_K = -0.93f
    private val NUB_MID_CY_K = -0.59f
    private val NUB_BOT_CY_K = -0.18f
    // TUNE arc intensity: how far the middle nub bends toward the gaze (0=rigid, 0.2=dramatic)
    private val NUB_ARC_K = 0.10f

    // TUNE: HORN_SIDE_ROT = static lean of each side horn; COUNTER = slight away-from-paddle tilt; ORBIT = 0 disables orbit
    private val HORN_SIDE_ROT = 10f
    private val HORN_COUNTER_MAX = 10f       // very subtle: horns tip slightly away from the paddle
    private val HORN_ORBIT_MAX = 1f         // 0 = no group orbit around body center

    // Wings: viewBox 43.53 x 28.28
    private val WING_W_K = 43.53f / 55.86f * 1.3f  // ~1.013
    private val WING_H_K = 28.28f / 55.86f * 1.3f  // ~0.658
    private val WING_PIVOT_X_K = 0.78f
    private val WING_CENTER_OFFSET = 0.5f
    private val WING_PERSPECTIVE_ANGLE_MAX = 14f
    private val WING_PERSPECTIVE_SCALE_K = 0.05f

    // Face follow
    private val FACE_FOLLOW_X_K = 0.10f
    private val FACE_FOLLOW_Y_K = 0.02f

    // Shadow
    private val SHADOW_LATERAL_K = 0.50f
    private val SHADOW_LIT_BODY_R = 1.4f
    private val SHADOW_LIT_BODY_ABOVE_K = 0.7f
    private val SHADOW_LIT_WING_R_MIN = 0.8f
    private val SHADOW_LIT_WING_ABOVE_K = 0.55f
    private val SHADOW_HORN_LIT_R = 0.65f
    private val SHADOW_ALPHA = 0.244f

    // Cached per-frame
    private var cachedRadius = -1f
    private var r = 0f

    private fun ensureCache() {
        val newR = renderer.radius
        if (cachedRadius != newR) {
            cachedRadius = newR
        }
    }

    // Wing flap
    private var wingPhase = 0f
    private var displayedWingAngle = 0f
    private var lastX = Float.NaN
    private var lastY = Float.NaN

    // Blink
    private var blinkCountdown = Random.nextInt(60, 181)
    private var blinkFrame = 0
    private val BLINK_DURATION = 4

    // Shadow
    private var shadowDx = 0f

    // Animation state machine
    private enum class DragonAnim { Default, AlmostHit, JustHit, Celebration, Chatter, Yawn }

    private var currentAnim = DragonAnim.Default
    private var animFrame = 0
    private var animLoop = false
    private var dangerFromSweetSpot = false
    private var lastPhase = ChargePhase.Idle
    private var framesSinceTrigger = 0
    private var threatX = Float.NaN
    private var threatY = Float.NaN

    // Durations
    private val ANIM_ALMOST_HIT = 35
    private val ANIM_JUST_HIT = 30
    private val ANIM_CELEBRATION = 50
    private val ANIM_CHATTER = 50
    private val ANIM_YAWN = 100
    private val YAWN_THRESHOLD = 30000
    private val IDLE_FLAP_SPEED = 0.03f

    // Per-frame state
    private var frameColors: ColorGroup = theme.main
    private var irisOffX = 0f
    private var irisOffY = 0f
    private var hornFollowAngle = 0f
    private var hornOrbitAngle = 0f
    private var faceOffX = 0f
    private var faceOffY = 0f
    private var wingAngle = 0f
    private var eyeOpen = true

    // Smoothed animation blend values
    private var wingAnimOffset = 0f
    private var hornDroopyBlend = 0f
    private var hornFlaredBlend = 0f

    override fun DrawScope.drawBody() {
        ensureCache()
        frameColors = responsiveGroup

        if (lastX.isNaN()) { lastX = renderer.x; lastY = renderer.y }
        val speed = hypot(renderer.x - lastX, renderer.y - lastY)
        lastX = renderer.x; lastY = renderer.y
        wingPhase += IDLE_FLAP_SPEED + speed * 0.025f
        wingAngle = sin(wingPhase) * 55f
        val targetWingAngle = when (currentAnim) {
            DragonAnim.AlmostHit, DragonAnim.JustHit -> 0f
            DragonAnim.Celebration -> 30f
            DragonAnim.Yawn -> 22f
            DragonAnim.Chatter -> sin(animFrame.toFloat() * 0.6f) * 35f
            else -> wingAngle
        }
        val wingLerpRate = if (currentAnim == DragonAnim.Chatter) 0.5f else 0.18f
        displayedWingAngle = lerp(displayedWingAngle, targetWingAngle, wingLerpRate)

        blinkCountdown--
        if (blinkCountdown <= 0) {
            blinkFrame = BLINK_DURATION
            blinkCountdown = Random.nextInt(60, 181)
        }
        eyeOpen = blinkFrame == 0
        if (blinkFrame > 0) blinkFrame--

        computeIrisOffset()
        hornFollowAngle = -irisOffX * HORN_COUNTER_MAX
        hornOrbitAngle = -irisOffX * HORN_ORBIT_MAX
        faceOffX = irisOffX * r * FACE_FOLLOW_X_K
        faceOffY = irisOffY * r * FACE_FOLLOW_Y_K

        if (currentAnim == DragonAnim.Default) {
            framesSinceTrigger++
            if (framesSinceTrigger >= YAWN_THRESHOLD) {
                framesSinceTrigger = 0
                startAnim(DragonAnim.Yawn)
            }
        } else {
            framesSinceTrigger = 0
        }

        if (currentAnim != DragonAnim.Default) {
            animFrame++
            val duration = when (currentAnim) {
                DragonAnim.AlmostHit -> ANIM_ALMOST_HIT
                DragonAnim.JustHit -> ANIM_JUST_HIT
                DragonAnim.Celebration -> ANIM_CELEBRATION
                DragonAnim.Chatter -> ANIM_CHATTER
                DragonAnim.Yawn -> ANIM_YAWN
                else -> 0
            }
            if (animFrame >= duration) {
                if (animLoop && currentAnim == DragonAnim.Celebration && Settings.gameOver) {
                    animFrame = 0
                } else {
                    currentAnim = DragonAnim.Default
                    animFrame = 0
                    animLoop = false
                }
            }
        }

        val canvas = drawContext.canvas
        canvas.save()
        canvas.translate(renderer.x, renderer.y)
        if (renderer.isHigh) canvas.scale(-1f, -1f)

        r = cachedRadius
        shadowDx = lerp(shadowDx, irisOffX * r * SHADOW_LATERAL_K, 0.12f)


        drawWingsForState()


        drawHeadWithShadow()

        withTransform({ rotate(hornOrbitAngle, pivot = Offset.Zero) }) {
            drawSideHorns()
        }
        drawMiddleNubs()

        canvas.restore()
    }

    // ── iris direction ─────────────────────────────────────────────────────────

    private fun computeIrisOffset() {
        val useThreat = currentAnim == DragonAnim.AlmostHit &&
            !dangerFromSweetSpot && !threatX.isNaN() && !threatY.isNaN()

        val wx: Float
        val wy: Float
        if (useThreat) {
            wx = threatX - renderer.x
            wy = threatY - renderer.y
        } else {
            val paddle = renderer.effect as? PaddleLaunchEffect
            if (paddle != null) {
                wx = paddle.paddleX - renderer.x
                wy = paddle.paddleY - renderer.y
            } else {
                wx = 0f; wy = renderer.radius
            }
        }
        val dist = hypot(wx, wy).coerceAtLeast(0.001f)
        val nX = wx / dist; val nY = wy / dist
        irisOffX = if (renderer.isHigh) -nX else nX
        irisOffY = if (renderer.isHigh) -nY else nY
    }

    // ── body ───────────────────────────────────────────────────────────────────

    private fun DrawScope.drawHeadWithShadow() {
        val body = DragonSkinPainters.body
        val secondary = Color(frameColors.primary)
        val bounds = Rect(-r * 1.2f, -r * 1.2f, r * 1.2f, r * 1.2f)
        drawContext.canvas.saveLayer(bounds, Paint())
        if (body != null) {
            val w = r * BODY_W_K
            val h = r * BODY_H_K
            drawSvgPart(body, 0f, 0f, w, h, tint = secondary)
        } else {
            drawCircle(secondary, r, Offset.Zero)
        }
        withTransform({ translate(faceOffX, faceOffY) }) {
            drawEyesLayer()
            drawMouthForState()
        }
        // Shadow drawn last so it covers eyes and mouth
        val litR = r * SHADOW_LIT_BODY_R
        val litBounds = Rect(
            shadowDx - litR, -r * SHADOW_LIT_BODY_ABOVE_K - litR,
            shadowDx + litR, -r * SHADOW_LIT_BODY_ABOVE_K + litR
        )
        val srcAtopPaint = Paint().apply { blendMode = BlendMode.SrcAtop }
        drawContext.canvas.saveLayer(bounds, srcAtopPaint)
        drawRect(color = Color(0f, 0f, 0f, SHADOW_ALPHA), topLeft = bounds.topLeft, size = bounds.size)
        val litPath = Path().apply { addOval(litBounds) }
        val dstOutPaint = Paint().apply { blendMode = BlendMode.DstOut }
        drawContext.canvas.saveLayer(litBounds, dstOutPaint)
        drawPath(litPath, Color.White)
        drawContext.canvas.restore()
        drawContext.canvas.restore()
        drawContext.canvas.restore()
    }

    // ── wings ──────────────────────────────────────────────────────────────────

    private fun DrawScope.drawWingsForState() {
        val targetAnimOffset = when (currentAnim) {
            DragonAnim.AlmostHit, DragonAnim.JustHit -> {
                val t = easeIn(animFrame.toFloat(), 3f)
                lerp(0f, -20f, t)
            }
            DragonAnim.Celebration -> {
                val t = easeIn(animFrame.toFloat(), 8f)
                lerp(0f, 20f, t)
            }
            DragonAnim.Yawn -> {
                val t = easeIn(animFrame.toFloat(), 8f)
                lerp(0f, 15f, t)
            }
            else -> 0f
        }
        wingAnimOffset = lerp(wingAnimOffset, targetAnimOffset, 0.12f)
        drawWing(left = true, angleDeg = displayedWingAngle, animAngleOffset = wingAnimOffset)
        drawWing(left = false, angleDeg = displayedWingAngle, animAngleOffset = wingAnimOffset)
    }

    private fun DrawScope.drawWing(left: Boolean, angleDeg: Float, animAngleOffset: Float) {
        val painter = if (left) DragonSkinPainters.wingL else DragonSkinPainters.wingR
        if (painter == null) return
        val sign = if (left) -1f else 1f
        val pivotX = sign * r * WING_PIVOT_X_K
        val centerX = pivotX + sign * r * WING_CENTER_OFFSET
        val centerY = 0f
        val w = r * WING_W_K
        val h = r * WING_H_K
        val rotation = sign * -(angleDeg + animAngleOffset) + (-sign * irisOffX * WING_PERSPECTIVE_ANGLE_MAX)
        val perspScale = 1f - sign * irisOffX * WING_PERSPECTIVE_SCALE_K
        withTransform({
            rotate(rotation, pivot = Offset(pivotX, 0f))
        }) {
            val wBounds = Rect(
                centerX - w - r * 0.15f, centerY - h - r * 0.15f,
                centerX + w + r * 0.15f, centerY + h + r * 0.15f
            )
            drawContext.canvas.saveLayer(wBounds, Paint())
            drawSvgPart(painter, centerX, centerY, w, h, scaleX = perspScale, scaleY = perspScale,
                tint = Color(frameColors.secondary))
            val growFactor = ((sign * irisOffX + 1f) / 2f).coerceIn(0f, 1f)
            val litR = r * SHADOW_LIT_WING_R_MIN * lerp(1f, 2f, growFactor)
            val worldLitCx = centerX + shadowDx
            val worldLitCy = centerY - r * SHADOW_LIT_WING_ABOVE_K
            val invRad = -rotation * (PI.toFloat() / 180f)
            val cosInv = cos(invRad)
            val sinInv = sin(invRad)
            val dxLit = worldLitCx - pivotX
            val dyLit = worldLitCy
            val localLitCx = pivotX + dxLit * cosInv - dyLit * sinInv
            val localLitCy = dxLit * sinInv + dyLit * cosInv
            val litPath = Path().apply {
                addOval(Rect(localLitCx - litR, localLitCy - litR, localLitCx + litR, localLitCy + litR))
            }
            withTransform({ clipPath(litPath, ClipOp.Difference) }) {
                drawRect(
                    color = Color(0f, 0f, 0f, SHADOW_ALPHA),
                    topLeft = Offset(centerX - w - r * 0.15f, centerY - h - r * 0.15f),
                    size = Size(w * 2f + r * 0.3f, h * 2f + r * 0.3f),
                    blendMode = BlendMode.SrcAtop
                )
            }
            drawContext.canvas.restore()
        }
    }

    // ── horns ──────────────────────────────────────────────────────────────────

    private fun DrawScope.drawSideHorns() {
        val droopyTarget = when (currentAnim) {
            DragonAnim.AlmostHit, DragonAnim.JustHit ->
                easeIn(animFrame.toFloat(), 8f)
            DragonAnim.Chatter -> 0.35f
            else -> 0f
        }
        val flaredTarget = when (currentAnim) {
            DragonAnim.Celebration -> easeIn(animFrame.toFloat(), 8f)
            else -> 0f
        }
        hornDroopyBlend = lerp(hornDroopyBlend, droopyTarget, 0.12f)
        hornFlaredBlend = lerp(hornFlaredBlend, flaredTarget, 0.12f)

        drawHorn(
            painter = DragonSkinPainters.hornLeft,
            cx = -r * HORN_SIDE_CX_K,
            cy = r * HORN_SIDE_CY_K,
            w = r * HORN_SIDE_W_K,
            h = r * HORN_SIDE_H_K,
            rotDeg = -HORN_SIDE_ROT + hornFollowAngle,
            droopyBlend = hornDroopyBlend, flaredBlend = hornFlaredBlend,
            shadowSign = -1f
        )
        drawHorn(
            painter = DragonSkinPainters.hornRight,
            cx = r * HORN_SIDE_CX_K,
            cy = r * HORN_SIDE_CY_K,
            w = r * HORN_SIDE_W_K,
            h = r * HORN_SIDE_H_K,
            rotDeg = HORN_SIDE_ROT + hornFollowAngle,
            droopyBlend = hornDroopyBlend, flaredBlend = hornFlaredBlend,
            shadowSign = 1f
        )
    }

    private fun DrawScope.drawMiddleNubs() {
        // Arc displacement: middle nub bends most toward gaze, top stays centered
        val arcX = irisOffX * r * NUB_ARC_K

        // Bottom nub: tracks horizontal midpoint between the eyes, no rotation
        drawHorn(
            painter = DragonSkinPainters.hornMidBot,
            cx = faceOffX,
            cy = r * NUB_BOT_CY_K,
            w = r * NUB_BOT_W_K,
            h = r * NUB_BOT_H_K,
            rotDeg = 0f,
            droopyBlend = 0f, flaredBlend = 0f,
            shadowSign = 0f
        )
        // Middle nub: arcs the most, barely rotates
        drawHorn(
            painter = DragonSkinPainters.hornMidMid,
            cx = arcX,
            cy = r * NUB_MID_CY_K,
            w = r * NUB_MID_W_K,
            h = r * NUB_MID_H_K,
            rotDeg = hornFollowAngle * 0.2f,
            droopyBlend = hornDroopyBlend * 0.4f, flaredBlend = hornFlaredBlend * 0.4f,
            shadowSign = 0f
        )
        // Top nub: stays at x=0 (midpoint of left/right horns), full rotation
        drawHorn(
            painter = DragonSkinPainters.hornMidTop,
            cx = 0f,
            cy = r * NUB_TOP_CY_K,
            w = r * NUB_TOP_W_K,
            h = r * NUB_TOP_H_K,
            rotDeg = hornFollowAngle,
            droopyBlend = hornDroopyBlend, flaredBlend = hornFlaredBlend,
            shadowSign = 0f
        )
    }

    private fun DrawScope.drawHorn(
        painter: Painter?,
        cx: Float, cy: Float,
        w: Float, h: Float,
        rotDeg: Float,
        droopyBlend: Float, flaredBlend: Float,
        shadowSign: Float = 0f
    ) {
        if (painter == null) return
        val anchorY = cy + h * 0.5f
        val rot = when {
            droopyBlend > 0.001f ->
                lerp(rotDeg, rotDeg * 1.36f + kotlin.math.sign(rotDeg) * 6f, droopyBlend)
            flaredBlend > 0.001f ->
                lerp(rotDeg, rotDeg - kotlin.math.sign(rotDeg) * 6f, flaredBlend)
            else -> rotDeg
        }
        val tint = ColorFilter.tint(Color(frameColors.secondary))
        // Draw at the painter's constant intrinsic size; scale to the on-screen box via the canvas.
        // Keeps the shared VectorPainter's cached layer from being re-rasterized at carousel vs.
        // in-game sizes (see drawSvgPart). Without this, horns "scale" with the carousel.
        val iSize = painter.intrinsicSize
        val refW = if (iSize.width.isFinite() && iSize.width > 0f) iSize.width else w
        val refH = if (iSize.height.isFinite() && iSize.height > 0f) iSize.height else h

        if (shadowSign != 0f) {
            val margin = r * 0.25f
            val bounds = Rect(cx - w / 2f - margin, cy - h / 2f - margin,
                              cx + w / 2f + margin, cy + h / 2f + margin)
            drawContext.canvas.saveLayer(bounds, Paint())
            withTransform({
                translate(cx - w / 2f, cy - h / 2f)
                val pivot = Offset(w / 2f, anchorY - (cy - h / 2f))
                if (rot != 0f) rotate(rot, pivot = pivot)
                scale(w / refW, h / refH, pivot = Offset.Zero)
            }) {
                with(painter) { draw(Size(refW, refH), colorFilter = tint) }
            }
            // Lit side grows when the eye looks toward this horn; dark side shrinks
            val growFactor = ((shadowSign * irisOffX + 1f) / 2f).coerceIn(0f, 1f)
            val litR = r * SHADOW_HORN_LIT_R * lerp(0.6f, 1.8f, growFactor)
            val litPath = Path().apply {
                addOval(Rect(cx + shadowDx * 0.5f - litR, cy - h * 0.2f - litR,
                             cx + shadowDx * 0.5f + litR, cy - h * 0.2f + litR))
            }
            withTransform({ clipPath(litPath, ClipOp.Difference) }) {
                drawRect(
                    color = Color(0f, 0f, 0f, SHADOW_ALPHA),
                    topLeft = Offset(cx - w / 2f - margin, cy - h / 2f - margin),
                    size = Size(w + margin * 2f, h + margin * 2f),
                    blendMode = BlendMode.SrcAtop
                )
            }
            drawContext.canvas.restore()
        } else {
            withTransform({
                translate(cx - w / 2f, cy - h / 2f)
                val pivot = Offset(w / 2f, anchorY - (cy - h / 2f))
                if (rot != 0f) rotate(rot, pivot = pivot)
                scale(w / refW, h / refH, pivot = Offset.Zero)
            }) {
                with(painter) { draw(Size(refW, refH), colorFilter = tint) }
            }
        }
    }

    // ── eyes ───────────────────────────────────────────────────────────────────

    private var currentEyeScaleX = 1f
    private var currentEyeScaleY = 1f

    private fun DrawScope.drawEyesLayer() {
        currentEyeScaleX = 1f
        currentEyeScaleY = 1f
        when (currentAnim) {
            DragonAnim.Chatter -> drawOpenEyes(1f, 1f)
            DragonAnim.AlmostHit -> {
                val t = easeIn(animFrame.toFloat(), 8f)
                currentEyeScaleX = lerp(1f, 0.8f, t)
                currentEyeScaleY = lerp(1f, 1.3f, t)
                drawOpenEyes(currentEyeScaleX, currentEyeScaleY)
            }
            DragonAnim.JustHit -> drawClosedEyes()
            DragonAnim.Celebration -> {
                val t = easeIn(animFrame.toFloat(), 8f)
                currentEyeScaleX = lerp(1f, 1.3f, t)
                currentEyeScaleY = lerp(1f, 0.75f, t)
                drawOpenEyes(currentEyeScaleX, currentEyeScaleY)
                val eyeW = r * EYE_SCLERA_W_K
                val eyeH = r * EYE_SCLERA_H_K
                val cy = r * EYE_OFFSET_Y_K
                withTransform({ scale(currentEyeScaleX, currentEyeScaleY, pivot = Offset(-r * EYE_OFFSET_X_K, cy)) }) {
                    drawRect(Color(frameColors.primary),
                        topLeft = Offset(-r * EYE_OFFSET_X_K - eyeW / 2f, cy + eyeH * 0.1f),
                        size = Size(eyeW, eyeH * 0.6f))
                }
                withTransform({ scale(currentEyeScaleX, currentEyeScaleY, pivot = Offset(r * EYE_OFFSET_X_K, cy)) }) {
                    drawRect(Color(frameColors.primary),
                        topLeft = Offset(r * EYE_OFFSET_X_K - eyeW / 2f, cy + eyeH * 0.1f),
                        size = Size(eyeW, eyeH * 0.6f))
                }
            }
            DragonAnim.Yawn -> drawClosedEyes()
            else -> if (eyeOpen) drawOpenEyes(1f, 1f) else drawClosedEyes()
        }
    }

    private fun DrawScope.drawOpenEyes(scaleX: Float, scaleY: Float) {
        val lScl = DragonSkinPainters.eyeOpenL1
        val lPup = DragonSkinPainters.eyeOpenL2
        val rScl = DragonSkinPainters.eyeOpenR1
        val rPup = DragonSkinPainters.eyeOpenR2

        val sW = r * EYE_SCLERA_W_K
        val sH = r * EYE_SCLERA_H_K
        val pW = r * EYE_PUPIL_W_K
        val pH = r * EYE_PUPIL_H_K
        val cy = r * EYE_OFFSET_Y_K

        val maxOff = r * PUPIL_MAX_FOLLOW_K
        val ix = irisOffX * maxOff
        val iy = irisOffY * maxOff

        val layerPaint = Paint()
        val srcAtopPaint = Paint().apply { blendMode = BlendMode.SrcAtop }

        // Left eye: sclera as mask, pupil clipped to sclera shape
        if (lScl != null) {
            val lCx = -r * EYE_OFFSET_X_K
            val hw = sW / 2f * scaleX; val hh = sH / 2f * scaleY
            val bounds = Rect(lCx - hw, cy - hh, lCx + hw, cy + hh)
            drawContext.canvas.saveLayer(bounds, layerPaint)
            drawSvgPart(lScl, lCx, cy, sW, sH, scaleX = scaleX, scaleY = scaleY)
            if (lPup != null) {
                drawContext.canvas.saveLayer(bounds, srcAtopPaint)
                drawSvgPart(lPup, lCx + ix * scaleX, cy + r * PUPIL_OFFSET_Y_K + iy * scaleY,
                    pW, pH, scaleX = scaleX, scaleY = scaleY)
                drawContext.canvas.restore()
            }
            drawContext.canvas.restore()
        }

        // Right eye: sclera as mask, pupil clipped to sclera shape
        if (rScl != null) {
            val rCx = r * EYE_OFFSET_X_K
            val hw = sW / 2f * scaleX; val hh = sH / 2f * scaleY
            val bounds = Rect(rCx - hw, cy - hh, rCx + hw, cy + hh)
            drawContext.canvas.saveLayer(bounds, layerPaint)
            drawSvgPart(rScl, rCx, cy, sW, sH, scaleX = scaleX, scaleY = scaleY)
            if (rPup != null) {
                drawContext.canvas.saveLayer(bounds, srcAtopPaint)
                drawSvgPart(rPup, rCx + ix * scaleX, cy + r * PUPIL_OFFSET_Y_K + iy * scaleY,
                    pW, pH, scaleX = scaleX, scaleY = scaleY)
                drawContext.canvas.restore()
            }
            drawContext.canvas.restore()
        }
    }

    private fun DrawScope.drawClosedEyes() {
        val closedL = DragonSkinPainters.eyeClosedL
        val closedR = DragonSkinPainters.eyeClosedR
        val w = r * EYE_CLOSED_W_K
        val h = r * EYE_CLOSED_H_K
        val cy = r * EYE_OFFSET_Y_K
        val tint = Color(frameColors.secondary)
        if (closedL != null) {
            drawSvgPart(closedL, -r * EYE_OFFSET_X_K, cy, w, h, tint = tint)
        }
        if (closedR != null) {
            drawSvgPart(closedR, r * EYE_OFFSET_X_K, cy, w, h, tint = tint)
        }
    }

    // ── mouth ──────────────────────────────────────────────────────────────────

    private fun DrawScope.drawMouthForState() {
        when {
            currentAnim == DragonAnim.Chatter -> drawMouthChatter()
            currentAnim == DragonAnim.AlmostHit -> drawMouthOpen(r * MOUTH_OPEN_W_K, r * MOUTH_OPEN_H_K)
            currentAnim == DragonAnim.JustHit -> drawMouthClosed()
            currentAnim == DragonAnim.Celebration -> drawMouthOpen(r * MOUTH_OPEN_W_K, r * MOUTH_OPEN_H_K)
            currentAnim == DragonAnim.Yawn -> drawMouthGape()
            else -> drawMouthClosed()
        }
    }

    private fun DrawScope.drawMouthChatter() {
        if ((animFrame / 5) % 2 == 0) {
            drawMouthOpen(r * MOUTH_OPEN_W_K, r * MOUTH_OPEN_H_K)
        } else {
            drawMouthClosed()
        }
    }

    private fun DrawScope.drawMouthClosed() {
        val lips = DragonSkinPainters.mouthClosed2
        val fangs = DragonSkinPainters.mouthClosed1
        val cy = r * MOUTH_OFFSET_Y_K
        if (lips != null) {

            drawSvgPart(lips, 0f, cy, r * MOUTH_CLIP_W_K, r * MOUTH_CLIP_H_K,
                tint = Color(frameColors.secondary))
        }
        if (fangs != null) {
            drawSvgPart(fangs, 0f, cy + r * 0.1f, r * MOUTH_CFANG_W_K, r * MOUTH_CFANG_H_K,
                tint = Color(frameColors.secondary))
            drawSvgPart(fangs, 0f, cy, r * MOUTH_CFANG_W_K, r * MOUTH_CFANG_H_K,
                tint = Color(frameColors.primary))
        }
    }

    private fun DrawScope.drawMouthGape() {
        val lf = animFrame.coerceAtLeast(0)
        val gapeT = easeIn(lf.toFloat(), 15f)
        val growth = 1.45f
        val w = r * MOUTH_OPEN_W_K * lerp(1f, growth, gapeT)
        val h = r * MOUTH_OPEN_H_K * lerp(1f, growth, gapeT)
        drawMouthOpen(w, h)
    }

    private fun DrawScope.drawMouthOpen(w: Float, h: Float) {
        val lips = DragonSkinPainters.mouthOpen1
        val teeth = DragonSkinPainters.mouthOpen2
        val cy = r * MOUTH_OFFSET_Y_K
        val teethW = w * (MOUTH_TEETH_W_K / MOUTH_OPEN_W_K)
        val teethH = h * (MOUTH_TEETH_H_K / MOUTH_OPEN_H_K)
        if (lips != null) {
            drawSvgPart(lips, 0f, cy, w, h, tint = Color(frameColors.secondary))
        }
        if (teeth != null) {
            drawSvgPart(teeth, 0f, cy, teethW, teethH, tint = Color(frameColors.primary))
        }
    }

    // ── painter helpers ────────────────────────────────────────────────────────

    private fun DrawScope.drawSvgPart(
        painter: Painter,
        cx: Float, cy: Float,
        w: Float, h: Float,
        angleDeg: Float = 0f,
        scaleX: Float = 1f,
        scaleY: Float = 1f,
        tint: Color? = null
    ) {
        val filter = tint?.let { ColorFilter.tint(it) }
        // Draw the painter at its constant intrinsic size and scale that to fill the w×h box.
        // The in-game ball and the ball-selection carousel share ONE VectorPainter instance per part;
        // drawing the same painter at two different sizes within a single frame thrashes its cached
        // layer, so the live ball renders as a white box with a punched-out circle and "scales" with
        // the carousel. Keeping the draw-size constant decouples the cached render from on-screen size.
        val iSize = painter.intrinsicSize
        val refW = if (iSize.width.isFinite() && iSize.width > 0f) iSize.width else w
        val refH = if (iSize.height.isFinite() && iSize.height > 0f) iSize.height else h
        withTransform({
            translate(cx - w / 2f, cy - h / 2f)
            val pivot = Offset(w / 2f, h / 2f)
            if (angleDeg != 0f) rotate(angleDeg, pivot = pivot)
            if (scaleX != 1f || scaleY != 1f) scale(scaleX, scaleY, pivot = pivot)
            scale(w / refW, h / refH, pivot = Offset.Zero)
        }) {
            with(painter) { draw(Size(refW, refH), colorFilter = filter) }
        }
    }

    // ── PuckSkin hooks ─────────────────────────────────────────────────────────

    override fun onPhaseChanged(phase: ChargePhase) {
        when (phase) {
            ChargePhase.SweetSpot -> {
                dangerFromSweetSpot = true
                startAnim(DragonAnim.AlmostHit)
            }
            ChargePhase.Inert -> {
                startAnim(DragonAnim.Chatter)
            }
            ChargePhase.Idle -> {
                if (lastPhase == ChargePhase.SweetSpot) {
                    startAnim(DragonAnim.JustHit)
                } else if (currentAnim == DragonAnim.Default || currentAnim == DragonAnim.AlmostHit) {
                    currentAnim = DragonAnim.Default; animFrame = 0
                }
            }
            else -> {}
        }
        lastPhase = phase
    }

    override fun onDangerNear(threatX: Float, threatY: Float) {
        if (renderer.shielded) return
        if (currentAnim == DragonAnim.JustHit) return
        this.threatX = threatX
        this.threatY = threatY
        dangerFromSweetSpot = false
        startAnim(DragonAnim.AlmostHit)
    }

    override fun onDangerClear() {
        threatX = Float.NaN; threatY = Float.NaN
        if (currentAnim == DragonAnim.AlmostHit && !dangerFromSweetSpot) {
            currentAnim = DragonAnim.Default; animFrame = 0
        }
    }

    override fun onHit() {
        if (renderer.shielded) return
        startAnim(DragonAnim.JustHit)
    }

    override val explosionFrequency get() = 20

    override fun onUsedToScore(otherColor: Int, position: Point, highGoal: Boolean) {
        startAnim(DragonAnim.Chatter)
    }

    override fun onScored() {
        startAnim(DragonAnim.Celebration)
    }

    override fun onVictory(x: Float, y: Float) {
        animLoop = true
        startAnim(DragonAnim.Celebration)
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun startAnim(anim: DragonAnim) {
        currentAnim = anim
        animFrame = 0
        framesSinceTrigger = 0
    }

    private fun easeIn(frame: Float, duration: Float): Float =
        sin(min(frame, duration) / duration * HALF_PI)

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    companion object {
        private val HALF_PI = (PI / 2.0).toFloat()
    }
}
