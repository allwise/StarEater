package io.github.allwise.stareater.audio

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Disposable
import io.github.allwise.stareater.meta.Profile
import java.util.EnumMap

/**
 * Plays what [Sounds] describes. The synth renders everything to small WAV files in the app's own
 * storage at start-up (effects straight away, tunes on a background thread), and libGDX plays those.
 * If anything about audio fails, the game simply carries on silent.
 */
class Audio(private val profile: Profile) : Disposable {
    private val sounds = EnumMap<Sfx, Sound>(Sfx::class.java)
    private val lastPlayed = FloatArray(Sfx.values().size) { -1f }
    private val rendered = BooleanArray(Tune.values().size)
    private var dir: FileHandle? = null
    private var music: Music? = null
    private var playing: Tune? = null
    private var wanted: Tune? = null
    private var clock = 0f
    private var available = false

    /** Seconds before the wanted tune may start, and how far through its fade-in the current one is. */
    private var delay = 0f
    private var fadeIn = 0f
    private var fade = 1f

    fun load() {
        try {
            val d = if (Gdx.app.type == Application.ApplicationType.Desktop) Gdx.files.external(".stareater/audio") else Gdx.files.local("audio")
            d.mkdirs()
            dir = d
            for (s in Sfx.values()) {
                val file = d.child("sfx_${s.name.lowercase()}.wav")
                file.writeBytes(Synth.wav(Sounds.render(s).pcm()), false)
                sounds[s] = Gdx.audio.newSound(file)
            }
            available = true
            // Tunes take a moment each, so get ahead of the game on another thread.
            Thread({ for (t in Tune.values()) ensure(t) }, "stareater-synth").apply { isDaemon = true }.start()
        } catch (e: Exception) {
            Gdx.app.error("audio", "Sound disabled: ${e.message}")
        }
    }

    /** Makes sure a tune's file exists, rendering it now if the background thread has not got there yet. */
    @Synchronized
    private fun ensure(t: Tune): FileHandle? {
        val file = dir?.child("tune_${t.name.lowercase()}.wav") ?: return null
        if (!rendered[t.ordinal]) {
            try {
                file.writeBytes(Synth.wav(Sounds.render(t).pcm(0.8f)), false)
                rendered[t.ordinal] = true
            } catch (e: Exception) {
                return null
            }
        }
        return file
    }

    fun update(dt: Float) {
        clock += dt
        if (delay > 0f) {
            delay -= dt
            if (delay <= 0f) apply()
        }
        if (fade < 1f) {
            fade = minOf(1f, fade + dt / fadeIn.coerceAtLeast(0.01f))
            music?.volume = musicVolume()
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
        val gain = playing?.gain ?: 1f
        return (MUSIC_VOLUME * gain * fade * profile.musicVol / Profile.VOLUME_DEFAULT).coerceIn(0f, 1f)
    }

    /** Call after changing a volume in the profile, so the tune that is playing follows it. */
    fun volumeChanged() {
        apply()
        music?.volume = musicVolume()
    }

    private fun apply() {
        val target = if (profile.musicOn && profile.musicVol > 0 && delay <= 0f) wanted else null
        if (target == playing) return
        music?.let { it.stop(); it.dispose() }
        music = null
        playing = null
        if (target == null || !available) return
        try {
            val file = ensure(target) ?: return
            playing = target
            fade = if (fadeIn > 0f) 0f else 1f
            music = Gdx.audio.newMusic(file).apply {
                isLooping = true
                volume = musicVolume()
                play()
            }
        } catch (e: Exception) {
            playing = null
            Gdx.app.error("audio", "Could not play ${target.name}: ${e.message}")
        }
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

    override fun dispose() {
        music?.dispose()
        for (s in sounds.values) s.dispose()
    }

    companion object {
        /** Music level at the default volume setting. */
        private const val MUSIC_VOLUME = 0.45f

        /** Steps up a major scale, one per combo level, topping out an octave above the base note. */
        private val COMBO_STEPS = intArrayOf(0, 2, 4, 5, 7, 9, 11, 12, 12)

        fun comboPitch(combo: Int): Float {
            val semis = COMBO_STEPS[(combo - 1).coerceIn(0, COMBO_STEPS.size - 1)]
            return Math.pow(2.0, semis / 12.0).toFloat()
        }
    }
}
