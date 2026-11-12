package io.github.allwise.stareater.ui

import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.utils.Align
import io.github.allwise.stareater.StarEaterGame
import io.github.allwise.stareater.audio.Sfx
import io.github.allwise.stareater.audio.Tune
import io.github.allwise.stareater.gfx.Gfx
import io.github.allwise.stareater.gfx.Pal
import io.github.allwise.stareater.meta.SeedKey

/**
 * Pick the seed for a run. A key deals the same levels, rules and layouts to everyone who plays it,
 * so two people can take turns on one key and compare. The game is endless either way.
 */
class SeedScreen(game: StarEaterGame) : Screen(game) {
    private val key = CharArray(SeedKey.LENGTH)
    private var slot = 0

    /** Keyboard focus is either on the key (type away) or on the buttons (arrows and ENTER). */
    private var onButtons = false

    override val typing: Boolean get() = !onButtons

    override fun show() {
        game.audio.tune(Tune.MENU)
        set(game.profile.lastSeed.ifEmpty { SeedKey.dailyNow() })
        button(16f, Gfx.H - 50f, 110f, 36f, "BACK") { back() }.small = true
        val y = 196f
        button(Gfx.W / 2f - 330f, y, 210f, 44f, "PLAY THIS SEED") { play() }.accent = Pal.GOLD
        button(Gfx.W / 2f - 105f, y, 210f, 44f, "TODAY'S SEED") { set(SeedKey.dailyNow()) }
        button(Gfx.W / 2f + 120f, y, 210f, 44f, "RANDOM") { set(SeedKey.random()) }
        focus = 1
    }

    private fun set(k: String) {
        k.toCharArray().copyInto(key)
        slot = 0
    }

    private fun current(): String = String(key)

    private fun play() {
        val k = current()
        game.profile.lastSeed = k
        game.profile.save()
        game.go(IntroScreen(game, SeedKey.newRun(k)))
    }

    override fun back() = game.go(MenuScreen(game))

    private fun spin(i: Int, by: Int) {
        slot = i
        val n = SeedKey.ALPHABET.length
        key[i] = SeedKey.ALPHABET[(SeedKey.ALPHABET.indexOf(key[i]) + by + n * 4) % n]
        game.audio.play(Sfx.UI_MOVE)
    }

    private fun slotX(i: Int): Float = Gfx.W / 2f + (i - 2.5f) * (SLOT_W + 10f) + (if (i >= 3) 12f else -12f)

    override fun render(dt: Float) {
        age += dt
        g.rect(0f, 0f, Gfx.W, Gfx.H, Pal.BG)
        title("SEEDED RUN")
        val cx = Gfx.W / 2f
        g.text(g.small, "Same key, same run: the same levels, rules and layouts for everyone.", cx, 452f, Pal.TEXT, 1f, Align.center)
        g.text(g.tiny, "Pass a key to a friend and see who gets further. Every run shows its key, so a good one can be replayed.", cx, 428f, Pal.DIM, 1f, Align.center)

        for (i in 0 until SeedKey.LENGTH) {
            val x = slotX(i)
            val on = i == slot && !onButtons
            g.rect(x - SLOT_W / 2f, SLOT_Y, SLOT_W, SLOT_H, if (on) Pal.PANEL_HI else Pal.PANEL)
            g.frame(x - SLOT_W / 2f, SLOT_Y, SLOT_W, SLOT_H, 2f, if (on) Pal.GOLD else Pal.LINE)
            g.glyphAt(g.big, key[i], x, SLOT_Y + SLOT_H / 2f, Pal.TEXT)
            val c = if (on) Pal.GOLD else Pal.DIM
            g.triangle(x - 9f, SLOT_Y + SLOT_H + 6f, x + 9f, SLOT_Y + SLOT_H + 6f, x, SLOT_Y + SLOT_H + 17f, c)
            g.triangle(x - 9f, SLOT_Y - 6f, x + 9f, SLOT_Y - 6f, x, SLOT_Y - 17f, c)
        }
        g.rect(cx - 8f, SLOT_Y + SLOT_H / 2f - 2f, 16f, 4f, Pal.DIM)
        if (current() == SeedKey.dailyNow()) g.text(g.tiny, "THIS IS TODAY'S SEED", cx, SLOT_Y + SLOT_H + 40f, Pal.GOLD, 1f, Align.center)

        showFocus = onButtons
        drawButtons()
        if (game.keyHints) {
            val hint = if (onButtons) "ARROWS and ENTER   -   TAB: back to the key" else "TYPE a key, ENTER to play   -   TAB: buttons"
            g.text(g.tiny, hint, cx, 174f, Pal.DIM, 1f, Align.center)
        }

        // How you have done on this key so far.
        val rows = game.profile.seedScores(current(), 4)
        g.text(g.tiny, "YOUR BEST ON ${SeedKey.pretty(current())}", cx, 146f, Pal.CYAN, 1f, Align.center)
        if (rows.isEmpty()) g.text(g.tiny, "Not played yet.", cx, 118f, Pal.DIM, 1f, Align.center)
        for ((i, e) in rows.withIndex()) {
            val y = 122f - i * 24f
            g.text(g.small, "${i + 1}", cx - 150f, y, Pal.DIM)
            g.text(g.small, e.name, cx - 110f, y, Pal.TEXT)
            if (e.mastered) g.star(cx - 62f, y - 6f, 6f, 2.6f, 5, 90f, Pal.GOLD)
            g.text(g.small, "LV ${e.level}", cx + 20f, y, Pal.TEXT, 1f, Align.right)
            g.text(g.small, e.score.toString(), cx + 150f, y, Pal.TEXT, 1f, Align.right)
        }
    }

    override fun keyDown(key: Int): Boolean {
        if (key == Keys.TAB) {
            onButtons = !onButtons
            if (onButtons) focus = 1
            return true
        }
        if (onButtons) return super.keyDown(key)
        when (key) {
            Keys.LEFT -> slot = (slot + SeedKey.LENGTH - 1) % SeedKey.LENGTH
            Keys.RIGHT -> slot = (slot + 1) % SeedKey.LENGTH
            Keys.UP -> spin(slot, 1)
            Keys.DOWN -> spin(slot, -1)
            Keys.BACKSPACE -> slot = (slot - 1).coerceAtLeast(0)
            Keys.ENTER, Keys.NUMPAD_ENTER -> { game.audio.play(Sfx.UI_OK); play() }
            Keys.ESCAPE, Keys.BACK -> back()
            else -> return false
        }
        return true
    }

    override fun keyTyped(c: Char) {
        if (onButtons || !c.isLetterOrDigit()) return
        // Look-alikes are mapped onto the symbols the alphabet actually has.
        val symbol = when (val up = c.uppercaseChar()) {
            'I', '1' -> 'L'
            'O', '0' -> 'Q'
            else -> up
        }
        if (symbol !in SeedKey.ALPHABET) return
        key[slot] = symbol
        slot = (slot + 1).coerceAtMost(SeedKey.LENGTH - 1)
    }

    override fun touchDown(x: Float, y: Float, pointer: Int) {
        for (i in 0 until SeedKey.LENGTH) {
            val sx = slotX(i)
            if (x < sx - SLOT_W / 2f - 5f || x > sx + SLOT_W / 2f + 5f) continue
            if (y > SLOT_Y + SLOT_H / 2f && y < SLOT_Y + SLOT_H + 30f) { onButtons = false; spin(i, 1) }
            else if (y <= SLOT_Y + SLOT_H / 2f && y > SLOT_Y - 30f) { onButtons = false; spin(i, -1) }
        }
        super.touchDown(x, y, pointer)
    }

    override fun mouseMoved(x: Float, y: Float) {
        if (onButtons) super.mouseMoved(x, y)
    }

    private companion object {
        const val SLOT_W = 50f
        const val SLOT_H = 56f
        const val SLOT_Y = 296f
    }
}
