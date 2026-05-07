package utility

import android.os.Handler
import android.os.Looper

actual class GameLoop actual constructor(
    private val intervalMs: () -> Long,
    private val onTick: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var _isRunning: Boolean = false
    actual val isRunning: Boolean get() = _isRunning

    private val runnable: Runnable = Runnable {
        if (_isRunning) {
            onTick()
            handler.postDelayed(runnable, intervalMs())
        }
    }

    actual fun start() {
        if (!_isRunning) {
            _isRunning = true
            handler.postDelayed(runnable, intervalMs())
        }
    }

    actual fun stop() {
        _isRunning = false
        handler.removeCallbacksAndMessages(null)
    }
}
