package io.github.allwise.stareater.ui

import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Align
import io.github.allwise.stareater.StarEaterGame
import io.github.allwise.stareater.gfx.Gfx
import io.github.allwise.stareater.gfx.Pal
import io.github.allwise.stareater.sim.RunState

/** End of a run: bank the stardust, and take three initials if the score made the board. */
class GameOverScreen(game: StarEaterGame, private val run: RunState) : Screen(game) {
    private val dust = run.dust.toInt()
    private val mastered = game.profile.mastered
    private var rank = 0
    private var entering = false
    private val name = CharArray(3)
    private var slot = 0

    override fun show() {
        val p = game.profile
        rank = p.rankFor(run.score)
        p.finishRun(dust, run.level)
        entering = rank > 0
        p.initials.toCharArray().copyInto(name)

        val y = 40f
        button(Gfx.W / 2f - 330f, y, 210f, 44f, "PLAY AGAIN") { game.go(IntroScreen(game, RunState(System.nanoTime()))) }
        button(Gfx.W / 2f - 105f, y, 210f, 44f, "SKILL TREE") { game.go(SkillScreen(game)) }
        button(Gfx.W / 2f + 120f, y, 210f, 44f, "MENU") { game.go(MenuScreen(game)) }
        button(Gfx.W / 2f + 130f, SLOT_Y - 6f, 110f, 44f, "OK") { confirm() }.accent = Pal.GOLD
        refresh()
    }

    private fun refresh() {
        for ((i, b) in buttons.withIndex()) b.visible = if (i == 3) entering else !entering
        focus = if (entering) 3 else 0
    }

    private fun confirm() {
        if (!entering) return
        game.profile.addScore(String(name), run.score, run.level, mastered, System.currentTimeMillis())
        entering = false
        refresh()
    }

    override fun back() {
        if (entering) confirm() else game.go(MenuScreen(game))
    }

    private fun spin(i: Int, by: Int) {
        slot = i
        val n = (name[i] - 'A' + by + 26 * 4) % 26
        name[i] = 'A' + n
    }

    override fun render(dt: Float) {
        age += dt
        g.rect(0f, 0f, Gfx.W, Gfx.H, Pal.BG)
        val cx = Gfx.W / 2f
        g.glow(cx, 470f, 360f, Pal.RED, 0.14f)
        g.text(g.huge, "GAME OVER", cx, 512f, Pal.RED, 1f, Align.center)

        g.text(g.big, run.score.toString(), cx, 408f, Pal.TEXT, 1f, Align.center)
        g.text(g.tiny, "SCORE", cx, 370f, Pal.DIM, 1f, Align.center)

        stat(cx - 240f, "LEVEL", run.level.toString())
        stat(cx - 80f, "STARS", run.starsEaten.toString())
        stat(cx + 80f, "BEST COMBO", "x${run.bestCombo}")
        stat(cx + 240f, "STARDUST", "+$dust", Pal.STAR)

        if (rank > 0) {
            val pulse = 0.7f + 0.3f * MathUtils.sin(age * 6f)
            val board = if (mastered) "  -  MASTERED BOARD TOO" else ""
            g.text(g.body, "HIGH SCORE!  RANK #$rank$board", cx, 262f, Pal.GOLD, pulse, Align.center)
        } else {
            g.text(g.small, "${game.profile.stardust} stardust banked. Go spend it.", cx, 256f, Pal.DIM, 1f, Align.center)
        }

        if (entering) {
            g.text(g.tiny, if (game.keyHints) "TYPE YOUR INITIALS, THEN ENTER" else "SET YOUR INITIALS", cx, 232f, Pal.DIM, 1f, Align.center)
            for (i in 0 until 3) {
                val x = slotX(i)
                val on = i == slot
                g.rect(x - SLOT_W / 2f, SLOT_Y, SLOT_W, SLOT_H, if (on) Pal.PANEL_HI else Pal.PANEL)
                g.frame(x - SLOT_W / 2f, SLOT_Y, SLOT_W, SLOT_H, 2f, if (on) Pal.GOLD else Pal.LINE)
                g.glyphAt(g.big, name[i], x, SLOT_Y + SLOT_H / 2f, Pal.TEXT)
                val c = if (on) Pal.GOLD else Pal.DIM
                g.triangle(x - 9f, SLOT_Y + SLOT_H + 6f, x + 9f, SLOT_Y + SLOT_H + 6f, x, SLOT_Y + SLOT_H + 17f, c)
                g.triangle(x - 9f, SLOT_Y - 6f, x + 9f, SLOT_Y - 6f, x, SLOT_Y - 17f, c)
            }
        }
        drawButtons()
    }

    private fun stat(x: Float, label: String, value: String, c: com.badlogic.gdx.graphics.Color = Pal.TEXT) {
        g.text(g.body, value, x, 330f, c, 1f, Align.center)
        g.text(g.tiny, label, x, 302f, Pal.DIM, 1f, Align.center)
    }

    private fun slotX(i: Int): Float = Gfx.W / 2f - 60f + (i - 1) * (SLOT_W + 12f)

    override fun keyDown(key: Int): Boolean {
        if (!entering) return super.keyDown(key)
        when (key) {
            Keys.LEFT -> slot = (slot + 2) % 3
            Keys.RIGHT -> slot = (slot + 1) % 3
            Keys.UP -> spin(slot, 1)
            Keys.DOWN -> spin(slot, -1)
            Keys.BACKSPACE -> slot = (slot + 2) % 3
            Keys.ENTER, Keys.NUMPAD_ENTER, Keys.ESCAPE, Keys.BACK -> confirm()
            else -> return false
        }
        return true
    }

    override fun keyTyped(c: Char) {
        if (!entering || !c.isLetter()) return
        val up = c.uppercaseChar()
        if (up !in 'A'..'Z') return
        name[slot] = up
        slot = (slot + 1).coerceAtMost(2)
    }

    override fun touchDown(x: Float, y: Float, pointer: Int) {
        if (entering) {
            for (i in 0 until 3) {
                val sx = slotX(i)
                if (x < sx - SLOT_W / 2f - 6f || x > sx + SLOT_W / 2f + 6f) continue
                when {
                    y > SLOT_Y + SLOT_H / 2f && y < SLOT_Y + SLOT_H + 30f -> spin(i, 1)
                    y <= SLOT_Y + SLOT_H / 2f && y > SLOT_Y - 30f -> spin(i, -1)
                }
            }
        }
        super.touchDown(x, y, pointer)
    }

    private companion object {
        const val SLOT_W = 48f
        const val SLOT_H = 52f
        const val SLOT_Y = 150f
    }
}
