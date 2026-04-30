package gameobjects.puckstyle

/**
 * Named radius multiples for hot-path draw calls.
 *
 * Add new entries when you introduce a new magic multiplier. Naming: P060 = 0.60 × radius.
 * [PuckRenderer.r] rebuilds the backing array whenever radius changes — reads cost one array
 * lookup instead of a per-frame multiply, and every multiplier has a documented purpose.
 */
enum class BallSize(val factor: Float) {
    P018(0.18f),  // tight blur mask (fire scorch)
    P032(0.32f),  // small spark dot
    P038(0.38f),  // star inner-to-outer ratio (galaxy)
    P040(0.40f),  // core spawn-zone boundary
    P050(0.50f),  // half-radius depth (prism paddle tip)
    P060(0.60f),  // core orb radius — fire, metal, ice, plasma inner body
    P070(0.70f),  // scatter spawn jitter spread
    P075(0.75f),  // prism ember path reach
    P080(0.80f),  // near-body scale (prism tail entry size)
    P090(0.90f),  // tight gap just inside the body edge
    P100(1.00f),  // body edge (full radius — hexagon vertices, etc.)
    P110(1.10f),  // slightly beyond body edge (classic tail head size)
    P120(1.20f),
    P130(1.30f),
    P145(1.45f),  // fire scorch gradient outer reach
    P200(2.00f),  // double-radius spread
    P220(2.20f),  // prism scatter travel distance
    P500(5.00f),  // max paddle standoff distance from puck center
}
