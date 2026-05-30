package gameobjects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import gameobjects.puckstyle.ColorGroup
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

class Puck(radius: Float, x: Float, y: Float, val renderer: PuckRenderer) : Circle(radius, x, y, renderer.theme.main.primary, renderer.theme.main.secondary) {

    /** Preview mode — renders body as a dark silhouette and desaturates tail. Wraps renderer.preview. */
    var isPlaceholder: Boolean
        get() = renderer.preview
        set(value) { renderer.preview = value }

    val shrinkTicker = Ticker(((Settings.sweetSpotMax - Settings.sweetSpotMin) / Settings.chargeIncreaseRate).toInt())

    var bonusMovement = false
    var movement: Force = Force()
    var launch: Force = Force()

    private val _scratchForce = Force()
    private val _stepResult = Point()

    var impactPower: Float = 0.0f
        get() = (movement + launch).power

    val theme: ColorTheme
        get() = renderer.theme

    override fun setStroke(stroke: Int) {
        super.setStroke(stroke)
        renderer.strokeColor = stroke
    }

    override fun setFill(fill: Int) {
        super.setFill(fill)
        renderer.fillColor = fill
    }

    /** Full z-ordered draw: syncs position/physics state into renderer then delegates. */
    override fun DrawScope.drawTo() {
        syncRenderer()
        with(renderer) { draw() }
    }

    /**
     * Body-only draw at a custom radius — used by teleport shrink/grow animations.
     * Does not go through z-ordering; just draws the skin at the given radius.
     */
    fun DrawScope.drawBodyAtRadius(radius: Float) {
        syncRenderer()
        val savedRadius = renderer.radius
        renderer.radius = radius
        val skin = renderer.skin
        if (skin != null) {
            if (renderer.preview) {
                drawCircle(
                    color = androidx.compose.ui.graphics.Color(0.118f, 0.118f, 0.118f, 0.784f),
                    radius = radius,
                    center = Offset(x, y)
                )
            } else {
                with(skin) { drawBody() }
            }
        } else {
            drawCircle(androidx.compose.ui.graphics.Color(fillColor), radius, Offset(x, y))
            drawCircle(androidx.compose.ui.graphics.Color(strokeColor), radius, Offset(x, y),
                style = Stroke(width = Settings.strokeWidth))
        }
        renderer.radius = savedRadius
    }

    private fun syncRenderer() {
        renderer.x = x
        renderer.y = y
        renderer.radius = radius
        renderer.movementDirX = movement.direction.x
        renderer.movementDirY = movement.direction.y
        renderer.movementPower = impactPower

        // fillColor, strokeColor, frame, currentCharge, shielded, launched, baseFillColor,
        // and all effect state are set by Player before calling drawTo()
    }

    fun getNextDirection(): Point {
        val maxSpeed = if (launch.hasPower) Settings.maxPuckSpeed else Settings.maxPuckLaunchSpeed
        _scratchForce.setFrom(movement)
        _scratchForce.addForce(launch)
        val nextDirection = _scratchForce.stepInto(_stepResult, maxSpeed)
        val frictionAmount = if (renderer.shielded) Settings.friction * 0.5f else Settings.friction
        if (!bonusMovement) {
            movement.applyFriction(frictionAmount)
        }
        launch.applyFriction(frictionAmount)
        return nextDirection
    }



    fun clearForces() {
        movement = Force()
        launch = Force()
        renderer.resetState()
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
