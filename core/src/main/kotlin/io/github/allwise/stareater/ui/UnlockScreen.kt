package io.github.allwise.stareater.ui

import com.badlogic.gdx.utils.Align
import io.github.allwise.stareater.StarEaterGame
import io.github.allwise.stareater.audio.Sfx
import io.github.allwise.stareater.audio.Tune
import io.github.allwise.stareater.gfx.Gfx
import io.github.allwise.stareater.gfx.Pal
import io.github.allwise.stareater.input.Buzz
import io.github.allwise.stareater.sim.RunState

/**
 * What the full game adds, and the button that buys it. Reached from the MORE page, or at the end of
 * the demo with [run] - the run that just beat the first boss, already pointing at level 11 - in which
 * case buying carries straight on with it and NOT NOW ends it there.
 */
class UnlockScreen(game: StarEaterGame, private val run: RunState? = null) : Screen(game) {
    private lateinit var buy: Button
    private lateinit var restore: Button
    private lateinit var leave: Button
    private lateinit var carryOn: Button
    private var celebrated = false

    override fun show() {
        game.audio.tune(Tune.MENU)
        val cx = Gfx.W / 2f
        buy = button(cx - 170f, 96f, 340f, 48f, "UNLOCK") { game.buyFullGame() }.also { it.accent = Pal.GOLD }
        restore = button(cx - 170f, 46f, 160f, 36f, "RESTORE") { game.restoreFullGame() }.also { it.small = true }
        leave = button(cx + 10f, 46f, 160f, 36f, if (run != null) "NOT NOW" else "BACK") { back() }.also { it.small = true }
        carryOn = button(cx - 170f, 96f, 340f, 48f, if (run != null) "ON TO LEVEL ${run.level}" else "BACK TO THE MENU") { onward() }
        carryOn.accent = Pal.GOLD
        carryOn.visible = false
    }

    override fun back() {
        if (game.profile.demo && run != null) {
            // The run stops where the demo does: at the first boss, beaten.
            run.level--
            run.demoEnd = true
            game.go(GameOverScreen(game, run))
        } else onward()
    }

    private fun onward() {
        if (run != null && !game.profile.demo) game.go(IntroScreen(game, run))
        else game.go(MenuScreen(game, more = run == null))
    }

    override fun render(dt: Float) {
        age += dt
        val p = game.profile
        val owned = !p.demo
        if (owned && !celebrated) {
            celebrated = true
            game.audio.play(Sfx.HIGH_SCORE)
            game.buzz(Buzz.BOSS_DEAD)
        }
        buy.visible = !owned
        restore.visible = !owned
        leave.visible = !owned
        carryOn.visible = owned
        buy.label = game.storePrice?.let { "UNLOCK  -  $it" } ?: "UNLOCK"

        g.rect(0f, 0f, Gfx.W, Gfx.H, Pal.BG)
        val cx = Gfx.W / 2f
        g.glow(cx, 470f, 380f, if (owned) Pal.GOLD else Pal.CYAN, 0.14f)
        when {
            owned -> {
                g.text(g.huge, "UNLOCKED", cx, 510f, Pal.GOLD, 1f, Align.center)
                g.text(g.body, "Thank you! The whole machine is yours.", cx, 432f, Pal.TEXT, 1f, Align.center)
            }
            run != null -> {
                g.text(g.big, "THAT WAS THE DEMO", cx, 512f, Pal.TEXT, 1f, Align.center)
                g.text(g.small, "The first boss is down. There is a lot more where that came from.", cx, 470f, Pal.CYAN, 1f, Align.center)
            }
            else -> {
                g.text(g.big, "THE FULL GAME", cx, 512f, Pal.TEXT, 1f, Align.center)
                g.text(g.small, "The demo is the first ten levels and Arcade Matrix. This is the rest.", cx, 470f, Pal.CYAN, 1f, Align.center)
            }
        }

        // What you get, one line each.
        val rows = listOf(
            Triple(Pal.STAR, "LEVELS 11 TO 30", "Twenty more levels, every twist in the machine, and two more bosses."),
            Triple(Pal.CYAN, "ARCADE FLOW", "Glide, scroll and orbit, with pick-ups and three pockets of tricks."),
            Triple(Pal.PURPLE, "ENDLESS", "One level that never ends, and never stops getting worse."),
            Triple(Pal.GOLD, "ONE PURCHASE", "No ads, no timers, and nothing else to buy."),
        )
        for ((i, r) in rows.withIndex()) {
            val y = 398f - i * 56f
            g.star(cx - 300f, y - 8f, 9f, 4f, 5, 90f + age * 30f + i * 20f, r.first, if (owned) 1f else 0.85f)
            g.text(g.body, r.second, cx - 278f, y, r.first)
            g.text(g.tiny, r.third, cx - 278f, y - 24f, Pal.DIM)
        }

        drawButtons()
        game.storeNote?.let { if (!owned) g.text(g.tiny, it, cx, 160f, Pal.ORANGE, 1f, Align.center) }
    }
}
