package gameobjects.puckstyle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import gameobjects.Settings
import utility.Sounds
import utility.UiStrobeClock

enum class ColorKey { Main, Shield, Inert }

/**
 * Owns all visual components for a puck and manages their draw order via local z-indices.
 *
 * Draw order: each component (skin, tail, effect) declares its own zIndex. Components are sorted
 * smallest-first before drawing, so a tail with zIndex -1 renders behind a body at 0, and an
 * effect at 1 renders in front. Defaults: tail = -1, body = 0, effect = 1.
 */
class PuckRenderer(var theme: ColorTheme) {
    lateinit var skin: PuckSkin
    lateinit var tail: TailRenderer
    lateinit var effect: PaddleLaunchEffect

    // Fill/stroke color as ARGB ints (same format as Compose Color.toArgb())
    var fillColor: Int = Palette.WHITE
    var strokeColor: Int = 0xFFBFBFBF.toInt()  // LTGRAY equivalent
    var chargeColor: Int = Palette.WHITE        // deprecated but kept for compat

    // Stroke width used by skins/tails
    var strokeWidth: Float = Settings.strokeWidth

    var responsiveColorGroup: ColorGroup = theme.main

    /**
     * Whether [responsiveColorGroup] is currently a [RainbowOverride] strobe (set each [draw]).
     * Read by elements that need the *live* rainbow colour without re-deriving the gating: the
     * paddle's inverted charge fill, and effect-spawn sites that bake the current rainbow colour.
     */
    var responsiveIsRainbow: Boolean = false
        private set

    /** The hue driving [responsiveColorGroup] this frame; only meaningful when [responsiveIsRainbow]. */
    var responsiveHue: Float = 0f
        private set

    /**
     * Bake the current rainbow colour for an effect that should hold it for its whole lifetime
     * (persistent residuals, score bursts, collision rings, the metal blast scorch, …). Returns
     * [fallback] (the configured theme colour) when this puck isn't strobing, so non-rainbow
     * behaviour is unchanged.
     */
    fun bakedPrimary(fallback: Int): Int =
        if (responsiveIsRainbow) RainbowOverride.primaryColor(responsiveHue).toArgb() else fallback

    fun bakedSecondary(fallback: Int): Int =
        if (responsiveIsRainbow) RainbowOverride.secondaryColor(responsiveHue).toArgb() else fallback

    /**
     * A charge / fill colour that strobes at the *inverted* hue of the puck body, so the fill stays
     * visible over the (same-hue) strobing paddle bar. Returns [fallback] (the configured charge
     * colour, usually shield primary) when not strobing.
     */
    fun invertedChargeColor(fallback: Int): Int =
        if (responsiveIsRainbow)
            RainbowOverride.primaryColor(RainbowOverride.invertedHue(responsiveHue)).toArgb()
        else fallback

    private val layerOrder = ArrayList<Any>(3)

    internal fun attach(skin: PuckSkin, tail: TailRenderer, effect: PaddleLaunchEffect) {
        this.skin = skin
        this.tail = tail
        this.effect = effect
        rebuildLayerOrder(skin.zIndex, tail.zIndex, effect.zIndex)
    }

    internal fun attach(
        skin: PuckSkin, tail: TailRenderer, effect: PaddleLaunchEffect,
        skinZ: Int, tailZ: Int, effectZ: Int
    ) {
        this.skin = skin
        this.tail = tail
        this.effect = effect
        rebuildLayerOrder(skinZ, tailZ, effectZ)
    }

    var x: Float = 0f
    var y: Float = 0f

    var radius4: Float = 0f

    var radius: Float = 0f
        set(value) {
            radius4 = value * 4
            field = value
        }

    var frame: Int = 0

    /**
     * Color-strobe clock, decoupled from [frame]. Rainbow/Prism skins & tails read this for their
     * hue cycle so they keep strobing in static UI displays (the Ball Designer carousels, the
     * ball-select popup) where [frame] is held frozen to keep geometry still. In live play
     * [staticUiMode] is false, so this just returns [frame] — gameplay rendering is identical.
     */
    val strobe: Int get() = if (staticUiMode) UiStrobeClock.frame else frame

    var currentCharge: Float = 0f

    var movementDirX: Float = 0f
    var movementDirY: Float = 0f
    var movementPower: Float = 0f

    var preview: Boolean = false

    /**
     * When true, this renderer never plays charge/sweet-spot SFX. Set on the Ball Designer /
     * Color Picker live-motion previews, which run the real charge cycle purely for show — they
     * must stay silent. Always false for in-game renderers, so gameplay audio is unaffected.
     */
    var suppressSounds: Boolean = false

    /**
     * When true, the whole composition draws as a static "screenshot" for UI display
     * (the ball-selection carousel): the tail poses along [StaticTailPath] instead of trailing
     * live motion, and the paddle parks statically overhead at zero charge. Always false for
     * in-game renderers, so live gameplay is unaffected.
     */
    var staticUiMode: Boolean = false

    var shielded: Boolean = false
    var launched: Boolean = false

    /**
     * Rainbow colour overrides (see [RainbowOverride]). When [rainbowMain] is set, the puck's main
     * colours strobe; [rainbowShield] does the same for its shielded colours. Set from
     * [gameobjects.Settings] for gameplay pucks and from the Ball Designer's local edit state for
     * its previews. Inert/stunned never strobes (it stays the grey "inert" group).
     */
    var rainbowMain: Boolean = false
    var rainbowShield: Boolean = false
    var baseFillColor: Int = Palette.WHITE
    var effectEnabled: Boolean = true
    var inertLocked: Boolean = false
    val isInert: Boolean get() = inertLocked || hitStunned
    var hitStunned: Boolean = false
    var hitStunRatio: Float = 0f

    var chargePowerLocked: Boolean = false
    var isHigh: Boolean = false
    var isFlingHeld: Boolean = false
    var flingStartX: Float = 0f
    var flingStartY: Float = 0f
    var flingCurrentX: Float = 0f
    var flingCurrentY: Float = 0f

    private fun rebuildLayerOrder(skinZ: Int, tailZ: Int, effectZ: Int) {
        layerOrder.clear()
        val slots = ArrayList<Pair<Int, Any>>(3)
        slots.add(skinZ to skin)
        slots.add(tailZ to tail)
        slots.add(effectZ to effect)
        slots.sortBy { it.first }
        for ((_, component) in slots) layerOrder.add(component)
    }

    fun resetState() {
        shielded = false
        launched = false
        inertLocked = false
        hitStunned = false
        hitStunRatio = 0f
        currentCharge = 0f
        isFlingHeld = false
        flingStartX = 0f
        flingStartY = 0f
        flingCurrentX = 0f
        flingCurrentY = 0f
        effect.clearCharge()
        effect.reset()
    }

    fun playSweetSpotSound() {
        if (suppressSounds) return
        if (isHigh) {
            Sounds.playHighPlayerSweetSpotSound(y)
        } else {
            Sounds.playLowPlayerSweetSpotSound(x)
        }
    }

    fun DrawScope.draw() {
        responsiveColorGroup = when {
            isInert  -> theme.inert
            shielded -> theme.shield
            else     -> theme.main
        }
        // Rainbow override: replace the live colour group (once per draw) with a synced strobing one
        // so every component reading the responsive group cycles together. Inert never strobes.
        if (!isInert && (if (shielded) rainbowShield else rainbowMain)) {
            responsiveHue = RainbowOverride.hue(isHigh, strobe)
            responsiveColorGroup = RainbowOverride.group(isHigh, strobe)
            responsiveIsRainbow = true
        } else {
            responsiveIsRainbow = false
        }

        for (layer in layerOrder) {
            when (layer) {
                is PuckSkin -> {
                    if (preview) {
                        drawCircle(
                            color = Color(0.118f, 0.118f, 0.118f, 0.784f),
                            radius = radius,
                            center = Offset(x, y)
                        )
                    } else {
                        with(layer) { drawBody() }
                    }
                }
                is TailRenderer -> {
                    if (Settings.tailLength != 0) {
                        layer.renderWithPreview(this)
                    }
                }
                is PaddleLaunchEffect -> {
                    if (effectEnabled || layer.alwaysVisible) {
                        layer.renderWithPreview(this)
                    }
                }
            }
        }
    }
}
