package utility

import kotlin.time.TimeSource

// TimeSource.Monotonic is monotonic on Kotlin/Native (never wall-clock).
private val monotonicStart = TimeSource.Monotonic.markNow()

actual fun nowNanos(): Long = monotonicStart.elapsedNow().inWholeNanoseconds
