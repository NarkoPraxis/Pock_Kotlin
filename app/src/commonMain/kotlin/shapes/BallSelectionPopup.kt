package shapes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.runoutzone.pockpock.BD_WRAPPER_DARK
import enums.BallType
import gameobjects.Settings
import gameobjects.puckstyle.BallStyleFactory
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.CustomBallConfig
import gameobjects.puckstyle.PaddleLaunchEffect
import gameobjects.puckstyle.Palette
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.RandomRoll
import utility.Storage
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.tanh

class BallSelectionPopup(val isHigh: Boolean) : ScrollSnapCarousel() {

    var isOpen: Boolean = false
    var randomRoll: RandomRoll? = null

    private data class Slot(val type: BallType, val customStorageIndex: Int? = null)

    private var slots: List<Slot> = emptyList()
    private val rendererList: MutableList<PuckRenderer> = mutableListOf()

    override val itemCount: Int get() = slots.size

    val w: Float get() = Settings.screenWidth
    override val cx: Float get() = Settings.middleX

    // Place the carousel so the SELECTED ball's contact shadow lands just above the goal zone (from
    // each player's perspective — the high carousel is mirrored). This pulls the balls in toward
    // mid-screen compared to sitting them right on the goal edge.
    val cy: Float get() {
        val rSel = Settings.ballRadius * maxScale
        val shadowReach = rSel * (SHADOW_DROP_K + SHADOW_H_K / 2f)
        val gap = Settings.screenRatio * 0.5f
        return if (isHigh) Settings.topGoalBottom + gap + shadowReach
               else Settings.bottomGoalTop - gap - shadowReach
    }

    // Screen-space Y of the selected (centered, max-scale) ball's static paddle. The paddle is parked
    // toward mid-screen from the ball, so this sits between the ball centre (cy) and mid-screen. Used
    // to anchor the "Ready?" prompt. Mirrored automatically: for the high carousel the paddle points
    // down (toward mid), for the low carousel it points up.
    val selectedPaddleY: Float get() {
        val radius = Settings.ballRadius * maxScale
        val tiltRad = PaddleLaunchEffect.STATIC_PADDLE_TILT_DEG * (PI.toFloat() / 180f)
        val offset = cos(tiltRad) * radius * PaddleLaunchEffect.STATIC_PADDLE_DIST_K
        return if (isHigh) cy + offset else cy - offset
    }

    override val slotW: Float get() = Settings.screenRatio * 4f

    // Static-display tuning. The centered (selected) ball is largest; neighbors shrink and squish
    // together. Spacing is dynamic (see drawTo's tanh spread): the gap around a ball grows as it
    // approaches centre and closes back up as it leaves, so balls part to frame the selected one.
    private val minScale = 0.6f       // scale of fully-off-centre balls
    private val maxScale = 1.5f       // selected ball grows 50% larger
    private val spreadBase = 0.62f    // baseline gap between far-apart (squished) balls, slot units
    private val spreadBulge = 0.95f   // extra gap carved out around the centre ball, slot units

    override fun toLogicalX(screenX: Float): Float = if (isHigh) 2f * Settings.middleX - screenX else screenX

    val previewType: BallType get() = slots.getOrNull(snapIndex)?.type ?: BallType.Classic
    val previewCustomIndex: Int? get() = slots.getOrNull(snapIndex)?.customStorageIndex

    override fun onSnappedTo(index: Int) {
        rendererList.getOrNull(index)?.tail?.clear()
        val slot = slots.getOrNull(index) ?: return
        trySelect(slot)
    }

    fun open() {
        isOpen = true

        // Only show custom slots that are both unlocked and have a saved ball. No full ball-type
        // list and no locked entries — ads are accessed only in the Custom Ball Creator.
        slots = (0 until Storage.SLOT_COUNT).mapNotNull { i ->
            if (Storage.isSlotUnlocked(i) && Storage.loadCustomBall(i) != null) Slot(BallType.Random, i) else null
        }

        rendererList.clear()
        for (slot in slots) {
            val config = Storage.loadCustomBall(slot.customStorageIndex!!)!!
            val renderer = BallStyleFactory.buildCustomRenderer(config, ColorTheme.getTheme(isHigh))
            // Display these as frozen "screenshots": static swoosh tail + overhead static paddle.
            renderer.staticUiMode = true
            renderer.effect.frozen = true
            rendererList.add(renderer)
        }

        val currentCustomIdx = if (isHigh) Settings.highCustomBallIndex else Settings.lowCustomBallIndex
        val initialIndex = (currentCustomIdx
            ?.let { idx -> slots.indexOfFirst { it.customStorageIndex == idx }.takeIf { it >= 0 } }
            ?: 0)
            .coerceIn(0, (slots.size - 1).coerceAtLeast(0))

        scrollToIndex(initialIndex)
        rendererList.getOrNull(snapIndex)?.tail?.clear()
    }

    fun close() {
        isOpen = false
        cancelDrag()
    }

    private fun trySelect(slot: Slot): Boolean {
        val idx = slot.customStorageIndex ?: return false
        if (isHigh) {
            Settings.highBallType = BallType.Random
            Settings.highCustomBallIndex = idx
            Storage.saveHighCustomBallIndex(idx)
            Storage.saveHighBallType(BallType.Random)
        } else {
            Settings.lowBallType = BallType.Random
            Settings.lowCustomBallIndex = idx
            Storage.saveLowCustomBallIndex(idx)
            Storage.saveLowBallType(BallType.Random)
        }
        utility.Logic.applyBallStyles()
        return true
    }

    // One full-width drag rectangle covering the whole carousel zone, so the strip can be grabbed
    // anywhere — not just on the ball skins. The edge nearest mid-screen sits just above the body of
    // the (largest) selected ball; the opposite edge runs all the way to the screen edge. The high
    // carousel lives at the top of the screen, so its near-mid edge is the BOTTOM of the ball and it
    // extends up to y=0; the low carousel is the mirror.
    fun hitTest(x: Float, y: Float): Boolean {
        if (x <= cx - w / 2f || x >= cx + w / 2f) return false
        val selReach = Settings.ballRadius * maxScale + Settings.screenRatio * 0.75f
        return if (isHigh) y >= 0f && y < cy + selReach
               else        y > cy - selReach && y <= Settings.screenHeight
    }

    fun handleTouchEvent(action: Int, x: Float, y: Float): Boolean {
        if (!isOpen) return false
        val masked = action and 0xff
        if (masked == ACTION_DOWN || masked == ACTION_POINTER_DOWN) {
            if (!hitTest(x, y)) return false
        }
        return handleScrollTouchEvent(action, x, y)
    }

    fun DrawScope.drawTo() {
        if (!isOpen) return

        val theme = if (isHigh) ColorTheme.Warm else ColorTheme.Cold
        val pr = Settings.ballRadius
        // Fixed contact-shadow baseline. Every ball's shadow sits on this one line, so scaling a
        // ball only grows it UPWARD from a fixed footprint — the shadow's y never moves, reading as
        // "the asset grew" rather than "the asset slid." Anchored to the selected (max-scale) ball
        // so its pose is identical to before.
        val shadowBaselineY = cy + (pr * maxScale) * SHADOW_DROP_K
        val cullX = w / 2f + slotW
        // Contact-shadow tone: soft cold/warm-inert tint in light mode; the deep navy control-box
        // tone (BD_WRAPPER_DARK) in dark mode so it reads as a shadow rather than a pale smudge.
        val shadowColor = if (Storage.darkMode) BD_WRAPPER_DARK
                          else Color(Palette.withAlpha(theme.inert.primary, SHADOW_ALPHA))

        val canvas = drawContext.canvas

        canvas.save()
        if (isHigh) {
            // High player's whole carousel is mirrored 180°, so the local-space swoosh and overhead
            // paddle orient correctly (paddle toward mid-screen, swoosh toward the goal) for both ends.
            canvas.translate(cx, cy)
            canvas.scale(-1f, -1f)
            canvas.translate(-cx, -cy)
        }

        // No panel, border, chip, or selection highlight: balls float over the goal zone. Selection
        // is conveyed by size + spacing — the centered ball is largest, neighbors shrink and spread.
        for (i in slots.indices) {
            val previewRenderer = rendererList.getOrNull(i) ?: continue

            val rel = i - scrollX / slotW
            // Dynamic spread: a baseline gap everywhere plus an extra bulge near centre (tanh tapers
            // the bulge to ~0 away from centre). As a ball scrolls toward centre and scales up, the
            // gap around it opens; as it leaves and shrinks, neighbors squish back together.
            val drawX = cx + slotW * (spreadBase * rel + spreadBulge * tanh(rel))
            if (drawX < cx - cullX || drawX > cx + cullX) continue

            val dist = abs(rel).coerceAtMost(1f)
            // Raised-cosine bell: instead of lerping size linearly with distance (a sharp tent),
            // shape it as a cosine dome so the ball eases into max at centre and eases back out to
            // min at the edges. bell = 1 at dist 0, 0 at dist 1, with zero slope at both ends.
            val bell = 0.5f * (1f + cos((PI * dist).toFloat()))
            val scale = minScale + (maxScale - minScale) * bell
            val radius = pr * scale

            // Soft inert-primary contact shadow, proportioned from Ball Select.svg (~2.1r wide,
            // ~0.58r tall). It stays pinned to the fixed baseline; the ball centre floats above it
            // by radius*DROP so a growing ball rises off a stationary shadow. Tunables: SHADOW_*.
            val shadowW = radius * SHADOW_W_K
            val shadowH = radius * SHADOW_H_K
            val shadowCy = shadowBaselineY
            val ballCy = shadowBaselineY - radius * SHADOW_DROP_K
            drawOval(
                color = shadowColor,
                topLeft = Offset(drawX - shadowW / 2f, shadowCy - shadowH / 2f),
                size = Size(shadowW, shadowH)
            )

            // Static screenshot of the ball composition (tail swoosh + body + overhead paddle).
            // Frame is NOT advanced, so skins stay static too.
            previewRenderer.effectEnabled = true
            previewRenderer.radius = radius
            previewRenderer.strokeWidth = Settings.strokeWidth * scale
            previewRenderer.x = drawX
            previewRenderer.y = ballCy
            previewRenderer.fillColor = theme.main.primary
            previewRenderer.strokeColor = theme.main.secondary

            with(previewRenderer) { draw() }
        }

        canvas.restore()
    }

    companion object {
        // Contact-shadow tuning (radius multiples + alpha 0–255), from Ball Select.svg.
        private const val SHADOW_W_K     = 2.12f
        private const val SHADOW_H_K     = 0.58f
        private const val SHADOW_DROP_K  = 3.5f
        private const val SHADOW_ALPHA   = 130

        const val ACTION_MASK        = 0xff
        const val ACTION_DOWN        = 0
        const val ACTION_UP          = 1
        const val ACTION_MOVE        = 2
        const val ACTION_CANCEL      = 3
        const val ACTION_POINTER_DOWN = 5
        const val ACTION_POINTER_UP  = 6
    }
}
