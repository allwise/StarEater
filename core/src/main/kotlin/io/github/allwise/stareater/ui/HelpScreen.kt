package io.github.allwise.stareater.ui

import io.github.allwise.stareater.StarEaterGame
import io.github.allwise.stareater.gfx.Gfx
import io.github.allwise.stareater.gfx.Pal
import io.github.allwise.stareater.gfx.WorldRenderer
import io.github.allwise.stareater.sim.Enemies

class HelpScreen(game: StarEaterGame) : Screen(game) {
    override fun show() {
        button(16f, Gfx.H - 50f, 110f, 36f, "BACK") { back() }.small = true
    }

    override fun back() = game.go(MenuScreen(game))

    override fun render(dt: Float) {
        age += dt
        g.rect(0f, 0f, Gfx.W, Gfx.H, Pal.BG)
        title("HOW TO PLAY")
        val x = 60f
        val w = 500f
        var y = 458f
        fun section(head: String, text: String) {
            g.text(g.small, head, x, y, Pal.CYAN)
            y -= 22f
            y -= g.paragraph(g.tiny, text, x, y, w, Pal.TEXT) + 20f
        }
        section("THE DEAL", "You are the o. Eat enough stars to clear the level. Everything with a letter on it wants to eat you instead.")
        section(
            "THE TRICK",
            "Walk off one edge and you come out of the other. Most enemies never worked that out: they take the plain route " +
                "across the field. Wrap at the right moment and they have to turn around and cross the whole screen.",
        )
        section(
            "CONTROLS",
            if (game.keyHints) "Move: arrow keys or WASD.   Dash: SPACE.   Nova: E.   Pause: ESC.\nMouse works too: drag to steer."
            else "Drag anywhere to steer. In classic levels a quick tap steps once towards your finger. Tap the dials to dash or fire the nova.",
        )
        section(
            "THE RUN",
            "Level 1 is always the classic. After that every level rolls its own mix of one to five rules, and it only gets " +
                "faster, smarter and more crowded. Chain stars quickly for a combo multiplier.",
        )
        section(
            "THE LONG GAME",
            "Stardust buys permanent skills. Own the whole tree and your runs land on the MASTERED board, where everybody has " +
                "the same kit and the only thing left to upgrade is you.",
        )

        // Bestiary down the right-hand side.
        val bx = 610f
        var by = 458f
        g.text(g.small, "HOSTILES", bx, by, Pal.CYAN)
        by -= 30f
        for (t in Enemies.all) {
            val c = WorldRenderer.enemyColor(t)
            g.glow(bx + 10f, by - 4f, 20f, c, 0.6f)
            g.glyphAt(g.glyph, t.glyph, bx + 10f, by - 4f, c)
            g.text(g.small, t.title, bx + 32f, by + 6f, c)
            g.paragraph(g.tiny, t.blurb, bx + 32f, by - 10f, 300f, Pal.DIM)
            by -= 46f
        }
        drawButtons()
    }
}
