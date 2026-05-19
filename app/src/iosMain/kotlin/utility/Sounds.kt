@file:OptIn(ExperimentalForeignApi::class)
package utility

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.*
import platform.Foundation.*
import platform.darwin.*
import utility.Logic

actual object Sounds {
    private class SfxChannel(val player: AVAudioPlayerNode, val pitch: AVAudioUnitTimePitch)

    private val sfxChannels = mutableListOf<SfxChannel>()
    private val sfxBuffers = mutableMapOf<String, AVAudioPCMBuffer>()
    private val engine = AVAudioEngine()

    private var ambientPlayer: AVAudioPlayer? = null
    private var incomingPlayer: AVAudioPlayer? = null
    private var currentAmbienceName: String? = null
    private var crossfadeGeneration = 0
    private var isPaused = false
    private var adMuted = false

    private enum class AmbienceMode { MENU, GAME }
    private var ambienceMode: AmbienceMode? = null

    private const val CROSSFADE_SECS = 3.0

    private val effectiveSfxVol: Float
        get() {
            if (Storage.soundMasterMuted || Storage.soundSfxMuted) return 0f
            return (Storage.soundMasterVolume / 100f) * (Storage.soundSfxVolume / 100f)
        }

    private val effectiveBgVol: Float
        get() {
            if (Storage.soundMasterMuted || Storage.soundBackgroundMuted) return 0f
            return (Storage.soundMasterVolume / 100f) * (Storage.soundBackgroundVolume / 100f)
        }

    actual fun initialize(context: Any?) {
        setupAudioSession()
        setupSfxPool()
        loadSounds()
        registerLifecycleObservers()
    }

    actual fun initializeGame() {}

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
        ) { _ -> pauseAll() }
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = "UIApplicationWillEnterForegroundNotification",
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ ->
            if (!adMuted) resumeAll()
            // Any in-progress touches were cancelled by the system while backgrounded;
            // release pointer locks so neither player stays permanently frozen.
            Logic.releaseAllPointers()
        }
    }

    private fun setupSfxPool() {
        repeat(10) {
            val player = AVAudioPlayerNode()
            val pitch = AVAudioUnitTimePitch()
            engine.attachNode(player)
            engine.attachNode(pitch)
            engine.connect(player, pitch, format = null)
            engine.connect(pitch, engine.mainMixerNode, format = null)
            sfxChannels.add(SfxChannel(player, pitch))
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
            val buffer = AVAudioPCMBuffer(
                pCMFormat = file.processingFormat,
                frameCapacity = file.length.toUInt()
            ) ?: return@forEach
            file.readIntoBuffer(buffer, error = null)
            sfxBuffers[name] = buffer
        }
    }

    private fun playSfx(name: String, rate: Float = 1f, volume: Float = effectiveSfxVol) {
        if (isPaused || adMuted) return
        val buffer = sfxBuffers[name] ?: return
        val channel = sfxChannels.firstOrNull { !it.player.isPlaying() } ?: sfxChannels[0]
        channel.pitch.rate = rate
        channel.player.volume = volume
        channel.player.scheduleBuffer(buffer, completionHandler = null)
        channel.player.play()
    }

    actual fun playHighPlayerSound(x: Float) =
        playSfx("high_synth", rate = SoundSpatializer.getXRate(x))

    actual fun playLowPlayerSound(x: Float) =
        playSfx("low_synth", rate = SoundSpatializer.getXRate(x))

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
        isPaused = false
        try { engine.startAndReturnError(null) } catch (_: Exception) {}
        if (ambienceMode == AmbienceMode.GAME) return
        ambienceMode = AmbienceMode.GAME
        startAmbience("game_ambient_sound")
    }

    actual fun playMenuAmbiance() {
        isPaused = false
        try { engine.startAndReturnError(null) } catch (_: Exception) {}
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
            if (!isPaused && crossfadeGeneration == gen) beginCrossfade(gen)
        }
    }

    private fun beginCrossfade(gen: Int) {
        if (isPaused || crossfadeGeneration != gen) return
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
        isPaused = true
        try { engine.pause() } catch (_: Exception) {}
        ambientPlayer?.pause()
        incomingPlayer?.pause()
    }

    actual fun resumeAll() {
        if (adMuted) return
        isPaused = false
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
        pauseAll()
    }

    actual fun unmuteForAd() {
        adMuted = false
        isPaused = false
        resumeAll()
    }

    actual fun abandonAudioFocus() {
        // No-op on iOS — AVAudioSession handles interruptions automatically
    }
}
