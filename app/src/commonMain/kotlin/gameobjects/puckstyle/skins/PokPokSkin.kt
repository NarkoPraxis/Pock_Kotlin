package gameobjects.puckstyle.skins

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import gameobjects.Settings
import gameobjects.puckstyle.ChargePhase
import gameobjects.puckstyle.ColorGroup
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.PuckSkin
import gameobjects.puckstyle.paddles.ChickenLaunch
import gameobjects.puckstyle.paddles.ChickenLaunch.Companion.spawnFeatherExplosion
import physics.Point
import utility.Effects
import utility.PaintBucket
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * SVG-vector chicken skin. Same animation state machine as [ChickenSkin] but renders body parts
 * from `PokPok_Parts_*.svg` painters loaded via [PokPokSkinPainters] instead of math paths.
 *
 * ## Coordinate mapping
 *
 * Reference composition `PokPockSkin.svg` has viewBox 402.87×402.87. The chicken's body sphere
 * within that composition has approximate center (150, 190) and radius ~55.84 — i.e. the
 * standalone `Body.svg` (viewBox 111.68×111.68, single circle r=55.84) is drawn 1:1 over the
 * body sphere. Therefore world-scale factor from any standalone-part viewBox unit is
 * `r / 55.84` (the body sphere fills the puck radius).
 *
 * Anchor positions (eyes, mouth, wings, feathers) are deliberately driven by the same
 * `r`-relative offsets used in [ChickenSkin] rather than measured from composition coords, so
 * the animation skeleton lines up with the reused [ChickenLaunch] paddle and the existing
 * `ChickenTail`.
 */
class PokPokSkin(override val renderer: PuckRenderer) : PuckSkin {

    // ── part world sizes (multiples of radius) ─────────────────────────────────
    // All parts are sized proportionally to the body: (standalone size / 55.84) × r,
    // where 55.84 is the standalone Body.svg radius. The body fills the puck (diameter 2r),
    // so every other part inherits the same world-per-svg scale.
    private val BODY_WORLD_DIAM_K = 2f                       // × r

    // Eyes_Opened.svg: 83.23 × 42.98.
    private val EYES_WORLD_W_K = 83.23f / 55.84f             // ≈ 1.491 r
    private val EYES_WORLD_H_K = 42.98f / 55.84f             // ≈ 0.770 r
    private val EYES_OFFSET_Y_K = -0.18f                     // slightly above body center
    // Vertical offset from the eye-art center to the built-in pupil center (XML pupils sit
    // in the lower half of the eye-whites art). Used to align the dynamic iris dots.
    private val PUPIL_OFFSET_Y_K = (30f - 42.98f / 2f) / 42.98f * EYES_WORLD_H_K  // ≈ +0.152 r

    // Mouth_Open.svg: 29.07 × 29.59.
    private val MOUTH_W_K = 29.07f / 55.84f                  // ≈ 0.521 r
    private val MOUTH_H_K = 29.59f / 55.84f                  // ≈ 0.530 r
    // Mouth_Closed.svg: 24.23 × 32.08 (taller, narrower teardrop than Mouth_Open).
    private val MOUTH_CLOSED_W_K = 24.23f / 55.84f           // ≈ 0.434 r
    private val MOUTH_CLOSED_H_K = 32.08f / 55.84f           // ≈ 0.575 r
    // Beak top in the SVG composition sits at body center; center-y therefore ≈ half-height.
    private val MOUTH_OFFSET_Y_K = 0.29f

    // Head feathers — side art is square 28.9, middle is 24.46×38.99.
    private val FEATHER_SIDE_W_K   = 28.9f  / 55.84f         // ≈ 0.518 r
    private val FEATHER_SIDE_H_K   = 28.9f  / 55.84f
    private val FEATHER_MID_W_K    = 24.46f / 55.84f         // ≈ 0.438 r
    private val FEATHER_MID_H_K    = 38.99f / 55.84f         // ≈ 0.698 r
    // Feathers form a crown at the top of the body. Anchor (bottom-center of art) sits at
    // the body top curve, so the art's centre-y is one half-height above that anchor.
    private val FEATHER_MID_CY_K   = -1f - FEATHER_MID_H_K / 2f      // ≈ -1.349 r
    private val FEATHER_SIDE_CX_K  =  0.30f                          // left = -, right = +
    private val FEATHER_SIDE_CY_K  = -0.95f - FEATHER_SIDE_H_K / 2f  // ≈ -1.209 r
    private val FEATHER_SIDE_ROT   = 30f                             // degrees outward tilt

    // Wing native 28.9×28.9 — proportional to body.
    private val WING_W_K = 28.9f / 55.84f                    // ≈ 0.518 r
    private val WING_H_K = 28.9f / 55.84f
    private val WING_PIVOT_X_K = 0.85f                       // shoulder pivot (just inside body edge)
    private val WING_CENTER_OFFSET = 0.45f                   // wing center sits this far past the pivot

    // ── cached per-frame state ─────────────────────────────────────────────────
    private var cachedRadius = -1f
    private var r = 0f
    private var eyeR = 0f
    private var eyeX = 0f

    private fun ensureCache() {
        val newR = renderer.radius
        if (cachedRadius != newR) {
            cachedRadius = newR
            eyeR = newR * 0.25f
            // Pupil horizontal spacing matches the XML art (pupil centres sit at ±0.176 of the
            // eye-art width, which becomes ±0.262 r given EYES_WORLD_W_K).
            eyeX = newR * (0.176f * EYES_WORLD_W_K)
        }
    }

    // ── wing flap ──────────────────────────────────────────────────────────────
    private var wingPhase = 0f
    private var displayedWingAngle = 0f
    private var lastX = Float.NaN
    private var lastY = Float.NaN

    // ── blink ──────────────────────────────────────────────────────────────────
    private var blinkCountdown = Random.nextInt(60, 181)
    private var blinkFrame = 0
    private val BLINK_DURATION = 4

    // ── animation state machine ────────────────────────────────────────────────
    private enum class PokPokAnim { Default, AlmostHit, JustHit, Celebration, Taunt, Yawn }

    private var currentAnim = PokPokAnim.Default
    private var animFrame   = 0
    private var animLoop    = false
    private var dangerFromSweetSpot = false
    private var lastPhase   = ChargePhase.Idle
    private var winkRight   = true

    // Frames since any reactive animation last triggered (drives the idle Yawn).
    private var framesSinceTrigger = 0

    // Threat coords in world space; NaN when no active danger.
    private var threatX = Float.NaN
    private var threatY = Float.NaN

    // Durations (frames)
    private val ANIM_ALMOST_HIT   = 35
    private val ANIM_JUST_HIT     = 30
    private val ANIM_CELEBRATION  = 50
    private val ANIM_TAUNT        = 40
    private val ANIM_YAWN         = 100
    private val YAWN_THRESHOLD    = 30000

    // Stagger offsets
    private val DELAY_MOUTH   = 3
    private val DELAY_WINGS   = 10
    private val DELAY_FEATHERS = 10

    // Per-frame state hoisted so sub-draw methods can read them
    private var frameColors: ColorGroup = theme.main
    private var irisOffX = 0f
    private var irisOffY = 0f
    private var wingAngle = 0f
    private var eyeOpen = true

    override fun DrawScope.drawBody() {
        ensureCache()
        frameColors = responsiveGroup

        // Movement tracking for wing flap
        if (lastX.isNaN()) { lastX = renderer.x; lastY = renderer.y }
        val speed = hypot(renderer.x - lastX, renderer.y - lastY)
        lastX = renderer.x; lastY = renderer.y
        wingPhase += speed * 0.012f
        wingAngle = sin(wingPhase) * 22f
        val targetWingAngle = when (currentAnim) {
            PokPokAnim.AlmostHit, PokPokAnim.JustHit -> 0f
            PokPokAnim.Celebration -> 22f
            PokPokAnim.Yawn -> 22f
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

        // Frames-since-trigger drives the idle Yawn.
        if (currentAnim == PokPokAnim.Default) {
            framesSinceTrigger++
            if (framesSinceTrigger >= YAWN_THRESHOLD) {
                framesSinceTrigger = 0
                startAnim(PokPokAnim.Yawn)
            }
        } else {
            framesSinceTrigger = 0
        }

        // Advance animation frame and handle expiry
        if (currentAnim != PokPokAnim.Default) {
            animFrame++
            val duration = when (currentAnim) {
                PokPokAnim.AlmostHit   -> ANIM_ALMOST_HIT
                PokPokAnim.JustHit     -> ANIM_JUST_HIT
                PokPokAnim.Celebration -> ANIM_CELEBRATION
                PokPokAnim.Taunt       -> ANIM_TAUNT
                PokPokAnim.Yawn        -> ANIM_YAWN
                else                   -> 0
            }
            if (animFrame >= duration) {
                if (animLoop && currentAnim == PokPokAnim.Celebration && Settings.gameOver) {
                    animFrame = 0
                } else {
                    currentAnim = PokPokAnim.Default
                    animFrame   = 0
                    animLoop    = false
                }
            }
        }

        val canvas = drawContext.canvas
        canvas.save()
        canvas.translate(renderer.x, renderer.y)
        if (renderer.isHigh) canvas.scale(-1f, -1f)

        r = cachedRadius

        // Z-order, bottom to top: wings (behind body) → body → head feathers → eyes → mouth.
        drawWingsForState()
        drawBodyLayer()
        drawFeathersForState()
        drawEyesForState()
        drawMouthForState()

        canvas.restore()
    }

    // ── iris direction ─────────────────────────────────────────────────────────

    private fun computeIrisOffset() {
        val useThreat = currentAnim == PokPokAnim.AlmostHit &&
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
                wx = 0f; wy = renderer.radius
            }
        }
        val dist = hypot(wx, wy).coerceAtLeast(0.001f)
        val nX = wx / dist; val nY = wy / dist
        irisOffX = if (renderer.isHigh) -nX else nX
        irisOffY = if (renderer.isHigh) -nY else nY
    }

    // ── body ───────────────────────────────────────────────────────────────────

    private fun DrawScope.drawBodyLayer() {
        // Body SVG is a flat-fill circle tinted to the live primary colour. Falls back to a
        // plain disc if the painter hasn't loaded yet so the puck never renders empty.
        val body = PokPokSkinPainters.body
        val primary = Color(frameColors.primary)
        if (body != null) {
            val w = r * BODY_WORLD_DIAM_K
            drawSvgPart(body, 0f, 0f, w, w, tint = primary)
        } else {
            drawCircle(primary, r, Offset.Zero)
        }
    }

    // ── wings ──────────────────────────────────────────────────────────────────

    private fun DrawScope.drawWingsForState() {
        val baseScale = when (currentAnim) {
            PokPokAnim.AlmostHit, PokPokAnim.JustHit -> {
                val t = if (animFrame >= DELAY_WINGS) easeIn((animFrame - DELAY_WINGS).toFloat(), 3f) else 0f
                lerp(1f, 0.82f, t)
            }
            PokPokAnim.Celebration -> {
                val t = if (animFrame >= DELAY_WINGS) easeIn((animFrame - DELAY_WINGS).toFloat(), 8f) else 0f
                lerp(1f, 1.1f, t)
            }
            PokPokAnim.Yawn -> {
                val t = easeIn(animFrame.toFloat(), 8f)
                lerp(1f, 1.1f, t)
            }
            else -> 1f
        }
        drawWing(left = true,  angleDeg = displayedWingAngle, scale = baseScale)
        drawWing(left = false, angleDeg = displayedWingAngle, scale = baseScale)
    }

    private fun DrawScope.drawWing(left: Boolean, angleDeg: Float, scale: Float) {
        val painter = if (left) PokPokSkinPainters.wingLeft else PokPokSkinPainters.wingRight
        if (painter == null) return
        val sign = if (left) -1f else 1f
        val pivotX = sign * r * WING_PIVOT_X_K
        val centerX = pivotX + sign * r * WING_CENTER_OFFSET
        val centerY = 0f
        val w = r * WING_W_K
        val h = r * WING_H_K
        val rotation = sign * -angleDeg
        withTransform({
            rotate(rotation, pivot = Offset(pivotX, 0f))
        }) {
            drawSvgPart(painter, centerX, centerY, w, h, scaleX = scale, scaleY = scale,
                tint = Color(frameColors.primary))
        }
    }

    // ── feathers ───────────────────────────────────────────────────────────────

    private fun DrawScope.drawFeathersForState() {
        val droopy = currentAnim == PokPokAnim.AlmostHit || currentAnim == PokPokAnim.JustHit
        val flared = currentAnim == PokPokAnim.Celebration
        val t = when {
            droopy -> if (animFrame >= DELAY_FEATHERS) easeIn((animFrame - DELAY_FEATHERS).toFloat(), 8f) else 0f
            flared -> if (animFrame >= DELAY_FEATHERS) easeIn((animFrame - DELAY_FEATHERS).toFloat(), 8f) else 0f
            else   -> 0f
        }

        // Middle feather first so the side feathers overlap it (matches composition layering).
        drawHeadFeather(
            painter = PokPokSkinPainters.featherMiddle,
            cx = 0f,
            cy = r * FEATHER_MID_CY_K,
            w = r * FEATHER_MID_W_K,
            h = r * FEATHER_MID_H_K,
            rotDeg = 0f,
            droopy = droopy, flared = flared, t = t
        )
        drawHeadFeather(
            painter = PokPokSkinPainters.featherLeft,
            cx = -r * FEATHER_SIDE_CX_K,
            cy = r * FEATHER_SIDE_CY_K,
            w = r * FEATHER_SIDE_W_K,
            h = r * FEATHER_SIDE_H_K,
            rotDeg = -FEATHER_SIDE_ROT,
            droopy = droopy, flared = flared, t = t
        )
        drawHeadFeather(
            painter = PokPokSkinPainters.featherRight,
            cx = r * FEATHER_SIDE_CX_K,
            cy = r * FEATHER_SIDE_CY_K,
            w = r * FEATHER_SIDE_W_K,
            h = r * FEATHER_SIDE_H_K,
            rotDeg = FEATHER_SIDE_ROT,
            droopy = droopy, flared = flared, t = t
        )
    }

    private fun DrawScope.drawHeadFeather(
        painter: Painter?,
        cx: Float, cy: Float,
        w: Float, h: Float,
        rotDeg: Float,
        droopy: Boolean, flared: Boolean, t: Float
    ) {
        if (painter == null) return
        // Anchor point: bottom-center of the feather where it meets the head.
        val anchorY = cy + h * 0.5f
        val rot: Float
        val scaleX: Float
        val scaleY: Float
        when {
            droopy -> {
                rot    = lerp(rotDeg, rotDeg * 1.36f, t)
                scaleX = 1f
                scaleY = lerp(1f, 0.75f, t)
            }
            flared -> {
                rot    = rotDeg
                scaleX = lerp(1f, 1.15f, t)
                scaleY = lerp(1f, 1.25f, t)
            }
            else -> {
                rot = rotDeg; scaleX = 1f; scaleY = 1f
            }
        }
        val tint = ColorFilter.tint(Color(frameColors.primary))
        withTransform({
            translate(cx - w / 2f, cy - h / 2f)
            val pivot = Offset(w / 2f, anchorY - (cy - h / 2f))
            if (rot != 0f) rotate(rot, pivot = pivot)
            if (scaleX != 1f || scaleY != 1f) scale(scaleX, scaleY, pivot = pivot)
        }) {
            with(painter) { draw(Size(w, h), colorFilter = tint) }
        }
    }

    // ── eyes ───────────────────────────────────────────────────────────────────

    private fun DrawScope.drawEyesForState() {
        when (currentAnim) {
            PokPokAnim.Taunt       -> drawWinkEyes()
            PokPokAnim.AlmostHit   -> drawWideEyes()
            PokPokAnim.JustHit     -> drawWinceEyes()
            PokPokAnim.Celebration -> drawHappyEyes()
            PokPokAnim.Yawn        -> drawClosedEyes()
            else                   -> if (eyeOpen) drawOpenEyes() else drawClosedEyes()
        }
    }

    private fun DrawScope.drawOpenEyes(scaleX: Float = 1f, scaleY: Float = 1f) {
        val painter = PokPokSkinPainters.eyesOpen ?: return
        val w = r * EYES_WORLD_W_K
        val h = r * EYES_WORLD_H_K
        val cy = r * EYES_OFFSET_Y_K
        drawSvgPart(painter, 0f, cy, w, h, scaleX = scaleX, scaleY = scaleY)
        // Iris overlay — a small solid circle per eye that drifts with paddle direction.
        // Centred on the XML's built-in pupil location so the overlay and underlying art line up.
        val maxOff = eyeR * 0.45f
        val ix = irisOffX * maxOff
        val iy = irisOffY * maxOff
        val pupilR = eyeR * 0.30f
        val leftEyeCx  = -eyeX
        val rightEyeCx =  eyeX
        val pupilCy = cy + r * PUPIL_OFFSET_Y_K
        drawCircle(Color(frameColors.secondary), pupilR,
            Offset(leftEyeCx  + ix, pupilCy + iy))
        drawCircle(Color(frameColors.secondary), pupilR,
            Offset(rightEyeCx + ix, pupilCy + iy))
    }

    private fun DrawScope.drawClosedEyes() {
        val painter = PokPokSkinPainters.eyesClosed ?: return
        val w = r * EYES_WORLD_W_K
        val h = r * EYES_WORLD_H_K
        val cy = r * EYES_OFFSET_Y_K
        drawSvgPart(painter, 0f, cy, w, h)
    }

    private fun DrawScope.drawWideEyes() {
        val t = easeIn(animFrame.toFloat(), 8f)
        drawOpenEyes(scaleX = lerp(1f, 0.75f, t), scaleY = lerp(1f, 1.35f, t))
    }

    private fun DrawScope.drawWinceEyes() {
        val painter = PokPokSkinPainters.eyesClosed ?: return
        val w = r * EYES_WORLD_W_K
        val h = r * EYES_WORLD_H_K
        val cy = r * EYES_OFFSET_Y_K
        val decay   = 1f - animFrame.toFloat() / ANIM_JUST_HIT
        val shudder = sin(animFrame * 1.5f) * r * 0.02f * decay
        drawSvgPart(painter, 0f, cy + shudder, w, h)
    }

    private fun DrawScope.drawHappyEyes() {
        val t = easeIn(animFrame.toFloat(), 8f)
        val scaleX = lerp(1f, 1.3f, t)
        val scaleY = lerp(1f, 0.75f, t)
        val cy = r * EYES_OFFSET_Y_K
        val w = r * EYES_WORLD_W_K
        val h = r * EYES_WORLD_H_K
        drawOpenEyes(scaleX = scaleX, scaleY = scaleY)
        // Overdraw lower half of the eye region with the body color to create a grin shape.
        withTransform({ scale(scaleX, scaleY, pivot = Offset(0f, cy)) }) {
            drawRect(
                Color(frameColors.primary),
                topLeft = Offset(-w / 2f, cy + h * 0.1f),
                size = Size(w, h * 0.6f)
            )
        }
    }

    /** Wink: alternates between eyes each Taunt; the closed eye reopens after frame 25. */
    private fun DrawScope.drawWinkEyes() {
        // Draw both eyes open first (with iris), then overlay just one eye with eyesClosed.
        drawOpenEyes()
        val painter = PokPokSkinPainters.eyesClosed ?: return
        val w = r * EYES_WORLD_W_K
        val h = r * EYES_WORLD_H_K
        val cy = r * EYES_OFFSET_Y_K
        if (animFrame > 25) return
        // The eyes SVG is a single combined drawable. To wink only one side, clip to half.
        withTransform({
            val halfW = w / 2f
            val left = if (winkRight) 0f else -halfW
            clipRect(left, cy - h / 2f, left + halfW, cy + h / 2f)
        }) {
            drawSvgPart(painter, 0f, cy, w, h)
        }
    }

    // ── mouth ──────────────────────────────────────────────────────────────────

    private fun DrawScope.drawMouthForState() {
        when {
            currentAnim == PokPokAnim.AlmostHit   && animFrame >= DELAY_MOUTH -> drawMouthGape()
            currentAnim == PokPokAnim.JustHit                                  -> drawMouthGrimace()
            currentAnim == PokPokAnim.Celebration && animFrame >= DELAY_MOUTH -> drawMouthGape()
            currentAnim == PokPokAnim.Taunt       && animFrame >= DELAY_MOUTH -> drawMouthGape()
            currentAnim == PokPokAnim.Yawn                                    -> drawMouthGape(yawn = true)
            else -> drawMouthClosed()
        }
    }

    private fun DrawScope.drawMouthClosed() {
        val painter = PokPokSkinPainters.mouthClosed ?: return
        val w = r * MOUTH_CLOSED_W_K
        val h = r * MOUTH_CLOSED_H_K
        drawSvgPart(painter, 0f, r * MOUTH_OFFSET_Y_K, w, h,
            tint = Color(frameColors.secondary))
    }

    private fun DrawScope.drawMouthGape(yawn: Boolean = false) {
        val painter = PokPokSkinPainters.mouthOpen ?: return
        val lf = (animFrame - DELAY_MOUTH).coerceAtLeast(0)
        val gapeT = easeIn(lf.toFloat(), if (yawn) 15f else 5f)
        val growth = if (yawn) 1.45f else 1.15f
        val w = r * MOUTH_W_K * lerp(1f, growth, gapeT)
        val h = r * MOUTH_H_K * lerp(1f, growth, gapeT)
        drawSvgPart(painter, 0f, r * MOUTH_OFFSET_Y_K, w, h,
            tint = Color(frameColors.secondary))
    }

    private fun DrawScope.drawMouthGrimace() {
        val painter = PokPokSkinPainters.mouthClosed ?: return
        val t = easeIn(animFrame.toFloat(), 6f)
        val w = r * MOUTH_CLOSED_W_K * lerp(1f, 0.8f, t)
        val h = r * MOUTH_CLOSED_H_K * lerp(1f, 1.2f, t)
        drawSvgPart(painter, 0f, r * MOUTH_OFFSET_Y_K, w, h,
            tint = Color(frameColors.secondary))
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
        withTransform({
            translate(cx - w / 2f, cy - h / 2f)
            val pivot = Offset(w / 2f, h / 2f)
            if (angleDeg != 0f) rotate(angleDeg, pivot = pivot)
            if (scaleX != 1f || scaleY != 1f) scale(scaleX, scaleY, pivot = pivot)
        }) {
            with(painter) { draw(Size(w, h), colorFilter = filter) }
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
                startAnim(PokPokAnim.AlmostHit)
            }
            ChargePhase.Inert -> {
                winkRight = !winkRight
                startAnim(PokPokAnim.Taunt)
            }
            ChargePhase.Idle -> {
                if (lastPhase == ChargePhase.SweetSpot) {
                    startAnim(PokPokAnim.JustHit)
                } else if (currentAnim == PokPokAnim.Default || currentAnim == PokPokAnim.AlmostHit) {
                    currentAnim = PokPokAnim.Default; animFrame = 0
                }
            }
            else -> {}
        }
        lastPhase = phase
    }

    override fun onDangerNear(threatX: Float, threatY: Float) {
        if (renderer.shielded) return
        if (currentAnim == PokPokAnim.JustHit) return
        this.threatX = threatX
        this.threatY = threatY
        dangerFromSweetSpot = false
        startAnim(PokPokAnim.AlmostHit)
    }

    override fun onDangerClear() {
        threatX = Float.NaN; threatY = Float.NaN
        if (currentAnim == PokPokAnim.AlmostHit && !dangerFromSweetSpot) {
            currentAnim = PokPokAnim.Default; animFrame = 0
        }
    }

    override fun onHit() {
        if (renderer.shielded) return
        startAnim(PokPokAnim.JustHit)
    }

    override val explosionFrequency get() = 20

    override fun onUsedToScore(otherColor: Int, position: Point, highGoal: Boolean) {
        startAnim(PokPokAnim.Taunt)
        Effects.addPersistentEffect(
            FeatherCelebration(position.x, position.y, renderer.radius, highGoal,
                theme.main.primary, theme.main.secondary, fullCircle = false)
        )
    }

    override fun onScored() {
        startAnim(PokPokAnim.Celebration)
    }

    override fun onVictory(x: Float, y: Float) {
        animLoop = true
        startAnim(PokPokAnim.Celebration)
        Effects.addPersistentEffect(
            FeatherCelebration(x, y, renderer.radius, highGoal = true,
                theme.main.primary, theme.main.secondary, fullCircle = true)
        )
    }

    /** Identical to ChickenSkin.FeatherCelebration — copied verbatim per plan. */
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
                Feather(cx, cy, cos(a) * speed, sin(a) * speed,
                    Random.nextFloat() * 360f, (Random.nextFloat() - 0.5f) * 6f, Random.nextFloat())
            }
        }

        override fun step() { frame++; if (frame > 60) isDone = true }

        override fun draw(scope: DrawScope) {
            for (f in feathers) {
                f.x += f.vx
                f.y += f.vy
                f.angle += f.spin
                val alpha = (220f * (1f - frame / 60f).coerceAtLeast(0f)).toInt()
                if (alpha <= 0) continue
                val c = Palette.lerpColor(primary, secondary, f.colorMix)
                scope.withTransform({
                    rotate(f.angle, pivot = Offset(f.x, f.y))
                }) {
                    drawOval(
                        Color(Palette.withAlpha(c, alpha)),
                        topLeft = Offset(f.x - fw, f.y - fh),
                        size = Size(fw * 2f, fh * 2f)
                    )
                    drawLine(
                        Color(Palette.withAlpha(secondary, alpha)),
                        Offset(f.x, f.y + fh * 1.3f),
                        Offset(f.x, f.y - fh * 0.7f),
                        strokeWidth = fw * 0.35f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun startAnim(anim: PokPokAnim) {
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
