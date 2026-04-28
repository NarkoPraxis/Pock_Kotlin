package gameobjects.puckstyle

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Settings
import utility.PaintBucket

/**
 * Owns all visual components for a puck and manages their draw order via local z-indices.
 *
 * Draw order: each component (skin, tail, effect) declares its own zIndex. Components are sorted
 * smallest-first before drawing, so a tail with zIndex -1 renders behind a body at 0, and an
 * effect at 1 renders in front. Defaults: tail = -1, body = 0, effect = 1.
 *
 * Used directly by menu views (no Puck needed) and owned by Puck for gameplay.
 */
class PuckRenderer {

    var skin: PuckSkin? = null
    var tail: TailRenderer? = null
    var effect: LaunchEffect? = null

    // Position and size — synced from Puck each frame in gameplay; set directly in menus
    var x: Float = 0f
    var y: Float = 0f
    var radius: Float = 0f

    // Animation frame counter — incremented each draw in gameplay; also advanced in menus
    var frame: Int = 0

    // Charge visual state — read by reactive skins (Neon, Ghost, Rainbow) and tails
    var currentCharge: Float = 0f

    // Physics state — SpinnerSkin uses movement direction and power for rotation speed
    var movementDirX: Float = 0f
    var movementDirY: Float = 0f
    var movementPower: Float = 0f

    // Preview mode — renders body as a dark silhouette and desaturates tail
    var preview: Boolean = false

    // Paint objects owned here; Classic skin draws with fillPaint/strokePaint directly;
    // other skins use strokePaint.strokeWidth for sizing; some skins mutate chargePaint.color
    val fillPaint: Paint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true; isDither = true; style = Paint.Style.FILL
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        strokeWidth = Settings.strokeWidth
    }
    val strokePaint: Paint = Paint().apply {
        color = Color.LTGRAY
        isAntiAlias = true; isDither = true; style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        strokeWidth = Settings.strokeWidth
    }
    // Mutable by skins to theme charging (Neon, Ghost, Galaxy, Metal, Rainbow)
    val chargePaint: Paint = Paint().apply {
        color = Color.WHITE
        isAntiAlias = true; isDither = true; style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
        strokeWidth = Settings.strokeWidth
    }

    var fillColor: Int
        get() = fillPaint.color
        set(value) { fillPaint.color = value }

    var strokeColor: Int
        get() = strokePaint.color
        set(value) { strokePaint.color = value }

    // Gameplay state forwarded from Player before each draw
    var shielded: Boolean = false
    var launched: Boolean = false
    var baseFillColor: Int = Color.WHITE  // canonical fill color for tail trails
    var effectEnabled: Boolean = true
    var inertLocked: Boolean = false
    val isInert: Boolean get() = inertLocked || hitStunned
    var hitStunned: Boolean = false
    var hitStunRatio: Float = 0f  // 1.0 = full inert, fades to 0.0 as stun expires

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
        effect?.clearCharge()
        effect?.reset()
    }

    /** Resolves the theme ColorGroup that all style components should use for this frame. Priority: inert > shielded > main. */
    fun resolveColorGroup(theme: ColorTheme): ColorGroup = when {
        isInert -> theme.inert
        shielded -> theme.shield
        else -> theme.main
    }

    // Launch effect state forwarded from Player so effect.draw needs no Player reference
    var chargePowerLocked: Boolean = false
    var isHigh: Boolean = false
    var isFlingHeld: Boolean = false
    var flingStartX: Float = 0f
    var flingStartY: Float = 0f
    var flingCurrentX: Float = 0f
    var flingCurrentY: Float = 0f

    fun draw(canvas: Canvas) {
        data class Layer(val zIndex: Int, val drawFn: () -> Unit)
        val layers = ArrayList<Layer>(3)

        skin?.let { s ->
            layers.add(Layer(s.zIndex) {
                if (preview) canvas.drawCircle(x, y, radius, PaintBucket.placeholderPaint)
                else s.drawBody(canvas)
            })
        }
        tail?.let { t ->
            layers.add(Layer(t.zIndex) {
                t.renderWithPreview(canvas)
            })
        }
        if (effectEnabled) {
            effect?.let { e ->
                layers.add(Layer(e.zIndex) {
                    e.draw(canvas)
                })
            }
        }

        layers.sortBy { it.zIndex }
        layers.forEach { it.drawFn() }
    }
}
