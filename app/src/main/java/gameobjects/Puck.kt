package gameobjects

import android.graphics.Canvas
import android.graphics.Paint
import physics.Force
import physics.Point
import physics.Ticker
import shapes.Circle
import utility.PaintBucket

class Puck(radius: Float, x: Float, y: Float, fillColor: Int, strokeColor: Int) : Circle(radius, x, y, fillColor, strokeColor) {


    var shrinkTicker = Ticker(((Settings.sweetSpotMax - Settings.sweetSpotMin) / Settings.chargeIncreaseRate).toInt())

    val bonusPaint = Paint().apply {
        color = PaintBucket.effectColor
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = Settings.strokeWidth
    }

    val chargePaint = Paint().apply {
        color = strokeColor
        isAntiAlias = true
        isDither = true
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = Settings.strokeWidth
    }


    var bonusMovement = false
    var movement: Force = Force()
    var launch: Force = Force()

    fun getNextDirection() : Point {
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

    fun startBounce(bounceDirection: Point) : Point {
        if (movement.hasPower) {
            movement = Force(bounceDirection , movement.power + launch.power)
            launch.power = 0f
        }
        else if (launch.hasPower) {
            launch = Force(bounceDirection , movement.power + launch.power)
            movement.power = 0f
        }

        return getNextDirection()
    }

    fun drawCharge(canvas: Canvas, charge: Float) {
        if (charge > 0) {
            val chargeRadius = radius * (charge / Settings.sweetSpotMax)
            //draws circle for invulnerable check
            if (charge > Settings.sweetSpotMin && charge <= Settings.sweetSpotMax) {
                shrinkTicker.tick
                canvas.drawCircle(x, y, radius * shrinkTicker.ratio, strokePaint)
                canvas.drawCircle(x,y,radius, bonusPaint)
            }
            else if (shrinkTicker.finished && charge < Settings.sweetSpotMax / 2) {
                canvas.drawCircle(x, y, radius / 2, strokePaint)
            }
            else if (!shrinkTicker.finished && charge <= Settings.sweetSpotMax) {
                canvas.drawCircle(x, y, chargeRadius, chargePaint)
            }
            else {
                canvas.drawCircle(x, y, chargeRadius, strokePaint);
            }
        }
    }
}