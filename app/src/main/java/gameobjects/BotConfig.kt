package gameobjects

data class BotConfig(
    val accuracyVariance: Float,      // ± degrees off-course (higher = worse aim)
    val shotFrequencyVariance: Int,   // ± frames added to base shot interval
    // basePower and powerVariance are on a 0–100 scale where:
    //   0   = chargeStart (minimal charge)
    //   100 = SweetSpot window closes / Draining begins
    //  >100 = bot deliberately fires in Draining phase (weaker shot)
    val powerVariance: Float,         // ± percentage around basePower (uniform random)
    val baseShotFrequency: Int,       // base frames between shot attempts
    val basePower: Float,             // target charge as % of the way to Draining threshold
    val reactionDelay: Int,           // frames bot waits before acting after a goal reset
    val sweetSpotChance: Int,         // 0–100 % chance bot guarantees a mid-window SweetSpot hit
) {
    companion object {
        // Easy: mostly weak shots; ~35% organic sweet spot; ~5% accidentally overcharges into Draining.
        val Easy = BotConfig(
            accuracyVariance = 25f,
            shotFrequencyVariance = 40,
            powerVariance = 90f,
            baseShotFrequency = 90,
            basePower = 20f,
            reactionDelay = 50,
            sweetSpotChance = 5,
        )
        // Medium: mix of moderate and strong shots; ~56% organic sweet spot.
        val Medium = BotConfig(
            accuracyVariance = 12f,
            shotFrequencyVariance = 20,
            powerVariance = 40f,
            baseShotFrequency = 55,
            basePower = 45f,
            reactionDelay = 25,
            sweetSpotChance = 25,
        )
        // Hard: almost always near sweet spot organically; ~90% organic sweet spot.
        val Hard = BotConfig(
            accuracyVariance = 4f,
            shotFrequencyVariance = 8,
            powerVariance = 25f,
            baseShotFrequency = 30,
            basePower = 60f,
            reactionDelay = 10,
            sweetSpotChance = 65,
        )
    }
}
