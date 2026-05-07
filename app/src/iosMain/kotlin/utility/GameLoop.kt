package utility

import kotlinx.coroutines.*

actual class GameLoop actual constructor(
    private val intervalMs: () -> Long,
    private val onTick: () -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var _isRunning: Boolean = false
    actual val isRunning: Boolean get() = _isRunning

    actual fun start() {
        if (!_isRunning) {
            _isRunning = true
            scope.launch {
                while (_isRunning) {
                    delay(intervalMs())
                    if (_isRunning) onTick()
                }
            }
        }
    }

    actual fun stop() {
        _isRunning = false
        scope.coroutineContext.cancelChildren()
    }
}
