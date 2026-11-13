package io.github.allwise.stareater

import io.github.allwise.stareater.meta.MemoryStore
import io.github.allwise.stareater.meta.Profile
import io.github.allwise.stareater.meta.SeedKey
import io.github.allwise.stareater.sim.LevelGen
import io.github.allwise.stareater.meta.Skill
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MetaTest {
    @Test
    fun skillsNeedDustAndPrerequisites() {
        val p = Profile(MemoryStore())
        assertFalse(p.buy(Skill.AEGIS_1), "cannot buy with no stardust")
        p.grantDust(10_000)
        assertFalse(p.buy(Skill.AEGIS_2), "needs AEGIS I first")
        assertTrue(p.buy(Skill.AEGIS_1))
        assertTrue(p.buy(Skill.AEGIS_2))
        assertFalse(p.buy(Skill.AEGIS_2), "cannot buy twice")
        assertEquals(10_000 - Skill.AEGIS_1.cost - Skill.AEGIS_2.cost, p.stardust)
        assertEquals(2, p.loadout().shields)
    }

    @Test
    fun everySkillIsReachableAndBuyingAllMeansMastered() {
        val p = Profile(MemoryStore())
        p.grantDust(Skill.totalCost)
        var progress = true
        while (progress) {
            progress = false
            for (s in Skill.values()) if (p.buy(s)) progress = true
        }
        assertTrue(p.mastered)
        assertEquals(0, p.stardust)
        val l = p.loadout()
        assertTrue(l.dash && l.dashStun && l.nova && l.echo && l.secondWind && l.shieldRegen)
        assertEquals(3.8f, l.magnet)
        assertEquals(1.24f, l.speed, 0.001f)
    }

    @Test
    fun profileSurvivesAReload() {
        val store = MemoryStore()
        val a = Profile(store)
        a.grantDust(500)
        a.buy(Skill.THRUST_1)
        a.finishRun(dust = 42, level = 7)
        a.addScore("KIM", 1200, 7, mastered = false, nowMs = 1L)
        a.addScore("BOT", 900, 5, mastered = true, nowMs = 2L)
        a.addScore("ZED", 3000, 9, mastered = true, nowMs = 3L)

        val b = Profile(store)
        assertEquals(500 - Skill.THRUST_1.cost + 42, b.stardust)
        assertEquals(setOf(Skill.THRUST_1), b.owned)
        assertEquals(7, b.bestLevel)
        assertEquals(1, b.totalRuns)
        assertEquals("ZED", b.initials)
        assertEquals(listOf("ZED", "KIM", "BOT"), b.topScores(false).map { it.name })
        assertEquals(listOf("ZED", "BOT"), b.topScores(true).map { it.name })
        assertEquals(2, b.rankFor(2000))
        assertEquals(0, b.rankFor(0))
    }

    @Test
    fun seedKeysAreReadableAndStable() {
        repeat(200) {
            val key = SeedKey.random(kotlin.random.Random(it))
            assertTrue(SeedKey.isValid(key), key)
            assertEquals(key, SeedKey.normalise(key.lowercase()))
        }
        assertEquals("ABCDEF", SeedKey.normalise("abc-def"))
        assertEquals("LQLQAB", SeedKey.normalise("1 0 I O a b"), "look-alikes map onto real symbols")
        assertEquals(null, SeedKey.normalise("ABC"))
        assertEquals("ABC-DEF", SeedKey.pretty("ABCDEF"))
        assertEquals(SeedKey.daily(20_000), SeedKey.daily(20_000))
        assertTrue(SeedKey.daily(20_000) != SeedKey.daily(20_001))
    }

    /** The whole point of a key: the same key always deals the same run. */
    @Test
    fun sameKeySameRun() {
        fun deal(key: String): List<String> {
            val run = SeedKey.newRun(key)
            return (1..25).map { n ->
                run.level = n
                val spec = LevelGen.forRun(run)
                run.prevMove = spec.move
                "${spec.move} ${spec.modifiers} ${spec.roster.map { it.id }} ${spec.boss?.id}"
            }
        }
        assertEquals(deal("STAR22"), deal("STAR22"))
        assertTrue(deal("STAR22") != deal("STAR23"))
    }

    @Test
    fun seedBoardsOnlyShowTheirOwnSeed() {
        val p = Profile(MemoryStore())
        p.addScore("AAA", 500, 3, false, 1L, "STAR22")
        p.addScore("BBB", 900, 5, true, 2L, "STAR22")
        p.addScore("CCC", 9000, 9, false, 3L, "MQQN99")
        assertEquals(listOf("BBB", "AAA"), p.seedScores("STAR22").map { it.name })
        assertEquals(2, p.seedRankFor("STAR22", 700))
        assertEquals(1, p.seedRankFor("NEWKEY", 1))
        val reloaded = Profile(p.let { MemoryStore() }.also { store -> p.copyInto(store) })
        assertEquals(listOf("BBB", "AAA"), reloaded.seedScores("STAR22").map { it.name })
    }

    @Test
    fun arcadeRunsHaveABoardOfTheirOwn() {
        val p = Profile(MemoryStore())
        p.addScore("AAA", 5000, 8, false, 1L, "STAR22")
        p.addScore("ARC", 9000, 9, false, 2L, "STAR22", arcade = true)
        p.addScore("BBB", 2000, 4, true, 3L)
        assertEquals(listOf("AAA", "BBB"), p.topScores(false).map { it.name })
        assertEquals(listOf("BBB"), p.topScores(true).map { it.name })
        assertEquals(listOf("AAA"), p.seedScores("STAR22").map { it.name })
        assertEquals(listOf("ARC"), p.arcadeScores().map { it.name })
        // An arcade score is ranked against arcade scores only, and the other way round.
        assertEquals(1, p.rankFor(6000))
        assertEquals(2, p.rankFor(6000, arcade = true))
        assertEquals(1, p.rankFor(9500, arcade = true))

        val reloaded = Profile(MemoryStore().also { p.copyInto(it) })
        assertEquals(listOf("ARC"), reloaded.arcadeScores().map { it.name })
        assertEquals(listOf("AAA", "BBB"), reloaded.topScores(false).map { it.name })
        assertTrue(SeedKey.newRun("STAR22", arcade = true).arcade != null)
        assertTrue(SeedKey.newRun("STAR22").arcade == null)
    }

    @Test
    fun settingsSurviveAReload() {
        val store = MemoryStore()
        val a = Profile(store)
        assertEquals(Profile.VOLUME_DEFAULT, a.musicVol)
        assertEquals(0, a.screenAlign)
        assertFalse(a.tilt)
        a.musicVol = 3
        a.sfxVol = 99 // clamped
        a.screenAlign = -1
        a.tilt = true
        a.tiltSens = 2
        a.haptics = false
        a.save()

        val b = Profile(store)
        assertEquals(3, b.musicVol)
        assertEquals(Profile.VOLUME_MAX, b.sfxVol)
        assertEquals(-1, b.screenAlign)
        assertTrue(b.tilt)
        assertEquals(2, b.tiltSens)
        assertFalse(b.haptics)
    }
}
