package enums

/**
 * Sub-state of [GameState.Scored]: the score cinematic that freezes play, dims the screen, and
 * frames the pierced ball in a circular window before the balls return home.
 *
 *  - [Shrink]  the dim wash fades in and the window shrinks from screen-covering down onto the ball.
 *  - [Hold]    the small window is held while play stays frozen (Plan 3 hooks the pop here).
 *  - [Expand]  the window expands away while the balls lerp home; play resumes once it has cleared.
 */
enum class ScorePhase { Shrink, Hold, Expand }
