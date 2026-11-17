package io.github.allwise.stareater.input

/**
 * The vibration motor's vocabulary. Each feel is a short pattern of pulses: how long each one is,
 * how hard (1..255, where the phone can vary it at all), and how long to rest before the next.
 * Kept to a few tens of milliseconds - a tap, not a buzz - except where something big happened.
 */
enum class Buzz(val priority: Int, vararg val pattern: Pulse) {
    /** A menu tap: a reel stopping, a letter turning, a slot stored. */
    TAP(1, Pulse(12, 90)),

    /** Eating a star. Barely there: you will feel dozens of them. */
    STAR(0, Pulse(8, 60)),

    /** A gold star, pulsar or comet. */
    RARE(1, Pulse(14, 130)),

    /** A Nova star going off, or the Nova button. */
    NOVA(2, Pulse(30, 200)),

    /** The countdown clock's last five seconds. */
    TICK(1, Pulse(10, 110)),

    /** A capsule picked up, or something bought. */
    POWER(1, Pulse(18, 140)),

    /** A pocket item used. */
    ITEM(2, Pulse(25, 160)),

    /** Overdrive kicking in: a short rising surge. */
    SURGE(2, Pulse(15, 70, 25), Pulse(30, 150)),

    /** An enemy bites through the Serpent tail. */
    SNAG(1, Pulse(14, 100)),

    /** Every star you eat hurts the boss. */
    BOSS_HIT(2, Pulse(25, 170)),

    /** The Warden locks the edges. */
    SEAL(3, Pulse(60, 200)),

    /** A shield takes the hit. */
    SHIELD(4, Pulse(45, 210)),

    /** A Second Wind: knocked down and back up again. */
    REVIVE(4, Pulse(40, 220, 90), Pulse(40, 220)),

    /** Level cleared: two quick taps, the second firmer. */
    CLEAR(3, Pulse(20, 120, 70), Pulse(35, 180)),

    /** A boss goes down: a long rolling rumble. */
    BOSS_DEAD(5, Pulse(80, 255, 60), Pulse(80, 210, 60), Pulse(160, 255)),

    /** Eaten. A hard thud and a shorter one after it. */
    CRASH(6, Pulse(160, 255, 70), Pulse(60, 120)),
}

class Pulse(val ms: Int, val strength: Int, val restMs: Int = 0)

/**
 * Plays [Buzz] patterns through [motor] (milliseconds, strength), one pulse at a time, off the
 * frame clock. A stronger feel cuts in over a weaker one; a weaker one waits its turn or, for the
 * star taps that come in bursts, is simply dropped. Pure: the platform call is passed in.
 */
class Haptics(private val motor: (ms: Int, strength: Int) -> Unit) {
    private var current: Buzz? = null
    private var step = 0
    private var wait = 0f
    private var lastStar = -1f
    private var clock = 0f

    fun play(b: Buzz) {
        val now = current
        if (now != null && now.priority > b.priority) return
        // Star taps on a big combo would blur into one drone; keep them apart.
        if (b == Buzz.STAR && clock - lastStar < STAR_GAP) return
        if (b == Buzz.STAR) lastStar = clock
        current = b
        step = 0
        wait = 0f
        fire()
    }

    fun update(dt: Float) {
        clock += dt
        if (current == null) return
        wait -= dt
        if (wait <= 0f) fire()
    }

    /** Stops whatever is playing: the motor is left to finish the pulse it is on. */
    fun stop() {
        current = null
    }

    private fun fire() {
        val b = current ?: return
        if (step >= b.pattern.size) {
            current = null
            return
        }
        val p = b.pattern[step++]
        motor(p.ms, p.strength)
        wait = (p.ms + p.restMs) / 1000f
    }

    private companion object {
        const val STAR_GAP = 0.07f
    }
}
