package io.github.allwise.stareater.ui

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Align
import io.github.allwise.stareater.StarEaterGame
import io.github.allwise.stareater.gfx.Gfx
import io.github.allwise.stareater.gfx.Pal
import io.github.allwise.stareater.gfx.WorldRenderer
import io.github.allwise.stareater.sim.LevelGen
import io.github.allwise.stareater.sim.LevelSpec
import io.github.allwise.stareater.sim.Rules
import io.github.allwise.stareater.sim.RunState
import kotlin.random.Random

/** The briefing before each level: which rules got rolled, who is hunting you, how many stars. */
class IntroScreen(game: StarEaterGame, private val run: RunState, preset: LevelSpec? = null) : Screen(game) {
    private val spec = preset ?: roll(run)

    override fun show() {
        button(Gfx.W / 2f - 150f, 34f, 300f, 46f, if (run.level == 1) "START" else "NEXT LEVEL") { start() }
    }

    private fun start() = game.go(PlayScreen(game, run, spec))

    /** Backing out of a briefing ends the run (and still banks what you earned). */
    override fun back() = game.go(GameOverScreen(game, run))

    override fun render(dt: Float) {
        age += dt
        if (game.devAuto && age > 0.4f) { start(); return }
        g.rect(0f, 0f, Gfx.W, Gfx.H, Pal.BG)
        val cx = Gfx.W / 2f
        g.glow(cx, 520f, 420f, Pal.CYAN, 0.12f)
        g.text(g.big, "LEVEL ${spec.number}", cx, 528f, Pal.TEXT, 1f, Align.center)
        g.star(cx - 78f, 483f, 8f, 3.4f, 5, 90f, Pal.STAR)
        g.text(g.small, "EAT ${spec.quota} STARS", cx - 62f, 489f, Pal.STAR)

        // Rules: the movement mode up top, modifiers in a grid under it.
        val left = 80f
        val full = Gfx.W - 2 * left
        card(left, 406f, full, 58f, spec.move.title, spec.move.blurb, true, 0)
        val half = (full - 16f) / 2f
        for ((i, id) in spec.modifiers.withIndex()) {
            val info = Rules.byId(id)
            val x = left + (i % 2) * (half + 16f)
            val y = 342f - (i / 2) * 64f
            card(x, y, half, 56f, info.title, info.blurb, false, i + 1)
        }
        if (spec.modifiers.isEmpty()) {
            g.text(g.small, "No twists. Just you, the stars, and things with teeth.", cx, 368f, Pal.DIM, 1f, Align.center)
        }

        // Who is out there.
        val rosterTop = if (spec.modifiers.size > 2) 262f else if (spec.modifiers.isEmpty()) 318f else 322f
        g.text(g.tiny, "HOSTILES", left, rosterTop, Pal.DIM)
        for ((i, t) in spec.roster.withIndex()) {
            val x = left + (i % 2) * (half + 16f)
            val y = rosterTop - 28f - (i / 2) * 38f
            val c = WorldRenderer.enemyColor(t)
            g.glow(x + 12f, y - 2f, 22f, c, 0.6f)
            g.glyphAt(g.glyph, t.glyph, x + 12f, y - 2f, c)
            g.text(g.small, t.title, x + 34f, y + 8f, c)
            g.text(g.tiny, t.blurb, x + 34f, y - 8f, Pal.DIM)
        }

        drawButtons()
        val hint = if (game.keyHints) "ENTER to go   -   ESC ends the run" else "tap to go"
        g.text(g.tiny, hint, cx, 22f, Pal.DIM, 0.6f + 0.4f * MathUtils.sin(age * 4f), Align.center)
    }

    private fun card(x: Float, y: Float, w: Float, h: Float, title: String, blurb: String, primary: Boolean, order: Int) {
        // Cards deal themselves in one after another.
        val t = ((age - order * 0.08f) / 0.25f).coerceIn(0f, 1f)
        if (t <= 0f) return
        val slide = (1f - t) * 20f
        val c = if (primary) Pal.CYAN else Pal.GOLD
        g.rect(x, y - slide, w, h, Pal.PANEL, t)
        g.rect(x, y - slide, 4f, h, c, t)
        g.frame(x, y - slide, w, h, 1f, Pal.LINE, t)
        g.text(if (primary) g.body else g.small, title, x + 16f, y + h - 10f - slide, c, t)
        g.text(g.tiny, blurb, x + 16f, y + 20f - slide, Pal.TEXT, t * 0.85f)
    }

    companion object {
        /** Deterministic per run seed and level, so a level does not re-roll if you revisit its briefing. */
        fun roll(run: RunState): LevelSpec = LevelGen.generate(run.level, Random(run.seed * 31 + run.level * 7919L), run.prevMove)
    }
}
