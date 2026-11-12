package io.github.allwise.stareater.ui

import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Align
import io.github.allwise.stareater.StarEaterGame
import io.github.allwise.stareater.audio.Sfx
import io.github.allwise.stareater.gfx.Gfx
import io.github.allwise.stareater.gfx.Pal
import kotlin.math.abs

class Button(var x: Float, var y: Float, var w: Float, var h: Float, var label: String, val action: () -> Unit) {
    var enabled = true
    var visible = true
    var accent: Color = Pal.CYAN
    var small = false

    /** For buttons that make their own noise. */
    var silent = false

    val usable: Boolean get() = enabled && visible
    val cx: Float get() = x + w / 2f
    val cy: Float get() = y + h / 2f
    fun contains(px: Float, py: Float): Boolean = px >= x && px <= x + w && py >= y && py <= y + h
}

/**
 * Base for every screen. Gives each one the same input model for free: buttons that work with a
 * mouse, a finger, or just the arrow keys and ENTER. Coordinates are canvas pixels (960x540, y up).
 */
abstract class Screen(val game: StarEaterGame) {
    protected val g: Gfx get() = game.gfx
    protected val buttons = ArrayList<Button>()
    protected var focus = 0
    protected var age = 0f

    /** Set false to draw the buttons with none of them highlighted. */
    protected var showFocus = true
    private var pressed: Button? = null

    /** True while the screen wants raw letter keys (so global shortcuts must keep their hands off). */
    open val typing: Boolean get() = false

    open fun show() {}
    abstract fun render(dt: Float)

    /** ESC on desktop, the back button on Android. */
    open fun back() {}

    protected fun button(x: Float, y: Float, w: Float, h: Float, label: String, action: () -> Unit): Button =
        Button(x, y, w, h, label, action).also { buttons += it }

    protected fun drawButtons() {
        if (focus !in buttons.indices || !buttons[focus].usable) focus = buttons.indexOfFirst { it.usable }.coerceAtLeast(0)
        for ((i, b) in buttons.withIndex()) {
            if (!b.visible) continue
            val focused = showFocus && i == focus && b.enabled
            val a = if (b.enabled) 1f else 0.4f
            g.rect(b.x, b.y, b.w, b.h, if (focused) Pal.PANEL_HI else Pal.PANEL, 0.95f * a)
            g.frame(b.x, b.y, b.w, b.h, 2f, if (focused) b.accent else Pal.LINE, a)
            val font = if (b.small) g.small else g.body
            g.text(font, b.label, b.cx, b.cy + font.capHeight / 2f, if (focused) Pal.TEXT else Pal.DIM, a, Align.center)
        }
    }

    protected fun title(text: String) {
        g.text(g.big, text, Gfx.W / 2f, Gfx.H - 22f, Pal.TEXT, 1f, Align.center)
    }

    open fun keyDown(key: Int): Boolean {
        when (key) {
            Keys.ESCAPE, Keys.BACK -> back()
            Keys.UP, Keys.W -> moveFocus(0, 1)
            Keys.DOWN, Keys.S -> moveFocus(0, -1)
            Keys.LEFT, Keys.A -> moveFocus(-1, 0)
            Keys.RIGHT, Keys.D -> moveFocus(1, 0)
            Keys.ENTER, Keys.NUMPAD_ENTER, Keys.SPACE -> buttons.getOrNull(focus)?.takeIf { it.usable }?.let { click(it) }
            else -> return false
        }
        return true
    }

    open fun keyUp(key: Int) {}
    open fun keyTyped(c: Char) {}

    /** Moves focus to the nearest usable button in a direction. */
    protected fun moveFocus(dx: Int, dy: Int) {
        val from = buttons.getOrNull(focus) ?: return
        var best = -1
        var bestScore = Float.MAX_VALUE
        for ((i, b) in buttons.withIndex()) {
            if (i == focus || !b.usable) continue
            val ox = b.cx - from.cx
            val oy = b.cy - from.cy
            val along = ox * dx + oy * dy
            if (along <= 1f) continue
            val across = abs(ox * dy) + abs(oy * dx)
            val score = along + across * 2.5f
            if (score < bestScore) { bestScore = score; best = i }
        }
        if (best >= 0) {
            focus = best
            game.audio.play(Sfx.UI_MOVE)
        }
    }

    private fun click(b: Button) {
        if (!b.silent) game.audio.play(Sfx.UI_OK)
        b.action()
    }

    open fun touchDown(x: Float, y: Float, pointer: Int) {
        val i = buttons.indexOfFirst { it.usable && it.contains(x, y) }
        if (i >= 0) {
            focus = i
            pressed = buttons[i]
        }
    }

    open fun touchUp(x: Float, y: Float, pointer: Int) {
        val p = pressed
        pressed = null
        if (p != null && p.usable && p.contains(x, y)) click(p)
    }

    open fun touchDragged(x: Float, y: Float, pointer: Int) {}

    open fun mouseMoved(x: Float, y: Float) {
        val i = buttons.indexOfFirst { it.usable && it.contains(x, y) }
        if (i >= 0) focus = i
    }
}
