package io.github.allwise.stareater.ui

import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.utils.Align
import io.github.allwise.stareater.StarEaterGame
import io.github.allwise.stareater.gfx.Gfx
import io.github.allwise.stareater.gfx.Pal

/**
 * The high-score board. OPEN lists every run; MASTERED only lists runs played with the whole skill
 * tree, where everyone has the same kit and only skill separates the scores.
 */
class ScoresScreen(game: StarEaterGame) : Screen(game) {
    private var masteredOnly = false
    private lateinit var open: Button
    private lateinit var mastered: Button

    override fun show() {
        button(16f, Gfx.H - 50f, 110f, 36f, "BACK") { back() }.small = true
        open = button(Gfx.W / 2f - 170f, 428f, 165f, 38f, "OPEN") { masteredOnly = false }
        mastered = button(Gfx.W / 2f + 5f, 428f, 165f, 38f, "MASTERED") { masteredOnly = true }
        mastered.accent = Pal.GOLD
        focus = 1
    }

    override fun back() = game.go(MenuScreen(game))

    override fun keyDown(key: Int): Boolean {
        if (key == Keys.TAB) { masteredOnly = !masteredOnly; return true }
        return super.keyDown(key)
    }

    override fun render(dt: Float) {
        age += dt
        g.rect(0f, 0f, Gfx.W, Gfx.H, Pal.BG)
        title("HIGH SCORES")
        drawButtons()
        val tab = if (masteredOnly) mastered else open
        g.rect(tab.x, tab.y - 5f, tab.w, 3f, tab.accent)

        val left = 240f
        val right = Gfx.W - 240f
        val accent = if (masteredOnly) Pal.GOLD else Pal.CYAN
        g.text(g.tiny, "#", left, 404f, Pal.DIM)
        g.text(g.tiny, "NAME", left + 50f, 404f, Pal.DIM)
        g.text(g.tiny, "LEVEL", left + 230f, 404f, Pal.DIM, 1f, Align.right)
        g.text(g.tiny, "SCORE", right, 404f, Pal.DIM, 1f, Align.right)
        g.rect(left, 386f, right - left, 1f, Pal.LINE)

        val rows = game.profile.topScores(masteredOnly)
        for ((i, e) in rows.withIndex()) {
            val y = 372f - i * 32f
            val c = if (i == 0) accent else Pal.TEXT
            g.text(g.body, "${i + 1}", left, y, Pal.DIM)
            g.text(g.body, e.name, left + 50f, y, c)
            if (e.mastered) g.star(left + 112f, y - 8f, 8f, 3.4f, 5, 90f, Pal.GOLD)
            g.text(g.body, e.level.toString(), left + 230f, y, c, 1f, Align.right)
            g.text(g.body, e.score.toString(), right, y, c, 1f, Align.right)
        }
        if (rows.isEmpty()) {
            val msg = if (masteredOnly) "Nobody yet. Finish the skill tree, then come and claim it." else "No scores yet. The stars are not going to eat themselves."
            g.text(g.small, msg, Gfx.W / 2f, 300f, Pal.DIM, 1f, Align.center)
        }
        val note = if (masteredOnly) "Full skill tree only: same kit for everyone, skill decides." else "Every run counts here. A gold star marks a mastered run."
        g.text(g.tiny, note, Gfx.W / 2f, 28f, Pal.DIM, 1f, Align.center)
    }
}
