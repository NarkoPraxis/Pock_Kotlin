package shapes

import physics.Point

abstract class Shape(x: Float, y: Float) : Point(x, y) {
    abstract fun intersects(circle: Circle): Boolean
}
