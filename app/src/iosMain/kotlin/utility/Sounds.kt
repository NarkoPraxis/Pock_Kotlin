@file:OptIn(ExperimentalForeignApi::class)
package utility

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.pointed
import kotlinx.cinterop.value
import platform.AVFAudio.*
import platform.Foundation.*
import platform.darwin.*
import gameobjects.Settings
import utility.Logic

actual object Sounds {
    // endTimeSec is the wall-clock time (NSDate.timeIntervalSinceReferenceDate) when this
    // channel's currently-scheduled buffer is expected to finish playing. Channels are
    // selected by minimum endTimeSec so the new sound always lands on the longest-idle
    // node — mirroring SoundPool's auto-reuse of finished streams.
    private class SfxChannel(val player: AVAudioPlayerNode, val varispeed: AVAudioUnitVarispeed) {
        var endTimeSec: Double = 0.0
    }

    private val sfxChannels = mutableListOf<SfxChannel>()
    private val sfxBuffers = mutableMapOf<String, AVAudioPCMBuffer>()
    private val engine = AVAudioEngine()

    // Canonical PCM format that every channel speaks. Source MP3s ship at mixed sample
    // rates (some 44.1 kHz, some 48 kHz); AVAudioPlayerNode silently drops scheduleBuffer
    // calls whose format doesn't match the connection format, which is what made
    // sheilded_collision and other 44.1 kHz sounds inaudible. Converting every buffer to
    // this single format at load time keeps the connection format stable for every node.
    private val canonicalFormat: AVAudioFormat by lazy {
        AVAudioFormat(standardFormatWithSampleRate = 44100.0, channels = 2u)!!
    }

    private var ambientPlayer: AVAudioPlayer? = null
    private var incomingPlayer: AVAudioPlayer? = null
    private var currentAmbienceName: String? = null
    private var crossfadeGeneration = 0

    // Audio is suspended for explicit, independently-cleared reasons rather than a single
    // sticky `isPaused`/`adMuted` pair the old design could leave stuck (a dropped ad-dismiss
    // callback, or — the iOS-specific killer — an AVAudioSession interruption from a call/Siri/
    // ad that stops the engine in the foreground with nothing to restart it). Each reason has a
    // guaranteed clear path, so `audioSuspended` always reflects reality and self-heals.
    private var adMuted = false          // a rewarded ad is on screen
    private var appBackgrounded = false  // app went to background
    private var interrupted = false      // AVAudioSession interruption in progress (call, Siri, ad…)

    private val audioSuspended: Boolean
        get() = adMuted || appBackgrounded || interrupted

    // Safety net for a dropped ad-dismiss callback (see Android twin). dispatch_after can't be
    // cancelled, so a generation counter invalidates a stale watchdog.
    private var adWatchdogGen = 0
    private const val MAX_AD_MUTE_SECS = 90.0

    private var initialized = false

    private enum class AmbienceMode { MENU, GAME }
    private var ambienceMode: AmbienceMode? = null

    private const val CROSSFADE_SECS = 3.0

    private val effectiveSfxVol: Float
        get() {
            if (Storage.soundMasterMuted || Storage.soundSfxMuted) return 0f
            // Demo game plays behind the main menu; silence its SFX when menu sounds are muted.
            if (Settings.isDemoMode && Storage.menusMuted) return 0f
            return (Storage.soundMasterVolume / 100f) * (Storage.soundSfxVolume / 100f)
        }

    private val effectiveBgVol: Float
        get() {
            if (Storage.soundMasterMuted || Storage.soundBackgroundMuted) return 0f
            return (Storage.soundMasterVolume / 100f) * (Storage.soundBackgroundVolume / 100f)
        }

    actual fun initialize(context: Any?) {
        // Guard against a second call (e.g. host UIViewController recreated): re-running would
        // attach another 10 SFX nodes to the engine and stack duplicate notification observers.
        if (initialized) return
        initialized = true
        setupAudioSession()
        setupSfxPool()
        loadSounds()
        registerLifecycleObservers()
    }

    actual fun initializeGame() {
        // A new game only starts foregrounded, so re-assert a known-good baseline. This is the
        // backstop guaranteeing a fresh game is never silenced by stale state from a prior ad,
        // background, or interruption that failed to clear.
        appBackgrounded = false
        if (!audioSuspended) {
            AVAudioSession.sharedInstance().setActive(true, error = null)
            try { engine.startAndReturnError(null) } catch (_: Exception) {}
        }
    }

    private fun setupAudioSession() {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryPlayback, error = null)
        session.setActive(true, error = null)
    }

    private fun registerLifecycleObservers() {
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = "UIApplicationDidEnterBackgroundNotification",
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ ->
            appBackgrounded = true
            suspendAudio()
        }
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = "UIApplicationWillEnterForegroundNotification",
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ ->
            appBackgrounded = false
            resumeAudioIfAllowed()
            // Any in-progress touches were cancelled by the system while backgrounded;
            // release pointer locks so neither player stays permanently frozen.
            Logic.releaseAllPointers()
        }
        // AVAudioSession interruptions (incoming/declined call, Siri, alarm, another app's audio,
        // and a rewarded ad presenting its own audio) stop the engine and deactivate the session
        // WITHOUT backgrounding the app — so the foreground observer above never fires. Without
        // this handler the engine stays dead and every sound is silent after the interruption ends.
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVAudioSessionInterruptionNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { notification ->
            val type = (notification?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)
                ?.unsignedLongValue
            when (type) {
                AVAudioSessionInterruptionTypeBegan -> {
                    interrupted = true
                    suspendAudio()
                }
                AVAudioSessionInterruptionTypeEnded -> {
                    interrupted = false
                    resumeAudioIfAllowed()
                }
            }
        }
        // A route change (headphones plugged/unplugged, Bluetooth connect) stops AVAudioEngine.
        // It must be restarted or all audio dies until the next cold start.
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVAudioEngineConfigurationChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ ->
            if (!audioSuspended) {
                try { engine.startAndReturnError(null) } catch (_: Exception) {}
            }
        }
    }

    private fun setupSfxPool() {
        repeat(10) {
            val player = AVAudioPlayerNode()
            // AVAudioUnitVarispeed couples pitch to playback rate (resampling), matching
            // Android SoundPool.play(..., rate). AVAudioUnitTimePitch keeps pitch constant
            // while changing tempo — the wrong behavior for the spatializer.
            val varispeed = AVAudioUnitVarispeed()
            engine.attachNode(player)
            engine.attachNode(varispeed)
            engine.connect(player, varispeed, format = canonicalFormat)
            engine.connect(varispeed, engine.mainMixerNode, format = canonicalFormat)
            sfxChannels.add(SfxChannel(player, varispeed))
        }
        try { engine.startAndReturnError(null) } catch (_: Exception) {}
    }

    // Resolves a sound file name to an NSURL via the KMP compose-resources bundle path.
    // Files from commonMain/composeResources/files/sounds/ land at compose-resources/files/sounds/
    // inside the iOS app bundle.
    private fun soundUrl(name: String): NSURL? {
        for (ext in listOf("mp3", "wav", "m4a")) {
            val url = NSBundle.mainBundle.URLForResource(
                name = name,
                withExtension = ext,
                subdirectory = "compose-resources/composeResources/pock_kotlin.app.generated.resources/files/sounds"
            ) ?: continue
            return url
        }
        return null
    }

    private fun loadSounds() {
        listOf(
            "high_synth", "low_synth", "wall", "goal",
            "sheilds_up", "sheilded_collision", "charge_activated", "sweet_spot",
            "we_have_a_winner", "game_start"
        ).forEach { name ->
            val url = soundUrl(name) ?: return@forEach
            val file = AVAudioFile(forReading = url, error = null) ?: return@forEach
            val srcBuffer = AVAudioPCMBuffer(
                pCMFormat = file.processingFormat,
                frameCapacity = file.length.toUInt()
            ) ?: return@forEach
            file.readIntoBuffer(srcBuffer, error = null)

            // Resample/reformat into canonicalFormat so every sfxBuffer matches the
            // channel's connection format. Without this step, files whose sample rate
            // differs from the first-played file get dropped by scheduleBuffer.
            val srcFormat = file.processingFormat
            if (srcFormat.sampleRate == canonicalFormat.sampleRate &&
                srcFormat.channelCount == canonicalFormat.channelCount &&
                srcFormat.commonFormat == canonicalFormat.commonFormat) {
                sfxBuffers[name] = srcBuffer
                return@forEach
            }
            val converter = AVAudioConverter(fromFormat = srcFormat, toFormat = canonicalFormat)
                ?: return@forEach
            val ratio = canonicalFormat.sampleRate / srcFormat.sampleRate
            val outCapacity = (srcBuffer.frameLength.toDouble() * ratio).toULong().toUInt() + 1024u
            val dstBuffer = AVAudioPCMBuffer(
                pCMFormat = canonicalFormat,
                frameCapacity = outCapacity
            ) ?: return@forEach
            // Block-based API is required for sample-rate conversion. The simpler
            // convertToBuffer:fromBuffer: form asserts outputCapacity >= inputFrameLength
            // and does not actually resample, so it crashes when rates differ.
            var consumed = false
            converter.convertToBuffer(dstBuffer, error = null) { _, outStatus ->
                if (consumed) {
                    outStatus?.pointed?.value = AVAudioConverterInputStatus_EndOfStream
                    null
                } else {
                    consumed = true
                    outStatus?.pointed?.value = AVAudioConverterInputStatus_HaveData
                    srcBuffer
                }
            }
            sfxBuffers[name] = dstBuffer
        }
    }

    private fun playSfx(name: String, rate: Float = 1f, volume: Float = effectiveSfxVol) {
        if (audioSuspended) return
        val buffer = sfxBuffers[name] ?: return

        val now = NSDate.timeIntervalSinceReferenceDate
        // Pick the channel whose scheduled buffer finished longest ago (or finishes
        // soonest, if every channel is still busy). Cannot use AVAudioPlayerNode.isPlaying
        // for this — it returns true from the first play() onward and never flips back
        // to false on its own, which is what caused both the "queue grows forever" bug
        // and the "all channels appear busy, drop everything" bug.
        val channel = sfxChannels.minByOrNull { it.endTimeSec } ?: return

        val playbackRate = rate.toDouble().coerceAtLeast(0.001)
        val format = buffer.format
        val sampleRate = format.sampleRate
        val frames = buffer.frameLength.toDouble()
        val durationSec = if (sampleRate > 0.0) frames / sampleRate / playbackRate else 0.0
        channel.endTimeSec = now + durationSec

        // stop() before each schedule guarantees the node holds exactly one buffer
        // (no internal queue), and resets the node so the new play() starts immediately.
        // Under saturation this evicts the oldest in-flight sound — SoundPool parity.
        channel.player.stop()
        channel.varispeed.rate = rate
        channel.player.volume = volume
        channel.player.scheduleBuffer(buffer, completionHandler = null)
        channel.player.play()
    }

    actual fun stopAllSfx() {
        // Stop every channel and zero its end-time so a subsequent playSfx sees all
        // channels as idle and rotates fresh from index 0.
        sfxChannels.forEach {
            it.player.stop()
            it.endTimeSec = 0.0
        }
    }

    actual fun playHighPlayerSound(x: Float) =
        playSfx("high_synth", rate = SoundSpatializer.getYRate(x))

    actual fun playLowPlayerSound(x: Float) =
        playSfx("low_synth", rate = SoundSpatializer.getYRate(x))

    actual fun playLowPlayerSweetSpotSound(x: Float) =
        playSfx("sweet_spot", rate = SoundSpatializer.rates[2], volume = effectiveSfxVol * 0.9f)

    actual fun playHighPlayerSweetSpotSound(y: Float) =
        playSfx("sweet_spot", rate = SoundSpatializer.rates[0], volume = effectiveSfxVol * 0.9f)

    actual fun playGameStart() {}

    actual fun playWallSound(y: Float) =
        playSfx("wall", rate = SoundSpatializer.getYRate(y))

    actual fun playGoalSound(y: Float) =
        playSfx("goal", rate = SoundSpatializer.getYRate(y))

    actual fun playScoreSound(y: Float) =
        playSfx("goal", rate = SoundSpatializer.getYRate(y))

    actual fun playChargeCollision(x: Float) =
        playSfx("sheilded_collision", rate = SoundSpatializer.getXRate(x))

    actual fun playDoubleChargeCollision(x: Float) =
        playSfx("sheilded_collision", rate = SoundSpatializer.getXRate(x))

    actual fun playChargeBlastOff(x: Float) =
        playSfx("sheilds_up", rate = SoundSpatializer.getXRate(x))

    actual fun playTeleportStart(y: Float) =
        playSfx("charge_activated", rate = SoundSpatializer.getYRate(y))

    actual fun playTeleportFinish(x: Float) =
        playSfx("charge_activated", rate = SoundSpatializer.getXRate(x))

    actual fun playWeHaveAWinner() = playSfx("we_have_a_winner")

    actual fun playGameAmbiance() {
        try { engine.startAndReturnError(null) } catch (_: Exception) {}
        if (ambienceMode == AmbienceMode.GAME) return
        ambienceMode = AmbienceMode.GAME
        startAmbience("game_ambient_sound")
    }

    actual fun playMenuAmbiance() {
        try { engine.startAndReturnError(null) } catch (_: Exception) {}
        // Menu sounds muted: stop any menu music that's playing and stay silent.
        // Clearing the players + ambienceMode means a later unmute restarts cleanly.
        if (Storage.menusMuted) {
            if (ambienceMode == AmbienceMode.MENU) {
                ambientPlayer?.stop()
                incomingPlayer?.stop()
                ambientPlayer = null
                incomingPlayer = null
                ambienceMode = null
            }
            return
        }
        if (ambienceMode == AmbienceMode.MENU) {
            ambientPlayer?.let { if (!it.playing) it.play() }
            return
        }
        ambienceMode = AmbienceMode.MENU
        startAmbience("menu_ambient_sound")
    }

    private fun startAmbience(name: String) {
        currentAmbienceName = name
        crossfadeGeneration++
        val gen = crossfadeGeneration

        val outgoing = ambientPlayer
        val url = soundUrl(name) ?: return
        val incoming = AVAudioPlayer(contentsOfURL = url, error = null) ?: return
        incoming.numberOfLoops = 0
        incoming.volume = 0f
        incoming.prepareToPlay()
        incoming.play()
        incomingPlayer = incoming

        outgoing?.setVolume(0f, fadeDuration = CROSSFADE_SECS)
        incoming.setVolume(effectiveBgVol, fadeDuration = CROSSFADE_SECS)

        val completeNs = ((CROSSFADE_SECS + 0.1) * 1_000_000_000.0).toLong()
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, completeNs), dispatch_get_main_queue()) {
            if (crossfadeGeneration != gen) return@dispatch_after
            outgoing?.stop()
            ambientPlayer = incoming
            incomingPlayer = null
            scheduleNextCrossfade()
        }
    }

    // Schedules beginCrossfade() to fire CROSSFADE_SECS before the current ambient track ends.
    // Uses a generation counter so stale dispatch_after callbacks (e.g. after backgrounding) are no-ops.
    private fun scheduleNextCrossfade() {
        val player = ambientPlayer ?: return
        val gen = ++crossfadeGeneration
        val remaining = player.duration - player.currentTime
        val delay = (remaining - CROSSFADE_SECS).coerceAtLeast(0.0)
        val delayNs = (delay * 1_000_000_000.0).toLong()
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, delayNs), dispatch_get_main_queue()) {
            if (!audioSuspended && crossfadeGeneration == gen) beginCrossfade(gen)
        }
    }

    private fun beginCrossfade(gen: Int) {
        if (audioSuspended || crossfadeGeneration != gen) return
        val name = currentAmbienceName ?: return
        val outgoing = ambientPlayer ?: return
        val url = soundUrl(name) ?: return
        val incoming = AVAudioPlayer(contentsOfURL = url, error = null) ?: return
        incoming.numberOfLoops = 0
        incoming.volume = 0f
        incoming.prepareToPlay()
        incoming.play()
        incomingPlayer = incoming

        outgoing.setVolume(0f, fadeDuration = CROSSFADE_SECS)
        incoming.setVolume(effectiveBgVol, fadeDuration = CROSSFADE_SECS)

        val completeNs = ((CROSSFADE_SECS + 0.1) * 1_000_000_000.0).toLong()
        val newGen = ++crossfadeGeneration
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, completeNs), dispatch_get_main_queue()) {
            if (crossfadeGeneration != newGen) return@dispatch_after
            outgoing.stop()
            ambientPlayer = incoming
            incomingPlayer = null
            scheduleNextCrossfade()
        }
    }

    actual fun pauseAll() {
        appBackgrounded = true
        suspendAudio()
    }

    actual fun resumeAll() {
        appBackgrounded = false
        resumeAudioIfAllowed()
    }

    /** Pause the engine and ambient players. Idempotent; safe to call from any suspend reason. */
    private fun suspendAudio() {
        try { engine.pause() } catch (_: Exception) {}
        ambientPlayer?.pause()
        incomingPlayer?.pause()
    }

    /**
     * Reactivate the session, restart the engine, and resume music — but only when no reason to
     * stay silent remains. Called from every "clear a reason" path (resumeAll/foreground,
     * interruption-ended, unmuteForAd). Whichever clears the last reason performs the resume,
     * so no single path can strand the system silent.
     */
    private fun resumeAudioIfAllowed() {
        if (audioSuspended) return
        AVAudioSession.sharedInstance().setActive(true, error = null)
        try { engine.startAndReturnError(null) } catch (_: Exception) {}
        ambientPlayer?.let { player ->
            if (!player.playing) {
                player.play()
                // Reschedule crossfade from current playback position only if not mid-fade
                if (incomingPlayer == null) scheduleNextCrossfade()
            }
        }
        incomingPlayer?.let { if (!it.playing) it.play() }
    }

    actual fun autoPauseSfx() {
        try { engine.pause() } catch (_: Exception) {}
    }

    actual fun autoResumeSfx() {
        try { engine.startAndReturnError(null) } catch (_: Exception) {}
    }

    actual fun applyBackgroundVolume() {
        val vol = effectiveBgVol
        ambientPlayer?.volume = vol
        incomingPlayer?.volume = vol
    }

    actual fun muteForAd() {
        adMuted = true
        suspendAudio()
        // Arm the safety net in case the ad SDK never reports back.
        val gen = ++adWatchdogGen
        val ns = (MAX_AD_MUTE_SECS * 1_000_000_000.0).toLong()
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, ns), dispatch_get_main_queue()) {
            if (adWatchdogGen == gen && adMuted) {
                Settings.adIsPlaying = false
                clearAdMute()
            }
        }
    }

    actual fun unmuteForAd() {
        clearAdMute()
    }

    private fun clearAdMute() {
        adWatchdogGen++   // invalidate any pending watchdog
        adMuted = false
        resumeAudioIfAllowed()
        // Revive music even if the ad's audio session fully stopped our players.
        if (ambienceMode == AmbienceMode.MENU) playMenuAmbiance()
    }

    actual fun abandonAudioFocus() {
        // No-op on iOS — AVAudioSession handles interruptions automatically
    }
}
