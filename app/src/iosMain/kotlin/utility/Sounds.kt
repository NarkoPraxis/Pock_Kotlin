@file:OptIn(ExperimentalForeignApi::class)
package utility

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.*
import platform.Foundation.*
import platform.Darwin.*

actual object Sounds {
    private class SfxChannel(val player: AVAudioPlayerNode, val pitch: AVAudioUnitTimePitch)

    private val sfxChannels = mutableListOf<SfxChannel>()
    private val sfxBuffers = mutableMapOf<String, AVAudioPCMBuffer>()
    private val engine = AVAudioEngine()
    private var ambientPlayer: AVAudioPlayer? = null
    private var incomingPlayer: AVAudioPlayer? = null

    private enum class AmbienceMode { MENU, GAME }
    private var ambienceMode: AmbienceMode? = null

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
    }

    actual fun initializeGame() {}

    private fun setupAudioSession() {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryAmbient, error = null)
        session.setActive(true, error = null)
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

    private fun loadSounds() {
        listOf(
            "high_synth", "low_synth", "wall", "goal",
            "sheilds_up", "sheilded_collision", "charge_activated", "sweet_spot",
            "we_have_a_winner", "game_start"
        ).forEach { name ->
            val url = NSBundle.mainBundle.URLForResource(name, withExtension = "mp3")
                ?: NSBundle.mainBundle.URLForResource(name, withExtension = "wav")
                ?: NSBundle.mainBundle.URLForResource(name, withExtension = "m4a")
                ?: return@forEach
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
        val buffer = sfxBuffers[name] ?: return
        val channel = sfxChannels.firstOrNull { !it.player.isPlaying } ?: sfxChannels[0]
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
        if (ambienceMode == AmbienceMode.GAME) return
        ambienceMode = AmbienceMode.GAME
        startAmbience("game_ambient_sound")
    }

    actual fun playMenuAmbiance() {
        if (ambienceMode == AmbienceMode.MENU) {
            ambientPlayer?.let { if (!it.playing) it.play() }
            return
        }
        ambienceMode = AmbienceMode.MENU
        startAmbience("menu_ambient_sound")
    }

    private fun startAmbience(name: String) {
        val url = NSBundle.mainBundle.URLForResource(name, withExtension = "mp3")
            ?: NSBundle.mainBundle.URLForResource(name, withExtension = "m4a")
            ?: return
        val outgoing = ambientPlayer
        val incoming = AVAudioPlayer(contentsOfURL = url, error = null) ?: return
        incoming.numberOfLoops = -1
        incoming.volume = 0f
        incoming.play()
        incomingPlayer = incoming

        outgoing?.setVolume(0f, fadeDuration = 3.0)
        incoming.setVolume(effectiveBgVol, fadeDuration = 3.0)

        dispatch_after(
            dispatch_time(DISPATCH_TIME_NOW, 3_100_000_000L),
            dispatch_get_main_queue()
        ) {
            outgoing?.stop()
            ambientPlayer = incoming
            incomingPlayer = null
        }
    }

    actual fun pauseAll() {
        try { engine.pause() } catch (_: Exception) {}
        ambientPlayer?.pause()
        incomingPlayer?.pause()
    }

    actual fun resumeAll() {
        try { engine.startAndReturnError(null) } catch (_: Exception) {}
        ambientPlayer?.let { if (!it.playing) it.play() }
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

    actual fun abandonAudioFocus() {
        // No-op on iOS — AVAudioSession handles interruptions automatically
    }
}
