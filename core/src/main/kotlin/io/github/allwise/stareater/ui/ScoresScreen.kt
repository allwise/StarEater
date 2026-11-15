package io.github.allwise.stareater.ui

import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Align
import io.github.allwise.stareater.StarEaterGame
import io.github.allwise.stareater.audio.Tune
import io.github.allwise.stareater.gfx.Gfx
import io.github.allwise.stareater.gfx.Pal
import io.github.allwise.stareater.meta.SeedKey
import io.github.allwise.stareater.sim.GameMode

/**
 * The high-score boards: one for each mode, and one for whichever seed key was played last.
 *
 * Two marks, rather than more boards. A gold star is a campaign run played with the whole skill
 * tree; a diamond is an arcade or endless run that never used anything out of its pockets.
 */
class ScoresScreen(game: StarEaterGame) : Screen(game) {
    private var tab = 0
    private val tabs = ArrayList<Button>()

    override fun show() {
        game.audio.tune(Tune.MENU)
        button(16f, Gfx.H - 50f, 110f, 36f, "BACK") { back() }.small = true
        val names = listOf("CAMPAIGN", "MATRIX", "FLOW", "ENDLESS", "SEED")
        val accents = listOf(Pal.CYAN, Pal.GREEN, Pal.PURPLE, Pal.ORANGE, Pal.PINK)
        val w = 140f
        val x0 = Gfx.W / 2f - (w * names.size + 10f * (names.size - 1)) / 2f
        for ((i, name) in names.withIndex()) {
            tabs += button(x0 + i * (w + 10f), 428f, w, 38f, name) { tab = i }.also { it.accent = accents[i]; it.small = true }
        }
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
        val seedTab = tab == SEED
        val mode = if (seedTab) GameMode.CAMPAIGN else GameMode.values()[tab]
        val left = 240f
        val right = Gfx.W - 240f
        val accent = current.accent
        g.text(g.tiny, "#", left, 404f, Pal.DIM)
        g.text(g.tiny, "NAME", left + 50f, 404f, Pal.DIM)
        g.text(g.tiny, if (seedTab) "LEVEL" else "SEED", left + 250f, 404f, Pal.DIM, 1f, Align.right)
        g.text(g.tiny, "SCORE", right, 404f, Pal.DIM, 1f, Align.right)
        g.rect(left, 386f, right - left, 1f, Pal.LINE)

        val rows = if (seedTab) p.seedScores(seed) else p.topScores(mode)
        for ((i, e) in rows.withIndex()) {
            val y = 372f - i * 32f
            // The leader shimmers.
            val c = if (i == 0) g.mix(accent, Pal.WHITE, 0.25f + 0.25f * MathUtils.sin(age * 4f)) else Pal.TEXT
            g.text(g.body, "${i + 1}", left, y, Pal.DIM)
            g.text(g.body, e.name, left + 50f, y, c)
            if (e.mastered) g.star(left + 112f, y - 8f, 8f, 3.4f, 5, 90f + (if (i == 0) age * 60f else 0f), Pal.GOLD)
            if (e.pure) g.star(left + 112f, y - 8f, 8f, 5.6f, 2, 90f, Pal.SHIELD)
            if (seedTab) g.text(g.body, "LV ${e.level}", left + 250f, y, c, 1f, Align.right)
            else g.text(g.small, if (e.seed.isEmpty()) "LV ${e.level}" else "${SeedKey.pretty(e.seed)}  LV ${e.level}", left + 290f, y - 3f, Pal.DIM, 1f, Align.right)
            g.text(g.body, e.score.toString(), right, y, c, 1f, Align.right)
        }
        if (rows.isEmpty()) {
            val msg = when {
                seedTab -> if (seed.isEmpty()) "Play a seeded run and its board shows up here." else "No scores on this seed yet."
                !p.unlocked(mode) -> "Locked. Beat the boss on level ${mode.unlockedBy} to open ${mode.title}."
                mode == GameMode.CAMPAIGN -> "No scores yet. The stars are not going to eat themselves."
                else -> "Nobody yet. It is open, and it is waiting."
            }
            g.text(g.small, msg, Gfx.W / 2f, 300f, Pal.DIM, 1f, Align.center)
        }
        val note = when {
            seedTab -> if (seed.isEmpty()) "One seed key deals the same run to everyone who plays it." else "Campaign runs on seed ${SeedKey.pretty(seed)}: the same levels for everyone. Pick another in SEEDED RUN."
            mode == GameMode.CAMPAIGN -> "Thirty levels, three bosses. A gold star marks a run with the whole skill tree."
            mode == GameMode.MATRIX -> "Tile by tile, every level. A diamond marks a pure run: nothing used from the pockets."
            mode == GameMode.FLOW -> "Everything that glides. A diamond marks a pure run: nothing used from the pockets."
            else -> "One level that never ends. A diamond marks a pure run: nothing used from the pockets."
        }
        g.text(g.tiny, note, Gfx.W / 2f, 28f, Pal.DIM, 1f, Align.center)
    }

    private companion object {
        const val SEED = 4
    }
}
