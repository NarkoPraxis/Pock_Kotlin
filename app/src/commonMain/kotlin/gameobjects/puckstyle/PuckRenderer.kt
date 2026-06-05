package gameobjects.puckstyle

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import gameobjects.Settings
import utility.Sounds

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

    var currentCharge: Float = 0f

    var movementDirX: Float = 0f
    var movementDirY: Float = 0f
    var movementPower: Float = 0f

    var preview: Boolean = false

    /**
     * When true, the whole composition draws as a static "screenshot" for UI display
     * (the ball-selection carousel): the tail poses along [StaticTailPath] instead of trailing
     * live motion, and the paddle parks statically overhead at zero charge. Always false for
     * in-game renderers, so live gameplay is unaffected.
     */
    var staticUiMode: Boolean = false

    var shielded: Boolean = false
    var launched: Boolean = false
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
