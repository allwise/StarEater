package io.github.allwise.stareater.sim

/**
 * The collectables of arcade mode. There is no skill tree in an arcade run: everything you have, you
 * picked up off the field.
 *
 *  - Permanent ones last the whole run and stack, up to [max].
 *  - Timed ones run for [seconds] and are topped back up by picking another.
 *  - A charge is held until you spend it.
 */
enum class PowerUp(val title: String, val blurb: String, val seconds: Float, val max: Int, val weight: Float) {
    SPEED("THRUSTERS", "Permanent. Move 8% faster, up to four times over.", 0f, 4, 1f),
    FRENZY("FRENZY", "Permanent. Combos last longer.", 0f, 3, 0.8f),
    LUCK("LUCKY STARS", "Permanent. Rare stars turn up more often.", 0f, 3, 0.8f),
    SHIELD("SHIELD", "Soaks one hit. Fades after 20 seconds.", 20f, 1, 1.2f),
    MAGNET("MAGNET", "Stars drift towards you for 15 seconds.", 15f, 1, 1.1f),
    DASH("BLINK DASH", "A dash to use for 25 seconds.", 25f, 1, 0.9f),
    ECHO("WRAP ECHO", "For 20 seconds, wrapping leaves a decoy behind.", 20f, 1, 0.6f),
    NOVA("NOVA", "One stun blast, kept until you fire it.", 0f, 1, 0.7f);

    val timed: Boolean get() = seconds > 0f
    val permanent: Boolean get() = !timed && this != NOVA
}

/** A power-up lying on the field, waiting to be flown into. */
class Capsule(val kind: PowerUp) : Body() {
    var age = 0f

    /** Seconds until it gives up and vanishes, or 0 for "stays until it scrolls away". */
    var ttl = 0f
}

/**
 * What an arcade run has collected so far. It owns the run's [loadout] and rewrites it whenever
 * something is picked up or runs out, so the rest of the sim never needs to know which mode it is in.
 */
class Arcade {
    val loadout = Loadout()
    private val stacks = IntArray(PowerUp.values().size)
    private val timers = FloatArray(PowerUp.values().size)

    /** A nova charge in the tank. */
    var novaHeld = false
        private set

    fun stacks(p: PowerUp): Int = stacks[p.ordinal]

    /** Seconds left on a timed power-up; 0 when it is not running. */
    fun left(p: PowerUp): Float = timers[p.ordinal]

    fun active(p: PowerUp): Boolean = when {
        p.timed -> timers[p.ordinal] > 0f
        p == PowerUp.NOVA -> novaHeld
        else -> stacks[p.ordinal] > 0
    }

    /** False once a power-up has nothing more to give, so the field stops offering it. */
    fun wants(p: PowerUp): Boolean = when {
        p.timed -> true
        p == PowerUp.NOVA -> !novaHeld
        else -> stacks[p.ordinal] < p.max
    }

    fun grant(p: PowerUp) {
        when {
            p.timed -> timers[p.ordinal] = p.seconds
            p == PowerUp.NOVA -> novaHeld = true
            else -> stacks[p.ordinal] = minOf(p.max, stacks[p.ordinal] + 1)
        }
        refresh()
    }

    /** Ends a timed power-up early (a shield that took its hit) or spends the nova. */
    fun spend(p: PowerUp) {
        if (p.timed) timers[p.ordinal] = 0f else if (p == PowerUp.NOVA) novaHeld = false
        refresh()
    }

    /** Runs the clocks down. Calls [expired] for each power-up that just ran out. */
    inline fun tick(dt: Float, expired: (PowerUp) -> Unit) {
        for (p in PowerUp.values()) {
            if (!p.timed || left(p) <= 0f) continue
            if (drain(p, dt)) expired(p)
        }
    }

    /** Returns true if that was the last of it. */
    fun drain(p: PowerUp, dt: Float): Boolean {
        timers[p.ordinal] -= dt
        if (timers[p.ordinal] > 0f) return false
        timers[p.ordinal] = 0f
        refresh()
        return true
    }

    private fun refresh() {
        val l = loadout
        l.speed = 1f + 0.08f * stacks(PowerUp.SPEED)
        l.comboTime = 1f + 0.2f * stacks(PowerUp.FRENZY)
        l.luck = 0.5f * stacks(PowerUp.LUCK)
        l.shields = if (active(PowerUp.SHIELD)) 1 else 0
        l.magnet = if (active(PowerUp.MAGNET)) 3.4f else 0f
        l.dash = active(PowerUp.DASH)
        l.echo = active(PowerUp.ECHO)
        l.nova = novaHeld
    }
}
