package io.github.allwise.stareater.ui

import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Align
import io.github.allwise.stareater.StarEaterGame
import io.github.allwise.stareater.audio.Tune
import io.github.allwise.stareater.gfx.Gfx
import io.github.allwise.stareater.gfx.Pal
import io.github.allwise.stareater.meta.SeedKey

/**
 * The high-score boards.
 *  - OPEN lists every run.
 *  - MASTERED only lists runs played with the whole skill tree: same kit for everyone.
 *  - SEED lists runs on one seed key: same levels for everyone.
 *  - ARCADE lists arcade runs: no skill tree at all, only what was picked up on the way.
 */
class ScoresScreen(game: StarEaterGame) : Screen(game) {
    private var tab = 0
    private val tabs = ArrayList<Button>()

    override fun show() {
        game.audio.tune(Tune.MENU)
        button(16f, Gfx.H - 50f, 110f, 36f, "BACK") { back() }.small = true
        val w = 150f
        val x0 = Gfx.W / 2f - (w * 4 + 30f) / 2f
        tabs += button(x0, 428f, w, 38f, "OPEN") { tab = 0 }
        tabs += button(x0 + w + 10f, 428f, w, 38f, "MASTERED") { tab = 1 }.also { it.accent = Pal.GOLD }
        tabs += button(x0 + (w + 10f) * 2, 428f, w, 38f, "SEED") { tab = 2 }.also { it.accent = Pal.PINK }
        tabs += button(x0 + (w + 10f) * 3, 428f, w, 38f, "ARCADE") { tab = 3 }.also { it.accent = Pal.GREEN }
        focus = 1
    }

    override fun back() = game.go(MenuScreen(game))

    override fun keyDown(key: Int): Boolean {
        if (key == Keys.TAB) { tab = (tab + 1) % tabs.size; return true }
        return super.keyDown(key)
    }

    override fun render(dt: Float) {
        age += dt
        g.rect(0f, 0f, Gfx.W, Gfx.H, Pal.BG)
        title("HIGH SCORES")
        drawButtons()
        val current = tabs[tab]
        g.rect(current.x, current.y - 5f, current.w, 3f, current.accent)

        val p = game.profile
        val seed = p.lastSeed
        val left = 240f
        val right = Gfx.W - 240f
        val accent = current.accent
        g.text(g.tiny, "#", left, 404f, Pal.DIM)
        g.text(g.tiny, "NAME", left + 50f, 404f, Pal.DIM)
        g.text(g.tiny, if (tab == 2) "LEVEL" else "SEED", left + 250f, 404f, Pal.DIM, 1f, Align.right)
        g.text(g.tiny, "SCORE", right, 404f, Pal.DIM, 1f, Align.right)
        g.rect(left, 386f, right - left, 1f, Pal.LINE)

        val rows = when (tab) {
            0 -> p.topScores(false)
            1 -> p.topScores(true)
            2 -> p.seedScores(seed)
            else -> p.arcadeScores()
        }
        for ((i, e) in rows.withIndex()) {
            val y = 372f - i * 32f
            // The leader shimmers.
            val c = if (i == 0) g.mix(accent, Pal.WHITE, 0.25f + 0.25f * MathUtils.sin(age * 4f)) else Pal.TEXT
            g.text(g.body, "${i + 1}", left, y, Pal.DIM)
            g.text(g.body, e.name, left + 50f, y, c)
            if (e.mastered) g.star(left + 112f, y - 8f, 8f, 3.4f, 5, 90f + (if (i == 0) age * 60f else 0f), Pal.GOLD)
            if (tab == 2) g.text(g.body, "LV ${e.level}", left + 250f, y, c, 1f, Align.right)
            else g.text(g.small, if (e.seed.isEmpty()) "LV ${e.level}" else "${SeedKey.pretty(e.seed)}  LV ${e.level}", left + 290f, y - 3f, Pal.DIM, 1f, Align.right)
            g.text(g.body, e.score.toString(), right, y, c, 1f, Align.right)
        }
        if (rows.isEmpty()) {
            val msg = when (tab) {
                0 -> "No scores yet. The stars are not going to eat themselves."
                1 -> "Nobody yet. Finish the skill tree, then come and claim it."
                3 -> "No arcade runs yet. The power-ups are just lying there."
                else -> if (seed.isEmpty()) "Play a seeded run and its board shows up here." else "No scores on this seed yet."
            }
            g.text(g.small, msg, Gfx.W / 2f, 300f, Pal.DIM, 1f, Align.center)
        }
        val note = when (tab) {
            0 -> "Every run counts here. A gold star marks a mastered run."
            1 -> "Full skill tree only: same kit for everyone, skill decides."
            3 -> "Arcade runs: no skill tree, only what you picked up on the way."
            else -> if (seed.isEmpty()) "One seed key deals the same run to everyone who plays it." else "Runs on seed ${SeedKey.pretty(seed)}: the same levels for everyone. Pick another in SEEDED RUN."
        }
        g.text(g.tiny, note, Gfx.W / 2f, 28f, Pal.DIM, 1f, Align.center)
    }
}
