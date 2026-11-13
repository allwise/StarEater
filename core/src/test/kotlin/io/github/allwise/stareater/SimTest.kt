package io.github.allwise.stareater

import io.github.allwise.stareater.meta.Skill
import io.github.allwise.stareater.sim.Arcade
import io.github.allwise.stareater.sim.Bot
import io.github.allwise.stareater.sim.Eater
import io.github.allwise.stareater.sim.Enemies
import io.github.allwise.stareater.sim.Geo
import io.github.allwise.stareater.sim.Gravity
import io.github.allwise.stareater.sim.InputState
import io.github.allwise.stareater.sim.LevelGen
import io.github.allwise.stareater.sim.LevelSpec
import io.github.allwise.stareater.sim.Loadout
import io.github.allwise.stareater.sim.MoveKind
import io.github.allwise.stareater.sim.OrbitMovement
import io.github.allwise.stareater.sim.PowerUp
import io.github.allwise.stareater.sim.Rules
import io.github.allwise.stareater.sim.RunState
import io.github.allwise.stareater.sim.Stalker
import io.github.allwise.stareater.sim.Warden
import io.github.allwise.stareater.sim.Warper
import io.github.allwise.stareater.sim.Well
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
                assertEquals(n % LevelSpec.BOSS_EVERY == 0, spec.boss != null, "boss levels are every tenth, and only those")
                assertTrue(!("fog" in spec.modifiers && "lightsout" in spec.modifiers), "two kinds of darkness at once")
                if (spec.move == MoveKind.ORBIT) assertTrue(n >= 3, "orbit too early")
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
        // Arcade runs: the same levels, but with power-ups turning up and running out.
        for (move in MoveKind.values()) for (level in listOf(2, 9, 23)) {
            val compatible = Rules.all.filter { move in it.moves }.map { it.id }
            val run = RunState(level.toLong()).also { it.arcade = Arcade() }
            val spec = LevelGen.build(level, move, compatible.shuffled(Random(level)).take(2), Enemies.roster)
            soak(World(spec, run.arcade!!.loadout, run, level.toLong()), "arcade $move L$level")
            worlds++
        }
        for (boss in Enemies.bosses) for (move in listOf(MoveKind.GRID, MoveKind.GLIDE)) for (level in listOf(10, 30)) {
            val spec = LevelGen.bossLevel(level, Random(level), boss, move)
            soak(world(spec, full, seed = level.toLong()), "boss ${boss.id} $move L$level")
            worlds++
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
            assertTrue(w.capsules.size <= World.MAX_CAPSULES, "$label: power-ups piled up (${w.capsules.size})")
            w.fx.clear()
        }
    }

    /** Whatever is in the middle - dwarf, star, giant or black hole - doing nothing must be survivable. */
    @Test
    fun coastingInOrbitIsSurvivable() {
        for (stage in 0..3) {
            val w = world(LevelGen.build(5, MoveKind.ORBIT, emptyList(), listOf(Eater)))
            val sun = OrbitMovement.sunOf(w, stage)
            w.sun = sun
            w.player.place(sun.x + OrbitMovement.START_RADIUS, sun.y)
            w.player.vx = 0f
            w.player.vy = kotlin.math.sqrt(sun.gm / OrbitMovement.START_RADIUS)
            val input = InputState()
            repeat(60 * 30) {
                w.enemies.clear()
                w.stars.clear() // nothing to eat, so the level cannot end under us
                w.update(1f / 60f, input)
                w.fx.clear()
            }
            assertEquals(World.Phase.PLAYING, w.phase, "stage $stage")
            val r = kotlin.math.hypot(w.player.x - sun.x, w.player.y - sun.y)
            assertTrue(r in 4f..10f, "stage $stage: a hands-off orbit should stay an orbit, but ended at radius $r")
        }
    }

    @Test
    fun aSunOnlyPullsButABlackHoleIsTheEnd() {
        // Into a sun, even the heaviest: out the other side, shield untouched.
        val w = world(LevelGen.build(5, MoveKind.ORBIT, emptyList(), listOf(Eater)), Loadout(shields = 1))
        w.sun = OrbitMovement.sunOf(w, 3)
        val sun = w.sun!!
        w.player.place(sun.x + 0.2f, sun.y)
        w.player.vx = 0f; w.player.vy = 0f
        repeat(30) { w.enemies.clear(); w.update(1f / 60f, InputState()) }
        assertEquals(World.Phase.PLAYING, w.phase)
        assertEquals(1, w.player.shields, "a sun must not cost a shield")

        // Into a black hole: dead, shields or no shields.
        val b = world(LevelGen.build(5, MoveKind.ORBIT, emptyList(), listOf(Eater)), Loadout(shields = 2))
        b.sun = OrbitMovement.sunOf(b, Gravity.HOLE)
        b.enemies.clear()
        b.player.place(b.sun!!.x + 0.3f, b.sun!!.y)
        b.player.invuln = 5f
        b.update(1f / 60f, InputState())
        assertEquals(World.Phase.DEAD, b.phase, "a black hole goes straight through shields and invulnerability")

        // Only a Second Wind cheats it - once - and throws you clear.
        val c = world(LevelGen.build(5, MoveKind.ORBIT, emptyList(), listOf(Eater)), Loadout(secondWind = true))
        c.sun = OrbitMovement.sunOf(c, Gravity.HOLE)
        c.enemies.clear()
        c.player.place(c.sun!!.x + 0.3f, c.sun!!.y)
        c.update(1f / 60f, InputState())
        assertEquals(World.Phase.PLAYING, c.phase)
        assertTrue(c.run.secondWindUsed)
        assertTrue(kotlin.math.hypot(c.player.x - c.sun!!.x, c.player.y - c.sun!!.y) > c.sun!!.radius, "should be thrown clear")
    }

    @Test
    fun sunsComeInThreeStagesAndPullHarderEachTime() {
        assertTrue(Gravity.factor(1) < Gravity.factor(2) && Gravity.factor(2) < Gravity.factor(3))
        assertTrue(Gravity.factor(2) < 1f && Gravity.factor(3) > 1f, "the old single setting sat between stages 2 and 3")
        val stages = HashSet<Int>()
        for (seed in 0 until 200L) for (move in listOf(MoveKind.GLIDE, MoveKind.SCROLL)) for (level in listOf(4, 9, 20)) {
            val w = world(LevelGen.build(level, move, listOf("gravity"), listOf(Eater)), seed = seed)
            assertTrue(w.wells.isNotEmpty())
            assertTrue(w.wells.size <= if (move == MoveKind.SCROLL) 3 else 2, "$move L$level has ${w.wells.size} wells")
            assertTrue(w.wells.count { it.blackHole } <= 2)
            for (well in w.wells) {
                assertTrue(well.stage in 0..3)
                stages += well.stage
            }
        }
        assertEquals(setOf(0, 1, 2, 3), stages)
        assertEquals(3, world(LevelGen.build(20, MoveKind.SCROLL, listOf("gravity"), listOf(Eater))).wells.size)
    }

    @Test
    fun aDriftingSunIsHarmlessAndADriftingBlackHoleIsNot() {
        fun fly(stage: Int): World {
            val w = world(LevelGen.build(6, MoveKind.GLIDE, listOf("gravity"), listOf(Eater)), Loadout(shields = 1))
            w.wells.clear()
            w.wells += Well(10f, 7f, stage, 22f * Gravity.factor(stage))
            w.player.place(10.2f, 7f)
            w.player.vx = 0f; w.player.vy = 0f
            repeat(20) { w.enemies.clear(); w.update(1f / 60f, InputState()) }
            return w
        }
        for (stage in 1..3) {
            val w = fly(stage)
            assertEquals(World.Phase.PLAYING, w.phase, "stage $stage")
            assertEquals(1, w.player.shields, "stage $stage")
        }
        assertEquals(World.Phase.DEAD, fly(Gravity.HOLE).phase)
    }

    /** A shy star against the star magnet: the small maw cannot hold it, the big one fights it to a draw. */
    @Test
    fun shyStarsPutUpAFightAgainstTheMagnet() {
        fun gapAfter(magnet: Float, seconds: Float, start: Float = 2f, track: ((Float) -> Unit)? = null): Float {
            val w = world(LevelGen.build(3, MoveKind.GLIDE, listOf("shy"), listOf(Eater)), Loadout(magnet = magnet))
            w.player.place(10f, 7f)
            var d = 0f
            repeat((seconds * 60).toInt()) {
                w.enemies.clear()
                // One star only, and never let the level top the field back up around it.
                while (w.stars.size > 1) w.stars.removeAt(w.stars.size - 1)
                if (it == 0) w.stars[0].let { s -> s.place(10f + start, 7f); s.vx = 0f; s.vy = 0f; s.ttl = 0f }
                w.player.place(10f, 7f)
                w.player.vx = 0f; w.player.vy = 0f
                w.update(1f / 60f, InputState())
                if (w.stars.isEmpty()) return 0f
                d = w.dist(w.stars[0].x, w.stars[0].y, 10f, 7f)
                track?.invoke(d)
            }
            return d
        }
        val free = gapAfter(0f, 0.4f)
        val weak = gapAfter(2.2f, 0.4f)
        assertTrue(free > 2.9f, "with no magnet a shy star should get away as it always did, got $free")
        assertTrue(weak > 2.05f, "the small maw must not be able to reel a shy star in, got $weak")
        assertTrue(weak < free - 0.3f, "but it should slow the star's retreat: $weak vs $free")

        // The big maw: neither side wins quickly. The star sways in and out but is not simply swallowed.
        var nearest = 99f
        var furthest = 0f
        val end = gapAfter(3.8f, 4f, start = 2.6f) { nearest = minOf(nearest, it); furthest = maxOf(furthest, it) }
        assertTrue(end > 0f && nearest > 0.9f, "the big maw simply swallowed a shy star (nearest $nearest)")
        assertTrue(furthest < 3.8f, "and it should not lose its grip either (furthest $furthest)")
        assertTrue(furthest - nearest > 0.3f, "there should be some give and take: $nearest to $furthest")
    }

    /** Checkpoints and saved levels only work if level N of a seed is the same however you got to it. */
    @Test
    fun aLevelIsTheSameHoweverYouReachIt() {
        fun show(s: LevelSpec) = "${s.move} ${s.modifiers} ${s.roster.map { it.id }} ${s.boss?.id} ${s.quota}"
        for (seed in listOf(1L, 77L, 123456789L)) {
            val walked = RunState(seed)
            for (n in 1..25) {
                walked.level = n
                val spec = LevelGen.forRun(walked)
                walked.prevMove = spec.move
                val jumped = RunState(seed).also { it.level = n; it.startLevel = n }
                assertEquals(show(spec), show(LevelGen.forRun(jumped)), "seed $seed level $n")
                assertEquals(show(spec), show(LevelGen.forSeed(seed, n)))
            }
        }
    }

    @Test
    fun deepSpaceIsFourScreensBig() {
        val spec = LevelGen.build(5, MoveKind.GLIDE, listOf("deep"), listOf(Eater))
        assertEquals(LevelSpec.FIELD_W * 2, spec.width)
        assertEquals(LevelSpec.FIELD_H * 2, spec.height)
        val w = world(spec)
        // Still a torus: walk off the far edge and come back on the near one.
        w.enemies.clear()
        w.player.place(w.w - 0.1f, 5f)
        w.player.vx = 8f
        w.update(1f / 30f, InputState())
        assertTrue(w.player.x < 1f, "expected a wrap, got x=${w.player.x}")
    }

    @Test
    fun lightsOutLightsUpWhereYouEat() {
        val w = world(LevelGen.build(5, MoveKind.GRID, listOf("lightsout"), listOf(Eater)))
        w.enemies.clear()
        assertTrue(w.fogRadius > 0f)
        val before = w.flares.size
        val star = w.stars.first()
        w.player.place(star.x, star.y)
        w.update(1f / 60f, InputState())
        assertEquals(before + 1, w.flares.size)
        assertEquals(star.x, w.flares.last().x)
    }

    @Test
    fun bossesAreOutEatenNotStunned() {
        val spec = LevelGen.bossLevel(10, Random(1))
        val w = world(spec)
        val boss = w.boss!!
        assertTrue(boss.type.boss)
        boss.spawnT = 0f
        w.stun(boss, 3f)
        assertEquals(0f, boss.stun, "a boss must never be frozen solid")
        assertTrue(boss.slow > 0f)

        val input = InputState()
        var guard = 0
        while (w.phase == World.Phase.PLAYING && guard++ < 20_000) {
            // Park the boss far away and eat: we are testing the health bar, not the fight.
            boss.place(1f, 1f)
            w.enemies.retainAll { it === boss }
            w.stars.firstOrNull()?.let { w.player.place(it.x, it.y) }
            w.player.invuln = 1f
            w.update(0.016f, input)
        }
        assertEquals(World.Phase.CLEARED, w.phase)
        assertEquals(null, w.boss)
        assertTrue(w.enemies.none { it.type.boss })
    }

    private fun arcadeWorld(spec: LevelSpec, seed: Long = 1L): World {
        val run = RunState(seed).also { it.arcade = Arcade() }
        return World(spec, run.arcade!!.loadout, run, seed)
    }

    @Test
    fun arcadePowerUpsTurnUpAndGetCollected() {
        val w = arcadeWorld(LevelGen.build(3, MoveKind.GRID, emptyList(), listOf(Eater)))
        val input = InputState()
        input.taps += io.github.allwise.stareater.sim.Dir.RIGHT // the clocks only run once you have moved
        var frames = 0
        while (w.capsules.isEmpty() && frames++ < 60 * 30) {
            w.enemies.clear()
            w.stars.clear()
            w.update(1f / 60f, input)
            input.clearEdges()
        }
        assertTrue(w.capsules.isNotEmpty(), "no power-up turned up in thirty seconds")
        val c = w.capsules.first()
        assertTrue(!w.isWallAt(c.x, c.y))
        w.player.place(c.x, c.y)
        w.enemies.clear()
        w.fx.clear()
        w.update(1f / 60f, input)
        assertTrue(w.arcade!!.active(c.kind), "${c.kind} was not granted")
        assertTrue(w.fx.any { it is io.github.allwise.stareater.sim.Fx.Powered })

        // A normal run never sees one.
        val plain = world(LevelGen.build(3, MoveKind.GRID, emptyList(), listOf(Eater)))
        repeat(60 * 30) { plain.enemies.clear(); plain.stars.clear(); plain.update(1f / 60f, input) }
        assertTrue(plain.capsules.isEmpty())
    }

    @Test
    fun arcadeSpeedStaysButTheShieldRunsOut() {
        val a = Arcade()
        a.grant(PowerUp.SPEED)
        a.grant(PowerUp.SPEED)
        a.grant(PowerUp.SHIELD)
        a.grant(PowerUp.MAGNET)
        assertEquals(1.16f, a.loadout.speed, 0.001f)
        assertEquals(1, a.loadout.shields)
        assertTrue(a.loadout.magnet > 0f)

        val expired = ArrayList<PowerUp>()
        var t = 0f
        while (t < 30f) { a.tick(0.1f) { expired += it }; t += 0.1f }
        assertEquals(setOf(PowerUp.SHIELD, PowerUp.MAGNET), expired.toSet())
        assertEquals(0, a.loadout.shields)
        assertEquals(0f, a.loadout.magnet)
        assertEquals(1.16f, a.loadout.speed, 0.001f, "permanent power-ups must not run out")

        // Permanent ones stop being offered once they are maxed.
        repeat(10) { a.grant(PowerUp.SPEED) }
        assertEquals(PowerUp.SPEED.max, a.stacks(PowerUp.SPEED))
        assertTrue(!a.wants(PowerUp.SPEED))
        assertTrue(a.wants(PowerUp.SHIELD))
    }

    @Test
    fun anArcadeShieldSoaksOneHitAndCarriesIntoTheNextLevel() {
        val spec = LevelGen.build(3, MoveKind.GRID, emptyList(), listOf(Eater))
        val run = RunState(1L).also { it.arcade = Arcade() }
        run.arcade!!.grant(PowerUp.SHIELD)
        run.arcade!!.grant(PowerUp.NOVA)
        val w = World(spec, run.arcade!!.loadout, run, 1L)
        assertEquals(1, w.player.shields, "a shield still running should come along to the next level")
        assertEquals(World.NOVA_COST, w.player.novaCharge, "so should a held nova")
        w.hitPlayer()
        assertEquals(World.Phase.PLAYING, w.phase)
        assertTrue(!run.arcade!!.active(PowerUp.SHIELD), "a popped shield is gone, whatever its clock said")
        w.player.invuln = 0f
        w.hitPlayer()
        assertEquals(World.Phase.DEAD, w.phase)
    }

    @Test
    fun edgeBlockWarnsThenShutsThenReopens() {
        val w = world(LevelGen.build(1, MoveKind.GRID, emptyList(), listOf(Eater)))
        w.enemies.clear()
        val warper = w.spawnEnemyAt(Warper, 15.5f, 2.5f, 0f)
        val input = InputState()
        fun stepLeftFromTheEdge(): Float {
            w.enemies.retainAll { it === warper }
            warper.place(15.5f, 2.5f)
            w.player.place(0.5f, 7.5f)
            w.player.stepCd = 0f
            w.player.queue.clear()
            input.taps += io.github.allwise.stareater.sim.Dir.LEFT
            w.update(0.016f, input)
            input.clearEdges()
            return w.player.x
        }

        w.sealEdges(1f, 2f)
        assertTrue(w.sealing && !w.sealed)
        assertTrue(w.fx.any { it is io.github.allwise.stareater.sim.Fx.EdgeWarn })
        assertEquals(29.5f, stepLeftFromTheEdge(), "the wrap must still work during the warning")

        var guard = 0
        while (!w.sealed && guard++ < 200) w.update(0.016f, input)
        assertTrue(w.sealed)
        assertTrue(w.fx.any { it is io.github.allwise.stareater.sim.Fx.EdgeSeal })
        assertEquals(0.5f, stepLeftFromTheEdge(), "a shut edge must stop the player")
        assertTrue(!warper.wraps, "and it must stop the ones that know the trick")

        guard = 0
        while (w.sealing && guard++ < 400) { w.enemies.retainAll { it === warper }; warper.place(15.5f, 2.5f); w.update(0.016f, input) }
        assertTrue(!w.sealing)
        assertTrue(w.fx.any { it is io.github.allwise.stareater.sim.Fx.EdgeOpen })
        assertEquals(29.5f, stepLeftFromTheEdge(), "and the wrap must come back afterwards")
        assertTrue(warper.wraps)
    }

    @Test
    fun theWardenLocksTheEdgesWithFairWarning() {
        val spec = LevelGen.bossLevel(30, Random(3), Warden, MoveKind.GLIDE)
        val w = world(spec)
        val boss = w.boss!!
        val input = InputState()
        var warned = -1f
        var shut = -1f
        var frames = 0
        while (shut < 0f && frames++ < 60 * 40) {
            boss.place(2f, 2f)
            w.enemies.retainAll { it === boss }
            w.meteors.clear()
            w.stars.clear()
            w.player.place(20f, 10f)
            w.player.invuln = 1f
            w.update(1f / 60f, input)
            if (warned < 0f && w.sealing) warned = w.time
            if (w.sealed) shut = w.time
        }
        assertTrue(warned > 0f, "the Warden never tried to lock the edges")
        assertTrue(shut - warned >= 2f, "only ${shut - warned}s of warning before the edges shut")
    }
}
