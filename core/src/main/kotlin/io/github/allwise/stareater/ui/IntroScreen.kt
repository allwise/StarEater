package io.github.allwise.stareater.ui

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Align
import io.github.allwise.stareater.StarEaterGame
import io.github.allwise.stareater.audio.Sfx
import io.github.allwise.stareater.gfx.Creatures
import io.github.allwise.stareater.gfx.Gfx
import io.github.allwise.stareater.gfx.Pal
import io.github.allwise.stareater.gfx.WorldRenderer
import io.github.allwise.stareater.meta.SeedKey
import io.github.allwise.stareater.sim.Enemy
import io.github.allwise.stareater.sim.EnemyType
import io.github.allwise.stareater.sim.Leviathan
import io.github.allwise.stareater.sim.LevelGen
import io.github.allwise.stareater.sim.LevelSpec
import io.github.allwise.stareater.sim.MoveKind
import io.github.allwise.stareater.sim.Rules
import io.github.allwise.stareater.sim.RunState

/** The briefing before each level: which rules got rolled, who is hunting you, how many stars. */
class IntroScreen(game: StarEaterGame, private val run: RunState, preset: LevelSpec? = null) : Screen(game) {
    private val spec = preset ?: roll(run)
    private val creatures = Creatures(game.gfx)
    private val models = HashMap<EnemyType, Enemy>()

    override fun show() {
        button(Gfx.W / 2f - 150f, 34f, 300f, 46f, if (run.level == 1) "START" else if (spec.boss != null) "FIGHT" else "NEXT LEVEL") { start() }
        if (spec.boss != null) game.audio.play(Sfx.BOSS_INTRO)
    }

    private fun start() = game.go(PlayScreen(game, run, spec))

    /** Backing out of a briefing ends the run (and still banks what you earned). */
    override fun back() = game.go(GameOverScreen(game, run))

    override fun render(dt: Float) {
        age += dt
        if (game.devAuto && age > 0.4f) { start(); return }
        g.rect(0f, 0f, Gfx.W, Gfx.H, Pal.BG)
        val cx = Gfx.W / 2f
        val boss = spec.boss
        g.glow(cx, 520f, 420f, if (boss != null) Pal.RED else Pal.CYAN, 0.12f)
        g.text(g.big, if (boss != null) "LEVEL ${spec.number}  -  BOSS" else "LEVEL ${spec.number}", cx, 528f, if (boss != null) Pal.RED else Pal.TEXT, 1f, Align.center)
        val goal = if (boss != null) "EAT ${spec.quota} STARS TO BRING IT DOWN" else "EAT ${spec.quota} STARS"
        val goalW = g.width(g.small, goal)
        g.star(cx - goalW / 2f - 14f, 483f, 8f, 3.4f, 5, 90f, Pal.STAR)
        g.text(g.small, goal, cx - goalW / 2f, 489f, Pal.STAR)
        if (run.key.isNotEmpty()) g.text(g.tiny, "SEED ${SeedKey.pretty(run.key)}", Gfx.W - 80f, 524f, Pal.DIM, 1f, Align.right)

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

        var rosterTop = if (spec.modifiers.size > 2) 262f else 322f
        if (boss != null) {
            // The star of the show gets a card of its own.
            val t = ((age - 0.15f) / 0.3f).coerceIn(0f, 1f)
            val c = WorldRenderer.enemyColor(boss)
            g.rect(left, 268f, full, 122f, Pal.PANEL, t)
            g.rect(left, 268f, 4f, 122f, Pal.RED, t)
            g.frame(left, 268f, full, 122f, 1f, Pal.LINE, t)
            g.glow(left + 90f, 329f, 110f, c, 0.5f * t)
            portrait(boss, left + 90f, 329f, 2.6f, t)
            g.text(g.big, boss.title, left + 190f, 366f, c, t)
            g.paragraph(g.small, boss.blurb, left + 190f, 326f, full - 220f, Pal.TEXT, t)
            rosterTop = 246f
        } else if (spec.modifiers.isEmpty()) {
            g.text(g.small, "No twists. Just you, the stars, and things with teeth.", cx, 368f, Pal.DIM, 1f, Align.center)
        }

        // Who is out there.
        g.text(g.tiny, if (boss != null) "AND ITS FRIENDS" else "HOSTILES", left, rosterTop, Pal.DIM)
        for ((i, t) in spec.roster.withIndex()) {
            val x = left + (i % 2) * (half + 16f)
            val y = rosterTop - 28f - (i / 2) * 38f
            val c = WorldRenderer.enemyColor(t)
            g.glow(x + 12f, y - 2f, 22f, c, 0.6f)
            // Letters on the grid, creatures everywhere else - same as in the level itself.
            if (spec.move == MoveKind.GRID) g.glyphAt(g.glyph, t.glyph, x + 12f, y - 2f, c) else portrait(t, x + 12f, y - 2f, 0.9f, 1f)
            g.text(g.small, t.title, x + 34f, y + 8f, c)
            g.text(g.tiny, t.blurb, x + 34f, y - 8f, Pal.DIM)
        }

        drawButtons()
        val hint = if (game.keyHints) "ENTER to go   -   ESC ends the run" else "tap to go"
        g.text(g.tiny, hint, cx, 22f, Pal.DIM, 0.6f + 0.4f * MathUtils.sin(age * 4f), Align.center)
    }

    private fun portrait(type: EnemyType, x: Float, y: Float, scale: Float, a: Float) {
        val model = models.getOrPut(type) { Enemy(type) }
        val c = WorldRenderer.enemyColor(type)
        if (type === Leviathan) creatures.serpentHead(x, y, 0f, 13f * scale, age, c, a)
        else creatures.draw(model, x, y, 0f, scale, age, 0.4f, c, a)
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
        /** Depends only on the run's seed and level, so a level never re-rolls if you revisit its briefing. */
        fun roll(run: RunState): LevelSpec = LevelGen.forRun(run)
    }
}
