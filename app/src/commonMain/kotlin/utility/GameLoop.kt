package utility

expect class GameLoop(intervalMs: () -> Long, onTick: () -> Unit) {
    fun start()
    fun stop()
    val isRunning: Boolean
}
