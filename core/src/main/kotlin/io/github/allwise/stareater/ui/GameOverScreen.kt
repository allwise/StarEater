package io.github.allwise.stareater.ui

import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Align
import io.github.allwise.stareater.StarEaterGame
import io.github.allwise.stareater.audio.Sfx
import io.github.allwise.stareater.gfx.Gfx
import io.github.allwise.stareater.gfx.Pal
import io.github.allwise.stareater.gfx.Sparkles
import io.github.allwise.stareater.gfx.WorldRenderer
import io.github.allwise.stareater.meta.SeedKey
import io.github.allwise.stareater.sim.RunState

/** End of a run: bank the stardust, and take three initials if the score made a board. */
class GameOverScreen(game: StarEaterGame, private val run: RunState) : Screen(game) {
    private val dust = run.dust.toInt()
    private val mastered = game.profile.mastered
    private val sparkles = Sparkles(game.gfx)
    private var rank = 0
    private var seedRank = 0
    private var entering = false
    private val name = CharArray(3)
    private var slot = 0

    override val typing: Boolean get() = entering

    override fun show() {
        val p = game.profile
        rank = p.rankFor(run.score)
        seedRank = p.seedRankFor(run.key, run.score)
        p.finishRun(dust, run.level)
        if (run.key.isNotEmpty()) p.lastSeed = run.key
        // A score is worth initials if it makes the open board or the board for its own seed.
        entering = rank > 0 || seedRank > 0
        p.initials.toCharArray().copyInto(name)
        game.audio.tune(null)
        game.audio.play(if (rank in 1..3 || seedRank == 1) Sfx.HIGH_SCORE else Sfx.GAME_OVER)

        val y = 34f
        val w = 196f
        val x0 = Gfx.W / 2f - (w * 4 + 12f * 3) / 2f
        button(x0, y, w, 44f, "NEW RUN") { game.go(IntroScreen(game, SeedKey.newRun(SeedKey.random()))) }
        button(x0 + (w + 12f), y, w, 44f, "SAME SEED") { game.go(IntroScreen(game, SeedKey.newRun(run.key))) }.enabled = SeedKey.isValid(run.key)
        button(x0 + (w + 12f) * 2, y, w, 44f, "SKILL TREE") { game.go(SkillScreen(game)) }
        button(x0 + (w + 12f) * 3, y, w, 44f, "MENU") { game.go(MenuScreen(game)) }
        button(Gfx.W / 2f + 130f, SLOT_Y + 4f, 110f, 44f, "OK") { confirm() }.accent = Pal.GOLD
        refresh()
    }

    private fun refresh() {
        for ((i, b) in buttons.withIndex()) b.visible = if (i == 4) entering else !entering
        focus = if (entering) 4 else 0
    }

    private fun confirm() {
        if (!entering) return
        game.profile.addScore(String(name), run.score, run.level, mastered, System.currentTimeMillis(), run.key)
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
        game.audio.play(Sfx.UI_MOVE)
    }

    override fun render(dt: Float) {
        age += dt
        val party = rank > 0 || seedRank == 1
        g.rect(0f, 0f, Gfx.W, Gfx.H, Pal.BG)
        val cx = Gfx.W / 2f
        g.glow(cx, 470f, 360f, if (party) Pal.GOLD else Pal.RED, 0.14f)
        if (party) sparkles.celebrate(dt)
        sparkles.draw(dt)
        g.text(g.huge, "GAME OVER", cx, 518f, if (party) rainbow(age * 0.6f) else Pal.RED, 1f, Align.center)

        // The score counts up, because of course it does.
        val shown = (run.score * (age / 0.8f).coerceAtMost(1f)).toInt()
        g.text(g.big, shown.toString(), cx, 418f, Pal.TEXT, 1f, Align.center)
        g.text(g.tiny, "SCORE", cx, 382f, Pal.DIM, 1f, Align.center)

        stat(cx - 300f, "LEVEL", run.level.toString())
        stat(cx - 150f, "STARS", run.starsEaten.toString())
        stat(cx, "BEST COMBO", "x${run.bestCombo}")
        stat(cx + 150f, "STARDUST", "+$dust", Pal.STAR)
        stat(cx + 300f, "SEED", if (run.key.isEmpty()) "-" else SeedKey.pretty(run.key), Pal.CYAN)

        val pulse = 0.75f + 0.25f * MathUtils.sin(age * 6f)
        when {
            rank > 0 -> {
                val extra = (if (mastered) "  -  MASTERED" else "") + (if (seedRank > 0) "  -  #$seedRank ON THIS SEED" else "")
                g.text(g.body, "HIGH SCORE!  RANK #$rank$extra", cx, 276f, rainbow(age * 0.9f), pulse, Align.center)
            }
            seedRank > 0 -> g.text(g.body, "#$seedRank ON SEED ${SeedKey.pretty(run.key)}", cx, 276f, Pal.CYAN, pulse, Align.center)
            else -> g.text(g.small, "${game.profile.stardust} stardust banked. Go spend it.", cx, 270f, Pal.DIM, 1f, Align.center)
        }

        if (entering) {
            g.text(g.tiny, if (game.keyHints) "TYPE YOUR INITIALS, THEN ENTER" else "SET YOUR INITIALS", cx, 246f, Pal.DIM, 1f, Align.center)
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

    /** Cycles smoothly through the party palette. */
    private fun rainbow(t: Float): Color {
        val colors = WorldRenderer.PARTY
        val f = ((t % 1f) + 1f) % 1f * colors.size
        val i = f.toInt() % colors.size
        return g.mix(colors[i], colors[(i + 1) % colors.size], f - f.toInt())
    }

    private fun stat(x: Float, label: String, value: String, c: Color = Pal.TEXT) {
        g.text(g.body, value, x, 340f, c, 1f, Align.center)
        g.text(g.tiny, label, x, 312f, Pal.DIM, 1f, Align.center)
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
            Keys.ENTER, Keys.NUMPAD_ENTER, Keys.ESCAPE, Keys.BACK -> { game.audio.play(Sfx.UI_OK); confirm() }
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
