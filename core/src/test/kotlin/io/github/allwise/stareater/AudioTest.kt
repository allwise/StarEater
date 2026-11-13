package io.github.allwise.stareater

import io.github.allwise.stareater.audio.Audio
import io.github.allwise.stareater.audio.Sfx
import io.github.allwise.stareater.audio.Sounds
import io.github.allwise.stareater.audio.Synth
import io.github.allwise.stareater.audio.Tune
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioTest {
    /** Rendered audio is also dropped here, so you can listen without launching the game. */
    private val preview = File("build/synth-preview").apply { mkdirs() }

    private fun rms(pcm: ShortArray, from: Int = 0, to: Int = pcm.size): Double {
        var sum = 0.0
        for (i in from until to) sum += pcm[i].toDouble() * pcm[i]
        return sqrt(sum / (to - from).coerceAtLeast(1)) / 32767.0
    }

    @Test
    fun noteNamesMapToTheRightPitches() {
        assertEquals(440f, Synth.freq("A4"), 0.01f)
        assertEquals(261.63f, Synth.freq("C4"), 0.05f)
        assertEquals(554.37f, Synth.freq("C#5"), 0.05f)
        assertEquals(233.08f, Synth.freq("Bb3"), 0.05f)
        assertEquals(Synth.freq("G#4"), Synth.freq("Ab4"), 0.001f)
    }

    @Test
    fun aRenderedNoteActuallySoundsAtItsPitch() {
        val pcm = Synth.Track(1f).tone(0f, 1f, Synth.Wave.TRI, Synth.freq("A4"), vol = 0.5f).pcm()
        var crossings = 0
        for (i in 1 until pcm.size) if (pcm[i - 1] < 0 && pcm[i] >= 0) crossings++
        assertEquals(440.0, crossings.toDouble(), 3.0)
    }

    @Test
    fun everyEffectRendersToSomethingAudible() {
        for (s in Sfx.values()) {
            val pcm = Sounds.render(s).pcm()
            assertTrue(pcm.size in 400..(Synth.RATE * 7 / 2), "${s.name}: odd length ${pcm.size}")
            assertTrue(rms(pcm) > 0.03, "${s.name} is nearly silent")
            assertTrue(pcm.maxOf { abs(it.toInt()) } in 27000..32767, "${s.name} is not normalised")
            // Effects must not end on a click: the tail should have died away.
            assertTrue(rms(pcm, pcm.size - 40) < 0.2, "${s.name} is cut off mid-sound")
            File(preview, "sfx_${s.name.lowercase()}.wav").writeBytes(Synth.wav(pcm))
        }
    }

    @Test
    fun everyTuneIsAFullLoopWithNoDeadAir() {
        for (t in Tune.values()) {
            val pcm = Sounds.render(t).pcm(0.8f)
            val seconds = pcm.size / Synth.RATE.toFloat()
            assertTrue(seconds in 8f..40f, "${t.name}: ${seconds}s is not a sensible loop length")
            // Eight bars: every one of them should have something going on.
            val bar = pcm.size / 8
            for (b in 0 until 8) assertTrue(rms(pcm, b * bar, (b + 1) * bar) > 0.04, "${t.name}: bar ${b + 1} is silent")
            File(preview, "tune_${t.name.lowercase()}.wav").writeBytes(Synth.wav(pcm))
        }
    }

    /** The tune after a run is the menu's, heard through a wall: the top end should be mostly gone. */
    @Test
    fun theAfterTuneIsMuffled() {
        fun brightness(pcm: ShortArray): Double {
            // Energy of the sample-to-sample difference over energy of the signal: high for bright sounds.
            var diff = 0.0
            var total = 0.0
            for (i in 1 until pcm.size) {
                val d = (pcm[i] - pcm[i - 1]).toDouble()
                diff += d * d
                total += pcm[i].toDouble() * pcm[i]
            }
            return diff / total
        }
        val menu = brightness(Sounds.render(Tune.MENU).pcm(0.8f))
        val after = brightness(Sounds.render(Tune.AFTER).pcm(0.8f))
        assertTrue(after < menu * 0.25, "after-run tune is not dampened: $after vs $menu")
    }

    @Test
    fun lowpassKeepsALoopSeamless() {
        val t = Synth.song(120, listOf(Synth.Voice(Synth.Wave.SQUARE, 0.3f, Synth.bars("A3 - - - - - - - - - - - - - - -"))), emptyList())
        val pcm = t.lowpass(800f).pcm()
        // No jump across the loop point bigger than the jumps inside the loop.
        var biggest = 0
        for (i in 1 until pcm.size) biggest = maxOf(biggest, abs(pcm[i] - pcm[i - 1]))
        assertTrue(abs(pcm[0] - pcm[pcm.size - 1]) <= biggest, "the filtered loop clicks where it wraps")
    }

    @Test
    fun wavContainerIsWellFormed() {
        val pcm = ShortArray(100) { (it * 300).toShort() }
        val wav = Synth.wav(pcm)
        assertEquals(44 + 200, wav.size)
        assertEquals("RIFF", String(wav, 0, 4))
        assertEquals("WAVE", String(wav, 8, 4))
        assertEquals("data", String(wav, 36, 4))
        fun int(at: Int) = (wav[at].toInt() and 0xff) or ((wav[at + 1].toInt() and 0xff) shl 8) or ((wav[at + 2].toInt() and 0xff) shl 16) or ((wav[at + 3].toInt() and 0xff) shl 24)
        assertEquals(Synth.RATE, int(24))
        assertEquals(200, int(40))
        assertEquals(wav.size - 8, int(4))
    }

    @Test
    fun comboPitchClimbsButStaysInRange() {
        var last = 0f
        for (combo in 1..9) {
            val p = Audio.comboPitch(combo)
            assertTrue(p >= last && p in 1f..2f, "combo $combo -> $p")
            last = p
        }
        assertEquals(2f, Audio.comboPitch(9), 0.001f)
    }
}
