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
import physics.Point
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class CatSkin(override val renderer: PuckRenderer) : PuckSkin {

    // Body viewBox: 125.54 x 125.07 -> radius ~62.77
    // Scale factor: r / 62.77
    // Full composition viewBox: 149.69 x 223.71
    // Body center in composition: (~74.85, ~81.64)
    private val SVG_BODY_R = 62.77f

    private val BODY_DIAM_K = 2f

    // Ear dimensions (viewBox 52.52 x 58.01)
    private val EAR_W_K = 52.52f / SVG_BODY_R
    private val EAR_H_K = 58.01f / SVG_BODY_R
    // Ear L1 center in composition: ~(35.53, 49.5) -> offset from body center: (-39.32, -32.14) -> / 62.77
    private val EAR_L_CX_K = -0.626f
    private val EAR_L_CY_K = -0.512f
    // Ear R1 center: ~(114.15, 49.5) -> offset: (39.3, -32.14)
    private val EAR_R_CX_K = 0.626f
    private val EAR_R_CY_K = -0.512f
    // Ear inside (viewBox 21.51 x 35.07)
    private val EAR_INNER_W_K = 21.51f / SVG_BODY_R
    private val EAR_INNER_H_K = 35.07f / SVG_BODY_R
    // Ear_L2 center in composition: ~(24.03, 42.12) -> offset from body center: (-50.82, -39.52) -> / 62.77
    private val EAR_INNER_L_CX_K = -0.810f
    private val EAR_INNER_L_CY_K = -0.630f
    // Ear_R2 center in composition: ~(125.66, 42.12)
    private val EAR_INNER_R_CX_K = 0.810f
    private val EAR_INNER_R_CY_K = -0.630f

    // Ear animation (subtle, like dragon horns at ~40% PokPok feather intensity)
    private val EAR_COUNTER_MAX = 16f
    private val EAR_ORBIT_MAX = 6f

    // Eye whites (viewBox 43.75 x 33.58)
    private val EYE_WHITE_W_K = 43.75f / SVG_BODY_R
    private val EYE_WHITE_H_K = 33.58f / SVG_BODY_R
    // Eye_Open_L1 center in composition: ~(40.87, 86.41) -> offset from body center: (-33.98, 4.77)
    private val EYE_L_CX_K = -0.541f
    private val EYE_L_CY_K = 0.076f
    // Eye_Open_R1 center in composition: ~(108.82, 86.41)
    private val EYE_R_CX_K = 0.541f
    private val EYE_R_CY_K = 0.076f

    // Eye pupil/iris (viewBox 26.85 x 26.08)
    private val EYE_IRIS_W_K = 26.85f / SVG_BODY_R
    private val EYE_IRIS_H_K = 26.08f / SVG_BODY_R
    // Eye_Open_L2 center in composition: ~(44.2, 89.64) -> offset: (-30.65, 8.0)
    private val IRIS_L_CX_K = -0.488f
    private val IRIS_L_CY_K = 0.127f
    // Eye_Open_R2 center in composition: ~(105.49, 89.64)
    private val IRIS_R_CX_K = 0.488f
    private val IRIS_R_CY_K = 0.127f
    private val IRIS_MAX_FOLLOW_K = 0.14f

    // Eye closed (viewBox 46.42 x 29.13)
    private val EYE_CLOSED_W_K = 46.42f / SVG_BODY_R
    private val EYE_CLOSED_H_K = 29.13f / SVG_BODY_R
    // Closed L center in composition: ~(42.21, 85.41) -> offset: (-32.64, 3.77)
    private val EYE_CLOSED_L_CX_K = -0.520f
    private val EYE_CLOSED_L_CY_K = 0.060f
    // Closed R center in composition: ~(107.48, 85.41)
    private val EYE_CLOSED_R_CX_K = 0.520f
    private val EYE_CLOSED_R_CY_K = 0.060f

    // Mouth open 1 (viewBox 29.62 x 33.94)
    private val MOUTH_OPEN1_W_K = 29.62f / SVG_BODY_R
    private val MOUTH_OPEN1_H_K = 33.94f / SVG_BODY_R
    // Mouth center in composition: ~(74.87, 117.77) -> offset from body center: (0.02, 36.13)
    private val MOUTH_CX_K = 0f
    private val MOUTH_CY_K = 0.575f

    // Mouth open 2 (viewBox 19.87 x 5.63)
    private val MOUTH_OPEN2_W_K = 19.87f / SVG_BODY_R
    private val MOUTH_OPEN2_H_K = 5.63f / SVG_BODY_R
    // Mouth_Open2 center in composition: ~(74.84, 130.53) -> offset: (0, 48.89)
    private val MOUTH2_CY_K = 0.779f

    // Mouth closed (viewBox 18.2 x 21.71)
    private val MOUTH_CLOSED_W_K = 18.2f / SVG_BODY_R
    private val MOUTH_CLOSED_H_K = 21.71f / SVG_BODY_R
    private val MOUTH_CLOSED_CY_K = 0.52f

    // Fur top (viewBox 38.05 x 35.19)
    private val FUR_TOP_W_K = 38.05f / SVG_BODY_R
    private val FUR_TOP_H_K = 35.19f / SVG_BODY_R
    // Fur_Top center in composition: ~(67.33, 17.6) -> offset from body center: (-7.52, -64.04)
    private val FUR_TOP_CX_K = -0.12f
    private val FUR_TOP_CY_K = -1.02f
    private val FUR_TOP_COUNTER_MAX = 20f

    // Side fur L1 (viewBox 72.53 x 40.81)
    private val FUR_L1_W_K = 72.53f / SVG_BODY_R
    private val FUR_L1_H_K = 40.81f / SVG_BODY_R
    // Fur_L1 center in composition: ~(36.27, 67.24) -> offset: (-38.58, -14.4)
    private val FUR_L1_CX_K = -0.614f
    private val FUR_L1_CY_K = -0.229f

    // Side fur L2 (viewBox 68.01 x 51.75)
    private val FUR_L2_W_K = 68.01f / SVG_BODY_R
    private val FUR_L2_H_K = 51.75f / SVG_BODY_R
    // Fur_L2 center in composition: ~(42.13, 78.47) -> offset: (-32.72, -3.17)
    private val FUR_L2_CX_K = -0.521f
    private val FUR_L2_CY_K = -0.050f

    // Side fur R1 (viewBox 72.53 x 40.81)
    private val FUR_R1_W_K = 72.53f / SVG_BODY_R
    private val FUR_R1_H_K = 40.81f / SVG_BODY_R
    // Fur_R1 center in composition: ~(113.42, 67.24) -> offset: (38.57, -14.4)
    private val FUR_R1_CX_K = 0.614f
    private val FUR_R1_CY_K = -0.229f

    // Side fur R2 (viewBox 68.01 x 51.75)
    private val FUR_R2_W_K = 68.01f / SVG_BODY_R
    private val FUR_R2_H_K = 51.75f / SVG_BODY_R
    // Fur_R2 center in composition: ~(107.56, 78.47)
    private val FUR_R2_CX_K = 0.521f
    private val FUR_R2_CY_K = -0.050f

    // Face follow
    private val FACE_FOLLOW_X_K = 0.12f
    private val FACE_FOLLOW_Y_K = 0.02f

    // Shadow
    private val SHADOW_ALPHA = 0.244f
    private val SHADOW_LIT_BODY_R = 1.4f
    private val SHADOW_LIT_BODY_ABOVE_K = 0.5f
    private val SHADOW_LATERAL_K = 0.50f
    private val SHADOW_LIT_FUR_R_MIN = 0.8f
    private val SHADOW_LIT_FUR_ABOVE_K = 0.55f
    private val SHADOW_FUR_TOP_LIT_R = 0.65f

    // Cached values
    private var cachedRadius = -1f
    private var r = 0f

    private fun ensureCache() {
        val newR = renderer.radius
        if (cachedRadius != newR) {
            cachedRadius = newR
            r = newR
        }
    }

    // Wing/fur phase for wind animation
    private var furPhase = 0f

    // Blink
    private var blinkCountdown = Random.nextInt(60, 181)
    private var blinkFrame = 0
    private val BLINK_DURATION = 4

    // Movement tracking
    private var lastX = Float.NaN
    private var lastY = Float.NaN

    // Shadow lateral shift
    private var shadowDx = 0f

    // Animation state machine
    private enum class CatAnim { Default, AlmostHit, JustHit, Celebration, Chatter, Yawn }

    private var currentAnim = CatAnim.Default
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

    // Per-frame state
    private var frameColors: ColorGroup = theme.main
    private var irisOffX = 0f
    private var irisOffY = 0f
    private var earFollowAngle = 0f
    private var earOrbitAngle = 0f
    private var furTopAngle = 0f
    private var faceOffX = 0f
    private var faceOffY = 0f
    private var eyeOpen = true

    // Smooth animation blends
    private var earDroopyBlend = 0f
    private var earFlaredBlend = 0f

    override fun DrawScope.drawBody() {
        ensureCache()
        r = cachedRadius
        frameColors = responsiveGroup

        if (lastX.isNaN()) { lastX = renderer.x; lastY = renderer.y }
        val speed = hypot(renderer.x - lastX, renderer.y - lastY)
        lastX = renderer.x; lastY = renderer.y

        // Wind-driven fur phase (constant, independent of speed)
        furPhase += 0.02f

        // Blink
        blinkCountdown--
        if (blinkCountdown <= 0) {
            blinkFrame = BLINK_DURATION
            blinkCountdown = Random.nextInt(60, 181)
        }
        eyeOpen = blinkFrame == 0
        if (blinkFrame > 0) blinkFrame--

        // Look direction
        computeIrisOffset()
        earFollowAngle = -irisOffX * EAR_COUNTER_MAX
        earOrbitAngle = -irisOffX * EAR_ORBIT_MAX
        furTopAngle = -irisOffX * FUR_TOP_COUNTER_MAX * 0.5f
        faceOffX = irisOffX * r * FACE_FOLLOW_X_K
        faceOffY = irisOffY * r * FACE_FOLLOW_Y_K

        // Idle yawn
        if (currentAnim == CatAnim.Default) {
            framesSinceTrigger++
            if (framesSinceTrigger >= YAWN_THRESHOLD) {
                framesSinceTrigger = 0
                startAnim(CatAnim.Yawn)
            }
        } else {
            framesSinceTrigger = 0
        }

        // Advance animation
        if (currentAnim != CatAnim.Default) {
            animFrame++
            val duration = when (currentAnim) {
                CatAnim.AlmostHit -> ANIM_ALMOST_HIT
                CatAnim.JustHit -> ANIM_JUST_HIT
                CatAnim.Celebration -> ANIM_CELEBRATION
                CatAnim.Chatter -> ANIM_CHATTER
                CatAnim.Yawn -> ANIM_YAWN
                else -> 0
            }
            if (animFrame >= duration) {
                if (animLoop && currentAnim == CatAnim.Celebration && Settings.gameOver) {
                    animFrame = 0
                } else {
                    currentAnim = CatAnim.Default
                    animFrame = 0
                    animLoop = false
                }
            }
        }

        val canvas = drawContext.canvas
        canvas.save()
        canvas.translate(renderer.x, renderer.y)
        if (renderer.isHigh) canvas.scale(-1f, -1f)

        shadowDx = lerp(shadowDx, irisOffX * r * SHADOW_LATERAL_K, 0.12f)

        // Draw order: ears behind -> body -> fur -> face
        drawEarsForState()
        drawBodyLayer()
        drawSideFurForState()
        drawFurTopForState()

        withTransform({ translate(faceOffX, faceOffY) }) {
            drawEyesLayer()
            drawIrisLayer()
            drawMouthForState()
        }

        canvas.restore()
    }

    // ── iris direction ─────────────────────────────────────────────────────────

    private fun computeIrisOffset() {
        val useThreat = currentAnim == CatAnim.AlmostHit &&
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

    // ── ears (drawn behind body) ──────────────────────────────────────────────

    private fun DrawScope.drawEarsForState() {
        val droopyTarget = when (currentAnim) {
            CatAnim.AlmostHit, CatAnim.JustHit ->
                easeIn(animFrame.toFloat(), 8f)
            else -> 0f
        }
        val flaredTarget = when (currentAnim) {
            CatAnim.Celebration ->
                easeIn(animFrame.toFloat(), 8f)
            else -> 0f
        }
        earDroopyBlend = lerp(earDroopyBlend, droopyTarget, 0.12f)
        earFlaredBlend = lerp(earFlaredBlend, flaredTarget, 0.12f)

        val secondary = Color(frameColors.secondary)
        val primary = Color(frameColors.primary)

        // Left ear
        withTransform({ rotate(earOrbitAngle, pivot = Offset.Zero) }) {
            val rot = computeEarRotation(-15f + earFollowAngle)
            drawEarPart(CatSkinPainters.earL1, r * EAR_L_CX_K, r * EAR_L_CY_K,
                r * EAR_W_K, r * EAR_H_K, rot, secondary)
            drawEarPart(CatSkinPainters.earL2, r * EAR_INNER_L_CX_K, r * EAR_INNER_L_CY_K,
                r * EAR_INNER_W_K, r * EAR_INNER_H_K, rot, primary)
        }

        // Right ear
        withTransform({ rotate(earOrbitAngle, pivot = Offset.Zero) }) {
            val rot = computeEarRotation(15f + earFollowAngle)
            drawEarPart(CatSkinPainters.earR1, r * EAR_R_CX_K, r * EAR_R_CY_K,
                r * EAR_W_K, r * EAR_H_K, rot, secondary)
            drawEarPart(CatSkinPainters.earR2, r * EAR_INNER_R_CX_K, r * EAR_INNER_R_CY_K,
                r * EAR_INNER_W_K, r * EAR_INNER_H_K, rot, primary)
        }
    }

    private fun computeEarRotation(baseRot: Float): Float {
        return when {
            earDroopyBlend > 0.001f ->
                lerp(baseRot, baseRot * 1.36f + kotlin.math.sign(baseRot) * 10f, earDroopyBlend)
            earFlaredBlend > 0.001f ->
                lerp(baseRot, baseRot - kotlin.math.sign(baseRot) * 10f, earFlaredBlend)
            else -> baseRot
        }
    }

    private fun DrawScope.drawEarPart(
        painter: Painter?, cx: Float, cy: Float, w: Float, h: Float,
        rotDeg: Float, tint: Color
    ) {
        if (painter == null) return
        val anchorY = cy + h * 0.5f
        val filter = ColorFilter.tint(tint)
        withTransform({
            translate(cx - w / 2f, cy - h / 2f)
            val pivot = Offset(w / 2f, anchorY - (cy - h / 2f))
            if (rotDeg != 0f) rotate(rotDeg, pivot = pivot)
        }) {
            with(painter) { draw(Size(w, h), colorFilter = filter) }
        }
    }

    // ── body ──────────────────────────────────────────────────────────────────

    private fun DrawScope.drawBodyLayer() {
        val body = CatSkinPainters.body
        val secondary = Color(frameColors.secondary)
        val bounds = Rect(-r * 1.2f, -r * 1.2f, r * 1.2f, r * 1.2f)
        drawContext.canvas.saveLayer(bounds, Paint())
        if (body != null) {
            val w = r * BODY_DIAM_K
            drawSvgPart(body, 0f, 0f, w, w, tint = secondary)
        } else {
            drawCircle(secondary, r, Offset.Zero)
        }
        val litR = r * SHADOW_LIT_BODY_R
        val litPath = Path().apply {
            addOval(Rect(
                shadowDx - litR,
                -r * SHADOW_LIT_BODY_ABOVE_K - litR,
                shadowDx + litR,
                -r * SHADOW_LIT_BODY_ABOVE_K + litR
            ))
        }
        withTransform({ clipPath(litPath, ClipOp.Difference) }) {
            drawRect(
                color = Color(0f, 0f, 0f, SHADOW_ALPHA),
                topLeft = Offset(-r * 1.2f, -r * 1.2f),
                size = Size(r * 2.4f, r * 2.4f),
                blendMode = BlendMode.SrcAtop
            )
        }
        drawContext.canvas.restore()
    }

    // ── side fur (wind-only animation) ────────────────────────────────────────

    private fun DrawScope.drawSideFurForState() {
        val amplitude = when (currentAnim) {
            CatAnim.Celebration -> 10f
            else -> 6f
        }
        val leftAngle = sin(furPhase) * amplitude
        val rightAngle = sin(furPhase + PI.toFloat()) * amplitude

        val secondary = Color(frameColors.secondary)

        // Left side fur with shadow
        drawSideFurPair(
            CatSkinPainters.furL1, r * FUR_L1_CX_K, r * FUR_L1_CY_K, r * FUR_L1_W_K, r * FUR_L1_H_K,
            CatSkinPainters.furL2, r * FUR_L2_CX_K, r * FUR_L2_CY_K, r * FUR_L2_W_K, r * FUR_L2_H_K,
            leftAngle, secondary, isLeft = true
        )

        // Right side fur with shadow
        drawSideFurPair(
            CatSkinPainters.furR1, r * FUR_R1_CX_K, r * FUR_R1_CY_K, r * FUR_R1_W_K, r * FUR_R1_H_K,
            CatSkinPainters.furR2, r * FUR_R2_CX_K, r * FUR_R2_CY_K, r * FUR_R2_W_K, r * FUR_R2_H_K,
            rightAngle, secondary, isLeft = false
        )
    }

    private fun DrawScope.drawSideFurPair(
        outer: Painter?, outerCx: Float, outerCy: Float, outerW: Float, outerH: Float,
        inner: Painter?, innerCx: Float, innerCy: Float, innerW: Float, innerH: Float,
        angle: Float, tint: Color, isLeft: Boolean
    ) {
        val maxW = maxOf(outerW, innerW) + r * 0.3f
        val maxH = maxOf(outerH, innerH) + r * 0.3f
        val groupCx = (outerCx + innerCx) / 2f
        val groupCy = (outerCy + innerCy) / 2f
        val bounds = Rect(groupCx - maxW, groupCy - maxH, groupCx + maxW, groupCy + maxH)

        drawContext.canvas.saveLayer(bounds, Paint())
        withTransform({ rotate(angle, pivot = Offset(groupCx, groupCy + maxH * 0.3f)) }) {
            val filter = ColorFilter.tint(tint)
            if (outer != null) {
                withTransform({ translate(outerCx - outerW / 2f, outerCy - outerH / 2f) }) {
                    with(outer) { draw(Size(outerW, outerH), colorFilter = filter) }
                }
            }
            if (inner != null) {
                withTransform({ translate(innerCx - innerW / 2f, innerCy - innerH / 2f) }) {
                    with(inner) { draw(Size(innerW, innerH), colorFilter = filter) }
                }
            }
        }

        val sign = if (isLeft) -1f else 1f
        val growFactor = ((sign * irisOffX + 1f) / 2f).coerceIn(0f, 1f)
        val litR = r * SHADOW_LIT_FUR_R_MIN * lerp(1f, 2f, growFactor)
        val litCx = groupCx + shadowDx
        val litCy = groupCy - r * SHADOW_LIT_FUR_ABOVE_K
        val litPath = Path().apply {
            addOval(Rect(litCx - litR, litCy - litR, litCx + litR, litCy + litR))
        }
        withTransform({ clipPath(litPath, ClipOp.Difference) }) {
            drawRect(
                color = Color(0f, 0f, 0f, SHADOW_ALPHA),
                topLeft = Offset(bounds.left, bounds.top),
                size = Size(bounds.width, bounds.height),
                blendMode = BlendMode.SrcAtop
            )
        }
        drawContext.canvas.restore()
    }

    // ── fur top (head decoration) ─────────────────────────────────────────────

    private fun DrawScope.drawFurTopForState() {
        val painter = CatSkinPainters.furTop ?: return
        val cx = r * FUR_TOP_CX_K
        val cy = r * FUR_TOP_CY_K
        val w = r * FUR_TOP_W_K
        val h = r * FUR_TOP_H_K

        val fBounds = Rect(cx - w, cy - h, cx + w, cy + h)
        drawContext.canvas.saveLayer(fBounds, Paint())
        withTransform({
            translate(cx - w / 2f, cy - h / 2f)
            val pivot = Offset(w / 2f, h)
            if (furTopAngle != 0f) rotate(furTopAngle, pivot = pivot)
        }) {
            with(painter) { draw(Size(w, h), colorFilter = ColorFilter.tint(Color(frameColors.secondary))) }
        }

        val litPath = Path().apply {
            addOval(Rect(
                -r * SHADOW_FUR_TOP_LIT_R,
                cy - r * SHADOW_FUR_TOP_LIT_R,
                r * SHADOW_FUR_TOP_LIT_R,
                cy + r * SHADOW_FUR_TOP_LIT_R
            ))
        }
        withTransform({ clipPath(litPath, ClipOp.Difference) }) {
            drawRect(
                color = Color(0f, 0f, 0f, SHADOW_ALPHA),
                topLeft = Offset(fBounds.left, fBounds.top),
                size = Size(fBounds.width, fBounds.height),
                blendMode = BlendMode.SrcAtop
            )
        }
        drawContext.canvas.restore()
    }

    // ── eyes ──────────────────────────────────────────────────────────────────

    private fun DrawScope.drawEyesLayer() {
        when (currentAnim) {
            CatAnim.AlmostHit -> {
                val t = easeIn(animFrame.toFloat(), 8f)
                val scX = lerp(1f, 0.8f, t)
                val scY = lerp(1f, 1.3f, t)
                drawOpenEyes(scX, scY)
            }
            CatAnim.JustHit -> drawClosedEyes()
            CatAnim.Celebration -> {
                val t = easeIn(animFrame.toFloat(), 8f)
                val scX = lerp(1f, 1.3f, t)
                val scY = lerp(1f, 0.75f, t)
                drawOpenEyes(scX, scY)
                val secondary = Color(frameColors.secondary)
                drawRect(secondary, topLeft = Offset(
                    r * EYE_L_CX_K - r * EYE_WHITE_W_K * scX / 2f,
                    r * EYE_L_CY_K + r * EYE_WHITE_H_K * scY * 0.1f),
                    size = Size(r * EYE_WHITE_W_K * scX, r * EYE_WHITE_H_K * scY * 0.6f))
                drawRect(secondary, topLeft = Offset(
                    r * EYE_R_CX_K - r * EYE_WHITE_W_K * scX / 2f,
                    r * EYE_R_CY_K + r * EYE_WHITE_H_K * scY * 0.1f),
                    size = Size(r * EYE_WHITE_W_K * scX, r * EYE_WHITE_H_K * scY * 0.6f))
            }
            CatAnim.Yawn -> drawClosedEyes()
            CatAnim.Chatter -> drawOpenEyes(1f, 1f)
            else -> if (eyeOpen) drawOpenEyes(1f, 1f) else drawClosedEyes()
        }
    }

    private fun DrawScope.drawOpenEyes(scaleX: Float, scaleY: Float) {
        val eyeL1 = CatSkinPainters.eyeOpenL1
        val eyeR1 = CatSkinPainters.eyeOpenR1
        if (eyeL1 != null) {
            drawSvgPart(eyeL1, r * EYE_L_CX_K, r * EYE_L_CY_K,
                r * EYE_WHITE_W_K, r * EYE_WHITE_H_K, scaleX = scaleX, scaleY = scaleY)
        }
        if (eyeR1 != null) {
            drawSvgPart(eyeR1, r * EYE_R_CX_K, r * EYE_R_CY_K,
                r * EYE_WHITE_W_K, r * EYE_WHITE_H_K, scaleX = scaleX, scaleY = scaleY)
        }
    }

    private fun DrawScope.drawClosedEyes() {
        val closedL = CatSkinPainters.eyeClosedL
        val closedR = CatSkinPainters.eyeClosedR
        val tint = Color(frameColors.primary)
        if (closedL != null) {
            drawSvgPart(closedL, r * EYE_CLOSED_L_CX_K, r * EYE_CLOSED_L_CY_K,
                r * EYE_CLOSED_W_K, r * EYE_CLOSED_H_K, tint = tint)
        }
        if (closedR != null) {
            drawSvgPart(closedR, r * EYE_CLOSED_R_CX_K, r * EYE_CLOSED_R_CY_K,
                r * EYE_CLOSED_W_K, r * EYE_CLOSED_H_K, tint = tint)
        }
    }

    // ── iris (pupils on top of eyes) ──────────────────────────────────────────

    private fun DrawScope.drawIrisLayer() {
        val showIris = when (currentAnim) {
            CatAnim.JustHit, CatAnim.Yawn -> false
            CatAnim.Default -> eyeOpen
            else -> true
        }
        if (!showIris) return

        val irisL = CatSkinPainters.eyeOpenL2
        val irisR = CatSkinPainters.eyeOpenR2
        val maxOff = r * IRIS_MAX_FOLLOW_K
        val offX = irisOffX * maxOff
        val offY = irisOffY * maxOff

        if (irisL != null) {
            drawSvgPart(irisL, r * IRIS_L_CX_K + offX, r * IRIS_L_CY_K + offY,
                r * EYE_IRIS_W_K, r * EYE_IRIS_H_K)
        }
        if (irisR != null) {
            drawSvgPart(irisR, r * IRIS_R_CX_K + offX, r * IRIS_R_CY_K + offY,
                r * EYE_IRIS_W_K, r * EYE_IRIS_H_K)
        }
    }

    // ── mouth ─────────────────────────────────────────────────────────────────

    private fun DrawScope.drawMouthForState() {
        when {
            currentAnim == CatAnim.Chatter -> drawMouthChatter()
            currentAnim == CatAnim.AlmostHit -> drawMouthOpen()
            currentAnim == CatAnim.JustHit -> drawMouthClosed()
            currentAnim == CatAnim.Celebration -> drawMouthOpen()
            currentAnim == CatAnim.Yawn -> drawMouthOpen(yawn = true)
            else -> drawMouthClosed()
        }
    }

    private fun DrawScope.drawMouthChatter() {
        if ((animFrame / 5) % 2 == 0) {
            drawMouthOpen()
        } else {
            drawMouthClosed()
        }
    }

    private fun DrawScope.drawMouthClosed() {
        val painter = CatSkinPainters.mouthClosed ?: return
        drawSvgPart(painter, r * MOUTH_CX_K, r * MOUTH_CLOSED_CY_K,
            r * MOUTH_CLOSED_W_K, r * MOUTH_CLOSED_H_K,
            tint = Color(frameColors.primary))
    }

    private fun DrawScope.drawMouthOpen(yawn: Boolean = false) {
        val painter1 = CatSkinPainters.mouthOpen1 ?: return
        val painter2 = CatSkinPainters.mouthOpen2

        val gapeT = easeIn(animFrame.toFloat(), if (yawn) 15f else 5f)
        val growth = if (yawn) 1.45f else 1.15f
        val scale = lerp(1f, growth, gapeT)

        val w1 = r * MOUTH_OPEN1_W_K * scale
        val h1 = r * MOUTH_OPEN1_H_K * scale
        drawSvgPart(painter1, r * MOUTH_CX_K, r * MOUTH_CY_K, w1, h1,
            tint = Color(frameColors.primary))

        if (painter2 != null) {
            val w2 = r * MOUTH_OPEN2_W_K * scale
            val h2 = r * MOUTH_OPEN2_H_K * scale
            drawSvgPart(painter2, r * MOUTH_CX_K, r * MOUTH2_CY_K, w2, h2,
                tint = Color(frameColors.secondary))
        }
    }

    // ── painter helper ────────────────────────────────────────────────────────

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
        withTransform({
            translate(cx - w / 2f, cy - h / 2f)
            val pivot = Offset(w / 2f, h / 2f)
            if (angleDeg != 0f) rotate(angleDeg, pivot = pivot)
            if (scaleX != 1f || scaleY != 1f) scale(scaleX, scaleY, pivot = pivot)
        }) {
            with(painter) { draw(Size(w, h), colorFilter = filter) }
        }
    }

    // ── PuckSkin hooks ────────────────────────────────────────────────────────

    override fun onPhaseChanged(phase: ChargePhase) {
        when (phase) {
            ChargePhase.SweetSpot -> {
                dangerFromSweetSpot = true
                startAnim(CatAnim.AlmostHit)
            }
            ChargePhase.Inert -> {
                startAnim(CatAnim.Chatter)
            }
            ChargePhase.Idle -> {
                if (lastPhase == ChargePhase.SweetSpot) {
                    startAnim(CatAnim.JustHit)
                } else if (currentAnim == CatAnim.Default || currentAnim == CatAnim.AlmostHit) {
                    currentAnim = CatAnim.Default; animFrame = 0
                }
            }
            else -> {}
        }
        lastPhase = phase
    }

    override fun onDangerNear(threatX: Float, threatY: Float) {
        if (renderer.shielded) return
        if (currentAnim == CatAnim.JustHit) return
        this.threatX = threatX
        this.threatY = threatY
        dangerFromSweetSpot = false
        startAnim(CatAnim.AlmostHit)
    }

    override fun onDangerClear() {
        threatX = Float.NaN; threatY = Float.NaN
        if (currentAnim == CatAnim.AlmostHit && !dangerFromSweetSpot) {
            currentAnim = CatAnim.Default; animFrame = 0
        }
    }

    override fun onHit() {
        if (renderer.shielded) return
        startAnim(CatAnim.JustHit)
    }

    override val explosionFrequency get() = 20

    override fun onUsedToScore(otherColor: Int, position: Point, highGoal: Boolean) {
        startAnim(CatAnim.Chatter)
    }

    override fun onScored() {
        startAnim(CatAnim.Celebration)
    }

    override fun onVictory(x: Float, y: Float) {
        animLoop = true
        startAnim(CatAnim.Celebration)
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun startAnim(anim: CatAnim) {
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
