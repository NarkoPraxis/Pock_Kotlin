package utility

actual object Sounds {
    actual fun initialize(context: Any?) {}
    actual fun initializeGame() {}
    actual fun playHighPlayerSound(x: Float) {}
    actual fun playLowPlayerSound(x: Float) {}
    actual fun playLowPlayerSweetSpotSound(x: Float) {}
    actual fun playHighPlayerSweetSpotSound(y: Float) {}
    actual fun playGameStart() {}
    actual fun playWallSound(y: Float) {}
    actual fun playGoalSound(y: Float) {}
    actual fun playScoreSound(y: Float) {}
    actual fun playChargeCollision(x: Float) {}
    actual fun playDoubleChargeCollision(x: Float) {}
    actual fun playChargeBlastOff(x: Float) {}
    actual fun playTeleportStart(y: Float) {}
    actual fun playTeleportFinish(x: Float) {}
    actual fun playGameAmbiance() {}
    actual fun playMenuAmbiance() {}
    actual fun pauseAll() {}
    actual fun resumeAll() {}
    actual fun autoPauseSfx() {}
    actual fun autoResumeSfx() {}
    actual fun applyBackgroundVolume() {}
    actual fun playWeHaveAWinner() {}
    actual fun abandonAudioFocus() {}
}
