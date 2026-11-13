package io.github.allwise.stareater.sim

/** Anything with a position. Positions are in tile units; cell centres sit at `n + 0.5`. */
open class Body {
    var x = 0f
    var y = 0f

    /** Where the body was before its last discrete step, and how far through the hop it is (render only). */
    var px = 0f
    var py = 0f
    var tween = 1f
    var tweenDur = 0.08f

    var vx = 0f
    var vy = 0f

    fun place(nx: Float, ny: Float) {
        x = nx; y = ny; px = nx; py = ny; tween = 1f
    }

    fun stepTo(nx: Float, ny: Float, dur: Float) {
        px = x; py = y; x = nx; y = ny; tween = 0f; tweenDur = dur
    }

    fun tick(dt: Float) {
        if (tween < 1f) tween = minOf(1f, tween + dt / tweenDur)
    }
}

class Player : Body() {
    var faceX = 1f
    var faceY = 0f
    var lastDir = Dir.RIGHT

    /** True once the player has moved at all this level (the Restless rule waits for it). */
    var started = false

    var shields = 0
    var shieldRegen = 0f
    var invuln = 0f
    var dashCd = 0f
    var novaCharge = 0
    var echoCd = 0f

    // Grid stepping state.
    var stepCd = 0f
    var holdGate = 0f
    val queue = ArrayList<Dir>(2)
}

class Enemy(val type: EnemyType) : Body() {
    /** Where the brain wants to go (any length) and how hard it is trying (speed factor). */
    var desireX = 0f
    var desireY = 0f
    var pace = 1f

    /** Ballistic enemies ignore steering and fly along [dirX],[dirY] until something stops them. */
    var ballistic = false
    var dirX = 0f
    var dirY = 0f
    var travel = 0f

    var wraps = false
    var state = 0
    var timer = 0f

    /** A second clock, for brains that are counting down to two different things. */
    var timer2 = 0f
    var stun = 0f

    /** Counts down while the enemy is materialising: harmless and frozen until it hits zero. */
    var spawnT = 0f

    /** Remaining lifetime for short-lived enemies, or -1 for "until the level ends". */
    var life = -1f

    var stepAcc = 0f
    var seed = 0f
    var dead = false

    /** Bosses cannot be stunned; they are slowed for this long instead. */
    var slow = 0f

    /** Counts down after a boss takes a hit (render only). */
    var hurt = 0f

    /** Body segments trailing behind the head, for enemies that have a body. */
    var trail: ArrayList<Pt>? = null
    var trailX = 0f
    var trailY = 0f

    /** Facing and animation clock. Owned by the renderer; the sim never reads them. */
    var heading = 0f
    var animT = 0f

    val radius: Float get() = type.radius

    val active: Boolean get() = spawnT <= 0f && stun <= 0f && !dead
}

enum class StarType(val value: Int, val title: String) {
    COMMON(1, "Star"),
    GOLD(3, "Gold star"),
    PULSAR(4, "Pulsar"),
    COMET(5, "Comet"),
    NOVA(10, "Nova"),
}

class Star(val type: StarType) : Body() {
    var age = 0f

    /** Seconds until the star fizzles out, or 0 for "stays put". */
    var ttl = 0f

    /** In orbit mode: radius, angle and angular speed around the centre. Radius 0 means "not orbiting". */
    var orbitR = 0f
    var orbitA = 0f
    var orbitW = 0f

    /** Pulsars can only be eaten while they are lit. */
    val lit: Boolean get() = type != StarType.PULSAR || (age % PULSAR_PERIOD) < PULSAR_ON

    companion object {
        const val PULSAR_PERIOD = 2.6f
        const val PULSAR_ON = 1.7f
    }
}

/** A rock that sweeps a whole row or column after a warning. */
class Meteor(val horizontal: Boolean, val line: Int, val dir: Int, var warn: Float, var pos: Float) {
    val x: Float get() = if (horizontal) pos else line + 0.5f
    val y: Float get() = if (horizontal) line + 0.5f else pos
}

/**
 * How hard things pull. A sun comes in three stages - a dwarf, a star, a giant - and only ever
 * pulls: you can fly straight through one. A black hole (stage 0) pulls about like a middling sun,
 * but touching it is the end.
 */
object Gravity {
    const val HOLE = 0

    /** Strength relative to the one setting the game used to have, which sat at "two and a half". */
    fun factor(stage: Int): Float = (if (stage == HOLE) 2f else stage.toFloat()) / 2.5f
}

/** A gravity well drifting about a level: a sun of stage 1 to 3, or a black hole. */
class Well(var x: Float, var y: Float, val stage: Int, val pull: Float) {
    val blackHole: Boolean get() = stage == Gravity.HOLE

    /** How big it is, in tiles. For a black hole this is the event horizon: inside it you are gone. */
    val radius: Float get() = if (blackHole) HORIZON else 0.4f + 0.2f * stage

    companion object {
        const val HORIZON = 0.7f
    }
}

/** The thing in the middle of an orbit level: a sun, or a black hole. */
class Sun(val x: Float, val y: Float, val radius: Float, val gm: Float, val stage: Int) {
    val blackHole: Boolean get() = stage == Gravity.HOLE
}

/** A patch of light left behind in a Lights Out level. */
class Flare(var x: Float, var y: Float, val radius: Float, var life: Float, val maxLife: Float)

/** The Wrap Echo afterimage that enemies chase instead of you. */
class Decoy(val x: Float, val y: Float, var life: Float)

class Pt(var x: Float, var y: Float)

/** Things that happened this frame which the presentation layer may want to show. */
sealed class Fx {
    class StarEaten(val x: Float, val y: Float, val type: StarType, val points: Int, val combo: Int) : Fx()
    class StarLost(val x: Float, val y: Float) : Fx()
    class ShieldHit(val x: Float, val y: Float) : Fx()
    class Revive(val x: Float, val y: Float) : Fx()
    class Death(val x: Float, val y: Float) : Fx()
    class Dash(val x0: Float, val y0: Float, val x1: Float, val y1: Float) : Fx()
    class Nova(val x: Float, val y: Float, val radius: Float) : Fx()
    class Wrap(val x0: Float, val y0: Float, val x1: Float, val y1: Float) : Fx()
    class EnemySpawn(val x: Float, val y: Float) : Fx()
    class EnemyGone(val x: Float, val y: Float) : Fx()
    class Stun(val x: Float, val y: Float) : Fx()
    class Alert(val x: Float, val y: Float) : Fx()
    class MeteorWarn : Fx()
    class BossHit(val x: Float, val y: Float, val bossX: Float, val bossY: Float) : Fx()
    class BossDead(val x: Float, val y: Float) : Fx()
    class TimeUp : Fx()
    class Cleared : Fx()

    /** Arcade mode: a power-up was collected, or one just ran out. */
    class Powered(val x: Float, val y: Float, val kind: PowerUp) : Fx()
    class PowerLost(val kind: PowerUp) : Fx()

    /** The edge block: warning given, edges shut, edges open again. */
    class EdgeWarn : Fx()
    class EdgeSeal : Fx()
    class EdgeOpen : Fx()
}

/** What the player's unlocked skills amount to inside a level. A default Loadout is the bare ship. */
class Loadout(
    var speed: Float = 1f,
    var shields: Int = 0,
    var shieldRegen: Boolean = false,
    var secondWind: Boolean = false,
    var dash: Boolean = false,
    var dashCdMul: Float = 1f,
    var dashStun: Boolean = false,
    var echo: Boolean = false,
    var magnet: Float = 0f,
    var comboTime: Float = 1f,
    var luck: Float = 0f,
    var nova: Boolean = false,
    var dustMul: Float = 1f,
)

/** State that survives from level to level within one run. */
class RunState(val seed: Long, val key: String = "") {
    var level = 1
    var score = 0
    var dust = 0f
    var starsEaten = 0
    var bestCombo = 1
    var secondWindUsed = false

    /** The level this run began on: 1, or further in from a checkpoint or a saved level. */
    var startLevel = 1
    var prevMove: MoveKind? = null
    var time = 0f

    /** Set on arcade runs: no skill tree, the kit is whatever got picked up. */
    var arcade: Arcade? = null
}
