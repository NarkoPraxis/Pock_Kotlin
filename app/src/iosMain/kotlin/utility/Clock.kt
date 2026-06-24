package utility

import kotlin.system.getTimeNanos

// getTimeNanos() is monotonic on Kotlin/Native (never wall-clock).
actual fun nowNanos(): Long = getTimeNanos()
