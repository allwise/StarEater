package io.github.allwise.stareater.meta

import io.github.allwise.stareater.sim.RunState
import kotlin.random.Random

/**
 * A run's seed, as six characters a human can read out over voice chat. Two players who start a
 * run from the same key get the same sequence of levels, rules, rosters and layouts, which is what
 * makes scores on a key comparable.
 */
object SeedKey {
    /** No I, O, 0 or 1: nothing that can be misread. 32 symbols = 5 bits each. */
    const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val LENGTH = 6

    fun random(rng: Random = Random.Default): String = encode(rng.nextInt() and MASK)

    /** The same key for everyone on a given day (days since 1970-01-01, UTC). */
    fun daily(epochDay: Long): String = encode((mix(epochDay * 0x9E3779B97F4A7C15uL.toLong() + 0x5EED) and MASK.toLong()).toInt())

    fun dailyNow(nowMs: Long = System.currentTimeMillis()): String = daily(Math.floorDiv(nowMs, 86_400_000L))

    fun isValid(key: String): Boolean = key.length == LENGTH && key.all { it in ALPHABET }

    /** Upper-cases and maps the look-alikes people will inevitably type. Returns null if still not a key. */
    fun normalise(raw: String): String? {
        val cleaned = raw.uppercase().filter { it.isLetterOrDigit() }.map {
            when (it) {
                'I', '1' -> 'L'
                'O', '0' -> 'Q'
                else -> it
            }
        }.joinToString("")
        return cleaned.takeIf { isValid(it) }
    }

    /** `ABCDEF` -> `ABC-DEF`. */
    fun pretty(key: String): String = if (key.length == LENGTH) key.substring(0, 3) + "-" + key.substring(3) else key

    fun newRun(key: String): RunState = RunState(seedOf(key), key)

    private fun encode(value: Int): String {
        val sb = StringBuilder(LENGTH)
        for (i in LENGTH - 1 downTo 0) sb.append(ALPHABET[(value ushr (i * 5)) and 31])
        return sb.toString()
    }

    /** The 64-bit seed a key stands for. */
    fun seedOf(key: String): Long {
        var v = 0L
        for (c in key) v = (v shl 5) or ALPHABET.indexOf(c).coerceAtLeast(0).toLong()
        return mix(v + 0x51A2EA7E2L)
    }

    /** splitmix64 finaliser: spreads 30 bits of key over the whole seed. */
    private fun mix(x: Long): Long {
        var z = x
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    private const val MASK = (1 shl (LENGTH * 5)) - 1
}
