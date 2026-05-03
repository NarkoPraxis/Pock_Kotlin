package gameobjects

data class BotConfig(
    val accuracyVariance: Float,
    val shotFrequencyVariance: Int,
    val powerVariance: Float,
    val baseShotFrequency: Int,
    val basePower: Float,
    val reactionDelay: Int,
    val sweetSpotChance: Int,
) {
    companion object {
        val Easy = BotConfig(
            accuracyVariance = 25f,
            shotFrequencyVariance = 40,
            powerVariance = 15f,
            baseShotFrequency = 90,
            basePower = 25f,
            reactionDelay = 50,
            sweetSpotChance = 5,
        )
        val Medium = BotConfig(
            accuracyVariance = 12f,
            shotFrequencyVariance = 20,
            powerVariance = 8f,
            baseShotFrequency = 55,
            basePower = 35f,
            reactionDelay = 25,
            sweetSpotChance = 25,
        )
        val Hard = BotConfig(
            accuracyVariance = 4f,
            shotFrequencyVariance = 8,
            powerVariance = 3f,
            baseShotFrequency = 30,
            basePower = 44f,
            reactionDelay = 10,
            sweetSpotChance = 65,
        )
    }
}
