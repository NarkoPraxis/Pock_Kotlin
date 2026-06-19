package utility

/** Monotonic high-resolution clock in nanoseconds. Used only by the dev profiler hot path. */
expect fun nowNanos(): Long
