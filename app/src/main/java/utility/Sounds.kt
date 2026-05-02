package utility

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
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
    var menuAmbienceId: Int = 0
    var menuAmbienceStreamId: Int = 0
    var weHaveAWinnerId: Int = 0
    var stingerTransitionId: Int = 0
    lateinit var context: Context
    var rateIndex = 0

    private enum class AmbienceMode { MENU, GAME }
    private var ambienceMode: AmbienceMode? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    // Crossfade system — outgoing fades from 1→0, incoming fades from 0→1.
    // Both use a smoothstep curve so they meet at exactly 50% at the midpoint.
    private const val CROSSFADE_MS = 3000L
    private const val STEP_MS = 16L
    private var ambienceRes: Int = 0
    private var primaryPlayer: MediaPlayer? = null   // currently audible track
    private var secondaryPlayer: MediaPlayer? = null // fading-in track during crossfade
    private val ambienceHandler = Handler(Looper.getMainLooper())
    private var scheduleRunnable: Runnable? = null   // fires when it's time to start next crossfade
    private var fadeRunnable: Runnable? = null       // drives the volume animation each frame
    private var fadeStartTime: Long = 0L
    private var fadePauseOffset: Long = 0L           // accumulated elapsed ms before last pause

    fun initialize(context: Context) {
        this.context = context.applicationContext
        requestAudioFocus()
        weHaveAWinnerId = load(R.raw.we_have_a_winner)
        stingerTransitionId = load(R.raw.stinger_transition)
        lowPlayerSoundId = load(R.raw.low_synth)
        highPlayerSoundId = load(R.raw.high_synth)
        wallSoundId = load(R.raw.wall)
        goalSoundId = load(R.raw.goal)
        chargeBlastoffId = load(R.raw.sheilds_up)
        chargeCollisionId = load(R.raw.sheilded_collision)
        twoChargeCollisionId = load(R.raw.sheilded_collision)
        teleportId = load(R.raw.charge_activated)
        scoreId = load(R.raw.goal)
    }

    fun initializeGame() {
        height = Settings.screenHeight
        width = Settings.screenWidth
        cellWidth = width / 6f
        cellHeight = height / 6f
    }

    private val effectiveBackgroundVol: Float
        get() {
            if (Storage.soundMasterMuted || Storage.soundBackgroundMuted) return 0f
            return (Storage.soundMasterVolume / 100f) * (Storage.soundBackgroundVolume / 100f)
        }

    private val effectiveSfxVol: Float
        get() {
            if (Storage.soundMasterMuted || Storage.soundSfxMuted) return 0f
            return (Storage.soundMasterVolume / 100f) * (Storage.soundSfxVolume / 100f)
        }

    fun applyBackgroundVolume() {
        val vol = effectiveBackgroundVol
        try { primaryPlayer?.setVolume(vol, vol) } catch (e: Exception) {}
        try { secondaryPlayer?.setVolume(vol, vol) } catch (e: Exception) {}
    }

    fun playHighPlayerSound(x: Float) {
        val v = effectiveSfxVol
        soundPool.play(highPlayerSoundId, v, v, 1, 0, rates[getXRate(x)])
    }

    fun playLowPlayerSound(x: Float) {
        val v = effectiveSfxVol
        soundPool.play(lowPlayerSoundId, v, v, 1, 0, rates[getXRate(x)])
    }
    fun playWallSound(y: Float) {
        val v = effectiveSfxVol
        soundPool.play(wallSoundId, v, v, 1, 0, rates[getYRate(y)])
    }
    fun playGoalSound(y: Float) {
        val v = effectiveSfxVol
        soundPool.play(goalSoundId, v, v, 1, 0, rates[getYRate(y)])
    }
    fun playScoreSound(y: Float) {
        val v = effectiveSfxVol
        soundPool.play(scoreId, v, v, 1, 0, rates[getYRate(y)])
    }
    fun playChargeCollision(x: Float) {
        val v = effectiveSfxVol
        soundPool.play(chargeCollisionId, v, v, 1, 0, rates[getXRate(x)])
    }
    fun playDoubleChargeCollision(x: Float) {
        val v = effectiveSfxVol
        soundPool.play(twoChargeCollisionId, v, v, 1, 0, rates[getXRate(x)])
    }
    fun playChargeBlastOff(x: Float) {
        val v = effectiveSfxVol
        soundPool.play(chargeBlastoffId, v, v, 1, 0, rates[getXRate(x)])
    }
    fun playTeleportStart(y: Float) {
        val v = effectiveSfxVol
        soundPool.play(teleportId, v, v, 1, 0, rates[getYRate(y)])
    }
    fun playTeleportFinish(x: Float) {
        val v = effectiveSfxVol
        soundPool.play(teleportId, v, v, 1, 0, rates[getXRate(x)])
    }

    fun playGameAmbiance() {
        if (ambienceMode == AmbienceMode.GAME) return
        ambienceMode = AmbienceMode.GAME
        startAmbience(R.raw.game_ambient_sound)
    }

    fun playMenuAmbiance() {
        if (ambienceMode == AmbienceMode.MENU) {
            try { if (primaryPlayer?.isPlaying == false) primaryPlayer?.start() } catch (e: Exception) { }
            return
        }
        ambienceMode = AmbienceMode.MENU
        startAmbience(R.raw.menu_ambient_sound)
    }

    fun pauseAll() {
        soundPool.autoPause()
        // Accumulate elapsed fade time before canceling the animation runnable
        if (fadeRunnable != null) {
            fadePauseOffset += System.currentTimeMillis() - fadeStartTime
        }
        cancelAllCallbacks()
        try { if (primaryPlayer?.isPlaying == true) primaryPlayer?.pause() } catch (e: Exception) { }
        try { if (secondaryPlayer?.isPlaying == true) secondaryPlayer?.pause() } catch (e: Exception) { }
    }

    fun resumeAll() {
        soundPool.autoResume()
        val pri = primaryPlayer ?: return
        val sec = secondaryPlayer
        try { if (!pri.isPlaying) pri.start() } catch (e: Exception) { }
        try { if (sec != null && !sec.isPlaying) sec.start() } catch (e: Exception) { }
        // Reset the wall-clock reference so fadePauseOffset carries the paused progress
        fadeStartTime = System.currentTimeMillis()
        if (sec != null) {
            runFadeAnimation(pri, sec)
        } else {
            scheduleNextCrossfade()
        }
    }

    // Starts a fresh track, releasing any previous players and canceling pending crossfades.
    private fun startAmbience(resId: Int) {
        cancelAllCallbacks()
        primaryPlayer?.release()
        secondaryPlayer?.release()
        secondaryPlayer = null
        fadePauseOffset = 0L
        ambienceRes = resId
        primaryPlayer = MediaPlayer.create(context, resId)?.also {
            val vol = effectiveBackgroundVol
            it.setVolume(vol, vol)
            it.isLooping = false
            it.start()
        }
        scheduleNextCrossfade()
    }

    // Posts a delayed callback that fires when the crossfade should begin
    // (i.e. CROSSFADE_MS before the track ends).
    private fun scheduleNextCrossfade() {
        val player = primaryPlayer ?: return
        val remaining = player.duration.toLong() - player.currentPosition.toLong()
        val delay = remaining - CROSSFADE_MS
        val r = Runnable { beginCrossfade() }
        scheduleRunnable = r
        if (delay > 0) ambienceHandler.postDelayed(r, delay) else ambienceHandler.post(r)
    }

    // Creates the incoming player and kicks off the volume animation.
    private fun beginCrossfade() {
        scheduleRunnable = null
        val res = ambienceRes
        if (res == 0) return
        val outgoing = primaryPlayer ?: return
        val incoming = MediaPlayer.create(context, res) ?: return
        incoming.setVolume(0f, 0f)
        incoming.isLooping = false
        incoming.start()
        secondaryPlayer = incoming
        fadePauseOffset = 0L
        fadeStartTime = System.currentTimeMillis()
        runFadeAnimation(outgoing, incoming)
    }

    // Drives the crossfade animation at STEP_MS intervals.
    // Outgoing: ease-out from 1→0.  Incoming: ease-in from 0→1.
    // Both curves use smoothstep so they meet at exactly 0.5 at t=0.5.
    private fun runFadeAnimation(outgoing: MediaPlayer, incoming: MediaPlayer) {
        fadeRunnable?.let { ambienceHandler.removeCallbacks(it) }
        fadeRunnable = object : Runnable {
            override fun run() {
                val elapsed = fadePauseOffset + (System.currentTimeMillis() - fadeStartTime)
                val t = (elapsed.toFloat() / CROSSFADE_MS).coerceIn(0f, 1f)
                val smooth = t * t * (3f - 2f * t)   // smoothstep: ease-in on incoming, ease-out on outgoing
                val bgVol = effectiveBackgroundVol
                try { outgoing.setVolume((1f - smooth) * bgVol, (1f - smooth) * bgVol) } catch (e: Exception) { }
                try { incoming.setVolume(smooth * bgVol, smooth * bgVol) } catch (e: Exception) { }
                if (t < 1f) {
                    ambienceHandler.postDelayed(this, STEP_MS)
                } else {
                    try { outgoing.release() } catch (e: Exception) { }
                    primaryPlayer = incoming
                    secondaryPlayer = null
                    fadeRunnable = null
                    fadePauseOffset = 0L
                    scheduleNextCrossfade()
                }
            }
        }
        ambienceHandler.post(fadeRunnable!!)
    }

    private fun cancelAllCallbacks() {
        scheduleRunnable?.let { ambienceHandler.removeCallbacks(it) }
        fadeRunnable?.let { ambienceHandler.removeCallbacks(it) }
        scheduleRunnable = null
        fadeRunnable = null
    }

    private fun requestAudioFocus() {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { focusChange ->
                if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                    focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    pauseAll()
                }
            }
            .build()
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.requestAudioFocus(audioFocusRequest!!)
    }

    fun abandonAudioFocus() {
        audioFocusRequest?.let {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.abandonAudioFocusRequest(it)
            audioFocusRequest = null
        }
    }

    fun playWeHaveAWinner() {
        val v = effectiveSfxVol
        soundPool.play(weHaveAWinnerId, v, v, 1, 0, 1f)
    }

    private fun getXRate(x: Float) : Int {
        val index = (x / cellWidth).toInt()
        return if (index < 0 || index > 5) 5 else index
    }

    private fun getYRate(y: Float) : Int {
        var test = (y / cellHeight).toInt()
        if (test < 0 || test > 5) {
            test = 5
        }
        return test
    }

    private fun load(id: Int) : Int {
        return soundPool.load(context, id, 1)
    }

    private fun loadAmbient(id: Int) : Int {
        return ambientSoundPool.load(context, id, 2)
    }
}
