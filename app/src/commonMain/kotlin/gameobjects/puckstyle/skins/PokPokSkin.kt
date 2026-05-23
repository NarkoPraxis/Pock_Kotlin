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
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.paddles.ChickenLaunch.Companion.spawnFeatherExplosion
import physics.Point
import utility.Effects
import utility.PaintBucket
import kotlin.math.PI
import kotlin.math.abs
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
    private val BODY_WORLD_DIAM_K = 2f

    // Eyes_Opened.svg: 83.23 × 42.98.
    private val EYES_WORLD_W_K = 83.23f / 55.84f             // ≈ 1.491 r
    private val EYES_WORLD_H_K = 42.98f / 55.84f             // ≈ 0.770 r
    private val EYES_OFFSET_Y_K = -0.18f
    private val PUPIL_OFFSET_Y_K = (30f - 42.98f / 2f) / 42.98f * EYES_WORLD_H_K  // ≈ +0.152 r
    // Dynamic pupil constants — tweak PUPIL_R_K for pupil size, PUPIL_MAX_FOLLOW_K for travel.
    private val PUPIL_R_K             = 0.16f   // black pupil radius
    private val PUPIL_COVER_R_K       = 0.25f   // white disc erasing SVG's fixed pupils
    private val PUPIL_HIGHLIGHT_R_K   = 0.11f   // white shine dot inside each pupil
    private val PUPIL_MAX_FOLLOW_K    = 0.20f   // max follow displacement
    // PUPIL_CENTRE_Y_OFFSET_K shifts the orbit centre up (more negative) or down from the
    // SVG's built-in pupil row.  Tweak this to fix the "rotating around wrong spot" feel.
    private val PUPIL_CENTRE_Y_OFFSET_K = -0.12f
    // Positions of the static pupils baked into the Eyes_Opened SVG (body-relative space).
    private val STATIC_PUPIL_LEFT_X_K  = -0.238f
    private val STATIC_PUPIL_RIGHT_X_K =  0.213f

    // Beak — tweak MOUTH_OFFSET_Y_K to reposition vertically, W/H to resize.
    private val MOUTH_W_K        = 0.57f   // was 29.07/55.84 ≈ 0.521
    private val MOUTH_H_K        = 0.58f   // was 29.59/55.84 ≈ 0.530
    private val MOUTH_CLOSED_W_K = 0.48f   // was 24.23/55.84 ≈ 0.434
    private val MOUTH_CLOSED_H_K = 0.63f   // was 32.08/55.84 ≈ 0.575
    private val MOUTH_OFFSET_Y_K = 0.26f   // was 0.29; reduced moves beak toward body centre

    // Head feathers — tweak FEATHER_SIDE_ROT for fan spread, CY constants to overlap head.
    // FEATHER_COUNTER_MAX controls counter-rotate against the eye look direction.
    private val FEATHER_SIDE_W_K    = 28.9f  / 55.84f
    private val FEATHER_SIDE_H_K    = 28.9f  / 55.84f
    private val FEATHER_MID_W_K     = 24.46f / 55.84f
    private val FEATHER_MID_H_K     = 38.99f / 55.84f
    private val FEATHER_MID_CY_K    = -0.85f - FEATHER_MID_H_K / 2f  // was -1.0; overlaps head
    private val FEATHER_SIDE_CX_K   =  0.25f                          // was 0.30; tighter spread
    private val FEATHER_SIDE_CY_K   = -0.80f - FEATHER_SIDE_H_K / 2f // was -0.95; overlaps head
    private val FEATHER_SIDE_ROT    = 18f                             // was 30; less outward fan
    private val FEATHER_COUNTER_MAX = 40f   // degrees each feather counter-rotates vs eye direction
    // FEATHER_ORBIT_MAX: how far the whole feather group swings around ball centre (counter to eyes).
    // Tweak this independently from FEATHER_COUNTER_MAX.
    private val FEATHER_ORBIT_MAX   = 20f   // degrees; group orbits ball centre counter to eyes

    // Beak rotation — tilts toward the look direction (opposite of feather counter-rotation).
    // BEAK_ROT_MAX: max tilt angle.  BEAK_PIVOT_ABOVE_K: pivot sits this many r-units above beak centre.
    private val BEAK_ROT_MAX        = -15f   // degrees max beak tilt toward look target
    private val BEAK_PIVOT_ABOVE_K  = 0.20f // pivot above beak centre in r-units; tweak to reposition

    // Wings — tweak WING_PIVOT_X_K to move in/out, WING_W/H_K to resize.
    private val WING_W_K           = 0.7f   // was 28.9/55.84 ≈ 0.518; slightly bigger
    private val WING_H_K           = 0.7f
    private val WING_PIVOT_X_K     = 0.78f   // was 0.85; shoulder closer to body
    private val WING_CENTER_OFFSET = 0.38f   // was 0.45; wing centre closer in
    // Perspective response to look direction (x-only).
    // WING_PERSPECTIVE_ANGLE_MAX: extra tilt toward/away from body for nearer/farther wing.
    // WING_PERSPECTIVE_SCALE_K: scale adjustment — nearer wing shrinks, farther wing grows.
    private val WING_PERSPECTIVE_ANGLE_MAX = 14f   // degrees; tweak for stronger/weaker tilt
    private val WING_PERSPECTIVE_SCALE_K   = 0.05f // fraction; tweak for stronger/weaker size change

    // Face follow — tweak X for lateral range, Y for vertical (keep small).
    private val FACE_FOLLOW_X_K = 0.15f
    private val FACE_FOLLOW_Y_K = 0.02f

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
            eyeX = newR * (0.22f * EYES_WORLD_W_K)
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

    // ── directional shadow ─────────────────────────────────────────────────────
    private var shadowDx = 0f               // smoothed lateral crescent shift; follows look direction
    private val SHADOW_LATERAL_K       = 0.50f // left/right shift of lit window with look direction
    Shroobish


    Live
    Shroobish

    Steven Dawson

    Shroobish
    Shroobish

    Shroobish’s Screen

    // Lit-window approach: full shadow covers the part; a moving oval exempts the lit zone.
    // Oval is bigger than the part and sits above centre so the shadow crescent falls at the bottom.
    // ABOVE_K moves the oval centre upward (negative Y). Larger ABOVE_K = thinner bottom crescent.
    // LIT_R controls size: must be > part radius at centre or the whole part is always in shadow.
    private val SHADOW_LIT_BODY_R       = 1.4f // lit oval radius for body (r; > 1.0 = larger than body)
    private val SHADOW_LIT_BODY_ABOVE_K = 0.5f // oval centre above ball centre (r units)
    private val SHADOW_LIT_WING_R_MIN   = 0.8f // lit oval min radius for wings (r units); grows 2× toward side
    private val SHADOW_LIT_WING_ABOVE_K = 0.55f // oval centre above wing centre (r units)
    private val SHADOW_FEATHER_LIT_R    = 0.65f // radius of fixed lit zone above ball for feathers (r units)
    private val SHADOW_ALPHA            = 0.244f // V-matched to #2e89b7 shadow vs #52b6f2 body: 1 - (183/242)

    // ── animation state machine ────────────────────────────────────────────────
    private enum class PokPokAnim { Default, AlmostHit, JustHit, Celebration, Chatter, Yawn }

    private var currentAnim = PokPokAnim.Default
    private var animFrame   = 0
    private var animLoop    = false
    private var dangerFromSweetSpot = false
    private var lastPhase   = ChargePhase.Idle

    // Frames since any reactive animation last triggered (drives the idle Yawn).
    private var framesSinceTrigger = 0

    // Threat coords in world space; NaN when no active danger.
    private var threatX = Float.NaN
    private var threatY = Float.NaN

    // Durations (frames)
    private val ANIM_ALMOST_HIT   = 35
    private val ANIM_JUST_HIT     = 30
    private val ANIM_CELEBRATION  = 50
    private val ANIM_CHATTER      = 50
    private val ANIM_YAWN         = 100
    private val YAWN_THRESHOLD    = 30000
    private val IDLE_FLAP_SPEED   = 0.03f  // baseline radians per frame for idle wing bob

    // Stagger offsets
    private val DELAY_MOUTH   = 0
    private val DELAY_WINGS   = 0
    private val DELAY_FEATHERS = 0

    // Per-frame state hoisted so sub-draw methods can read them
    private var frameColors: ColorGroup = theme.main
    private var irisOffX = 0f
    private var irisOffY = 0f
    private var featherFollowAngle = 0f  // counter-rotates each individual feather vs eye direction
    private var featherOrbitAngle  = 0f  // counter-rotates the whole feather group around ball centre
    private var beakRotAngle       = 0f  // tilts beak toward look direction
    private var faceOffX = 0f            // lateral face-group nudge toward look target
    private var faceOffY = 0f            // vertical face-group nudge (subtler)
    private var wingAngle = 0f
    private var eyeOpen = true

    // Smoothed animation blend values — lerp toward their target each frame so transitions ease out.
    private var wingAnimOffset    = 0f
    private var featherDroopyBlend = 0f
    private var featherFlaredBlend = 0f

    override fun DrawScope.drawBody() {
        ensureCache()
        frameColors = responsiveGroup

        // Movement tracking for wing flap
        if (lastX.isNaN()) { lastX = renderer.x; lastY = renderer.y }
        val speed = hypot(renderer.x - lastX, renderer.y - lastY)
        lastX = renderer.x; lastY = renderer.y
        wingPhase += IDLE_FLAP_SPEED + speed * 0.025f
        wingAngle = sin(wingPhase) * 40f
        val targetWingAngle = when (currentAnim) {
            PokPokAnim.AlmostHit, PokPokAnim.JustHit -> 0f
            PokPokAnim.Celebration -> 22f
            PokPokAnim.Yawn -> 22f
            PokPokAnim.Chatter -> sin(animFrame.toFloat() * 0.6f) * 30f
            else -> wingAngle
        }
        val wingLerpRate = if (currentAnim == PokPokAnim.Chatter) 0.5f else 0.18f
        displayedWingAngle = lerp(displayedWingAngle, targetWingAngle, wingLerpRate)

        // Blink
        blinkCountdown--
        if (blinkCountdown <= 0) {
            blinkFrame = BLINK_DURATION
            blinkCountdown = Random.nextInt(60, 181)
        }
        eyeOpen = blinkFrame == 0
        if (blinkFrame > 0) blinkFrame--

        // Look direction — tracks paddle by default; tracks threat during proximity AlmostHit
        computeIrisOffset()
        featherFollowAngle = -irisOffX * FEATHER_COUNTER_MAX
        featherOrbitAngle  = -irisOffX * FEATHER_ORBIT_MAX
        beakRotAngle       =  irisOffX * BEAK_ROT_MAX
        faceOffX = irisOffX * r * FACE_FOLLOW_X_K
        faceOffY = irisOffY * r * FACE_FOLLOW_Y_K

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
                PokPokAnim.Chatter     -> ANIM_CHATTER
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
        // Lateral shift: shadow crescent leans in the same direction the bird looks.
        shadowDx = lerp(shadowDx, irisOffX * r * SHADOW_LATERAL_K, 0.12f)

        // Z-order: wings → feathers → body → [face group: eye SVGs → mouth → eye pupils]
        drawWingsForState()

        // Feather group: saveLayer wraps the rotating feathers AND the fixed lit-circle mask so the
        // shadow boundary stays anchored to ball-centre space while feathers orbit through it.
        // Bounds wide enough for feathers at max orbit (20°) plus individual counter-rotation (40°).
        val fBounds = Rect(-r * 1.4f, -r * 2.0f, r * 1.4f, -r * 0.6f)
        drawContext.canvas.saveLayer(fBounds, Paint())
        withTransform({ rotate(featherOrbitAngle, pivot = Offset.Zero) }) {
            drawFeathersForState()
        }

        // Lit zone: circle directly above ball centre.  Feathers inside it are unaffected;
        // pixels outside it (sides) get the shadow overlay via SrcAtop.
        val litPath = Path().apply {
            addOval(Rect(
                -r * SHADOW_FEATHER_LIT_R,
                r * FEATHER_MID_CY_K - r * SHADOW_FEATHER_LIT_R,
                r * SHADOW_FEATHER_LIT_R,
                r * FEATHER_MID_CY_K + r * SHADOW_FEATHER_LIT_R
            ))
        }
        withTransform({ clipPath(litPath, ClipOp.Difference) }) {
            drawRect(
                color = Color(0f, 0f, 0f, SHADOW_ALPHA),
                topLeft = Offset(-r * 1.4f, -r * 2.0f),
                size = Size(r * 2.8f, r * 1.4f),
                blendMode = BlendMode.SrcAtop
            )
        }
        drawContext.canvas.restore()

        drawBodyLayer()

        withTransform({ translate(faceOffX, faceOffY) }) {
            drawEyesSvgLayer()

            // Beak tilts toward look direction around a pivot above its centre.
            withTransform({
                rotate(beakRotAngle, pivot = Offset(0f, r * MOUTH_OFFSET_Y_K - r * BEAK_PIVOT_ABOVE_K))
            }) {
                drawMouthForState()
            }

            drawEyesPupilsLayer()
        }

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

    private fun DrawScope.drawBodyLayer() {
        val body = PokPokSkinPainters.body
        val secondary = Color(frameColors.secondary)
        val bounds = Rect(-r * 1.2f, -r * 1.2f, r * 1.2f, r * 1.2f)
        drawContext.canvas.saveLayer(bounds, Paint())
        if (body != null) {
            val w = r * BODY_WORLD_DIAM_K
            drawSvgPart(body, 0f, 0f, w, w, tint = secondary)
        } else {
            drawCircle(secondary, r, Offset.Zero)
        }
        // Lit window: larger than body, above centre, moves left/right with look direction.
        // Shadow fills everything OUTSIDE this window (SrcAtop clips to body pixels).
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

    // ── wings ──────────────────────────────────────────────────────────────────

    private fun DrawScope.drawWingsForState() {
        val targetAnimOffset = when (currentAnim) {
            PokPokAnim.AlmostHit, PokPokAnim.JustHit -> {
                val t = if (animFrame >= DELAY_WINGS) easeIn((animFrame - DELAY_WINGS).toFloat(), 3f) else 0f
                lerp(0f, -15f, t)
            }
            PokPokAnim.Celebration -> {
                val t = if (animFrame >= DELAY_WINGS) easeIn((animFrame - DELAY_WINGS).toFloat(), 8f) else 0f
                lerp(0f, 15f, t)
            }
            PokPokAnim.Yawn -> {
                val t = easeIn(animFrame.toFloat(), 8f)
                lerp(0f, 15f, t)
            }
            else -> 0f
        }
        wingAnimOffset = lerp(wingAnimOffset, targetAnimOffset, 0.12f)
        drawWing(left = true,  angleDeg = displayedWingAngle, animAngleOffset = wingAnimOffset)
        drawWing(left = false, angleDeg = displayedWingAngle, animAngleOffset = wingAnimOffset)
    }

    private fun DrawScope.drawWing(left: Boolean, angleDeg: Float, animAngleOffset: Float) {
        val painter = if (left) PokPokSkinPainters.wingLeft else PokPokSkinPainters.wingRight
        if (painter == null) return
        val sign = if (left) -1f else 1f
        val pivotX = sign * r * WING_PIVOT_X_K
        val centerX = pivotX + sign * r * WING_CENTER_OFFSET
        val centerY = 0f
        val w = r * WING_W_K
        val h = r * WING_H_K
        // Perspective: nearer wing (sign matches irisOffX sign) tilts toward body and shrinks;
        // farther wing tilts away and grows.  -sign*irisOffX gives the correct ±direction.
        val rotation     = sign * -(angleDeg + animAngleOffset) + (-sign * irisOffX * WING_PERSPECTIVE_ANGLE_MAX)
        val perspScale   = 1f - sign * irisOffX * WING_PERSPECTIVE_SCALE_K
        withTransform({
            rotate(rotation, pivot = Offset(pivotX, 0f))
        }) {
            val wBounds = Rect(centerX - w - r * 0.15f, centerY - h - r * 0.15f,
                               centerX + w + r * 0.15f, centerY + h + r * 0.15f)
            drawContext.canvas.saveLayer(wBounds, Paint())
            drawSvgPart(painter, centerX, centerY, w, h, scaleX = perspScale, scaleY = perspScale,
                tint = Color(frameColors.secondary))
            // Lit window: larger than wing, above wing centre, moves with look direction.
            // Grows 2× min when bird looks toward this wing, shrinks to min when looking away.
            val growFactor = ((sign * irisOffX + 1f) / 2f).coerceIn(0f, 1f)
            val litR = r * SHADOW_LIT_WING_R_MIN * lerp(1f, 2f, growFactor)
            // The lit window must stay fixed in world space regardless of wing rotation.
            // Inverse-rotate the world-space oval centre by -rotation around (pivotX, 0f)
            // so its position in the already-rotated canvas doesn't track the flap.
            val worldLitCx = centerX + shadowDx
            val worldLitCy = centerY - r * SHADOW_LIT_WING_ABOVE_K
            val invRad = -rotation * (PI.toFloat() / 180f)
            val cosInv = cos(invRad)
            val sinInv = sin(invRad)
            val dxLit = worldLitCx - pivotX
            val dyLit = worldLitCy   // pivotY is 0
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

    // ── feathers ───────────────────────────────────────────────────────────────

    private fun DrawScope.drawFeathersForState() {
        val droopyTarget = when (currentAnim) {
            PokPokAnim.AlmostHit, PokPokAnim.JustHit ->
                if (animFrame >= DELAY_FEATHERS) easeIn((animFrame - DELAY_FEATHERS).toFloat(), 8f) else 0f
            PokPokAnim.Chatter -> 0.35f
            else -> 0f
        }
        val flaredTarget = when (currentAnim) {
            PokPokAnim.Celebration ->
                if (animFrame >= DELAY_FEATHERS) easeIn((animFrame - DELAY_FEATHERS).toFloat(), 8f) else 0f
            else -> 0f
        }
        featherDroopyBlend = lerp(featherDroopyBlend, droopyTarget, 0.12f)
        featherFlaredBlend = lerp(featherFlaredBlend, flaredTarget, 0.12f)

        drawHeadFeather(
            painter = PokPokSkinPainters.featherMiddle,
            cx = 0f,
            cy = r * FEATHER_MID_CY_K,
            w = r * FEATHER_MID_W_K,
            h = r * FEATHER_MID_H_K,
            rotDeg = featherFollowAngle,
            droopyBlend = featherDroopyBlend, flaredBlend = featherFlaredBlend
        )
        drawHeadFeather(
            painter = PokPokSkinPainters.featherLeft,
            cx = -r * FEATHER_SIDE_CX_K,
            cy = r * FEATHER_SIDE_CY_K,
            w = r * FEATHER_SIDE_W_K,
            h = r * FEATHER_SIDE_H_K,
            rotDeg = -FEATHER_SIDE_ROT + featherFollowAngle,
            droopyBlend = featherDroopyBlend, flaredBlend = featherFlaredBlend
        )
        drawHeadFeather(
            painter = PokPokSkinPainters.featherRight,
            cx = r * FEATHER_SIDE_CX_K,
            cy = r * FEATHER_SIDE_CY_K,
            w = r * FEATHER_SIDE_W_K,
            h = r * FEATHER_SIDE_H_K,
            rotDeg = FEATHER_SIDE_ROT + featherFollowAngle,
            droopyBlend = featherDroopyBlend, flaredBlend = featherFlaredBlend
        )
    }

    private fun DrawScope.drawHeadFeather(
        painter: Painter?,
        cx: Float, cy: Float,
        w: Float, h: Float,
        rotDeg: Float,
        droopyBlend: Float, flaredBlend: Float
    ) {
        if (painter == null) return
        val anchorY = cy + h * 0.5f
        val rot = when {
            droopyBlend > 0.001f ->
                lerp(rotDeg, rotDeg * 1.36f + kotlin.math.sign(rotDeg) * 10f, droopyBlend)
            flaredBlend > 0.001f ->
                lerp(rotDeg, rotDeg - kotlin.math.sign(rotDeg) * 10f, flaredBlend)
            else -> rotDeg
        }
        val tint = ColorFilter.tint(Color(frameColors.secondary))
        withTransform({
            translate(cx - w / 2f, cy - h / 2f)
            val pivot = Offset(w / 2f, anchorY - (cy - h / 2f))
            if (rot != 0f) rotate(rot, pivot = pivot)
        }) {
            with(painter) { draw(Size(w, h), colorFilter = tint) }
        }
    }

    // ── eyes ───────────────────────────────────────────────────────────────────

    // Set by drawEyesSvgLayer each frame; read by drawEyesPupilsLayer to match scale.
    private var currentEyeScaleX = 1f
    private var currentEyeScaleY = 1f

    private fun DrawScope.drawEyesSvgLayer() {
        currentEyeScaleX = 1f
        currentEyeScaleY = 1f
        when (currentAnim) {
            PokPokAnim.Chatter     -> drawOpenEyesSvgPart(1f, 1f)
            PokPokAnim.AlmostHit   -> {
                val t = easeIn(animFrame.toFloat(), 8f)
                currentEyeScaleX = lerp(1f, 0.8f, t)
                currentEyeScaleY = lerp(1f, 1.3f, t)
                drawOpenEyesSvgPart(currentEyeScaleX, currentEyeScaleY)
            }
            PokPokAnim.JustHit     -> drawOpenEyesSvgPart(1f, 1f)
            PokPokAnim.Celebration -> {
                val t = easeIn(animFrame.toFloat(), 8f)
                currentEyeScaleX = lerp(1f, 1.3f, t)
                currentEyeScaleY = lerp(1f, 0.75f, t)
                drawOpenEyesSvgPart(currentEyeScaleX, currentEyeScaleY)
                val cy = r * EYES_OFFSET_Y_K
                val w  = r * EYES_WORLD_W_K
                val h  = r * EYES_WORLD_H_K
                withTransform({ scale(currentEyeScaleX, currentEyeScaleY, pivot = Offset(0f, cy)) }) {
                    drawRect(Color(frameColors.secondary),
                        topLeft = Offset(-w / 2f, cy + h * 0.1f), size = Size(w, h * 0.6f))
                }
            }
            PokPokAnim.Yawn        -> drawClosedEyes()
            else                   -> if (eyeOpen) drawOpenEyesSvgPart(1f, 1f) else drawClosedEyes()
        }
    }

    private fun DrawScope.drawEyesPupilsLayer() {
        val showPupils = when (currentAnim) {
            PokPokAnim.JustHit, PokPokAnim.Yawn -> true
            PokPokAnim.Default -> eyeOpen
            else -> true
        }
        if (!showPupils) return
        if (currentAnim == PokPokAnim.Celebration) {
            drawOpenEyesPupils(currentEyeScaleX, currentEyeScaleY, irisX = 0f, irisY = 0f)
        } else {
            drawOpenEyesPupils(currentEyeScaleX, currentEyeScaleY)
        }
    }

    private fun DrawScope.drawOpenEyesSvgPart(scaleX: Float, scaleY: Float) {
        val painter = PokPokSkinPainters.eyesOpen ?: return
        val w = r * EYES_WORLD_W_K
        val h = r * EYES_WORLD_H_K
        val cy = r * EYES_OFFSET_Y_K
        drawSvgPart(painter, 0f, cy, w, h, scaleX = scaleX, scaleY = scaleY)
    }

    private fun DrawScope.drawOpenEyesPupils(
        scaleX: Float, scaleY: Float,
        irisX: Float = irisOffX, irisY: Float = irisOffY
    ) {
        val cy = r * EYES_OFFSET_Y_K
        val rawPupilOffY = r * PUPIL_OFFSET_Y_K + r * PUPIL_CENTRE_Y_OFFSET_K
        val pupilCy    = cy + rawPupilOffY * scaleY
        val pupilR     = r * PUPIL_R_K
        val hlR        = r * PUPIL_HIGHLIGHT_R_K
        val maxOff     = r * PUPIL_MAX_FOLLOW_K
        val ix         = irisX * maxOff * scaleX
        val iy         = irisY * maxOff * scaleY
        val scaledEyeX = eyeX * scaleX

        drawCircle(Color.Black, pupilR, Offset(-scaledEyeX + ix, pupilCy + iy))
        drawCircle(Color.Black, pupilR, Offset( scaledEyeX + ix, pupilCy + iy))
        drawCircle(Color.White, hlR,    Offset(-scaledEyeX + ix, pupilCy + iy - pupilR * 0.6f))
        drawCircle(Color.White, hlR,    Offset( scaledEyeX + ix, pupilCy + iy - pupilR * 0.6f))
    }

    private fun DrawScope.drawClosedEyes() {
        val painter = PokPokSkinPainters.eyesClosed ?: return
        val w = r * EYES_WORLD_W_K
        val h = r * EYES_WORLD_H_K
        val cy = r * EYES_OFFSET_Y_K
        drawSvgPart(painter, 0f, cy, w, h, tint = Color(frameColors.primary))
    }

    private fun DrawScope.drawWinceEyes() {
        val painter = PokPokSkinPainters.eyesClosed ?: return
        val w = r * EYES_WORLD_W_K
        val h = r * EYES_WORLD_H_K
        val cy = r * EYES_OFFSET_Y_K
        val decay   = 1f - animFrame.toFloat() / ANIM_JUST_HIT
        val shudder = sin(animFrame * 1.5f) * r * 0.02f * decay
        drawSvgPart(painter, 0f, cy + shudder, w, h, tint = Color(frameColors.secondary))
    }

    // ── mouth ──────────────────────────────────────────────────────────────────

    private fun DrawScope.drawMouthForState() {
        when {
            currentAnim == PokPokAnim.Chatter                                  -> drawMouthChatter()
            currentAnim == PokPokAnim.AlmostHit   && animFrame >= DELAY_MOUTH -> drawMouthGape()
            currentAnim == PokPokAnim.JustHit                                  -> drawMouthGrimace()
            currentAnim == PokPokAnim.Celebration && animFrame >= DELAY_MOUTH -> drawMouthGape()
            currentAnim == PokPokAnim.Yawn                                    -> drawMouthGape(yawn = true)
            else -> drawMouthClosed()
        }
    }

    private fun DrawScope.drawMouthChatter() {
        if ((animFrame / 5) % 2 == 0) {
            drawMouthOpen(r * MOUTH_W_K, r * MOUTH_H_K)
        } else {
            drawMouthClosed()
        }
    }

    private fun DrawScope.drawMouthClosed() {
        val painter = PokPokSkinPainters.mouthClosed ?: return
        val w = r * MOUTH_CLOSED_W_K
        val h = r * MOUTH_CLOSED_H_K

        drawSvgPart(painter, 0f, r * MOUTH_OFFSET_Y_K, w, h,
            tint = Color(frameColors.primary))
    }

    private fun DrawScope.drawMouthGape(yawn: Boolean = false) {
        val lf = (animFrame - DELAY_MOUTH).coerceAtLeast(0)
        val gapeT = easeIn(lf.toFloat(), if (yawn) 15f else 5f)
        val growth = if (yawn) 1.45f else 1.15f
        val w = r * MOUTH_W_K * lerp(1f, growth, gapeT)
        val h = r * MOUTH_H_K * lerp(1f, growth, gapeT)
        drawMouthOpen(w, h)
    }

    private fun DrawScope.drawMouthGrimace() {
        val painter = PokPokSkinPainters.mouthClosed ?: return
        val t = easeIn(animFrame.toFloat(), 6f)
        val w = r * MOUTH_CLOSED_W_K * lerp(1f, 0.8f, t)
        val h = r * MOUTH_CLOSED_H_K * lerp(1f, 1.2f, t)
        drawSvgPart(painter, 0f, r * MOUTH_OFFSET_Y_K, w, h,
            tint = Color(frameColors.primary))
    }

    // Open-mouth render: bottom + top = primary, middle = secondary.
    // All three parts share the same position/size so they compose as one drawing.
    private fun DrawScope.drawMouthOpen(w: Float, h: Float) {
        val bottom = PokPokSkinPainters.mouthBottom ?: return
        val middle = PokPokSkinPainters.mouthMiddle ?: return
        val top    = PokPokSkinPainters.mouthTop    ?: return
        val cy = r * MOUTH_OFFSET_Y_K

        drawSvgPart(bottom, 0f, cy, w, h, tint = Color(frameColors.primary))
        drawSvgPart(middle, 0f, cy, w, h, tint = Color(frameColors.secondary))
        drawSvgPart(top,    0f, cy, w, h, tint = Color(frameColors.primary))
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
                startAnim(PokPokAnim.Chatter)
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
        startAnim(PokPokAnim.Chatter)
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
