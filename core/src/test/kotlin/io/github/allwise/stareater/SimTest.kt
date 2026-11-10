package io.github.allwise.stareater

import io.github.allwise.stareater.meta.Skill
import io.github.allwise.stareater.sim.Bot
import io.github.allwise.stareater.sim.Eater
import io.github.allwise.stareater.sim.Enemies
import io.github.allwise.stareater.sim.Geo
import io.github.allwise.stareater.sim.InputState
import io.github.allwise.stareater.sim.LevelGen
import io.github.allwise.stareater.sim.LevelSpec
import io.github.allwise.stareater.sim.Loadout
import io.github.allwise.stareater.sim.MoveKind
import io.github.allwise.stareater.sim.Rules
import io.github.allwise.stareater.sim.RunState
import io.github.allwise.stareater.sim.Stalker
import io.github.allwise.stareater.sim.Warper
import io.github.allwise.stareater.sim.World
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimTest {
    private fun world(spec: LevelSpec, loadout: Loadout = Loadout(), seed: Long = 1L) = World(spec, loadout, RunState(seed), seed)

    @Test
    fun wrapMaths() {
        assertEquals(29f, Geo.wrap(-1f, 30f))
        assertEquals(0f, Geo.wrap(30f, 30f))
        assertEquals(-3f, Geo.wrapDelta(27f, 30f))
        assertEquals(3f, Geo.wrapDelta(-27f, 30f))
        assertEquals(10f, Geo.wrapDelta(10f, 30f))
    }

    @Test
    fun levelOneIsAlwaysTheOriginal() {
        repeat(50) { seed ->
            val spec = LevelGen.generate(1, Random(seed))
            assertEquals(MoveKind.GRID, spec.move)
            assertTrue(spec.modifiers.isEmpty())
            assertEquals(listOf(Eater, Stalker), spec.roster)
            assertEquals(1, spec.ruleCount)
        }
    }

    @Test
    fun generatedLevelsAreWellFormed() {
        val seenMoves = HashSet<MoveKind>()
        val seenRules = HashSet<String>()
        val seenEnemies = HashSet<String>()
        var maxRules = 0
        for (seed in 0 until 400) {
            val rng = Random(seed)
            for (n in 2..40) {
                val spec = LevelGen.generate(n, rng)
                assertTrue(spec.ruleCount in 1..LevelSpec.MAX_RULES, "rule count ${spec.ruleCount}")
                assertEquals(spec.modifiers.size, spec.modifiers.toSet().size, "duplicate rules")
                for (m in spec.modifiers) {
                    val info = Rules.byId(m)
                    assertTrue(spec.move in info.moves, "$m is not valid in ${spec.move}")
                    assertTrue(info.minLevel <= n, "$m too early at level $n")
                }
                assertTrue(spec.roster.isNotEmpty())
                assertTrue(spec.roster.any { it in Enemies.hunters }, "no hunter on the roster")
                assertTrue(spec.roster.all { it.inRoster && it.minLevel <= n })
                seenMoves += spec.move
                seenRules += spec.modifiers
                seenEnemies += spec.roster.map { it.id }
                maxRules = maxOf(maxRules, spec.ruleCount)
            }
        }
        assertEquals(MoveKind.values().toSet(), seenMoves)
        assertEquals(Rules.all.map { it.id }.toSet(), seenRules)
        assertEquals(Enemies.roster.map { it.id }.toSet(), seenEnemies)
        assertEquals(LevelSpec.MAX_RULES, maxRules)
    }

    @Test
    fun difficultyScalesWithLevel() {
        val early = LevelGen.build(1, MoveKind.GRID, emptyList(), listOf(Eater))
        val late = LevelGen.build(20, MoveKind.GRID, emptyList(), listOf(Eater))
        assertTrue(late.quota > early.quota)
        assertTrue(late.enemySpeed > early.enemySpeed)
        assertTrue(late.enemyMax > early.enemyMax)
        assertTrue(late.iqBase > early.iqBase)
        assertTrue(late.spawnInterval < early.spawnInterval)
    }

    /** The whole point of the original: you can wrap, the basic enemies take the plain route. */
    @Test
    fun eaterGoesTheLongWayButWarperWraps() {
        val w = world(LevelGen.build(1, MoveKind.GRID, emptyList(), listOf(Eater)))
        w.enemies.clear()
        w.player.place(1.5f, 7.5f)
        val eater = w.spawnEnemyAt(Eater, 28.5f, 7.5f, 0f)
        val warper = w.spawnEnemyAt(Warper, 28.5f, 7.5f, 0f)
        Eater.think(w, eater, 0.016f)
        Warper.think(w, warper, 0.016f)
        assertTrue(eater.desireX < 0f, "eater should walk left across the whole field")
        assertTrue(warper.desireX > 0f, "warper should step right, through the wrap")
    }

    @Test
    fun playerWrapsAroundTheEdge() {
        val w = world(LevelGen.build(1, MoveKind.GRID, emptyList(), listOf(Eater)))
        w.enemies.clear()
        w.player.place(0.5f, 7.5f)
        val input = InputState()
        input.taps += io.github.allwise.stareater.sim.Dir.LEFT
        w.update(0.016f, input)
        assertEquals(29.5f, w.player.x)
    }

    @Test
    fun eatingTheQuotaClearsTheLevel() {
        val spec = LevelGen.build(1, MoveKind.GRID, emptyList(), listOf(Eater))
        val w = world(spec)
        w.enemies.clear()
        val input = InputState()
        var guard = 0
        while (w.phase == World.Phase.PLAYING && guard++ < 20_000) {
            w.enemies.clear()
            // Teleport onto the nearest star: we are testing bookkeeping, not skill.
            w.stars.firstOrNull()?.let { w.player.place(it.x, it.y) }
            w.update(0.016f, input)
        }
        assertEquals(World.Phase.CLEARED, w.phase)
        assertEquals(spec.quota, w.collected)
        assertTrue(w.run.score > 0)
        assertTrue(w.run.dust > 0f)
    }

    @Test
    fun shieldsAbsorbHitsThenYouDie() {
        val w = world(LevelGen.build(1, MoveKind.GRID, emptyList(), listOf(Eater)), Loadout(shields = 1))
        w.hitPlayer()
        assertEquals(World.Phase.PLAYING, w.phase)
        assertEquals(0, w.player.shields)
        w.hitPlayer() // still invulnerable from the first hit
        assertEquals(World.Phase.PLAYING, w.phase)
        w.player.invuln = 0f
        w.hitPlayer()
        assertEquals(World.Phase.DEAD, w.phase)
    }

    /** Every rule on every movement mode it claims to support, driven by the bot. Nothing may blow up. */
    @Test
    fun soakEveryRuleCombination() {
        val full = Skill.loadout(Skill.values().toSet())
        var worlds = 0
        for (move in MoveKind.values()) {
            val compatible = Rules.all.filter { move in it.moves }.map { it.id }
            val combos = ArrayList<List<String>>()
            combos += emptyList<String>()
            compatible.forEach { combos += listOf(it) }
            val rng = Random(move.ordinal)
            repeat(30) { combos += compatible.shuffled(rng).take(1 + rng.nextInt(4)) }
            for ((i, mods) in combos.withIndex()) {
                val level = 1 + (i * 7) % 35
                val spec = LevelGen.build(level, move, mods, Enemies.roster)
                soak(world(spec, if (i % 2 == 0) full else Loadout(), seed = i.toLong()), "$move $mods L$level")
                worlds++
            }
        }
        assertTrue(worlds > 100)
    }

    private fun soak(w: World, label: String) {
        val bot = Bot()
        val input = InputState()
        var frames = 0
        while (w.phase == World.Phase.PLAYING && frames++ < 60 * 45) {
            input.clearEdges()
            bot.drive(w, input)
            w.update(1f / 60f, input)
            val p = w.player
            assertTrue(p.x.isFinite() && p.y.isFinite(), "$label: player position went non-finite")
            assertTrue(p.x >= 0f && p.x <= w.w && p.y >= 0f && p.y <= w.h, "$label: player left the field at ${p.x},${p.y}")
            assertTrue(!w.isWallAt(p.x, p.y), "$label: player inside a wall")
            for (e in w.enemies) {
                assertTrue(e.x.isFinite() && e.y.isFinite(), "$label: ${e.type.id} went non-finite")
                assertTrue(e.x >= 0f && e.x <= w.w && e.y >= 0f && e.y <= w.h, "$label: ${e.type.id} left the field at ${e.x},${e.y}")
            }
            assertTrue(w.enemies.size <= w.spec.enemyMax + 8, "$label: enemy count ran away (${w.enemies.size})")
            assertTrue(w.stars.size <= 40, "$label: star count ran away (${w.stars.size})")
            w.fx.clear()
        }
    }
}
