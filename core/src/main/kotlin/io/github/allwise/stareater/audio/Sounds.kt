package io.github.allwise.stareater.audio

import io.github.allwise.stareater.audio.Synth.Track
import io.github.allwise.stareater.audio.Synth.Voice
import io.github.allwise.stareater.audio.Synth.Wave
import io.github.allwise.stareater.audio.Synth.arp
import io.github.allwise.stareater.audio.Synth.bars
import io.github.allwise.stareater.audio.Synth.bass
import io.github.allwise.stareater.audio.Synth.freq

/** Every sound effect in the game. [gain] is how loud it plays relative to the others. */
enum class Sfx(val gain: Float) {
    EAT(0.55f), EAT_RARE(0.5f), BLAST(0.7f), WRAP(0.4f), DASH(0.5f), SHIELD(0.7f), REVIVE(0.7f), DEATH(0.8f),
    SPAWN(0.3f), ALERT(0.45f), WARN(0.4f), STUN(0.35f), CLEAR(0.7f), BOSS_HIT(0.6f), BOSS_DEAD(0.9f),
    BOSS_INTRO(0.7f), TICK(0.4f), GAME_OVER(0.7f), HIGH_SCORE(0.7f), UI_MOVE(0.25f), UI_OK(0.4f), UI_BUY(0.55f), UI_NO(0.45f),
}

/** The looping tunes. One per movement mode, plus the menu and the boss. */
enum class Tune { MENU, CLASSIC, GLIDE, SCROLL, ORBIT, BOSS }

/**
 * The game's sound, written as code. Effects are little recipes of slides and noise bursts; tunes
 * are four-channel tracker patterns: a lead, an arpeggio, a triangle bass and a noise drum kit.
 */
object Sounds {

    fun render(s: Sfx): Track = when (s) {
        // Base pitch is low on purpose: the game plays it up to an octave higher as the combo climbs.
        Sfx.EAT -> Track(0.2f)
            .tone(0f, 0.045f, Wave.PULSE25, freq("E5"), vol = 0.5f, decay = 10f)
            .tone(0.045f, 0.14f, Wave.PULSE25, freq("B5"), vol = 0.5f, decay = 14f)
        Sfx.EAT_RARE -> Track(0.36f).also { t ->
            for ((i, n) in listOf("C6", "E6", "G6", "C7").withIndex()) {
                t.tone(i * 0.05f, 0.12f, Wave.PULSE12, freq(n), vol = 0.4f, decay = 12f)
                t.tone(i * 0.05f, 0.12f, Wave.TRI, freq(n) * 2f, vol = 0.25f, decay = 16f)
            }
        }
        Sfx.BLAST -> Track(0.7f)
            .tone(0f, 0.65f, Wave.NOISE, 6000f, 400f, vol = 0.6f, decay = 5f)
            .tone(0f, 0.55f, Wave.SAW, 300f, 40f, vol = 0.5f, decay = 4f)
        Sfx.WRAP -> Track(0.2f)
            .tone(0f, 0.08f, Wave.TRI, 250f, 1000f, vol = 0.6f)
            .tone(0.08f, 0.1f, Wave.TRI, 1000f, 500f, vol = 0.6f, decay = 12f)
        Sfx.DASH -> Track(0.18f)
            .tone(0f, 0.14f, Wave.NOISE, 9000f, 1500f, vol = 0.4f, decay = 12f)
            .tone(0f, 0.12f, Wave.SAW, 900f, 300f, vol = 0.3f, decay = 10f)
        Sfx.SHIELD -> Track(0.32f)
            .tone(0f, 0.28f, Wave.SQUARE, 520f, 130f, vol = 0.45f, decay = 7f)
            .tone(0f, 0.2f, Wave.PULSE12, 1040f, 260f, vol = 0.3f, decay = 9f)
            .tone(0f, 0.08f, Wave.NOISE, 5000f, vol = 0.4f, decay = 30f)
        Sfx.REVIVE -> Track(1.05f).also { t ->
            t.tone(0f, 0.5f, Wave.SAW, 110f, 880f, vol = 0.35f)
            for (n in listOf("C5", "E5", "G5", "C6")) t.tone(0.45f, 0.55f, Wave.PULSE25, freq(n), vol = 0.22f, decay = 4f)
        }
        Sfx.DEATH -> Track(1.0f).also { t ->
            for ((i, n) in listOf("A4", "F4", "D4", "A3", "F3", "D3").withIndex()) {
                t.tone(i * 0.08f, 0.09f, Wave.SQUARE, freq(n), vol = 0.45f)
            }
            t.tone(0.45f, 0.5f, Wave.NOISE, 3000f, 200f, vol = 0.5f, decay = 4f)
            t.tone(0.45f, 0.5f, Wave.TRI, 110f, 35f, vol = 0.6f, decay = 4f)
        }
        Sfx.SPAWN -> Track(0.18f).tone(0f, 0.16f, Wave.TRI, 98f, 147f, vol = 0.6f, decay = 6f)
        Sfx.ALERT -> Track(0.3f)
            .tone(0f, 0.28f, Wave.SAW, 220f, 880f, vol = 0.35f)
            .tone(0f, 0.28f, Wave.PULSE25, 222f, 888f, vol = 0.25f)
        Sfx.WARN -> Track(0.2f)
            .tone(0f, 0.06f, Wave.PULSE25, 1320f, vol = 0.4f)
            .tone(0.11f, 0.06f, Wave.PULSE25, 1320f, vol = 0.4f)
        Sfx.STUN -> Track(0.16f).tone(0f, 0.14f, Wave.PULSE12, 1400f, 350f, vol = 0.45f, decay = 10f)
        Sfx.CLEAR -> Track(1.0f).also { t ->
            for ((i, n) in listOf("C5", "E5", "G5", "C6").withIndex()) t.tone(i * 0.085f, 0.09f, Wave.PULSE25, freq(n), vol = 0.4f)
            for (n in listOf("G5", "C6", "E6")) t.tone(0.34f, 0.6f, Wave.PULSE25, freq(n), vol = 0.25f, decay = 4f, vibrato = 0.15f)
            t.tone(0.34f, 0.6f, Wave.TRI, freq("C3"), vol = 0.5f, decay = 3f)
        }
        Sfx.BOSS_HIT -> Track(0.16f)
            .tone(0f, 0.1f, Wave.NOISE, 8000f, 2000f, vol = 0.45f, decay = 25f)
            .tone(0f, 0.14f, Wave.SQUARE, 196f, 98f, vol = 0.45f, decay = 12f)
        Sfx.BOSS_DEAD -> Track(1.5f).also { t ->
            t.tone(0f, 1.3f, Wave.NOISE, 7000f, 150f, vol = 0.6f, decay = 2.5f)
            t.tone(0f, 1.2f, Wave.SAW, 200f, 30f, vol = 0.5f, decay = 2f)
            for ((i, n) in listOf("C5", "E5", "G5", "C6", "E6", "G6", "C7").withIndex()) {
                t.tone(0.5f + i * 0.07f, 0.16f, Wave.PULSE25, freq(n), vol = 0.3f, decay = 8f)
            }
        }
        Sfx.BOSS_INTRO -> Track(1.7f).also { t ->
            for ((i, n) in listOf("E2", "D#2", "E2").withIndex()) {
                val at = i * 0.45f
                val dur = if (i == 2) 0.75f else 0.42f
                t.tone(at, dur, Wave.SAW, freq(n), vol = 0.45f, vibrato = 0.2f, vibratoRate = 5f)
                t.tone(at, dur, Wave.TRI, freq(n) / 2f, vol = 0.5f)
            }
        }
        Sfx.TICK -> Track(0.05f).tone(0f, 0.03f, Wave.PULSE12, 1760f, vol = 0.4f)
        Sfx.GAME_OVER -> Track(1.6f).also { t ->
            for ((i, n) in listOf("E5", "C5", "A4").withIndex()) {
                t.tone(i * 0.24f, 0.22f, Wave.PULSE25, freq(n), vol = 0.35f, decay = 3f)
                t.tone(i * 0.24f, 0.22f, Wave.TRI, freq(n) / 2f, vol = 0.4f)
            }
            t.tone(0.72f, 0.8f, Wave.PULSE25, freq("E4"), vol = 0.35f, decay = 2.5f, vibrato = 0.25f)
            t.tone(0.72f, 0.8f, Wave.TRI, freq("A2"), vol = 0.5f, decay = 2f)
        }
        Sfx.HIGH_SCORE -> Track(1.5f).also { t ->
            val notes = listOf("G5" to 0f, "C6" to 0.1f, "E6" to 0.2f, "G6" to 0.3f, "E6" to 0.5f, "G6" to 0.6f)
            for ((n, at) in notes) t.tone(at, 0.11f, Wave.PULSE25, freq(n), vol = 0.4f)
            for (n in listOf("C6", "E6", "G6", "C7")) t.tone(0.72f, 0.7f, Wave.PULSE25, freq(n), vol = 0.2f, decay = 3f, vibrato = 0.2f)
            t.tone(0.72f, 0.7f, Wave.TRI, freq("C3"), vol = 0.5f, decay = 2.5f)
        }
        Sfx.UI_MOVE -> Track(0.04f).tone(0f, 0.025f, Wave.PULSE25, 880f, vol = 0.4f)
        Sfx.UI_OK -> Track(0.13f)
            .tone(0f, 0.04f, Wave.PULSE25, 660f, vol = 0.4f)
            .tone(0.04f, 0.08f, Wave.PULSE25, 990f, vol = 0.4f, decay = 10f)
        Sfx.UI_BUY -> Track(0.4f)
            .tone(0f, 0.06f, Wave.PULSE25, freq("B5"), vol = 0.4f)
            .tone(0.06f, 0.32f, Wave.PULSE25, freq("E6"), vol = 0.4f, decay = 8f)
        Sfx.UI_NO -> Track(0.16f)
            .tone(0f, 0.13f, Wave.SQUARE, 147f, vol = 0.4f)
            .tone(0f, 0.13f, Wave.SQUARE, 139f, vol = 0.3f)
    }

    // --- Tunes -------------------------------------------------------------------------------

    private val UP_DOWN = intArrayOf(0, 1, 2, 3, 2, 1)
    private val UP = intArrayOf(0, 1, 2, 3)
    private val SLOW = intArrayOf(0, -1, 1, -1, 2, -1, 3, -1, 2, -1, 1, -1, 2, -1, 3, -1)
    private val STAB = intArrayOf(0, 2, 1, 3, 0, 2, 1, 3)

    private val HALF = intArrayOf(0, -2, -2, -2, -2, -2, -2, -1, 0, -2, -2, -2, -2, -2, -2, -1)
    private val LONG = intArrayOf(0, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -2, -1)
    private val BOUNCE = intArrayOf(0, -1, 1, -1)
    private val DRIVE = intArrayOf(0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 0, -1, 1, -1)
    private val GALLOP = intArrayOf(0, -1, 0, 0)
    private val PULSE = intArrayOf(0, -2, -1, -1, 0, -2, -1, -1, 0, -2, -1, 0, -2, -1, 1, -1)

    private fun arps(octave: Int, shape: IntArray, vararg chords: String) = chords.flatMap { arp(it, octave, shape) }
    private fun basses(octave: Int, shape: IntArray, vararg chords: String) = chords.flatMap { bass(it, octave, shape) }
    private fun drums(bar: String) = bar.trim().split(Regex("\\s+"))

    fun render(t: Tune): Track = when (t) {
        // Dreamy and unhurried: vi-IV-I-V with a slow, singing lead.
        Tune.MENU -> {
            val chords = arrayOf("Am", "F", "C", "G", "Am", "F", "C", "G")
            val lead = bars(
                "E5 - - - - - C5 - A4 - - - - - - -",
                "F5 - - - - - E5 - C5 - - - A4 - - -",
                "G5 - - - - - E5 - C5 - - - - - D5 -",
                "D5 - - - B4 - - - G4 - - - - - - -",
                "A5 - - - - - E5 - C5 - E5 - A5 - - -",
                "A5 - - - G5 - F5 - - - C5 - F5 - - -",
                "E5 - - - G5 - - - C6 - - - B5 - G5 -",
                "D5 - - - G5 - - - B5 - A5 - G5 - - -",
            )
            Synth.song(
                96,
                listOf(
                    Voice(Wave.PULSE25, 0.2f, lead, decay = 1.2f, vibrato = 0.18f, wet = true),
                    Voice(Wave.PULSE12, 0.08f, arps(4, SLOW, *chords), decay = 6f, wet = true),
                    Voice(Wave.TRI, 0.3f, basses(2, HALF, *chords)),
                ),
                drums("K . . . H . . . . . H . H . . ."),
            )
        }
        // The original, so: bouncy, staccato, a little bit cheeky. A minor.
        Tune.CLASSIC -> {
            val chords = arrayOf("Am", "Am", "F", "G", "Am", "Am", "F", "E")
            val lead = bars(
                "A4 . C5 . E5 . C5 . A4 . C5 . E5 - - .",
                "A5 . G5 . E5 . C5 . D5 . E5 . C5 - - .",
                "F4 . A4 . C5 . A4 . F4 . A4 . C5 - - .",
                "G4 . B4 . D5 . B4 . G5 . F5 . D5 - B4 .",
                "A4 . C5 . E5 . C5 . A4 . C5 . E5 - - .",
                "A5 . G5 . E5 . G5 . A5 . C6 . A5 - - .",
                "F5 . E5 . C5 . A4 . C5 . F5 . A5 - - .",
                "E5 . G#5 . B5 . G#5 . E5 . D5 . B4 - G#4 .",
            )
            Synth.song(
                140,
                listOf(
                    Voice(Wave.SQUARE, 0.2f, lead, decay = 4f, wet = true),
                    Voice(Wave.PULSE12, 0.07f, arps(4, UP_DOWN, *chords), decay = 10f, wet = true),
                    Voice(Wave.TRI, 0.32f, basses(2, BOUNCE, *chords)),
                ),
                drums("K . H . S . H . K K H . S . H H"),
            )
        }
        // Long notes over rolling arpeggios: drifting. D minor.
        Tune.GLIDE -> {
            val chords = arrayOf("Dm", "Bb", "F", "C", "Dm", "Bb", "F", "C")
            val lead = bars(
                "D5 - - - F5 - - - A5 - - - - - G5 -",
                "F5 - - - D5 - - - Bb4 - - - - - C5 -",
                "A5 - - - - - G5 - F5 - - - C5 - - -",
                "E5 - - - G5 - - - C6 - - - Bb5 - G5 -",
                "A5 - - - D6 - - - A5 - - - F5 - A5 -",
                "Bb5 - - - A5 - G5 - F5 - - - D5 - - -",
                "C5 - F5 - A5 - C6 - A5 - - - F5 - - -",
                "G5 - - - E5 - - - C5 - D5 - E5 - - -",
            )
            Synth.song(
                118,
                listOf(
                    Voice(Wave.PULSE25, 0.19f, lead, decay = 1.5f, vibrato = 0.2f, wet = true),
                    Voice(Wave.PULSE12, 0.08f, arps(4, UP, *chords), decay = 9f, wet = true),
                    Voice(Wave.TRI, 0.3f, basses(2, PULSE, *chords)),
                ),
                drums("K . . H . . H . K . . H S . H ."),
            )
        }
        // Foot down. E minor, eighth-note bass that never lets up.
        Tune.SCROLL -> {
            val chords = arrayOf("Em", "Em", "C", "D", "Em", "G", "C", "B")
            val lead = bars(
                "E5 . E5 G5 . B5 . G5 E5 . E5 G5 . B5 - .",
                "E6 - D6 . B5 . G5 . A5 - B5 . G5 - E5 .",
                "C5 . C5 E5 . G5 . E5 C5 . C5 E5 . G5 - .",
                "D5 . F#5 . A5 . D6 . C6 - A5 . F#5 - D5 .",
                "E5 . E5 G5 . B5 . G5 E5 . E5 G5 . B5 - .",
                "D6 - B5 . G5 . B5 . D6 - G6 . D6 - B5 .",
                "E6 - C6 . G5 . C6 . E6 - G6 . E6 - C6 .",
                "B5 . D#6 . F#6 . D#6 . B5 . A5 . F#5 - D#5 .",
            )
            Synth.song(
                152,
                listOf(
                    Voice(Wave.PULSE25, 0.19f, lead, decay = 3.5f, wet = true),
                    Voice(Wave.PULSE12, 0.07f, arps(4, STAB, *chords), decay = 12f, wet = true),
                    Voice(Wave.TRI, 0.32f, basses(2, DRIVE, *chords)),
                ),
                drums("K . H . S . H K . K H . S . H ."),
            )
        }
        // Weightless: slow, sparse, lots of space between the notes.
        Tune.ORBIT -> {
            val chords = arrayOf("Fmaj7", "G", "Em", "Am", "Fmaj7", "G", "Em", "Am")
            val lead = bars(
                "C5 - - - - - - - E5 - - - - - A5 -",
                "G5 - - - - - - - B5 - - - D5 - - -",
                "E5 - - - - - G5 - B5 - - - - - - -",
                "A5 - - - - - E5 - C5 - - - - - - -",
                "A5 - - - C6 - - - A5 - - - F5 - E5 -",
                "D5 - - - G5 - - - B5 - - - D6 - - -",
                "B5 - - - - - G5 - E5 - - - G5 - - -",
                "A5 - - - - - - - - - - - . . . .",
            )
            Synth.song(
                90,
                listOf(
                    Voice(Wave.TRI, 0.3f, lead, decay = 0.6f, vibrato = 0.25f, wet = true),
                    Voice(Wave.PULSE12, 0.07f, arps(4, SLOW, *chords), decay = 4f, wet = true),
                    Voice(Wave.TRI, 0.26f, basses(2, LONG, *chords)),
                ),
                drums(". . . . H . . . . . . . H . . ."),
            )
        }
        // Something large is coming. Galloping bass, half-step dread.
        Tune.BOSS -> {
            val chords = arrayOf("Em", "Em", "F", "F", "Em", "Em", "G", "F")
            val lead = bars(
                "E5 . . E5 . . G5 . F5 . E5 . B4 . . .",
                "E5 . . E5 . . G5 . A5 . G5 . F5 . E5 .",
                "F5 . . F5 . . A5 . G5 . F5 . C5 . . .",
                "F5 . . A5 . . C6 . B5 . A5 . G5 . F5 .",
                "E5 . . E5 . . G5 . F5 . E5 . B4 . . .",
                "E5 . . E5 . . G5 . A5 . G5 . F5 . E5 .",
                "G5 . B5 . D6 . B5 . G5 . B5 . D6 . F6 .",
                "F6 . E6 . C6 . A5 . F5 . E5 . D#5 . . .",
            )
            Synth.song(
                165,
                listOf(
                    Voice(Wave.SAW, 0.17f, lead, decay = 5f, wet = true),
                    Voice(Wave.PULSE12, 0.06f, arps(3, UP, *chords), decay = 14f, wet = true),
                    Voice(Wave.TRI, 0.34f, basses(2, GALLOP, *chords)),
                ),
                drums("K . K . S . K K . K K . S . S S"),
            )
        }
    }
}
