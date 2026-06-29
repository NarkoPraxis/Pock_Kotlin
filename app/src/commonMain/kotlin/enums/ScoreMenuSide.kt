package enums

/**
 * Which side edge the score dial / pause menu lives against.
 *
 * [Left] — the half-disc is flush against the left screen edge (its arc bulging right into the
 * play area). Default.
 * [Right] — mirrored to the right edge (arc bulging left). Every spin direction reverses so the
 * numerals still exit toward the screen edge and enter from the middle.
 */
enum class ScoreMenuSide { Left, Right }
