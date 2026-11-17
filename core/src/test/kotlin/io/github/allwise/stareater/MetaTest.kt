package io.github.allwise.stareater

import io.github.allwise.stareater.meta.MemoryStore
import io.github.allwise.stareater.meta.Profile
import io.github.allwise.stareater.meta.SeedKey
import io.github.allwise.stareater.sim.LevelGen
import io.github.allwise.stareater.meta.Skill
import io.github.allwise.stareater.sim.GameMode
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
        assertTrue(l.overdrive && l.overdriveBonus > 0f && l.overdriveCdMul < 1f && l.nova && l.echo && l.secondWind && l.shieldRegen)
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
        assertEquals(listOf("ZED", "KIM", "BOT"), b.topScores().map { it.name })
        assertEquals(listOf("ZED", "BOT"), b.topScores(masteredOnly = true).map { it.name })
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
    fun everyModeHasABoardOfItsOwn() {
        val p = Profile(MemoryStore())
        p.addScore("AAA", 5000, 8, false, 1L, "STAR22")
        p.addScore("MAT", 9000, 9, false, 2L, "STAR22", GameMode.MATRIX, pure = true)
        p.addScore("FLO", 7000, 9, false, 3L, "STAR22", GameMode.FLOW)
        p.addScore("END", 99000, 31, false, 4L, "", GameMode.ENDLESS)
        p.addScore("BBB", 2000, 4, true, 5L)
        assertEquals(listOf("AAA", "BBB"), p.topScores().map { it.name })
        assertEquals(listOf("BBB"), p.topScores(masteredOnly = true).map { it.name })
        assertEquals(listOf("AAA"), p.seedScores("STAR22").map { it.name }, "the seed board is the campaign's")
        assertEquals(listOf("MAT"), p.topScores(GameMode.MATRIX).map { it.name })
        assertEquals(listOf("FLO"), p.topScores(GameMode.FLOW).map { it.name })
        assertEquals(listOf("END"), p.topScores(GameMode.ENDLESS).map { it.name })
        // A score is ranked against its own mode only.
        assertEquals(1, p.rankFor(6000))
        assertEquals(2, p.rankFor(6000, GameMode.MATRIX))
        assertEquals(1, p.rankFor(9500, GameMode.MATRIX))
        assertEquals(99000, p.bestScore(GameMode.ENDLESS))
        assertEquals(0, Profile(MemoryStore()).bestScore(GameMode.FLOW))

        val reloaded = Profile(MemoryStore().also { p.copyInto(it) })
        assertEquals(listOf("MAT"), reloaded.topScores(GameMode.MATRIX).map { it.name })
        assertTrue(reloaded.topScores(GameMode.MATRIX)[0].pure, "the pure mark must survive a reload")
        assertFalse(reloaded.topScores(GameMode.FLOW)[0].pure)
        assertEquals(listOf("AAA", "BBB"), reloaded.topScores().map { it.name })

        // Scores from before the arcade was split in two are kept, on the Flow board.
        val old = MemoryStore().also { it.put("scores", "OLD|4000|6|0|1|STAR22|1\nCAM|3000|5|1|2|STAR22|0") }
        assertEquals(listOf("OLD"), Profile(old).topScores(GameMode.FLOW).map { it.name })
        assertEquals(listOf("CAM"), Profile(old).topScores().map { it.name })

        val run = SeedKey.newRun("STAR22", GameMode.MATRIX, level = 11)
        assertTrue(run.arcade != null)
        assertEquals(1, run.level, "only the campaign has checkpoints")
        assertTrue(SeedKey.newRun("STAR22").arcade == null)
    }

    @Test
    fun theModesOpenOneBossAtATime() {
        val p = Profile(MemoryStore())
        assertTrue(p.unlocked(GameMode.CAMPAIGN))
        assertFalse(p.unlocked(GameMode.MATRIX) || p.unlocked(GameMode.FLOW) || p.unlocked(GameMode.ENDLESS))
        p.beatBoss(10)
        assertTrue(p.unlocked(GameMode.MATRIX) && !p.unlocked(GameMode.FLOW))
        p.beatBoss(20)
        assertTrue(p.unlocked(GameMode.FLOW) && !p.unlocked(GameMode.ENDLESS))
        p.beatBoss(30)
        assertTrue(p.unlocked(GameMode.ENDLESS))
        assertEquals(listOf(1, 11, 21), p.startLevels(), "there is no starting after the last boss: that is the end")

        // A save from before bosses were recorded still gets what it earned: level 14 means boss 1 fell.
        val old = MemoryStore().also { it.put("bestLevel", "14") }
        assertTrue(Profile(old).unlocked(GameMode.MATRIX) && !Profile(old).unlocked(GameMode.FLOW))

        p.grantDust(100)
        assertFalse(p.spend(101))
        assertTrue(p.spend(40))
        assertEquals(60, p.stardust)
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

    @Test
    fun beatingABossOpensAStartOnTheNextLevel() {
        val store = MemoryStore()
        val p = Profile(store)
        assertEquals(listOf(1), p.startLevels())
        p.startLevel = 11
        assertEquals(1, p.startLevel, "cannot start past a boss that has not been beaten")

        assertEquals(0, p.beatBoss(7), "only bosses count")
        assertEquals(11, p.beatBoss(10))
        assertEquals(0, p.beatBoss(10), "the same boss does not open anything twice")
        assertEquals(21, p.beatBoss(20))
        assertEquals(listOf(1, 11, 21), p.startLevels())
        p.startLevel = 21
        p.save()

        val again = Profile(store)
        assertEquals(20, again.checkpoint)
        assertEquals(21, again.startLevel)
        val run = SeedKey.newRun("STAR22", level = again.startLevel)
        assertEquals(21, run.level)
        assertEquals(21, run.startLevel)
        assertEquals(1, SeedKey.newRun("STAR22").level)
    }

    @Test
    fun fiveSavedSeedsSurviveAReload() {
        val store = MemoryStore()
        val p = Profile(store)
        assertEquals(5, p.saved.size)
        assertTrue(p.saved.all { it == null })
        p.store(0, "STAR22")
        p.store(3, "MQQN99")
        p.store(0, "ABCDEF") // storing again replaces
        assertEquals(listOf("ABCDEF", null, null, "MQQN99", null), Profile(store).saved.toList())

        // A save written back when slots also remembered a level still loads, as just its key.
        store.put("savedLevels", "STAR22:7:,,MQQN99:14:A,,")
        assertEquals(listOf("STAR22", null, "MQQN99", null, null), Profile(store).saved.toList())
    }

    /** The daily run: always from level 1, and the profile remembers which day it was last started. */
    @Test
    fun theDailyRunStartsAtLevelOneAndIsRememberedForTheDay() {
        val p = Profile(MemoryStore())
        p.beatBoss(10)
        p.startLevel = 11
        assertEquals(11, p.startLevel)
        val noon = 20_000L * 86_400_000L + 43_200_000L
        val run = SeedKey.dailyRun(noon)
        assertEquals(1, run.level, "a daily run never starts from a checkpoint")
        assertEquals(SeedKey.dailyNow(noon), run.key)
        assertEquals(20_000L, SeedKey.today(noon))

        val today = SeedKey.today(noon)
        assertTrue(p.dailyWaiting(today))
        p.startedDaily(today)
        assertFalse(p.dailyWaiting(today))
        assertTrue(p.dailyWaiting(today + 1), "and tomorrow there is a new one")
        val reloaded = Profile(MemoryStore().also { p.copyInto(it) })
        assertFalse(reloaded.dailyWaiting(today), "it survives a reload")
        // A save from before the daily was tracked has simply never played one.
        assertTrue(Profile(MemoryStore().also { it.put("stardust", "50") }).dailyWaiting(today))
    }

    /** Screen shake is off until asked for, and remembered once it is. */
    @Test
    fun screenShakeIsOffByDefault() {
        val p = Profile(MemoryStore())
        assertFalse(p.shake)
        assertTrue(p.haptics, "vibration, on the other hand, is on")
        p.shake = true
        assertTrue(Profile(MemoryStore().also { p.copyInto(it) }).shake)
    }
}
