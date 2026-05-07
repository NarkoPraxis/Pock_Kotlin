package utility

import gameobjects.Settings

object SoundSpatializer {
    internal val rates = floatArrayOf(1f, 1.1224248f, 1.33482399f, 1.49829912f, 1.68176432f, 2f)

    fun getXRate(x: Float): Float {
        val col = ((x / Settings.screenWidth) * rates.size).toInt().coerceIn(0, rates.size - 1)
        return rates[col]
    }

    fun getYRate(y: Float): Float {
        val row = ((y / Settings.screenHeight) * rates.size).toInt().coerceIn(0, rates.size - 1)
        return rates[row]
    }
}
