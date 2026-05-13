package utility

actual fun onGamePointerDown(x: Float, y: Float, pointerId: Int) {
    Logic.onPointerDown(x, y, pointerId)
}

actual fun onGamePointerMove(x: Float, y: Float, pointerId: Int) {
    Logic.onPointerMove(x, y, pointerId)
}

actual fun onGamePointerUp(x: Float, y: Float, pointerId: Int) {
    Logic.onPointerUp(x, y, pointerId)
}
