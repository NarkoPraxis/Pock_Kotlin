package gameobjects

import android.graphics.Canvas
import gameobjects.puckstyle.ColorTheme
import gameobjects.puckstyle.PuckRenderer
import gameobjects.puckstyle.skins.ClassicSkin
import gameobjects.puckstyle.tails.ClassicTail
import gameobjects.puckstyle.paddles.ClassicLaunch
import physics.Force
import physics.Point
import physics.Ticker
import shapes.Circle
import utility.PaintBucket

class Puck(radius: Float, x: Float, y: Float, fillColor: Int, strokeColor: Int) : Circle(radius, x, y, fillColor, strokeColor) {

    val renderer: PuckRenderer = PuckRenderer().also { r ->
        val theme = ColorTheme(fillColor, strokeColor, PaintBucket.effectColor, true)
        r.skin = ClassicSkin(theme)
        r.tail = ClassicTail(theme)
        r.effect = ClassicLaunch(theme)
        r.fillColor = fillColor
        r.strokeColor = strokeColor
    }

    /** Preview mode — renders body as a dark silhouette and desaturates tail. Wraps renderer.preview. */
    var isPlaceholder: Boolean
        get() = renderer.preview
        set(value) { renderer.preview = value }

    val shrinkTicker = Ticker(((Settings.sweetSpotMax - Settings.sweetSpotMin) / Settings.chargeIncreaseRate).toInt())

    var bonusMovement = false
    var movement: Force = Force()
    var launch: Force = Force()

    override fun setStroke(stroke: Int) {
        super.setStroke(stroke)
        renderer.strokeColor = stroke
    }

    override fun setFill(fill: Int) {
        super.setFill(fill)
        renderer.fillColor = fill
    }

    /** Full z-ordered draw: syncs position/physics state into renderer then delegates. */
    override fun drawTo(canvas: Canvas) {
        syncRenderer()
        renderer.draw(canvas)
    }

    /**
     * Body-only draw at a custom radius — used by teleport shrink/grow animations.
     * Does not go through z-ordering; just draws the skin at the given radius.
     */
    override fun drawTo(radius: Float, canvas: Canvas) {
        syncRenderer()
        val savedRadius = renderer.radius
        renderer.radius = radius
        val skin = renderer.skin
        if (skin != null) {
            if (renderer.preview) canvas.drawCircle(x, y, radius, PaintBucket.placeholderPaint)
            else skin.drawBody(canvas, renderer)
        } else {
            canvas.drawCircle(x, y, radius, fillPaint)
            canvas.drawCircle(x, y, radius, strokePaint)
        }
        renderer.radius = savedRadius
    }

    private fun syncRenderer() {
        renderer.x = x
        renderer.y = y
        renderer.radius = radius
        renderer.movementDirX = movement.direction.x
        renderer.movementDirY = movement.direction.y
        renderer.movementPower = movement.power
        // fillColor, strokeColor, frame, currentCharge, shielded, launched, baseFillColor,
        // and all effect state are set by Player before calling drawTo()
    }

    fun getNextDirection(): Point {
        val maxSpeed = if (launch.hasPower) Settings.maxPuckSpeed else Settings.maxPuckLaunchSpeed
        val nextDirection = (movement + launch).step(maxSpeed)
        if (!bonusMovement) {
            movement.applyFriction()
        }
        launch.applyFriction()
        return nextDirection
    }

    fun clearForces() {
        movement = Force()
        launch = Force()
    }

    fun startBounce(bounceDirection: Point): Point {
        if (movement.hasPower) {
            movement = Force(bounceDirection, movement.power + launch.power)
            launch.power = 0f
        } else if (launch.hasPower) {
            launch = Force(bounceDirection, movement.power + launch.power)
            movement.power = 0f
        }
        return getNextDirection()
    }
}
