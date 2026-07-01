package enums

/**
 * How long the goal stays "open" (scorable, spikes extended) after a collision arms it.
 *
 * The window is driven by the launched puck's [physics.Force.power] decaying under friction. The
 * three settings only change *when* that decay disarms the goal — the spike retract/extend animation
 * (Logic.updateSpikes / Drawing's spiky-goal builder) follows the same close point so the visual and
 * the rule stay in lockstep.
 *
 * [Fast] — closes early: the moment the launch power drops below half of [gameobjects.Settings.maxPuckLaunchSpeed]
 * (the "half-way mark" of a full-strength launch) the goal disarms, instead of waiting for a full decay.
 * [Normal] — the original behaviour: the goal stays open until the launch power fully decays to zero.
 * [Never] — the goal never closes; it is always open and visibly extended for the whole point
 * (shielded pucks are still protected by their bounce — the goal is just always scorable).
 */
enum class ScoreWindow { Fast, Normal, Never }
