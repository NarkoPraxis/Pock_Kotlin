package shapes

/**
 * Every tunable knob for the impact flash-effect system (see Plans/Impact Effects). All sizes are in
 * screenRatio units (never hardcoded pixels). Kept as one `object` of `var`s so the whole look can be
 * dialled from a single place — edit, rebuild, eyeball. Values here are Plan-01 starting points.
 */
object FlashTuning {
    // ── Wall-on-Ball layout ──────────────────────────────────────────────
    // How far each of the two sub-bursts sits from the collision point, along
    // the wall, in screenRatio units (one to each side). 0 = both fire from the
    // exact collision point (squished together).
    var wallSubBurstOffset = 0f
    // Half-angle of the spine/dot cone around each sub-burst's outward axis. Each
    // sub-burst's cone is rotated toward the play field so its wall-side edge sits
    // exactly along the wall (never fires into it); its far edge stops (90 - 2*this)
    // degrees short of the outward normal, so the two sub-bursts leave a central
    // gap of (180 - 4*this) degrees. At 22.5 that gap is exactly 90° — a side-to-side
    // look with a clear middle. Lower = narrower bands / wider gap.
    var coneSpreadDeg = 22.5f

    // ── Particle counts (per sub-burst, before intensity scaling) ────────
    var spikeCount = 5
    var dotCount = 5

    // ── Spike geometry (screenRatio units) ───────────────────────────────
    // Spikes render as filled triangles: a flat base (spikeBaseWidth wide) at the
    // inner end tapering to a point at the tip, so they read as spikes not lines.
    var spikeMaxLen = 2.4f          // peak spine length
    var spikeMaxLenJitter = 0.9f    // +/- random added per spine
    var spikeBaseWidth = 0.4f       // triangle base width at the spine's fat (origin) end
    // Life envelope (fractions of the burst lifespan, 0..1):
    var spikeGrowEnd = 0.32f        // t where a spine reaches spikeMaxLen
    var spikeBreakStart = 0.45f     // t where the inner end starts pulling away
    // Outward travel of a spine's *inner* end once it breaks apart:
    var spikeBreakTravel = 1.6f     // screenRatio units by end of life

    // ── Dot geometry (screenRatio units) ─────────────────────────────────
    var dotRadiusMin = 0.12f
    var dotRadiusMax = 0.5f
    var dotTravel = 2.6f            // how far a dot flies out over its life
    var dotTravelJitter = 1.2f

    // ── Timing ────────────────────────────────────────────────────────────
    var lifeFrames = 15            // whole-burst lifespan (game ticks)
    var lifeFramesJitter = 4       // +/- random per sub-burst
    var alphaFadeStart = 0.55f     // t where alpha begins fading 1 -> 0

    // ── Intensity scaling (Plan 01 always passes intensity = 1f) ─────────
    // How intensity maps onto count / size / life. Kept gentle on purpose.
    var intensityCountScale = 0.6f  // count *= (1 + (intensity-1)*this)
    var intensitySizeScale  = 0.5f
    var intensityLifeScale  = 0.4f
    var maxParticlesPerBurst = 20   // hard clamp so a big hit can't explode counts

    // ── Impact colour by speed (both collision types) ────────────────────
    // A slow hit (ball moving below this fraction of Settings.maxPuckSpeed) bakes the softer theme
    // PRIMARY colour instead of the secondary — an extra "low intensity" read. Fast hits use secondary.
    var slowColorSpeedFraction = 0.7f

    // ── Wall-on-Ball contextual intensity (Plan 02) ──────────────────────
    // intensity = base + speed + power + shield + spikes + angle, clamped. The BASE is the low-end
    // floor and the CLAMP is the high-end ceiling; lowering base widens the mild→hot spread downward
    // while the clamp preserves the peak look (the hottest hits still stack past it).
    var wallBaseIntensity  = 0.6f    // low-end floor (mildest possible hit)
    var wallSpeedWeight    = 0.35f   // scales with normalized ball speed
    var wallPowerWeight    = 0.20f   // scales with normalized launch+movement power
    var wallShieldBonus    = 0.15f   // a shielded bounce runs a touch hotter
    var wallSpikesBonus    = 0.25f   // striking extended goal spikes (or scoring) adds the most
    var wallAngleWeight    = 0.25f   // perpendicular/head-on hotter than a glancing graze
    var wallIntensityMax   = 1.65f   // hard clamp — the preserved peak
    var wallSpeedRef       = 30f     // reference "fast" speed used to normalize speed (also used by ball)
    var wallSpikesThreshold = 0.5f   // spikeProgress at/above this counts as "hit extended spikes"

    // ── Wall-on-Ball angle-driven asymmetry (user request) ───────────────
    // A head-on wall hit throws two equal sub-bursts. A glancing hit throws the FORWARD (along-travel)
    // sub-burst farther/bigger AND keeps its full intensity, while the OPPOSITE (against-travel) one is
    // both smaller/shorter-reaching AND much LESS INTENSE (fewer/shorter/smaller particles). Size bias
    // scales reach only; intensity bias scales count/size/life. Both peak at a fully-glancing hit.
    var wallForwardSizeBias  = 0.7f   // fully-glancing → forward sub-burst reach grows up to +this
    var wallBackwardSizeBias = 0.4f   // fully-glancing → opposite sub-burst reach shrinks up to this
    var wallBackwardSizeMin  = 0.35f  // floor so the trailing sub-burst never fully collapses in reach
    var wallBackwardIntensityBias = 0.75f  // fully-glancing → opposite sub-burst intensity drops up to this
    var wallBackwardIntensityMin  = 0.25f  // floor on that intensity multiplier

    // ── Ball-on-Ball contextual intensity (Plan 03) ──────────────────────
    // Tier order shielded > normal > inert (inert-on-anything is very little). Head-on (balls moving
    // opposite) hotter than a glancing (perpendicular) clip. speedRef reuses wallSpeedRef. Base is the
    // low-end floor; weights are sized so the hottest hits still reach the clamp (preserved peak).
    var ballBaseIntensity = 0.6f    // low-end floor
    var ballSpeedWeight   = 0.45f
    var ballShieldBonus   = 0.32f   // a shielded ball's burst is clearly the hottest tier
    var ballInertScale    = 0.35f   // an inert / near-stationary ball's burst is scaled WAY down
    var ballAngleWeight   = 0.38f   // opposite-direction (head-on) hotter than perpendicular
    var ballIntensityMax  = 1.7f    // hard clamp — the preserved peak
    var ballInertSpeed    = 2.0f    // at/below this per-frame speed a ball counts as "inert"

    // ── Ball-on-Ball shape (user request) ────────────────────────────────
    // The main burst is a forward "shotgun" along the ball's own heading, tighter than a wall burst.
    // A glancing (perpendicular) hit widens that cone so the spray reads as more scattered. A little
    // side-to-side spray (a few small, short-lived dots ±90° off the heading) rounds it out.
    var ballConeSpreadDeg = 22f     // main shotgun cone half-angle (tighter than the wall cone)
    var ballScatterScale  = 0.8f    // glancing widens the cone by up to this fraction (0 = never)
    var ballSideDotCount  = 4       // small side dots per burst (0 = none)
    var ballSideSpreadDeg = 26f     // half-angle of the little side sprays around ±90°
    var ballSideSizeScale = 0.45f   // side dots are this fraction of a normal dot's size/reach
    var ballSideLifeScale = 0.5f    // …and finish their arc this much sooner (short life)
}
