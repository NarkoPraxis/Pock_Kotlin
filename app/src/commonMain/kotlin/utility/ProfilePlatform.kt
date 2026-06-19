package utility

/**
 * Per-session (never per-frame) platform hooks for the dev profiler. None of these run on the hot
 * draw path: [readGcCount] is sampled twice per session, the rest run once in endSession().
 */

/** GC count since process start, or -1 if unavailable on this platform. Sampled twice per session. */
expect fun readGcCount(): Long

/** Writes [contents] to the app's external/Documents files dir; returns the absolute path (or "" on failure). */
expect fun writeProfileFile(name: String, contents: String): String

/** Human-readable device + OS string, e.g. "Pixel 4a / Android 13 / API 33". */
expect fun profileDeviceDescription(): String

/** App version string, e.g. "1.4 (build 27)". */
expect fun profileAppVersion(): String
