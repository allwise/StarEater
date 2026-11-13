package io.github.allwise.stareater.ui

import io.github.allwise.stareater.StarEaterGame
import io.github.allwise.stareater.audio.Tune
import io.github.allwise.stareater.gfx.Creatures
import io.github.allwise.stareater.gfx.Gfx
import io.github.allwise.stareater.gfx.Pal
import io.github.allwise.stareater.gfx.WorldRenderer
import io.github.allwise.stareater.sim.Enemies
import io.github.allwise.stareater.sim.Enemy

class HelpScreen(game: StarEaterGame) : Screen(game) {
    private val creatures = Creatures(game.gfx)
    private val models = Enemies.all.map { Enemy(it) }

    override fun show() {
        game.audio.tune(Tune.MENU)
        button(16f, Gfx.H - 50f, 110f, 36f, "BACK") { back() }.small = true
    }

    override fun back() = game.go(MenuScreen(game))

    override fun render(dt: Float) {
        age += dt
        g.rect(0f, 0f, Gfx.W, Gfx.H, Pal.BG)
        title("HOW TO PLAY")
        val x = 50f
        val w = 500f
        var y = 474f
        fun section(head: String, text: String) {
            g.text(g.small, head, x, y, Pal.CYAN)
            y -= 19f
            y -= g.paragraph(g.tiny, text, x, y, w, Pal.TEXT) + 9f
        }
        section("THE DEAL", "You are the o. Eat enough stars to clear the level. Everything with a letter on it wants to eat you instead.")
        section(
            "THE TRICK",
            "Walk off one edge and you come out of the other. Most enemies never worked that out: they take the plain " +
                "route, so a well-timed wrap sends them the long way round.",
        )
        section(
            "CONTROLS",
            if (game.keyHints) "Move: arrows or WASD.  Dash: SPACE.  Nova: E.  Pause: ESC.  Music: M.  Or drag with the mouse."
            else "Drag anywhere to steer, or turn on tilt steering in the options. In classic levels a quick tap steps " +
                "once towards your finger. Tap the dials to dash or fire the nova.",
        )
        section(
            "THE RUN",
            "Level 1 is the classic. Then a slot machine deals every level up to five rules: a way of moving plus twists " +
                "like darkness or debris. Every tenth level is a boss. Out-eat it: each star you swallow hurts it.",
        )
        section(
            "SEEDS",
            "Every run has a six-letter seed key; the same key deals the same levels to anyone. Hold a slot to save a " +
                "level for later. Beat a boss and new runs can start after it.",
        )
        section(
            "THE LONG GAME",
            "Stardust buys permanent skills. Own the whole tree and your runs land on the MASTERED board: same kit for " +
                "everybody.",
        )
        section(
            "ARCADE",
            "No skill tree: your kit is what you pick up. Power-ups are the tilted squares. Thrusters, frenzy and luck " +
                "last the run; shield, magnet, dash and echo run on a clock; a nova is kept until fired.",
        )

        // Bestiary: each one as its letter (classic levels) and as the creature it becomes elsewhere.
        val bx = 590f
        var by = 462f
        g.text(g.small, "HOSTILES", bx, by, Pal.CYAN)
        g.text(g.tiny, "letter on the grid, creature off it", bx + 100f, by - 2f, Pal.DIM)
        by -= 34f
        for (m in models) {
            val t = m.type
            val c = WorldRenderer.enemyColor(t)
            g.glyphAt(g.glyph, t.glyph, bx + 10f, by - 4f, c, 0.9f)
            g.glow(bx + 46f, by - 4f, 22f, c, 0.5f)
            creatures.draw(m, bx + 46f, by - 4f, 0f, if (t.glyph == 'e') 0.6f else 0.85f, age, 0.3f, c, 1f)
            g.text(g.small, t.title, bx + 72f, by + 6f, c)
            g.text(g.tiny, t.blurb, bx + 72f, by - 10f, Pal.DIM)
            by -= 40f
        }
        by -= 4f
        g.text(g.small, "BOSSES", bx, by, Pal.RED)
        g.text(g.tiny, "every tenth level", bx + 76f, by - 2f, Pal.DIM)
        by -= 26f
        for (b in Enemies.bosses) {
            g.text(g.tiny, b.title, bx + 10f, by, WorldRenderer.enemyColor(b))
            by -= 18f
        }
        drawButtons()
    }
}
