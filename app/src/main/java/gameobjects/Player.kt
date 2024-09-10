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
    var canScore = false
    var launchTo = Point(0f,0f)
    var launchFrom = Point(0f, 0f)
    var motion = MotionStates.Free
    var touch = TouchState.Up
    var movementSpeed = Settings.minPuckSpeed
    var charge = 0f
    var bonusCountdown = 0f
    var shielded = false
    var shouldReleaseCharge = false
    var chargePowerLocked = false
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

    private var tail = MutableList(Settings.tailLength) { DrawablePoint() }

    init {
        this.resetLocation = Point(puck.x, puck.y)
        setPuckStroke(puck.strokeColor)
        finger.setAlpha(50) //todo: get the correct colors so this isn't done through alpha anymore
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
        set(value) {
            finger.x = value
        }

    var fy: Float
        get() = finger.y
        set(value) {
            finger.y = value
        }

    var px: Float
        get() = puck.x
        set(value) {
            puck.x = value
        }

    var py: Float
        get() = puck.y
        set(value) {
            puck.y = value
        }

    var pRadius: Float
        get() = puck.radius
        set(value) {
            puck.radius = value
        }

    val power: Float
        get() = puck.movement.power + puck.launch.power

    val isLaunched: Boolean
        get() = puck.launch.hasPower

    val isTeleporting: Boolean
        get() = disappearing || reappearing


    fun drawTo(canvas: Canvas) {
        if (puck.x.isNaN() || puck.y.isNaN()) {
            puck.x = 500f
            puck.y = 500f
        }

        finger.moveTowardLocation(fingerTargetLocation)
        finger.drawTo(canvas)
        if (preparingToTeleport || isTeleporting) {
            drawTeleport(canvas)
        } else {
            puck.drawTo(canvas)
            drawTail(canvas)
        }

        if (finger != previousFingerLocation || puck != previousPuckLocation) {
            touchLocked = false
            shrinkTicker.reset()
        }
//        if (!disableEffects && !touchLocked && movementSpeed == 0f && touch != TouchState.Down && finger == previousFingerLocation) {
//            bonusCountdown += Settings.chargeIncreaseRate * 2
//
//            if (bonusCountdown > Settings.sweetSpotMin && bonusCountdown <= Settings.sweetSpotMax) {
//                shrinkTicker.tick
//                canvas.drawCircle(puck.x, puck.y,puck.radius * shrinkTicker.ratio, puck.strokePaint)
//                canvas.drawCircle(puck.x, puck.y, puck.radius , puck.bonusPaint)
//            }
//            else if (bonusCountdown <= Settings.sweetSpotMax) {
//                canvas.drawCircle(puck.x, puck.y,puck.radius * (bonusCountdown / Settings.sweetSpotMax), puck.strokePaint)
//            }
//            else {
//                touchLocked = true
//                bonusCountdown = 0f
//            }
//        }
//        else {
//            bonusCountdown = 0f
//        }




//        val direction = directionToFinger()
//        canvas.drawCircle(finger.x - direction.x * finger.radius, finger.y - direction.y * finger.radius, 10f, debug)

        if (!disableEffects) {
            puck.drawCharge(canvas, charge)
        }

        previousFingerLocation.x = fx
        previousFingerLocation.y = fy
        previousPuckLocation.x = px
        previousPuckLocation.y = py

//
//        canvas.drawLine(puck.x, puck.y, (puck.movement.direction.x * puck.movement.power* 10) + puck.x,  (puck.movement.direction.y * puck.movement.power * 10) + puck.y, movementPaint )
//        canvas.drawLine(puck.x, puck.y, (puck.launch.direction.x * puck.launch.power* 10) + puck.x,  (puck.launch.direction.y * puck.launch.power * 10) + puck.y, launchPaint )

//        canvas.drawText("bcd: $bonusCountdown", finger.x, finger.y, debugText)
//        canvas.drawText(if (bonusMovement) "BONUS" else "", finger.x, finger.y + 40, debugText)
//        launchTo.drawTo(canvas)
    }

    private fun drawTail(canvas: Canvas) {
        if (tail.size == 0) {
            tail = if (shielded) MutableList(80) {DrawablePoint()} else MutableList(20) { DrawablePoint() }
        }

        fun ratio(i: Int) = (i.toFloat() / (tail.size - 1))

        for(i in tail.size - 1 downTo 0) {
            if (i-1 >= 0) {
                tail[i] = tail[i-1]
            }else {
                tail[i] = DrawablePoint(puck)
            }

            if(shielded) tail[i].setColor(PaintBucket.effectColor) else if (isLaunched) tail[i].setColor(puck.fillColor) else tail[i].setColor(puckFillColor)
            val baseSize = puck.radius * 1.1f
            if (shielded) {
                tail[i].size = baseSize - (Settings.strokeWidth ) - (puck.radius) * ratio(i-1)
                tail[i].setAlpha((255f * (1 - ratio(i))).toInt())
            } else {
                tail[i].size = baseSize - (Settings.strokeWidth ) - (puck.radius) * ratio(i-1)
            }

            tail[i].drawTo(canvas)
        }

    }

    private fun drawTeleport(canvas: Canvas) {
        if (preparingToTeleport) {
            prepareTicker.tick
            puck.drawTo(pRadius, canvas)
//            canvas.drawCircle(px, py, pRadius * prepareTicker.ratio, teleportPaint)
            canvas.drawArc(px - pRadius, py - pRadius, px + pRadius, py + pRadius,
                0f, 360f * prepareTicker.ratio, false, teleportPaint)
            if (prepareTicker.finished) {
                preparingToTeleport = false
                disappearing = true
                prepareTicker.reset()
                tail.clear()
            }
        }
        if (disappearing) {
            puck.drawTo(pRadius * teleportTicker.ratio, canvas)
            canvas.drawCircle(px, py, pRadius* teleportTicker.ratio, teleportPaint)
            if (teleportTicker.tick) {
                disappearing = false
                reappearing = true
                teleportTicker.reset()
                puck.setLocation(finger)
            }
        } else if (reappearing) {
            puck.drawTo(pRadius - (pRadius * teleportTicker.ratio), canvas)
            canvas.drawCircle(px, py, pRadius* teleportTicker.ratio, teleportPaint)
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

    fun notLocked() : Boolean {
        return touch != TouchState.Locked || motion == MotionStates.Free
    }

    fun pucksIntersect(player: Player) : Boolean {
        return puck.intersects(player.puck)
    }

    fun directionToFinger() : Point {
        return puck.directionTo(finger)
    }

    fun score() {
        score++
        canScore = false
    }

    fun clearPower() {
        puck.clearForces()
    }

    private fun shouldBounce(nextLocation: Point, nextDirection: Point) : Boolean {
        val nextX = nextLocation.x
        val nextY = nextLocation.y
        val leftConstraint = Settings.screenLeft + puck.radius
        val rightConstraint = Settings.screenRight - puck.radius
        val topConstraint = (if (puck.launch.hasPower) Settings.screenTop else Settings.topGoalBottom) + puck.radius
        val bottomConstraint = (if (puck.launch.hasPower) Settings.screenBottom else Settings.bottomGoalTop ) - puck.radius
        val savedDirection = Point(nextDirection.x, nextDirection.y)

        if (nextX < leftConstraint) {
            nextDirection.x = -nextDirection.x
            bounceDirection = Direction.LEFT
            Sounds.playWallSound(puck.y)
        }
        else if (nextX > rightConstraint) {
            nextDirection.x = -nextDirection.x
            bounceDirection = Direction.RIGHT
            Sounds.playWallSound(puck.y)
        }

        if (nextY < topConstraint) {
            nextDirection.y = -nextDirection.y
            bounceDirection = Direction.TOP
//            preparingToTeleport = true
//            disableEffects = true
            Sounds.playGoalSound(puck.x)
        }
        else if (nextY > bottomConstraint) {
            nextDirection.y = -nextDirection.y
            bounceDirection = Direction.BOTTOM
//            preparingToTeleport = true
//            disableEffects = true
            Sounds.playGoalSound(puck.x)
        }
        return savedDirection.x != nextDirection.x || savedDirection.y != nextDirection.y
    }

    fun applyForces() : Boolean {
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

    //exclusively for adjusting player position when player doesn't have control
    fun moveTowardPoint(point: Point, maxSpeed: Float = 15f) : Boolean {
        val dir = puck.directionTo(point)
        val dist = puck.distanceTo(point)
        var adjusted = dist * Settings.basePuckDistanceModifier
        adjusted = if (adjusted > maxSpeed) maxSpeed else if (adjusted < Settings.minPuckSpeed) Settings.minPuckSpeed else adjusted

        return if (dist  < 5f ) {
            puck.setLocation(point.x, point.y)
            true
        }else {
            puck.setLocation(px + dir.x * adjusted, py + dir.y * adjusted)
            false
        }
    }

    fun launch(launchForce: Force) {
        puck.launch = launchForce
        puck.movement.power = 0f
    }

    fun increaseCharge() {
        if (!chargePowerLocked) {
            if (charge < Settings.chargeStart) {
                charge = Settings.chargeStart
            } else if (charge >= Settings.sweetSpotMax) {
                charge = Settings.sweetSpotMax * .5f
                chargePowerLocked = true
            } else {
                charge += Settings.chargeIncreaseRate
            }
        }
    }

    fun releaseCharge() : Boolean {
        shouldReleaseCharge = false
        shielded = false
        if (charge >= Settings.sweetSpotMin && charge <= Settings.sweetSpotMax) {
            shielded = true
            Sounds.playChargeBlastOff(puck.x)
        }
        puck.movement = Force(puck.directionTo(finger), charge)
        puck.shrinkTicker.reset()
        charge = 0f
        chargePowerLocked = false
        return shielded
    }
}