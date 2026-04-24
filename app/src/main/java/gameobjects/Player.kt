package gameobjects

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import enums.Direction
import enums.MotionStates
import enums.TouchState
import physics.Force
import physics.Point
import physics.Ticker
import shapes.Circle
import shapes.DrawablePoint
import utility.PaintBucket
import utility.Sounds

class Player(
             var puck: Puck = Puck(0f, 0f,0f, Color.BLACK, Color.LTGRAY),
             var finger: Circle = Circle(0f, 0f,0f, Color.BLACK, Color.GRAY),
             var isHigh: Boolean = true
) {

    var resetLocation = Point(0f, 0f)
    var penaltyLocation = Point(0f, 0f)
    var previousFingerLocation = Point(0f, 0f)
    var previousPuckLocation = Point(0f, 0f)
    var fingerTargetLocation = Point(0f, 0f)
    var score = 0
    var launchTo = Point(0f,0f)
    var launchFrom = Point(0f, 0f)
    var motion = MotionStates.Free
    var touch = TouchState.Up
    var movementSpeed = Settings.minPuckSpeed
    val charge: Float get() = puck.renderer.effect?.currentCharge ?: 0f
    var bonusCountdown = 0f
    var shielded = false
    var shouldReleaseCharge = false
    val chargePowerLocked: Boolean get() = puck.renderer.effect?.chargePowerLocked ?: false
    var touchLocked = false
    var disappearing = false
    var reappearing = false
    val teleportTicker = Ticker(Settings.teleportTickerTime)
    val prepareTicker = Ticker(Settings.prepareTeleportTickerTime, true)
    val shrinkTicker = Ticker(((Settings.sweetSpotMax - Settings.sweetSpotMin) / Settings.chargeIncreaseRate).toInt())
    var puckStrokeColor = puck.strokeColor
    var puckFillColor = puck.fillColor
    var bounceDirection = Direction.FULL
    var disableEffects = false
    var preparingToTeleport = false
    var lockedPointerId: Int = -1
    var overchargeFrames: Int = 0

    var isFlingHeld: Boolean = false
    val flingStart = Point(0f, 0f)
    val flingCurrent = Point(0f, 0f)
    var flingReleaseDir: Point? = null
    var flingReleaseBasePower: Float = 0f

    private var pendingLaunchDir: Point? = null
    private var pendingLaunchPower: Float = 0f

    init {
        this.resetLocation = Point(puck.x, puck.y)
        setPuckStroke(puck.strokeColor)
        finger.setAlpha(50)
    }

    private val debug = Paint().apply {
        textSize = 40f
        color = Color.BLACK
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = Settings.strokeWidth
    }

    private val debugText = Paint().apply {
        textSize = 40f
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    val teleportPaint = Paint().apply {
        color = PaintBucket.effectColor
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = Settings.strokeWidth
    }

    var fx: Float
        get() = finger.x
        set(value) { finger.x = value }

    var fy: Float
        get() = finger.y
        set(value) { finger.y = value }

    var px: Float
        get() = puck.x
        set(value) { puck.x = value }

    var py: Float
        get() = puck.y
        set(value) { puck.y = value }

    var pRadius: Float
        get() = puck.radius
        set(value) { puck.radius = value }

    val power: Float
        get() = puck.movement.power + puck.launch.power

    val isLaunched: Boolean
        get() = puck.launch.hasPower

    val isTeleporting: Boolean
        get() = disappearing || reappearing

    val isTouching: Boolean
        get() = touch == TouchState.Down || touch == TouchState.Ready


    fun drawTo(canvas: Canvas) {
        if (puck.x.isNaN() || puck.y.isNaN()) {
            puck.x = 500f
            puck.y = 500f
        }

        finger.setLocation(puck.x, puck.y)

        // Sync all renderer state before drawing
        val renderer = puck.renderer
        renderer.frame++
        renderer.currentCharge = charge
        renderer.shielded = shielded
        renderer.launched = isLaunched
        renderer.baseFillColor = puckFillColor
        renderer.chargePowerLocked = chargePowerLocked
        renderer.isHigh = isHigh
        renderer.isFlingHeld = isFlingHeld
        renderer.flingStartX = flingStart.x
        renderer.flingStartY = flingStart.y
        renderer.flingCurrentX = flingCurrent.x
        renderer.flingCurrentY = flingCurrent.y
        renderer.effectEnabled = !disableEffects

        if (chargePowerLocked) overchargeFrames++ else overchargeFrames = 0
        if (preparingToTeleport || isTeleporting) {
            drawTeleport(canvas)
        } else {
            puck.drawTo(canvas)
        }

        if (finger != previousFingerLocation || puck != previousPuckLocation) {
            touchLocked = false
            shrinkTicker.reset()
        }

        previousFingerLocation.x = fx
        previousFingerLocation.y = fy
        previousPuckLocation.x = px
        previousPuckLocation.y = py
    }

    private fun drawTeleport(canvas: Canvas) {
        if (preparingToTeleport) {
            prepareTicker.tick
            puck.drawTo(pRadius, canvas)
            canvas.drawArc(px - pRadius, py - pRadius, px + pRadius, py + pRadius,
                0f, 360f * prepareTicker.ratio, false, teleportPaint)
            if (prepareTicker.finished) {
                preparingToTeleport = false
                disappearing = true
                prepareTicker.reset()
                puck.renderer.tail?.clear()
            }
        }
        if (disappearing) {
            puck.drawTo(pRadius * teleportTicker.ratio, canvas)
            canvas.drawCircle(px, py, pRadius * teleportTicker.ratio, teleportPaint)
            if (teleportTicker.tick) {
                disappearing = false
                reappearing = true
                teleportTicker.reset()
                puck.setLocation(finger)
            }
        } else if (reappearing) {
            puck.drawTo(pRadius - (pRadius * teleportTicker.ratio), canvas)
            canvas.drawCircle(px, py, pRadius * teleportTicker.ratio, teleportPaint)
            if (teleportTicker.tick) {
                stopTeleportation()
            }
        }
    }

    fun prepareToTeleport() {
        preparingToTeleport = true
        disappearing = false
    }

    fun stopTeleportation() {
        preparingToTeleport = false
        disappearing = false
        reappearing = false
        teleportTicker.reset()
        disableEffects = false
    }

    fun setPuckStroke(color: Int) {
        puckStrokeColor = color
        puck.setStroke(color)
    }

    fun notLocked(): Boolean {
        return touch != TouchState.Locked || motion == MotionStates.Free
    }

    fun pucksIntersect(player: Player): Boolean {
        return puck.intersects(player.puck)
    }

    fun directionToFinger(): Point {
        return puck.directionTo(finger)
    }

    fun score() {
        if (score < Settings.pointsToWin) score++
    }

    fun clearPower() {
        puck.clearForces()
    }

    private fun shouldBounce(nextLocation: Point, nextDirection: Point): Boolean {
        val nextX = nextLocation.x
        val nextY = nextLocation.y
        val leftConstraint = Settings.screenLeft + puck.radius
        val rightConstraint = Settings.screenRight - puck.radius
        val canEnterGoal = puck.launch.hasPower && Settings.canScore
        val topConstraint = (if (canEnterGoal) Settings.screenTop else Settings.topGoalBottom) + puck.radius
        val bottomConstraint = (if (canEnterGoal) Settings.screenBottom else Settings.bottomGoalTop) - puck.radius
        val savedDirection = Point(nextDirection.x, nextDirection.y)

        if (nextX < leftConstraint) {
            nextDirection.x = -nextDirection.x
            bounceDirection = Direction.LEFT
            Sounds.playWallSound(puck.y)
        } else if (nextX > rightConstraint) {
            nextDirection.x = -nextDirection.x
            bounceDirection = Direction.RIGHT
            Sounds.playWallSound(puck.y)
        }

        if (nextY < topConstraint) {
            nextDirection.y = -nextDirection.y
            bounceDirection = Direction.TOP
            Sounds.playGoalSound(puck.x)
        } else if (nextY > bottomConstraint) {
            nextDirection.y = -nextDirection.y
            bounceDirection = Direction.BOTTOM
            Sounds.playGoalSound(puck.x)
        }
        return savedDirection.x != nextDirection.x || savedDirection.y != nextDirection.y
    }

    fun applyForces(): Boolean {
        val nextDirection = puck.getNextDirection()
        var nextLocation = puck + nextDirection
        movementSpeed = puck.distanceTo(nextLocation)
        if (shouldBounce(nextLocation, nextDirection)) {
            nextLocation = puck + puck.startBounce(nextDirection)
            movementSpeed = puck.distanceTo(nextLocation)
            return true
        }
        puck.setLocation(nextLocation)
        return false
    }

    fun moveTowardPoint(point: Point, maxSpeed: Float = 15f): Boolean {
        val dir = puck.directionTo(point)
        val dist = puck.distanceTo(point)
        var adjusted = dist * Settings.basePuckDistanceModifier
        adjusted = if (adjusted > maxSpeed) maxSpeed else if (adjusted < Settings.minPuckSpeed) Settings.minPuckSpeed else adjusted

        return if (dist < 5f) {
            puck.setLocation(point.x, point.y)
            true
        } else {
            puck.setLocation(px + dir.x * adjusted, py + dir.y * adjusted)
            false
        }
    }

    fun launch(launchForce: Force) {
        puck.launch = launchForce
        puck.movement.power = 0f
    }

    fun increaseCharge() {
        puck.renderer.effect?.increaseCharge()
    }

    fun clearCharge() {
        puck.renderer.effect?.clearCharge()
    }

    fun releaseCharge(): Boolean {
        shouldReleaseCharge = false
        shielded = false
        val wasOvercharged = chargePowerLocked
        if (charge >= Settings.sweetSpotMin && charge <= Settings.sweetSpotMax) {
            shielded = true
            Sounds.playChargeBlastOff(puck.x)
        }
        val direction = flingReleaseDir ?: Point(0f, 0f)
        val basePower = flingReleaseBasePower
        val power = if (wasOvercharged) minOf(basePower, Settings.sweetSpotMax * 0.5f) else basePower
        pendingLaunchDir = direction
        pendingLaunchPower = power
        puck.shrinkTicker.reset()
        puck.renderer.effect?.registerStrikeCallback { applyPendingLaunch() }
        puck.renderer.effect?.onRelease(puck.x, puck.y, puck.radius, shielded)
        puck.renderer.effect?.clearCharge()
        flingReleaseDir = null
        flingReleaseBasePower = 0f
        return shielded
    }

    private fun applyPendingLaunch() {
        val dir = pendingLaunchDir ?: return
        puck.movement = Force(dir, pendingLaunchPower)
        pendingLaunchDir = null
    }
}
