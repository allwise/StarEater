package io.github.allwise.stareater.sim

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Everything needed to build one level: which rules are in play, and how hard the numbers are. */
class LevelSpec(
    /** The level number the difficulty is scaled for. Only the endless mode ever changes it. */
    var number: Int,
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
     *
     * The mode narrows the choice: Matrix only ever deals the classic grid, Flow never does.
     */
    fun generate(n: Int, rng: Random, prevMove: MoveKind? = null, mode: GameMode = GameMode.CAMPAIGN): LevelSpec {
        if (mode == GameMode.ENDLESS) return endless(rng)
        if (n <= 1) return build(1, if (mode == GameMode.FLOW) MoveKind.GLIDE else MoveKind.GRID, emptyList(), listOf(Eater, Stalker))
        if (n % LevelSpec.BOSS_EVERY == 0) {
            return bossLevel(n, rng, move = when (mode) { GameMode.MATRIX -> MoveKind.GRID; GameMode.FLOW -> MoveKind.GLIDE; else -> null })
        }

        // Movement: any unlocked mode, but lean away from repeating the previous one.
        val moves = MoveKind.values().filter {
            (it != MoveKind.ORBIT || n >= 3) && when (mode) {
                GameMode.MATRIX -> it == MoveKind.GRID
                GameMode.FLOW -> it != MoveKind.GRID
                else -> true
            }
        }
        val move = pick(rng, moves) { if (it == prevMove) 0.35f else 1f }

        val maxRules = when {
            n <= 3 -> 2
            n <= 6 -> 3
            n <= 14 -> 4
            else -> LevelSpec.MAX_RULES
        }
        val minRules = when {
            n <= 5 -> 1
            n <= 16 -> 2
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
     * The one level an endless run is played on: a way of moving, two twists and three kinds of
     * hostile, dealt once and kept. Only the numbers change after that, see [scale].
     */
    fun endless(rng: Random): LevelSpec {
        val move = pick(rng, MoveKind.values().toList()) { 1f }
        val pool = Rules.all.filter { move in it.moves }.toMutableList()
        val mods = ArrayList<String>()
        while (mods.size < 2 && pool.isNotEmpty()) {
            val chosen = pool.removeAt(rng.nextInt(pool.size))
            mods += chosen.id
            pool.removeAll { it.id in chosen.conflicts || chosen.id in it.conflicts }
        }
        val available = Enemies.roster.toMutableList()
        val roster = ArrayList<EnemyType>()
        while (roster.size < 3) roster += available.removeAt(rng.nextInt(available.size))
        if (roster.none { it in Enemies.hunters }) roster[0] = pick(rng, Enemies.hunters) { 1f }
        return build(1, move, mods, roster.sortedBy { Enemies.all.indexOf(it) })
    }

    /**
     * The level a run is currently on. Depends only on the run's seed, mode and level number, so
     * every player on the same seed key is dealt exactly the same sequence.
     */
    fun forRun(run: RunState): LevelSpec = forSeed(run.seed, run.level, run.mode)

    /**
     * Level [n] of a seed. A level leans away from repeating the movement mode of the one before
     * it, so the levels are dealt in order from the first: that way level 17 is the same level 17
     * whether you played your way there or started from a checkpoint.
     */
    fun forSeed(seed: Long, n: Int, mode: GameMode = GameMode.CAMPAIGN): LevelSpec {
        if (mode == GameMode.ENDLESS) return endless(Random(seed * 31 + 7919L)).also { scale(it, n) }
        var prev: MoveKind? = null
        var spec = generate(1, Random(seed * 31 + 7919L), null, mode)
        for (i in 2..n) {
            prev = spec.move
            spec = generate(i, Random(seed * 31 + i * 7919L), prev, mode)
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
    fun build(n: Int, move: MoveKind, mods: List<String>, roster: List<EnemyType>): LevelSpec =
        LevelSpec(n, move, mods, roster).also { scale(it, n) }

    /**
     * Sets a spec's numbers for level [n]. The first ten levels climb steeply, as a run should when
     * everything is new; from there on the slope is gentle, so that the back two thirds of the
     * campaign are a matter of practice rather than a wall.
     *
     * Safe to call again on a spec that is already in play, which is how the endless mode gets harder.
     */
    fun scale(s: LevelSpec, n: Int) {
        s.number = n
        val k = n - 1
        // "Early" is the part of the curve up to the first boss; "late" is everything past it.
        val early = min(k, STEEP)
        val late = max(0, k - STEEP)
        s.quota = 8 + 2 * early + late
        if (s.quota > 46) s.quota = 46
        s.starsOnField = when (s.move) {
            MoveKind.SCROLL -> 5 + min(3, n / 5)
            MoveKind.ORBIT -> 4 + min(3, n / 4)
            else -> 2 + min(3, n / 4)
        }
        // A solar system needs room: bigger than the screen, so the camera follows.
        s.width = if (s.move == MoveKind.ORBIT) 40 else LevelSpec.FIELD_W
        s.height = if (s.move == MoveKind.ORBIT) 26 else LevelSpec.FIELD_H
        s.enemyStart = 1 + min(3, k / 6)
        s.enemyMax = min(12, 3 + (early * 0.7f).toInt() + late / 4)
        s.spawnInterval = max(4.5f, 11f - 0.5f * early - 0.1f * late)
        s.enemySpeed = 2f + 4.6f * (1f - exp(-early / 10f)) + 0.035f * late
        s.iqBase = min(0.85f, 0.08f + 0.06f * early + 0.012f * late)
        s.scrollSpeed = min(4.5f, 1.8f + 0.15f * early + 0.05f * late)

        s.starWeights[StarType.COMMON.ordinal] = 1f
        s.starWeights[StarType.GOLD.ordinal] = min(0.25f, 0.10f + 0.01f * k)
        s.starWeights[StarType.COMET.ordinal] = if (n >= 2) 0.07f else 0f
        s.starWeights[StarType.PULSAR.ordinal] = if (n >= 3) 0.08f else 0f
        s.starWeights[StarType.NOVA.ordinal] = if (n >= 4) 0.035f else 0f

        for (m in s.modifiers) Rules.byId(m).tune(s)
    }

    /** How many levels the steep part of the difficulty curve lasts. */
    private const val STEEP = 8

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
