package io.github.allwise.stareater.audio

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.AudioDevice
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable
import io.github.allwise.stareater.meta.Profile
import java.util.EnumMap

/**
 * Feeds a looping tune to the sound card a small block at a time, straight from memory.
 *
 * Handing a file to the platform's music player and asking it to loop leaves a hiccup at the seam on
 * a lot of phones: the player stops, seeks back and starts again. Reading the samples round and
 * round ourselves has no seam at all - the last sample of the tune is simply followed by the first -
 * and it lets the volume glide instead of jump.
 */
class LoopPlayer {
    private var data: ShortArray? = null
    private var pos = 0

    /** The volume the last block ended on. */
    var gain = 0f
        private set

    val idle: Boolean get() = data == null

    /** Starts a tune from its first sample, silent until [fill] is told otherwise. */
    fun start(pcm: ShortArray) {
        data = pcm
        pos = 0
        gain = 0f
    }

    fun stop() {
        data = null
        gain = 0f
    }

    /**
     * Writes the next block into [out], wrapping round the end of the tune, with the volume sliding
     * from where it was to [target] across the block so nothing ever clicks.
     */
    fun fill(out: ShortArray, target: Float) {
        val d = data
        if (d == null || d.isEmpty()) {
            out.fill(0)
            return
        }
        val step = (target - gain) / out.size
        for (i in out.indices) {
            gain += step
            out[i] = (d[pos] * gain).toInt().toShort()
            if (++pos == d.size) pos = 0
        }
        gain = target
    }
}

/**
 * Plays what [Sounds] describes. The synth renders everything at start-up: effects to small WAV
 * files that libGDX plays as sounds, tunes to memory, from where a [LoopPlayer] on its own thread
 * streams them. If anything about audio fails, the game simply carries on silent.
 */
class Audio(private val profile: Profile) : Disposable {
    private val sounds = EnumMap<Sfx, Sound>(Sfx::class.java)
    private val lastPlayed = FloatArray(Sfx.values().size) { -1f }
    private val tunes = arrayOfNulls<ShortArray>(Tune.values().size)
    private var wanted: Tune? = null
    private var clock = 0f
    private var available = false

    /** Seconds before the wanted tune may start, and how far through its fade-in it is. */
    private var delay = 0f
    private var fadeIn = 0f
    private var fade = 1f

    // What the streaming thread should be playing right now, and how loud.
    @Volatile private var streamTune: Tune? = null
    @Volatile private var streamGain = 0f
    @Volatile private var running = false
    @Volatile private var paused = false
    private var streamer: Thread? = null

    fun load() {
        try {
            val d = if (Gdx.app.type == Application.ApplicationType.Desktop) Gdx.files.external(".stareater/audio") else Gdx.files.local("audio")
            d.mkdirs()
            for (s in Sfx.values()) {
                val file = d.child("sfx_${s.name.lowercase()}.wav")
                file.writeBytes(Synth.wav(Sounds.render(s).pcm()), false)
                sounds[s] = Gdx.audio.newSound(file)
            }
            available = true
            running = true
            streamer = Thread({ stream() }, "stareater-music").apply { isDaemon = true; start() }
            // Tunes take a moment each, so get ahead of the game on another thread.
            Thread({ for (t in Tune.values()) ensure(t) }, "stareater-synth").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
        } catch (e: Exception) {
            Gdx.app.error("audio", "Sound disabled: ${e.message}")
        }
    }

    /** A tune's samples, rendering them now if the background thread has not got there yet. */
    @Synchronized
    private fun ensure(t: Tune): ShortArray? {
        tunes[t.ordinal]?.let { return it }
        return try {
            Sounds.render(t).pcm(0.8f).also { tunes[t.ordinal] = it }
        } catch (e: Exception) {
            null
        }
    }

    /** The music thread: keeps the sound card fed, a few hundredths of a second at a time. */
    private fun stream() {
        var device: AudioDevice? = null
        try {
            device = Gdx.audio.newAudioDevice(Synth.RATE, true)
            val player = LoopPlayer()
            val block = ShortArray(BLOCK)
            var current: Tune? = null
            var began = System.nanoTime()
            var written = 0L
            while (running) {
                val want = if (paused) null else streamTune
                if (player.idle) {
                    val pcm = want?.let { ensure(it) }
                    if (pcm == null) {
                        Thread.sleep(15)
                        continue
                    }
                    current = want
                    player.start(pcm)
                    began = System.nanoTime()
                    written = 0L
                }
                // A different tune (or none) is wanted: glide this one out first, then let go of it.
                player.fill(block, if (want == current) streamGain else 0f)
                device.writeSamples(block, 0, BLOCK)
                written += BLOCK
                if (want != current && player.gain <= 0f) player.stop()
                // A real device blocks until it has room, which is all the pacing this needs. One that
                // does not block (no sound card at all) must not be allowed to spin, so never run more
                // than half a second ahead of the clock - comfortably more than any real buffer holds.
                val ahead = written * 1000L / Synth.RATE - (System.nanoTime() - began) / 1_000_000L
                if (ahead > MAX_AHEAD_MS) Thread.sleep(ahead - MAX_AHEAD_MS + 50)
            }
        } catch (e: InterruptedException) {
            // Shutting down.
        } catch (e: Throwable) {
            Gdx.app?.error("audio", "Music disabled: ${e.message}")
        } finally {
            try { device?.dispose() } catch (e: Throwable) { /* already gone */ }
        }
    }

    fun update(dt: Float) {
        clock += dt
        if (delay > 0f) {
            delay -= dt
            if (delay <= 0f) apply()
        }
        if (fade < 1f) {
            fade = minOf(1f, fade + dt / fadeIn.coerceAtLeast(0.01f))
            streamGain = musicVolume()
        }
    }

    /** @param pitch playback speed, 0.5 to 2 */
    fun play(s: Sfx, pitch: Float = 1f, volume: Float = 1f) {
        if (!available || !profile.sfxOn || profile.sfxVol <= 0) return
        // The same effect many times in one instant (a blast stunning a whole swarm) is just noise.
        if (clock - lastPlayed[s.ordinal] < 0.04f) return
        lastPlayed[s.ordinal] = clock
        val level = s.gain * volume * profile.sfxVol / Profile.VOLUME_DEFAULT
        sounds[s]?.play(level.coerceAtMost(1f), pitch.coerceIn(0.5f, 2f), 0f)
    }

    /**
     * Switches the looping tune; null for silence. Asking for the tune already playing does nothing.
     * With [after], whatever is playing stops now and the new tune only starts that many seconds
     * later (room for a jingle); with [fadeIn] it creeps in over that long instead of just starting.
     */
    fun tune(t: Tune?, after: Float = 0f, fadeIn: Float = 0f) {
        wanted = t
        delay = after
        this.fadeIn = fadeIn
        apply()
    }

    private fun musicVolume(): Float {
        val gain = streamTune?.gain ?: 1f
        return (MUSIC_VOLUME * gain * fade * profile.musicVol / Profile.VOLUME_DEFAULT).coerceIn(0f, 1f)
    }

    /** Call after changing a volume in the profile, so the tune that is playing follows it. */
    fun volumeChanged() {
        apply()
        streamGain = musicVolume()
    }

    private fun apply() {
        val target = if (available && profile.musicOn && profile.musicVol > 0 && delay <= 0f) wanted else null
        if (target == streamTune) return
        fade = if (target != null && fadeIn > 0f) 0f else 1f
        streamTune = target
        streamGain = musicVolume()
    }

    fun toggleMusic() {
        profile.musicOn = !profile.musicOn
        profile.save()
        apply()
    }

    fun toggleSfx() {
        profile.sfxOn = !profile.sfxOn
        profile.save()
        play(Sfx.UI_OK)
    }

    /** The app has gone to the background: stop feeding the sound card until it is back. */
    fun pause() {
        paused = true
    }

    fun resume() {
        paused = false
    }

    override fun dispose() {
        running = false
        streamer?.let {
            it.interrupt()
            try { it.join(400) } catch (e: InterruptedException) { /* leaving anyway */ }
        }
        for (s in sounds.values) s.dispose()
    }

    companion object {
        /** Music level at the default volume setting. */
        private const val MUSIC_VOLUME = 0.45f

        /** Samples handed to the sound card at a time (about 23 ms), and the most to run ahead of the clock. */
        private const val BLOCK = 512
        private const val MAX_AHEAD_MS = 500L

        /** Steps up a major scale, one per combo level, topping out an octave above the base note. */
        private val COMBO_STEPS = intArrayOf(0, 2, 4, 5, 7, 9, 11, 12, 12)

        fun comboPitch(combo: Int): Float {
            val semis = COMBO_STEPS[(combo - 1).coerceIn(0, COMBO_STEPS.size - 1)]
            return Math.pow(2.0, semis / 12.0).toFloat()
        }
    }
}
