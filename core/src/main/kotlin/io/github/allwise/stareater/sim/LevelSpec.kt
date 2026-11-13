package io.github.allwise.stareater.sim

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Everything needed to build one level: which rules are in play, and how hard the numbers are. */
class LevelSpec(
    val number: Int,
    val move: MoveKind,
    /** Ids of the modifier rules, see [Rules]. */
    val modifiers: List<String>,
    val roster: List<EnemyType>,
) {
    /** Field size in tiles. Bigger than the view means the camera follows the player. */
    var width = FIELD_W
    var height = FIELD_H

    /** Set on boss levels: the big one. The quota is then its health. */
    var boss: EnemyType? = null

    var quota = 8
    var starsOnField = 2
    var enemyStart = 1
    var enemyMax = 3
    var spawnInterval = 10f

    /** Base enemy speed in tiles per second, before per-type and in-level ramp factors. */
    var enemySpeed = 2f
    var speedRamp = 0.004f
    var speedRampMax = 0.35f

    /** Enemy smarts, 0 (blunders about) to 1 (surgical). Climbs with [iqRamp] per second in-level. */
    var iqBase = 0.1f
    var iqRamp = 0.004f

    var scrollSpeed = 2f
    val starWeights = FloatArray(StarType.values().size)

    /** The movement mode counts as a rule, so this is always 1..5. */
    val ruleCount: Int get() = 1 + modifiers.size

    companion object {
        const val FIELD_W = 30
        const val FIELD_H = 15
        const val MAX_RULES = 5
        const val BOSS_EVERY = 10
    }
}

object LevelGen {
    /**
     * Rolls level [n]. Level 1 is always the bare original: classic movement, no modifiers, E and S.
     * After that everything is random, with the rule count and the numbers scaling with [n].
     */
    fun generate(n: Int, rng: Random, prevMove: MoveKind? = null): LevelSpec {
        if (n <= 1) return build(1, MoveKind.GRID, emptyList(), listOf(Eater, Stalker))
        if (n % LevelSpec.BOSS_EVERY == 0) return bossLevel(n, rng)

        // Movement: any unlocked mode, but lean away from repeating the previous one.
        val moves = MoveKind.values().filter { it != MoveKind.ORBIT || n >= 3 }
        val move = pick(rng, moves) { if (it == prevMove) 0.35f else 1f }

        val maxRules = when {
            n <= 3 -> 2
            n <= 6 -> 3
            n <= 10 -> 4
            else -> LevelSpec.MAX_RULES
        }
        val minRules = when {
            n <= 5 -> 1
            n <= 12 -> 2
            else -> 3
        }
        val wanted = minRules + rng.nextInt(maxRules - minRules + 1) - 1 // minus the movement rule
        val pool = Rules.all.filter { it.minLevel <= n && move in it.moves }.toMutableList()
        val mods = ArrayList<String>()
        while (mods.size < wanted && pool.isNotEmpty()) {
            val chosen = pool.removeAt(rng.nextInt(pool.size))
            mods += chosen.id
            pool.removeAll { it.id in chosen.conflicts || chosen.id in it.conflicts }
        }

        val available = Enemies.roster.filter { it.minLevel <= n }.toMutableList()
        val rosterSize = 1 + rng.nextInt(min(4, 1 + n / 2))
        val roster = ArrayList<EnemyType>()
        while (roster.size < rosterSize && available.isNotEmpty()) roster += available.removeAt(rng.nextInt(available.size))
        if (roster.none { it in Enemies.hunters }) {
            roster[0] = pick(rng, Enemies.hunters.filter { it.minLevel <= n }) { 1f }
        }
        return build(n, move, mods, roster.sortedBy { Enemies.all.indexOf(it) })
    }

    /**
     * The level a run is currently on. Depends only on the run's seed and level number, so every
     * player on the same seed key is dealt exactly the same sequence.
     */
    fun forRun(run: RunState): LevelSpec = forSeed(run.seed, run.level)

    /**
     * Level [n] of a seed. A level leans away from repeating the movement mode of the one before
     * it, so the levels are dealt in order from the first: that way level 17 is the same level 17
     * whether you played your way there, started from a checkpoint, or loaded it from a saved slot.
     */
    fun forSeed(seed: Long, n: Int): LevelSpec {
        var prev: MoveKind? = null
        var spec = generate(1, Random(seed * 31 + 7919L), null)
        for (i in 2..n) {
            prev = spec.move
            spec = generate(i, Random(seed * 31 + i * 7919L), prev)
        }
        return spec
    }

    /** Every tenth level: one enormous letter, a couple of minions, and no other twists. */
    fun bossLevel(n: Int, rng: Random, boss: EnemyType? = null, move: MoveKind? = null): LevelSpec {
        val tier = n / LevelSpec.BOSS_EVERY
        val who = boss ?: Enemies.bosses[(tier - 1).coerceAtLeast(0) % Enemies.bosses.size]
        val how = move ?: if (rng.nextBoolean()) MoveKind.GRID else MoveKind.GLIDE
        val s = build(n, how, emptyList(), listOf(if (who === Warden) Stalker else Eater))
        s.boss = who
        s.quota = min(40, 18 + n / 5)
        s.starsOnField = 3
        s.enemyStart = 1
        s.enemyMax = min(5, 2 + n / 20)
        s.spawnInterval = 10f
        return s
    }

    /** Builds a spec for an explicit rule set, with numbers scaled for level [n]. */
    fun build(n: Int, move: MoveKind, mods: List<String>, roster: List<EnemyType>): LevelSpec {
        val s = LevelSpec(n, move, mods, roster)
        val k = n - 1
        s.quota = min(60, 8 + 2 * k)
        s.starsOnField = when (move) {
            MoveKind.SCROLL -> 5 + min(3, n / 5)
            MoveKind.ORBIT -> 4 + min(3, n / 4)
            else -> 2 + min(3, n / 4)
        }
        if (move == MoveKind.ORBIT) {
            // A solar system needs room: bigger than the screen, so the camera follows.
            s.width = 40
            s.height = 26
        }
        s.enemyStart = 1 + min(3, k / 4)
        s.enemyMax = min(14, 3 + (k * 0.7f).toInt())
        s.spawnInterval = max(3.5f, 11f - 0.5f * k)
        s.enemySpeed = 2f + 4.6f * (1f - exp(-k / 10f))
        s.iqBase = min(0.85f, 0.08f + 0.06f * k)
        s.scrollSpeed = min(4.5f, 1.8f + 0.15f * k)

        s.starWeights[StarType.COMMON.ordinal] = 1f
        s.starWeights[StarType.GOLD.ordinal] = min(0.25f, 0.10f + 0.01f * k)
        s.starWeights[StarType.COMET.ordinal] = if (n >= 2) 0.07f else 0f
        s.starWeights[StarType.PULSAR.ordinal] = if (n >= 3) 0.08f else 0f
        s.starWeights[StarType.NOVA.ordinal] = if (n >= 4) 0.035f else 0f

        for (m in mods) Rules.byId(m).tune(s)
        return s
    }

    private fun <T> pick(rng: Random, items: List<T>, weight: (T) -> Float): T {
        var total = 0f
        for (i in items) total += weight(i)
        var r = rng.nextFloat() * total
        for (i in items) {
            r -= weight(i)
            if (r <= 0f) return i
        }
        return items.last()
    }
}
