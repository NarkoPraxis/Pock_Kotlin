package utility

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.example.puck.R
import gameobjects.Settings

object Sounds {

    var soundPool: SoundPool = SoundPool.Builder().setMaxStreams(10).setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build())
        .build()

    var ambientSoundPool: SoundPool = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build())
        .build()

    var topBound = Settings.topGoalBottom
    var bottomBound = Settings.bottomGoalTop
    var height = bottomBound - topBound
    var width = Settings.screenWidth
    var cellWidth = width / 6f
    var cellHeight = height / 6f
    var rates = arrayOf(1f, 1.1224248f, 1.33482399f, 1.49829912f, 1.68176432f, 2f)
    var highPlayerSoundId = 0
    var lowPlayerSoundId = 0
    var wallSoundId = 0
    var goalSoundId = 0
    var chargeCollisionId = 0
    var twoChargeCollisionId = 0
    var chargeBlastoffId = 0
    var teleportId = 0
    var scoreId = 0
    var trappedId = 0
    lateinit var gameAmbiencePlayer: MediaPlayer
    lateinit var menuAmbiencePlayer: MediaPlayer
    var menuAmbienceId: Int = 0
    var menuAmbienceStreamId: Int = 0
    var weHaveAWinnerId: Int = 0
    var stingerTransitionId: Int = 0
    lateinit var context: Context
    var rateIndex = 0

    fun initialize(context: Context) {
        this.context = context
        gameAmbiencePlayer = MediaPlayer.create(context, R.raw.game_ambient_sound)
        menuAmbiencePlayer = MediaPlayer.create(context, R.raw.menu_ambient_sound)
        weHaveAWinnerId = load(R.raw.we_have_a_winner)
        stingerTransitionId = load(R.raw.stinger_transition)
        lowPlayerSoundId = load(R.raw.low_synth)
        highPlayerSoundId = load(R.raw.high_synth)
        wallSoundId = load(R.raw.wall)
        goalSoundId = load(R.raw.goal)
        chargeBlastoffId = load(R.raw.sheilds_up)
        chargeCollisionId = load(R.raw.sheilded_collision)
        scoreId = load(R.raw.goal)
    }

    fun initializeGame() {

        height = Settings.screenHeight
        width = Settings.screenWidth
        cellWidth = width / 6f
        cellHeight = height / 6f
    }

    fun playHighPlayerSound(x: Float) {
        soundPool.play(highPlayerSoundId, 1f,1f,1,0, rates[getXRate(x)])
    }

    fun playLowPlayerSound(x: Float) {
        soundPool.play(lowPlayerSoundId, 1f,1f,1,0, rates[getXRate(x)])
    }
    fun playWallSound(y: Float) {
        soundPool.play(wallSoundId, 1f,1f,1,0, rates[getYRate(y)])
    }
    fun playGoalSound(y: Float) {
        soundPool.play(goalSoundId, 1f,1f,1,0, rates[getYRate(y)])
    }
    fun playScoreSound(y: Float) {
        soundPool.play(scoreId, 1f,1f,1,0, rates[getYRate(y)])
    }
    fun playChargeCollision(x: Float) {
        soundPool.play(chargeCollisionId, 1f,1f,1,0, rates[getXRate(x)])
    }
    fun playDoubleChargeCollision(x: Float) {
        soundPool.play(twoChargeCollisionId, 1f,1f,1,0, rates[getXRate(x)])
    }
    fun playChargeBlastOff(x: Float) {
        soundPool.play(chargeBlastoffId, 1f,1f,1,0, rates[getXRate(x)])
    }
    fun playTeleportStart(y: Float) {
        soundPool.play(teleportId, 1f,1f,1,0, rates[getYRate(y)])
    }
    fun playTeleportFinish(x: Float) {
        soundPool.play(teleportId, 1f,1f,1,0, rates[getXRate(x)])
    }


    fun playGameAmbiance() {
        menuAmbiencePlayer.reset()
        gameAmbiencePlayer = MediaPlayer.create(context, R.raw.game_ambient_sound)
        gameAmbiencePlayer.isLooping = true
        gameAmbiencePlayer.start()
    }

    fun playMenuAmbiance() {
        gameAmbiencePlayer.reset()
        menuAmbiencePlayer = MediaPlayer.create(context, R.raw.menu_ambient_sound)
        menuAmbiencePlayer.isLooping = true
        menuAmbiencePlayer.start()
    }

    fun playWeHaveAWinner() {
        soundPool.play(weHaveAWinnerId, 1f, 1f, 1, 0, 1f)
    }

    private fun getXRate(x: Float) : Int {
        return (x / cellWidth).toInt()
    }

    private fun getYRate(y: Float) : Int {
        var test = (y / cellHeight).toInt()
//        if (test < 0 || test > 5) {
//            test = 5
//        }
        return test
    }

    private fun load(id: Int) : Int {
        return soundPool.load(context, id, 1)
    }

    private fun loadAmbient(id: Int) : Int {
        return ambientSoundPool.load(context, id, 2)
    }
}