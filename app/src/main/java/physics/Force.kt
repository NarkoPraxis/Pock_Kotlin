package physics

import gameobjects.Settings

class Force(var direction: Point, var power: Float) {
    constructor() : this(Point(), 0f)

    init {
        direction = direction.normalized()
        restrictPower()
    }

    val hasPower : Boolean
        get() = power > 0

    operator fun plus(force: Force) : Force {
        val vector1 = Point(force.direction.x * force.power, force.direction.y * force.power)
        val vector2 = Point( direction.x * power, direction.y * power)
        return Force(vector1 + vector2, force.power + power)
    }

    fun step(maxStep: Float) : Point {
        val appliedPower = if (power > maxStep) maxStep else power
        return Point(direction.x  * appliedPower, direction.y * appliedPower)
    }

    fun setFrom(other: Force) {
        direction.x = other.direction.x
        direction.y = other.direction.y
        power = other.power
    }

    fun addForce(other: Force) {
        val v1x = other.direction.x * other.power
        val v1y = other.direction.y * other.power
        val v2x = direction.x * power
        val v2y = direction.y * power
        val rx = v1x + v2x
        val ry = v1y + v2y
        val len = kotlin.math.sqrt(rx * rx + ry * ry)
        if (len > 0f) { direction.x = rx / len; direction.y = ry / len }
        power = other.power + power
        restrictPower()
    }

    fun stepInto(dest: Point, maxStep: Float): Point {
        val appliedPower = if (power > maxStep) maxStep else power
        dest.x = direction.x * appliedPower
        dest.y = direction.y * appliedPower
        return dest
    }

    fun applyFriction(friction: Float) {
        power -= friction
        if(power <= 0) {
            power = 0f
        }
    }

    fun applyFriction() {
        applyFriction(Settings.friction)
    }

    fun addPower(power: Float) {
        this.power += power
        restrictPower()
    }

    private fun restrictPower() {
        if (this.power > Settings.maxPower) {
            this.power = Settings.maxPower
        }
    }
}