package physics

import gameobjects.Settings
import kotlin.math.*

open class Point(var x: Float, var y: Float) {
    constructor() : this(0f, 0f)

    operator fun Point.unaryPlus() =
        Point(+x, +y)

    operator fun unaryMinus(): Point {
        return Point(-x, -y)
    }

    operator fun plus(point: Point) : Point {
        return Point(this.x + point.x, this.y + point.y)
    }
    operator fun minus(point: Point) : Point {
        return Point(this.x - point.x, this.y - point.y)
    }
    override operator fun equals(other: Any?) : Boolean {

        return when (other) {
            is Point ->  {
                if (other.x.isNaN() || other.y.isNaN()) {
                    other.x = 0f
                    other.y = 0f
                }
                other.x.roundToInt() == x.roundToInt() && other.y.roundToInt() == y.roundToInt()
            }
            else -> false
        }
    }


    fun distanceTo(point: Point) : Float {
        val dx = x - point.x
        val dy = y - point.y
        return sqrt((dx * dx) + (dy * dy))
    }

    fun distanceTo(px: Float, py: Float) : Float {
        val dx = x - px
        val dy = y - py
        return sqrt((dx * dx) + (dy * dy))
    }

    fun directionTo(point: Point) : Point {
        val angle = directionAngle(point)
        return if (x >= point.x) Point(
            -cos(angle),
            -sin(angle)
        ) else Point(cos(angle), sin(angle))
    }

    private fun directionAngle(point: Point) : Float {
        val dx = x - point.x
        val dy = y - point.y
        return if (dx == 0f) {
            if (dy > 0f) 90f else if (dy < 0f) -90f else 0f
        } else if (dy == 0f) {
            if (dx > 0f) 0f else if (dx < 0f) 180f else 0f
        } else {
            atan((dy)/(dx))
        }
    }

    fun setLocation(dx: Float, dy: Float) {
        x = dx
        y = dy
    }

    fun setLocation(point: Point) {
       setLocation(point.x, point.y)
    }

    fun moveTowardLocation(point: Point)  {
        val step = distanceTo(point) / 10f

        val direction = directionTo(point).normalized()
        setLocation(x + step * direction.x, y + step * direction.y)
    }

    fun normalized() : Point {
        val length = sqrt((x*x) + (y*y))
        return if (length == 0f) Point(0f, 0f) else Point(x / length, y / length)
    }

    override fun toString(): String {
        return "x: $x y: $y"
    }



}