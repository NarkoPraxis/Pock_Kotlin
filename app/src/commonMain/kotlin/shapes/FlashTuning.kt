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
}
