package io.github.allwise.stareater

import io.github.allwise.stareater.meta.MemoryStore
import io.github.allwise.stareater.meta.Profile
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
}
