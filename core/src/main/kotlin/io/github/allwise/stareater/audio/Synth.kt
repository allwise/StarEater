package io.github.allwise.stareater.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * A tiny chip-style synthesiser: pulse, triangle, saw and noise voices with a simple envelope.
 * Everything the game plays is rendered by this at start-up, so there are no audio assets at all.
 * Pure Kotlin - no platform audio in here - which keeps it unit-testable.
 */
object Synth {
    /** Deliberately lo-fi. */
    const val RATE = 22050

    enum class Wave { SQUARE, PULSE25, PULSE12, TRI, SAW, NOISE }

    /** A mono mixing buffer. A looping track wraps note tails round to the start, for seamless loops. */
    class Track(samples: Int, private val loop: Boolean = false) {
        constructor(seconds: Float) : this((seconds * RATE).toInt())

        val data = FloatArray(samples)
        private val rnd = Random(1234)

        /**
         * Adds one note.
         * @param f1 end frequency, for slides
         * @param decay exponential fade rate per second; 0 sustains
         * @param vibrato depth in semitones
         */
        fun tone(
            at: Float, dur: Float, wave: Wave, f0: Float, f1: Float = f0, vol: Float = 0.5f,
            attack: Float = 0.003f, decay: Float = 0f, release: Float = 0.02f, vibrato: Float = 0f, vibratoRate: Float = 6f,
        ): Track {
            val n = (dur * RATE).toInt()
            val start = (at * RATE).toInt()
            val slide = if (f0 > 0f) f1 / f0 else 1f
            var phase = 0f
            var held = 0f
            var holdPhase = 1f
            for (i in 0 until n) {
                var idx = start + i
                if (idx >= data.size) { if (loop) idx %= data.size else break }
                val t = i / RATE.toFloat()
                var f = if (slide == 1f) f0 else f0 * slide.pow(i / n.toFloat())
                if (vibrato != 0f) f *= 2f.pow(vibrato / 12f * sin(2.0 * PI * vibratoRate * t).toFloat())
                val step = f / RATE
                val s = when (wave) {
                    Wave.NOISE -> {
                        // Sample-and-hold noise: the "frequency" sets how bright it is.
                        holdPhase += step
                        if (holdPhase >= 1f) { holdPhase -= floor(holdPhase); held = rnd.nextFloat() * 2f - 1f }
                        held
                    }
                    else -> {
                        phase += step
                        phase -= floor(phase)
                        when (wave) {
                            Wave.SQUARE -> if (phase < 0.5f) 1f else -1f
                            Wave.PULSE25 -> if (phase < 0.25f) 1f else -1f
                            Wave.PULSE12 -> if (phase < 0.125f) 1f else -1f
                            Wave.TRI -> 4f * abs(phase - 0.5f) - 1f
                            else -> 2f * phase - 1f
                        }
                    }
                }
                var env = min(1f, t / attack) * min(1f, (dur - t) / release)
                if (decay > 0f) env *= exp(-decay * t)
                data[idx] += s * env * vol
            }
            return this
        }

        /** Circular echo: taps wrap round the buffer, so a looping tune stays seamless. */
        fun echo(delay: Float, gain: Float): Track {
            val d = (delay * RATE).toInt().coerceAtLeast(1)
            val dry = data.copyOf()
            val n = data.size
            for (i in 0 until n) {
                var a = i - d
                var b = i - 2 * d
                if (loop) { a = Math.floorMod(a, n); b = Math.floorMod(b, n) }
                if (a >= 0) data[i] += dry[a] * gain
                if (b >= 0) data[i] += dry[b] * gain * gain
            }
            return this
        }

        fun mix(other: Track, gain: Float = 1f): Track {
            for (i in 0 until min(data.size, other.data.size)) data[i] += other.data[i] * gain
            return this
        }

        /** 16-bit samples, scaled so the loudest one sits at [peak]. */
        fun pcm(peak: Float = 0.85f): ShortArray {
            var max = 0.0001f
            for (v in data) max = maxOf(max, abs(v))
            val k = peak / max
            return ShortArray(data.size) { (data[it] * k * 32767f).roundToInt().coerceIn(-32768, 32767).toShort() }
        }
    }

    /** Wraps 16-bit mono PCM in a WAV container. */
    fun wav(pcm: ShortArray): ByteArray {
        val bytes = pcm.size * 2
        val out = ByteArray(44 + bytes)
        fun str(at: Int, s: String) { for (i in s.indices) out[at + i] = s[i].code.toByte() }
        fun int(at: Int, v: Int) { for (i in 0 until 4) out[at + i] = (v ushr (8 * i)).toByte() }
        fun short(at: Int, v: Int) { out[at] = v.toByte(); out[at + 1] = (v ushr 8).toByte() }
        str(0, "RIFF"); int(4, 36 + bytes); str(8, "WAVE")
        str(12, "fmt "); int(16, 16); short(20, 1); short(22, 1)
        int(24, RATE); int(28, RATE * 2); short(32, 2); short(34, 16)
        str(36, "data"); int(40, bytes)
        for (i in pcm.indices) short(44 + i * 2, pcm[i].toInt())
        return out
    }

    /** `A4` -> 440. Accepts sharps (`C#5`) and flats (`Bb3`). */
    fun freq(note: String): Float {
        val semis = when (note[0]) {
            'C' -> 0; 'D' -> 2; 'E' -> 4; 'F' -> 5; 'G' -> 7; 'A' -> 9; 'B' -> 11
            else -> throw IllegalArgumentException("Not a note: $note")
        }
        var i = 1
        var accidental = 0
        if (note[i] == '#') { accidental = 1; i++ } else if (note[i] == 'b') { accidental = -1; i++ }
        val octave = note.substring(i).toInt()
        val midi = 12 * (octave + 1) + semis + accidental
        return 440f * 2f.pow((midi - 69) / 12f)
    }

    // ----------------------------------------------------------------------------------------
    // A four-channel tracker
    // ----------------------------------------------------------------------------------------

    /**
     * One melodic channel. [steps] holds one token per sixteenth note: a note name starts a note,
     * `-` holds the previous one, `.` is silence.
     */
    class Voice(
        val wave: Wave, val vol: Float, val steps: List<String>,
        val decay: Float = 0f, val vibrato: Float = 0f, val wet: Boolean = false,
    )

    /**
     * Renders a looping tune.
     * @param drums one token per sixteenth: `K` kick, `S` snare, `H` hat, `.` nothing
     */
    fun song(bpm: Int, voices: List<Voice>, drums: List<String>): Track {
        val total = voices.maxOf { it.steps.size }
        for (v in voices) require(v.steps.size == total) { "Channel is ${v.steps.size} steps, expected $total" }
        require(drums.isEmpty() || total % drums.size == 0) { "Drum pattern does not fit the tune" }
        // A whole number of samples per step keeps the loop point exact.
        val stepSamples = (RATE * 60.0 / bpm / 4.0).roundToInt()
        val step = stepSamples / RATE.toFloat()
        val dry = Track(total * stepSamples, loop = true)
        val wet = Track(total * stepSamples, loop = true)
        for (v in voices) {
            val into = if (v.wet) wet else dry
            var i = 0
            while (i < total) {
                val token = v.steps[i]
                if (token == "." || token == "-") { i++; continue }
                var len = 1
                while (i + len < total && v.steps[i + len] == "-") len++
                into.tone(i * step, len * step * 0.92f, v.wave, freq(token), vol = v.vol, decay = v.decay, release = 0.03f, vibrato = v.vibrato)
                i += len
            }
        }
        // A dotted-eighth echo on the melodic channels gives the whole thing some room.
        wet.echo(step * 3f, 0.32f)
        dry.mix(wet)
        if (drums.isNotEmpty()) {
            for (i in 0 until total) {
                val at = i * step
                when (drums[i % drums.size]) {
                    "K" -> {
                        dry.tone(at, 0.13f, Wave.TRI, 170f, 42f, vol = 0.55f, decay = 14f)
                        dry.tone(at, 0.03f, Wave.NOISE, 3000f, vol = 0.12f, decay = 60f)
                    }
                    "S" -> {
                        dry.tone(at, 0.12f, Wave.NOISE, 7000f, 3500f, vol = 0.26f, decay = 22f)
                        dry.tone(at, 0.06f, Wave.TRI, 240f, 150f, vol = 0.2f, decay = 30f)
                    }
                    "H" -> dry.tone(at, 0.035f, Wave.NOISE, 11000f, vol = 0.1f, decay = 70f)
                }
            }
        }
        return dry
    }

    /** Splits bars written as space-separated tokens, checking each bar really is sixteen steps. */
    fun bars(vararg bar: String): List<String> = bar.flatMap { b ->
        val tokens = b.trim().split(Regex("\\s+"))
        require(tokens.size == 16) { "A bar needs 16 steps, got ${tokens.size}: $b" }
        tokens
    }

    private val CHORDS = mapOf(
        "Am" to listOf("A", "C", "E"), "F" to listOf("F", "A", "C"), "C" to listOf("C", "E", "G"),
        "G" to listOf("G", "B", "D"), "E" to listOf("E", "G#", "B"), "Dm" to listOf("D", "F", "A"),
        "Bb" to listOf("Bb", "D", "F"), "Em" to listOf("E", "G", "B"), "D" to listOf("D", "F#", "A"),
        "B" to listOf("B", "D#", "F#"), "Fmaj7" to listOf("F", "A", "C", "E"),
    )

    private val LETTERS = "CDEFGAB"

    /** The chord's tones climbing from [octave], each one above the last. */
    private fun ladder(chord: String, octave: Int, count: Int): List<String> {
        val tones = CHORDS[chord] ?: throw IllegalArgumentException("Unknown chord $chord")
        val out = ArrayList<String>()
        var oct = octave
        var last = -1
        var i = 0
        while (out.size < count) {
            val tone = tones[i % tones.size]
            val pos = LETTERS.indexOf(tone[0])
            if (pos <= last) oct++
            last = pos
            out += tone + oct
            i++
        }
        return out
    }

    /** One bar of arpeggio. [shape] indexes into the chord ladder, one entry per step; -1 rests. */
    fun arp(chord: String, octave: Int, shape: IntArray): List<String> {
        val notes = ladder(chord, octave, (shape.maxOrNull() ?: 0) + 1)
        return List(16) { val s = shape[it % shape.size]; if (s < 0) "." else notes[s] }
    }

    /** One bar of bass on the chord's root. [shape]: 0 root, 1 root an octave up, -1 rest, -2 hold. */
    fun bass(chord: String, octave: Int, shape: IntArray): List<String> {
        val root = CHORDS[chord]?.first() ?: throw IllegalArgumentException("Unknown chord $chord")
        return List(16) {
            when (shape[it % shape.size]) {
                0 -> root + octave
                1 -> root + (octave + 1)
                -2 -> "-"
                else -> "."
            }
        }
    }
}
