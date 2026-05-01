package gameobjects.puckstyle

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import gameobjects.Settings
import utility.PaintBucket

/** Which theme ColorGroup to use this frame — computed once in draw(), read by all components. */
enum class ColorKey { Main, Shield, Inert }

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

    // Component setters also rebuild the pre-sorted draw order so draw() has zero per-frame allocation.
    var skin: PuckSkin? = null
        set(value) { field = value; rebuildLayerOrder() }
    var tail: TailRenderer? = null
        set(value) { field = value; rebuildLayerOrder() }
    var effect: LaunchEffect? = null
        set(value) { field = value; rebuildLayerOrder() }

    // Position and size — synced from Puck each frame in gameplay; set directly in menus
    var x: Float = 0f
    var y: Float = 0f

    // Setting radius rebuilds the BallSize lookup table so subclass r() calls never multiply per frame.
    var radius: Float = 0f
        set(value) {
            field = value
        }

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

    // ---- per-frame color key ----
    // Resolved once at the start of draw(). All resolvedColors() calls chain through
    // resolveColorGroup(), which reads this instead of re-evaluating isInert/shielded on
    // every particle or facet in a hot inner loop.
    var colorKey: ColorKey = ColorKey.Main
        private set

    // ---- pre-sorted draw order ----
    // Rebuilt only when a component is assigned. draw() iterates this with zero allocation.
    private val layerOrder = ArrayList<Any>(3)

    private fun rebuildLayerOrder() {
        layerOrder.clear()
        val slots = ArrayList<Pair<Int, Any>>(3)
        skin?.let   { slots.add(it.zIndex to it) }
        tail?.let   { slots.add(it.zIndex to it) }
        effect?.let { slots.add(it.zIndex to it) }
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
        effect?.clearCharge()
        effect?.reset()
    }

    /**
     * Resolves the theme ColorGroup for this frame. Reads from [colorKey] — which is set once
     * at the top of draw() — so the isInert/shielded conditions are not re-evaluated per call.
     */
    fun resolveColorGroup(theme: ColorTheme): ColorGroup = when (colorKey) {
        ColorKey.Inert  -> theme.inert
        ColorKey.Shield -> theme.shield
        ColorKey.Main   -> theme.main
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
        // Resolve once — components call resolvedColors() → resolveColorGroup() which reads colorKey.
        // Prevents evaluating (inertLocked || hitStunned) hundreds of times per frame in particle loops.
        colorKey = when {
            isInert  -> ColorKey.Inert
            shielded -> ColorKey.Shield
            else     -> ColorKey.Main
        }

        // layerOrder is sorted by zIndex at component-assignment time; no ArrayList or lambda here.
        for (layer in layerOrder) {
            when (layer) {
                is PuckSkin -> {
                    if (preview) canvas.drawCircle(x, y, radius, PaintBucket.placeholderPaint)
                    else layer.drawBody(canvas)
                }
                is TailRenderer -> layer.renderWithPreview(canvas)
                is LaunchEffect -> {
                    if (effectEnabled || (layer is PaddleLaunchEffect && layer.alwaysVisible))
                        layer.draw(canvas)
                }
            }
        }
    }
}
