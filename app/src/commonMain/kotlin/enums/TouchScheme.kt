package enums

/**
 * How a touch-down event is assigned to a puck.
 *
 * [BySide] — legacy: a press claims the puck on whichever screen half it lands in
 * (top half → Top puck, bottom half → Bottom puck). After assignment the pointer is
 * tracked by id, so cross-midline drags keep control.
 *
 * [ByProximity] — the puck nearest the touch point receives the input, with no notion of
 * top/bottom or the midline. A puck can only be owned by one finger at a time, so if the
 * nearest puck is already owned, the other puck receives the input regardless of distance.
 */
enum class TouchScheme { BySide, ByProximity }
