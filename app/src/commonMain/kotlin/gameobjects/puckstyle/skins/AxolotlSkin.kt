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
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorGroup
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import gameobjects.puckstyle.paddles.BubbleLaunch
import physics.Point
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class AxolotlSkin(override val renderer: PuckRenderer) : PuckSkin {

    // Body: viewBox 109.93 x 90.57, half-width = 54.965 = radius
    private val BODY_W_K = 2f           // 109.93 / 54.965
    private val BODY_H_K = 90.57f / 54.965f  // ~1.648

    // Torso: viewBox 100.6 x 73.99, positioned below head center
    private val TORSO_W_K = 100.6f / 54.965f   // ~1.830
    private val TORSO_H_K = 73.99f / 54.965f   // ~1.346
    private val TORSO_BASE_Y_K = 0.40f          // anatomical Y offset below center (rest position)
    private val TORSO_FOLLOW_X_K = 0.15f        // max lateral trail/track offset
    private val TORSO_FOLLOW_Y_K = 0.20f        // max axial trail/track offset

    // Eyes_Pupil: viewBox 110.33 x 28.92
    private val EYES_PUPIL_W_K = 110.33f / 54.965f
    private val EYES_PUPIL_H_K = 28.92f / 54.965f
    private val EYES_PUPIL_Y_K = -0.263f

    // Eyes_White: viewBox 105.21 x 10.48
    private val EYES_WHITE_W_K = 105.21f / 54.965f
    private val EYES_WHITE_H_K = 10.48f / 54.965f
    private val EYES_WHITE_Y_K = -0.408f

    // Mouth_Closed: viewBox 97.83 x 11.94
    private val MOUTH_CLOSED_W_K = 97.83f / 54.965f
    private val MOUTH_CLOSED_H_K = 11.94f / 54.965f
    private val MOUTH_CLOSED_Y_K = 0.207f

    // Mouth_Open_1: viewBox 97.34 x 35.95
    private val MOUTH_OPEN1_W_K = 97.34f / 54.965f
    private val MOUTH_OPEN1_H_K = 35.95f / 54.965f
    private val MOUTH_OPEN1_Y_K = 0.287f

    // Mouth_Open_2: viewBox 77.29 x 18.64
    private val MOUTH_OPEN2_W_K = 77.29f / 54.965f
    private val MOUTH_OPEN2_H_K = 18.64f / 54.965f
    private val MOUTH_OPEN2_Y_K = 0.378f

    // Gills: viewBox 52.43 x 60.21
    private val GILL_W_K = 52.43f / 54.965f
    private val GILL_H_K = 60.21f / 54.965f
    private val GILL_L_X_K = -1.20f        // outward from head
    private val GILL_R_X_K = 1.20f         // outward from head
    private val GILL_PIVOT_X_K = 0.75f
    private val GILL_SIZE_K = 1.30f        // enlarged gills
    private val GILL_PERSPECTIVE_MAX = 10f // paddle-X driven inner-side bend (degrees)

    // Shadow
    private val SHADOW_ALPHA = 0.244f
    private val SHADOW_LIT_BODY_ABOVE_K = -.1f   // body-shape lit region covers ~top half of head
    private val SHADOW_LIT_GILL_R_MIN = 0.8f     // min lit radius for gills (r); grows 2× toward paddle side
    private val SHADOW_LIT_GILL_ABOVE_K = 0.6f
    private val SHADOW_LATERAL_K = 0.50f
    private val TORSO_LIT_ABOVE_K = 0.4f

    // Animation state machine
    private enum class AxolotlAnim { Default, AlmostHit, JustHit, Celebration, Chatter, Yawn }

    private var currentAnim = AxolotlAnim.Default
    private var animFrame = 0
    private var animLoop = false
    private var dangerFromSweetSpot = false
    private var lastPhase = ChargePhase.Idle

    private var framesSinceTrigger = 0

    private val ANIM_ALMOST_HIT = 35
    private val ANIM_JUST_HIT = 30
    private val ANIM_CELEBRATION = 50
    private val ANIM_CHATTER = 50
    private val ANIM_YAWN = 100
    private val YAWN_THRESHOLD = 30000

    // Per-frame state
    private var r = 0f
    private var frameColors: ColorGroup = theme.main
    private var shadowDx = 0f
    private var velocityDirX = 0f
    private var velocityDirY = 0f

    // Gill animation
    private var gillPhaseL = 0f
    private var gillPhaseR = 0.4f
    private var displayedGillAngleL = 0f
    private var displayedGillAngleR = 0f
    private var gillLaunchBendL = 0f
    private var gillLaunchBendR = 0f

    // Movement tracking
    private var lastX = Float.NaN
    private var lastY = Float.NaN

    // Torso paddle-tracking offset
    private var torsoOffX = 0f
    private var torsoOffY = 0f

    // Gill paddle-X perspective follow (normalized -1..1)
    private var gillFollowX = 0f

    // Eye animation
    private var currentEyeScaleX = 1f
    private var currentEyeScaleY = 1f

    override fun DrawScope.drawBody() {
        frameColors = responsiveGroup
        r = renderer.radius

        if (lastX.isNaN()) { lastX = renderer.x; lastY = renderer.y }
        val dx = renderer.x - lastX
        val dy = renderer.y - lastY
        val speed = hypot(dx, dy)
        if (speed > 0.001f) {
            velocityDirX = dx / speed
            velocityDirY = dy / speed
        }
        lastX = renderer.x; lastY = renderer.y

        // Gill idle bob
        gillPhaseL += 0.015f
        gillPhaseR += 0.015f
        val idleAngleL = sin(gillPhaseL) * 8f
        val idleAngleR = sin(gillPhaseR) * 8f

        // Gill launch bend: gills bend away from motion direction
        val launchBendTarget = if (speed > r * 0.02f) {
            val moveAngle = atan2(velocityDirY, velocityDirX)
            sin(moveAngle) * 15f
        } else 0f
        gillLaunchBendL = lerp(gillLaunchBendL, launchBendTarget, 0.08f)
        gillLaunchBendR = lerp(gillLaunchBendR, -launchBendTarget, 0.08f)

        // Gill tuck during AlmostHit/JustHit
        val gillTuckTarget = when (currentAnim) {
            AxolotlAnim.AlmostHit, AxolotlAnim.JustHit -> {
                val t = easeIn(animFrame.toFloat(), 3f)
                lerp(0f, -5f, t)
            }
            AxolotlAnim.Celebration -> {
                val t = easeIn(animFrame.toFloat(), 8f)
                lerp(0f, 5f, t)
            }
            else -> 0f
        }
        val totalAngleL = idleAngleL + gillLaunchBendL + gillTuckTarget
        val totalAngleR = idleAngleR + gillLaunchBendR + gillTuckTarget
        displayedGillAngleL = lerp(displayedGillAngleL, totalAngleL, 0.18f)
        displayedGillAngleR = lerp(displayedGillAngleR, totalAngleR, 0.18f)

        // Frames-since-trigger drives idle Yawn
        if (currentAnim == AxolotlAnim.Default) {
            framesSinceTrigger++
            if (framesSinceTrigger >= YAWN_THRESHOLD) {
                framesSinceTrigger = 0
                startAnim(AxolotlAnim.Yawn)
            }
        } else {
            framesSinceTrigger = 0
        }

        // Advance animation
        if (currentAnim != AxolotlAnim.Default) {
            animFrame++
            val duration = when (currentAnim) {
                AxolotlAnim.AlmostHit -> ANIM_ALMOST_HIT
                AxolotlAnim.JustHit -> ANIM_JUST_HIT
                AxolotlAnim.Celebration -> ANIM_CELEBRATION
                AxolotlAnim.Chatter -> ANIM_CHATTER
                AxolotlAnim.Yawn -> ANIM_YAWN
                else -> 0
            }
            if (animFrame >= duration) {
                if (animLoop && currentAnim == AxolotlAnim.Celebration && Settings.gameOver) {
                    animFrame = 0
                } else {
                    currentAnim = AxolotlAnim.Default
                    animFrame = 0
                    animLoop = false
                }
            }
        }

        // Torso trailing + gill follow + shadow: paddle-direction driven when active (same as CatSkin/PokPok),
        // rawFollowX is the unlerped paddle-X used directly for shadowDx.
        val paddle = renderer.effect as? PaddleLaunchEffect
        val rawFollowX: Float
        val torsoTargetX: Float
        val torsoTargetY: Float
        if (paddle != null) {
            val pdx = paddle.paddleX - renderer.x
            val pdy = paddle.paddleY - renderer.y
            val dist = hypot(pdx, pdy).coerceAtLeast(0.001f)
            rawFollowX = if (renderer.isHigh) -(pdx / dist) else pdx / dist
            torsoTargetX = rawFollowX * r * TORSO_FOLLOW_X_K
            torsoTargetY = (if (renderer.isHigh) -(pdy / dist) else pdy / dist) * r * TORSO_FOLLOW_Y_K
            gillFollowX = lerp(gillFollowX, rawFollowX, 0.08f)
        } else if (speed > r * 0.02f) {
            rawFollowX = 0f
            torsoTargetX = (if (renderer.isHigh) velocityDirX else -velocityDirX) * r * TORSO_FOLLOW_X_K
            torsoTargetY = (if (renderer.isHigh) velocityDirY else -velocityDirY) * r * TORSO_FOLLOW_Y_K
            gillFollowX = lerp(gillFollowX, 0f, 0.04f)
        } else {
            rawFollowX = 0f
            torsoTargetX = 0f
            torsoTargetY = 0f
            gillFollowX = lerp(gillFollowX, 0f, 0.04f)
        }
        torsoOffX = lerp(torsoOffX, torsoTargetX, 0.08f)
        torsoOffY = lerp(torsoOffY, torsoTargetY, 0.08f)
        // shadowDx follows raw paddle-X directly (no double-lag), identical to CatSkin/PokPok
        shadowDx = lerp(shadowDx, rawFollowX * r * SHADOW_LATERAL_K, 0.12f)

        val canvas = drawContext.canvas
        canvas.save()
        canvas.translate(renderer.x, renderer.y)
        if (renderer.isHigh) canvas.scale(-1f, -1f)

        // Z-order: torso -> gills -> body + eyes + mouth + head shadow (shadow drawn last to cover face)
        drawTorsoLayer()
        drawGill(left = true, angleDeg = displayedGillAngleL, followX = gillFollowX)
        drawGill(left = false, angleDeg = displayedGillAngleR, followX = gillFollowX)
        drawHeadWithShadow()

        canvas.restore()
    }

    // -- Torso (drawn behind head, tracks paddle position) --

    private fun DrawScope.drawTorsoLayer() {
        val torso = AxolotlSkinPainters.torso ?: return
        val primary = Color(frameColors.primary)
        val tw = r * TORSO_W_K
        val th = r * TORSO_H_K
        val cx = torsoOffX
        val cy = r * TORSO_BASE_Y_K + torsoOffY
        val bounds = Rect(cx - tw / 2f - r * 0.1f, cy - th / 2f - r * 0.1f,
            cx + tw / 2f + r * 0.1f, cy + th / 2f + r * 0.1f)
        drawContext.canvas.saveLayer(bounds, Paint())
        drawSvgPart(torso, cx, cy, tw, th, tint = primary)
        // Shadow: torso SVG shape used as lit region (shifted above torso center)
        val litCx = shadowDx
        val litCy = cy - r * TORSO_LIT_ABOVE_K
        val litBounds = Rect(
            minOf(bounds.left, litCx - tw / 2f - r * 0.1f),
            minOf(bounds.top, litCy - th / 2f - r * 0.1f),
            maxOf(bounds.right, litCx + tw / 2f + r * 0.1f),
            maxOf(bounds.bottom, litCy + th / 2f + r * 0.1f)
        )
        val srcAtopPaint = Paint().apply { blendMode = BlendMode.SrcAtop }
        drawContext.canvas.saveLayer(bounds, srcAtopPaint)
        drawRect(color = Color(0f, 0f, 0f, SHADOW_ALPHA), topLeft = bounds.topLeft, size = bounds.size)
        val dstOutPaint = Paint().apply { blendMode = BlendMode.DstOut }
        drawContext.canvas.saveLayer(litBounds, dstOutPaint)
        drawSvgPart(torso, litCx, litCy, tw, th)
        drawContext.canvas.restore()  // close lit erase layer
        drawContext.canvas.restore()  // close shadow layer
        drawContext.canvas.restore()  // close torso layer
    }

    // -- Head (body SVG + eyes + mouth, shadow applied last to cover face elements) --

    private fun DrawScope.drawHeadWithShadow() {
        val body = AxolotlSkinPainters.body
        val secondary = Color(frameColors.secondary)
        val bw = r * BODY_W_K
        val bh = r * BODY_H_K
        val bounds = Rect(-bw / 2f - r * 0.1f, -bh / 2f - r * 0.1f,
            bw / 2f + r * 0.1f, bh / 2f + r * 0.1f)
        drawContext.canvas.saveLayer(bounds, Paint())
        if (body != null) {
            drawSvgPart(body, 0f, 0f, bw, bh, tint = secondary)
        } else {
            drawOval(secondary, topLeft = Offset(-bw / 2f, -bh / 2f), size = Size(bw, bh))
        }
        drawEyesLayer()
        drawMouthForState()
        // Shadow drawn last so it covers eyes and mouth; body SVG shape defines the lit region
        val litCx = shadowDx
        val litCy = r * SHADOW_LIT_BODY_ABOVE_K
        val litBounds = Rect(
            minOf(bounds.left, litCx - bw / 2f - r * 0.1f),
            minOf(bounds.top, litCy - bh / 2f - r * 0.1f),
            maxOf(bounds.right, litCx + bw / 2f + r * 0.1f),
            maxOf(bounds.bottom, litCy + bh / 2f + r * 0.1f)
        )
        val srcAtopPaint = Paint().apply { blendMode = BlendMode.SrcAtop }
        drawContext.canvas.saveLayer(bounds, srcAtopPaint)
        drawRect(color = Color(0f, 0f, 0f, SHADOW_ALPHA), topLeft = bounds.topLeft, size = bounds.size)
        if (body != null) {
            val dstOutPaint = Paint().apply { blendMode = BlendMode.DstOut }
            drawContext.canvas.saveLayer(litBounds, dstOutPaint)
            drawSvgPart(body, litCx, litCy, bw, bh)
            drawContext.canvas.restore()  // close lit erase layer
        }
        drawContext.canvas.restore()  // close shadow layer
        drawContext.canvas.restore()  // close head layer
    }

    // -- Gills --

    private fun DrawScope.drawGill(left: Boolean, angleDeg: Float, followX: Float = 0f) {
        val painter = if (left) AxolotlSkinPainters.gillLeft else AxolotlSkinPainters.gillRight
        if (painter == null) return
        val sign = if (left) -1f else 1f
        val centerX = if (left) r * GILL_L_X_K else r * GILL_R_X_K
        val centerY = 0f
        val w = r * GILL_W_K * GILL_SIZE_K
        val h = r * GILL_H_K * GILL_SIZE_K
        val pivotX = -sign * r * GILL_PIVOT_X_K
        // Half the original orbit; inner-side bend driven by paddle X (same pattern as DragonSkin wings)
        val rotation = sign * -(angleDeg * 0.5f) + (-sign * followX * GILL_PERSPECTIVE_MAX)

        withTransform({
            rotate(rotation, pivot = Offset(pivotX, 0f))
        }) {
            val gBounds = Rect(centerX - w * 0.6f, centerY - h * 0.6f,
                centerX + w * 0.6f, centerY + h * 0.6f)
            drawContext.canvas.saveLayer(gBounds, Paint())
            drawSvgPart(painter, centerX, centerY, w, h, tint = Color(frameColors.secondary))
            val growFactor = ((sign * followX + 1f) / 2f).coerceIn(0f, 1f)
            val litR = r * SHADOW_LIT_GILL_R_MIN * lerp(1f, 2f, growFactor)
            val worldLitCx = centerX + shadowDx
            val worldLitCy = centerY - r * SHADOW_LIT_GILL_ABOVE_K
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
                    topLeft = gBounds.topLeft,
                    size = gBounds.size,
                    blendMode = BlendMode.SrcAtop
                )
            }
            drawContext.canvas.restore()
        }
    }

    // -- Eyes (no tracking, fixed position) --

    private fun DrawScope.drawEyesLayer() {
        currentEyeScaleX = 1f
        currentEyeScaleY = 1f
        when (currentAnim) {
            AxolotlAnim.AlmostHit -> {
                val t = easeIn(animFrame.toFloat(), 8f)
                currentEyeScaleY = lerp(1f, 1.3f, t)
                drawEyes(currentEyeScaleX, currentEyeScaleY)
            }
            AxolotlAnim.JustHit -> {
                val t = easeIn(animFrame.toFloat(), 8f)
                currentEyeScaleY = lerp(1f, 0.6f, t)
                drawEyes(currentEyeScaleX, currentEyeScaleY)
            }
            AxolotlAnim.Celebration -> {
                val t = easeIn(animFrame.toFloat(), 8f)
                currentEyeScaleX = lerp(1f, 1.3f, t)
                currentEyeScaleY = lerp(1f, 0.7f, t)
                drawEyes(currentEyeScaleX, currentEyeScaleY)
                val cy = r * EYES_PUPIL_Y_K
                val w = r * EYES_PUPIL_W_K
                val h = r * EYES_PUPIL_H_K
                withTransform({ scale(currentEyeScaleX, currentEyeScaleY, pivot = Offset(0f, cy)) }) {
                    drawRect(Color(frameColors.secondary),
                        topLeft = Offset(-w / 2f, cy + h * 0.1f), size = Size(w, h * 0.6f))
                }
            }
            AxolotlAnim.Yawn -> drawEyes(1f, 1f)
            else -> drawEyes(1f, 1f)
        }
    }

    private fun DrawScope.drawEyes(scaleX: Float, scaleY: Float) {
        val whiteP = AxolotlSkinPainters.eyesWhite
        val pupilP = AxolotlSkinPainters.eyesPupil
        // Both painters scaled together around the same pivot so whites squash/stretch with pupils
        val pivot = Offset(0f, r * EYES_PUPIL_Y_K)
        withTransform({ scale(scaleX, scaleY, pivot = pivot) }) {
            if (pupilP != null) {
                drawSvgPart(pupilP, 0f, r * EYES_PUPIL_Y_K, r * EYES_PUPIL_W_K, r * EYES_PUPIL_H_K)
            }
            if (whiteP != null) {
                drawSvgPart(whiteP, 0f, r * EYES_WHITE_Y_K, r * EYES_WHITE_W_K, r * EYES_WHITE_H_K)
            }
        }
    }

    // -- Mouth --

    private fun DrawScope.drawMouthForState() {
        when {
            currentAnim == AxolotlAnim.Chatter -> drawMouthChatter()
            currentAnim == AxolotlAnim.AlmostHit -> drawMouthGape()
            currentAnim == AxolotlAnim.JustHit -> drawMouthGrimace()
            currentAnim == AxolotlAnim.Celebration -> drawMouthGape()
            currentAnim == AxolotlAnim.Yawn -> drawMouthGape(yawn = true)
            else -> drawMouthClosed()
        }
    }

    private fun DrawScope.drawMouthChatter() {
        if ((animFrame / 5) % 2 == 0) {
            drawMouthOpen(1f)
        } else {
            drawMouthClosed()
        }
    }

    private fun DrawScope.drawMouthClosed() {
        val painter = AxolotlSkinPainters.mouthClosed ?: return
        val w = r * MOUTH_CLOSED_W_K
        val h = r * MOUTH_CLOSED_H_K
        drawSvgPart(painter, 0f, r * MOUTH_CLOSED_Y_K, w, h, tint = Color(0f, 0f, 0f, SHADOW_ALPHA))
    }

    private fun DrawScope.drawMouthGape(yawn: Boolean = false) {
        val gapeT = easeIn(animFrame.toFloat(), if (yawn) 15f else 5f)
        val growth = if (yawn) 1.45f else 1.15f
        val scale = lerp(1f, growth, gapeT)
        drawMouthOpen(scale)
    }

    private fun DrawScope.drawMouthGrimace() {
        val painter = AxolotlSkinPainters.mouthClosed ?: return
        val t = easeIn(animFrame.toFloat(), 6f)
        val w = r * MOUTH_CLOSED_W_K * lerp(1f, 0.8f, t)
        val h = r * MOUTH_CLOSED_H_K * lerp(1f, 1.2f, t)
        drawSvgPart(painter, 0f, r * MOUTH_CLOSED_Y_K, w, h, tint = Color(0f, 0f, 0f, SHADOW_ALPHA))
    }

    private fun DrawScope.drawMouthOpen(scale: Float) {
        val open1 = AxolotlSkinPainters.mouthOpen1 ?: return
        val open2 = AxolotlSkinPainters.mouthOpen2 ?: return
        // open1 = inside of mouth: secondary tint with full (uniform) shadow
        val mw1 = r * MOUTH_OPEN1_W_K
        val mh1 = r * MOUTH_OPEN1_H_K * scale
        val bounds1 = Rect(-mw1 / 2f - r * 0.05f, r * MOUTH_OPEN1_Y_K - mh1 / 2f - r * 0.05f,
            mw1 / 2f + r * 0.05f, r * MOUTH_OPEN1_Y_K + mh1 / 2f + r * 0.05f)
        drawContext.canvas.saveLayer(bounds1, Paint())
        drawSvgPart(open1, 0f, r * MOUTH_OPEN1_Y_K, mw1, mh1, tint = Color(frameColors.secondary))
        drawRect(color = Color(0f, 0f, 0f, SHADOW_ALPHA), topLeft = bounds1.topLeft,
            size = bounds1.size, blendMode = BlendMode.SrcAtop)
        drawContext.canvas.restore()
        // open2 = tongue: primary tint, no shadow, width unchanged
        drawSvgPart(open2, 0f, r * MOUTH_OPEN2_Y_K,
            r * MOUTH_OPEN2_W_K, r * MOUTH_OPEN2_H_K * scale,
            tint = Color(frameColors.primary))
    }

    // -- Painter helper --

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

    // -- PuckSkin hooks --

    override fun onPhaseChanged(phase: ChargePhase) {
        when (phase) {
            ChargePhase.SweetSpot -> {
                dangerFromSweetSpot = true
                startAnim(AxolotlAnim.AlmostHit)
            }
            ChargePhase.Inert -> {
                startAnim(AxolotlAnim.Chatter)
            }
            ChargePhase.Idle -> {
                if (lastPhase == ChargePhase.SweetSpot) {
                    startAnim(AxolotlAnim.JustHit)
                } else if (currentAnim == AxolotlAnim.Default || currentAnim == AxolotlAnim.AlmostHit) {
                    currentAnim = AxolotlAnim.Default; animFrame = 0
                }
            }
            else -> {}
        }
        lastPhase = phase
    }

    override fun onDangerNear(threatX: Float, threatY: Float) {
        if (renderer.shielded) return
        if (currentAnim == AxolotlAnim.JustHit) return
        dangerFromSweetSpot = false
        startAnim(AxolotlAnim.AlmostHit)
    }

    override fun onDangerClear() {
        if (currentAnim == AxolotlAnim.AlmostHit && !dangerFromSweetSpot) {
            currentAnim = AxolotlAnim.Default; animFrame = 0
        }
    }

    override fun onHit() {
        if (renderer.shielded) return
        startAnim(AxolotlAnim.JustHit)
    }

    override val explosionFrequency get() = 20

    private fun spawnBubbles(x: Float, y: Float) {
        BubbleLaunch.spawnBubbleBurst(x, y, renderer.radius, responsivePrimary, responsiveSecondary, renderer.isHigh)
    }

    override fun onCollisionWin(position: Point, speed: Float) {
        spawnBubbles(position.x, position.y)
    }

    override fun onShieldedCollision(position: Point) {
        spawnBubbles(position.x, position.y)
    }

    override fun onUsedToScore(otherColor: Int, position: Point, highGoal: Boolean) {
        startAnim(AxolotlAnim.Chatter)
        spawnBubbles(position.x, position.y)
    }

    override fun onScored() {
        startAnim(AxolotlAnim.Celebration)
    }

    override fun onVictory(x: Float, y: Float) {
        animLoop = true
        startAnim(AxolotlAnim.Celebration)
        spawnBubbles(x, y)
    }

    // -- Helpers --

    private fun startAnim(anim: AxolotlAnim) {
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
