package utility

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.runoutzone.pockpock.R
import gameobjects.Settings

actual object Sounds {

    var soundPool: SoundPool = SoundPool.Builder().setMaxStreams(10).setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build())
        .build()

    var highPlayerSoundId = 0
    var lowPlayerSoundId = 0
    var wallSoundId = 0
    var goalSoundId = 0
    var chargeCollisionId = 0
    var twoChargeCollisionId = 0
    var chargeBlastoffId = 0
    var teleportId = 0
    var sweetSpotSoundId = 0
    var gameStartSoundId = 0
    var scoreId = 0
    var weHaveAWinnerId: Int = 0
    var stingerTransitionId: Int = 0

    lateinit var context: Context

    private enum class AmbienceMode { MENU, GAME }
    private var ambienceMode: AmbienceMode? = null

    // We deliberately do NOT request audio focus. The game is meant to be played alongside
    // whatever the user is already listening to (audiobook, podcast, music, YouTube) without
    // pausing or ducking it. Requesting AUDIOFOCUS_GAIN would force those apps to pause, so we
    // never request focus and never react to focus changes — our SoundPool/MediaPlayer simply
    // mix on top of (or stay silent alongside) other media.
    //
    // SFX are gated by deriving "may I play?" from the explicit reasons to be silent,
    // rather than a single sticky `sfxPaused` boolean that the old design could leave
    // stuck `true` forever (a dropped ad-dismiss callback). Each reason below has a
    // guaranteed clear path, so the gate always self-heals.
    private var adMuted = false          // a rewarded ad is on screen
    private var appBackgrounded = false  // Activity is paused (onPause without onResume)

    private val sfxAllowed: Boolean
        get() = !adMuted && !appBackgrounded

    // Safety net: AdMob does not guarantee onAdDismissed/onAdFailedToShow ever fires. If it
    // doesn't, adMuted would otherwise stay true forever. This watchdog force-clears the ad
    // state after longer than any real rewarded ad could run.
    private const val MAX_AD_MUTE_MS = 90_000L
    private var adWatchdog: Runnable? = null

    private var initialized = false

    private const val CROSSFADE_MS = 3000L
    private const val STEP_MS = 16L
    private var ambienceRes: Int = 0
    private var primaryPlayer: MediaPlayer? = null
    private var secondaryPlayer: MediaPlayer? = null
    private val ambienceHandler = Handler(Looper.getMainLooper())
    private var scheduleRunnable: Runnable? = null
    private var fadeRunnable: Runnable? = null
    private var fadeStartTime: Long = 0L
    private var fadePauseOffset: Long = 0L

    actual fun initialize(context: Any?) {
        val ctx = (context as Context).applicationContext
        this.context = ctx
        // initialize() runs on every Activity onCreate, including recreate() (dark-mode toggle).
        // The SoundPool lives for the whole process (this is an `object`), so reloading would
        // orphan samples and reassign IDs to not-yet-loaded samples (async load → dropped play()
        // calls right after a recreate). Guard it.
        if (initialized) return
        initialized = true
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
        sweetSpotSoundId = load(R.raw.sweet_spot)
        gameStartSoundId = load(R.raw.game_start)
    }

    actual fun initializeGame() {
        // A new game only starts when the game screen is visible and the app is foregrounded,
        // so re-assert a known-good SFX baseline. This is the backstop that guarantees a fresh
        // game is never silenced by stale state carried in from a prior ad / pause / focus blip.
        appBackgrounded = false
        if (!adMuted) soundPool.autoResume()
    }

    private val effectiveBackgroundVol: Float
        get() {
            if (Storage.soundMasterMuted || Storage.soundBackgroundMuted) return 0f
            return (Storage.soundMasterVolume / 100f) * (Storage.soundBackgroundVolume / 100f)
        }

    private val effectiveSfxVol: Float
        get() {
            if (Storage.soundMasterMuted || Storage.soundSfxMuted) return 0f
            // Demo game plays behind the main menu; silence its SFX when menu sounds are muted.
            if (Settings.isDemoMode && Storage.menusMuted) return 0f
            return (Storage.soundMasterVolume / 100f) * (Storage.soundSfxVolume / 100f)
        }

    actual fun applyBackgroundVolume() {
        val vol = effectiveBackgroundVol
        try { primaryPlayer?.setVolume(vol, vol) } catch (e: Exception) {}
        try { secondaryPlayer?.setVolume(vol, vol) } catch (e: Exception) {}
    }

    // SoundPool.autoPause() only freezes already-playing streams; new play() calls bypass it.
    // This guard ensures no new SFX fire while the app is paused, an ad is showing, or audio
    // focus is lost. `sfxAllowed` is derived, so it can never get stuck silent.
    private fun playSfx(soundId: Int, vol: Float, rate: Float) {
        if (!sfxAllowed) return
        soundPool.play(soundId, vol, vol, 1, 0, rate)
    }

    actual fun playHighPlayerSound(x: Float) {
        playSfx(highPlayerSoundId, effectiveSfxVol, SoundSpatializer.getXRate(x))
    }

    actual fun playLowPlayerSound(x: Float) {
        playSfx(lowPlayerSoundId, effectiveSfxVol, SoundSpatializer.getXRate(x))
    }

    actual fun playLowPlayerSweetSpotSound(x: Float) {
        playSfx(sweetSpotSoundId, effectiveSfxVol * .9f, SoundSpatializer.rates[2])
    }

    actual fun playHighPlayerSweetSpotSound(y: Float) {
        playSfx(sweetSpotSoundId, effectiveSfxVol * .9f, SoundSpatializer.rates[0])
    }

    actual fun playGameStart() {
        //playSfx(gameStartSoundId, effectiveSfxVol, SoundSpatializer.getYRate(Settings.middleY))
    }

    actual fun playWallSound(y: Float) =
        playSfx(wallSoundId, effectiveSfxVol, SoundSpatializer.getYRate(y))

    actual fun playGoalSound(y: Float) =
        playSfx(goalSoundId, effectiveSfxVol, SoundSpatializer.getYRate(y))

    actual fun playScoreSound(y: Float) =
        playSfx(scoreId, effectiveSfxVol, SoundSpatializer.getYRate(y))

    actual fun playChargeCollision(x: Float) =
        playSfx(chargeCollisionId, effectiveSfxVol, SoundSpatializer.getXRate(x))

    actual fun playDoubleChargeCollision(x: Float) =
        playSfx(twoChargeCollisionId, effectiveSfxVol, SoundSpatializer.getXRate(x))

    actual fun playChargeBlastOff(x: Float) =
        playSfx(chargeBlastoffId, effectiveSfxVol, SoundSpatializer.getXRate(x))

    actual fun playTeleportStart(y: Float) =
        playSfx(teleportId, effectiveSfxVol, SoundSpatializer.getYRate(y))

    actual fun playTeleportFinish(x: Float) =
        playSfx(teleportId, effectiveSfxVol, SoundSpatializer.getXRate(x))

    actual fun playGameAmbiance() {
        if (ambienceMode == AmbienceMode.GAME) return
        ambienceMode = AmbienceMode.GAME
        startAmbience(R.raw.game_ambient_sound)
    }

    actual fun playMenuAmbiance() {
        // Menu sounds muted: tear down any menu music that's playing and stay silent.
        // Releasing the players + clearing ambienceMode means a later unmute restarts cleanly.
        if (Storage.menusMuted) {
            if (ambienceMode == AmbienceMode.MENU) {
                cancelAllCallbacks()
                try { primaryPlayer?.release() } catch (e: Exception) { }
                try { secondaryPlayer?.release() } catch (e: Exception) { }
                primaryPlayer = null
                secondaryPlayer = null
                ambienceMode = null
            }
            return
        }
        if (ambienceMode == AmbienceMode.MENU) {
            try { if (primaryPlayer?.isPlaying == false) primaryPlayer?.start() } catch (e: Exception) { }
            if (scheduleRunnable == null && fadeRunnable == null) scheduleNextCrossfade()
            return
        }
        ambienceMode = AmbienceMode.MENU
        startAmbience(R.raw.menu_ambient_sound)
    }

    actual fun pauseAll() {
        appBackgrounded = true
        soundPool.autoPause()
        pauseMusic()
    }

    actual fun stopAllSfx() {
        // SoundPool drops play() calls when all streams are saturated (setMaxStreams=10),
        // so there's no buildup to clear like there is on iOS. In-flight SFX are
        // short and self-terminating; no action needed.
    }

    actual fun resumeAll() {
        appBackgrounded = false
        resumeAudioIfAllowed()
    }

    actual fun autoPauseSfx() {
        soundPool.autoPause()
    }

    actual fun autoResumeSfx() {
        soundPool.autoResume()
    }

    /** Pause the ambient music players, preserving crossfade progress. */
    private fun pauseMusic() {
        if (fadeRunnable != null) {
            fadePauseOffset += System.currentTimeMillis() - fadeStartTime
        }
        cancelAllCallbacks()
        try { if (primaryPlayer?.isPlaying == true) primaryPlayer?.pause() } catch (e: Exception) { }
        try { if (secondaryPlayer?.isPlaying == true) secondaryPlayer?.pause() } catch (e: Exception) { }
    }

    /**
     * Resume music + SFX only when no reason to stay silent remains. Called from every
     * "clear a reason" path (resumeAll/onResume, unmuteForAd); whichever one clears the last
     * reason performs the resume. No single path can strand the system.
     */
    private fun resumeAudioIfAllowed() {
        if (adMuted || appBackgrounded) return
        soundPool.autoResume()
        val pri = primaryPlayer ?: return
        val sec = secondaryPlayer
        try { if (!pri.isPlaying) pri.start() } catch (e: Exception) { }
        try { if (sec != null && !sec.isPlaying) sec.start() } catch (e: Exception) { }
        fadeStartTime = System.currentTimeMillis()
        if (sec != null) {
            runFadeAnimation(pri, sec)
        } else {
            scheduleNextCrossfade()
        }
    }

    actual fun playWeHaveAWinner() =
        playSfx(weHaveAWinnerId, effectiveSfxVol, 1f)

    actual fun muteForAd() {
        adMuted = true
        soundPool.autoPause()
        pauseMusic()
        // Arm the safety net in case the ad never reports back (see MAX_AD_MUTE_MS).
        adWatchdog?.let { ambienceHandler.removeCallbacks(it) }
        val w = Runnable {
            adWatchdog = null
            if (adMuted) {
                // The ad SDK never delivered a dismiss/fail callback. Recover so SFX aren't
                // silenced for the rest of the session.
                Settings.adIsPlaying = false
                clearAdMute()
            }
        }
        adWatchdog = w
        ambienceHandler.postDelayed(w, MAX_AD_MUTE_MS)
    }

    actual fun unmuteForAd() {
        clearAdMute()
    }

    private fun clearAdMute() {
        adWatchdog?.let { ambienceHandler.removeCallbacks(it) }
        adWatchdog = null
        adMuted = false
        resumeAudioIfAllowed()
        playMenuAmbiance()
    }

    actual fun abandonAudioFocus() {
        // No-op: we never request audio focus (see note by adMuted/appBackgrounded), so there
        // is nothing to abandon. Other apps' media is never ours to release.
    }

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

    private fun scheduleNextCrossfade() {
        val player = primaryPlayer ?: return
        val remaining = player.duration.toLong() - player.currentPosition.toLong()
        val delay = remaining - CROSSFADE_MS
        val r = Runnable { beginCrossfade() }
        scheduleRunnable = r
        if (delay > 0) ambienceHandler.postDelayed(r, delay) else ambienceHandler.post(r)
    }

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

    private fun runFadeAnimation(outgoing: MediaPlayer, incoming: MediaPlayer) {
        fadeRunnable?.let { ambienceHandler.removeCallbacks(it) }
        fadeRunnable = object : Runnable {
            override fun run() {
                val elapsed = fadePauseOffset + (System.currentTimeMillis() - fadeStartTime)
                val t = (elapsed.toFloat() / CROSSFADE_MS).coerceIn(0f, 1f)
                val smooth = t * t * (3f - 2f * t)
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

    private fun load(id: Int): Int {
        return soundPool.load(context, id, 1)
    }
}
