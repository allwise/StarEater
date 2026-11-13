package io.github.allwise.stareater.ui

import com.badlogic.gdx.utils.Align
import io.github.allwise.stareater.StarEaterGame
import io.github.allwise.stareater.audio.Sfx
import io.github.allwise.stareater.gfx.Gfx
import io.github.allwise.stareater.gfx.Pal
import io.github.allwise.stareater.meta.Profile
import io.github.allwise.stareater.meta.SavedLevel

/**
 * The five saved-level slots, as a row of buttons a screen can drop in. They work like the presets
 * on a car radio: press and hold one to store the level in hand, tap it to recall what it holds.
 *
 * @param wide wide slots spell out what they hold; narrow ones are just numbered squares
 * @param current the level that holding a slot would store, or null if there is nothing to store
 * @param recall what a tap on a filled slot does; when null a tap just says what is in there
 */
class SavedSlots(
    private val screen: Screen,
    private val game: StarEaterGame,
    x: Float, y: Float, private val wide: Boolean,
    private val current: () -> SavedLevel?,
    private val recall: ((SavedLevel) -> Unit)? = null,
) {
    val buttons = ArrayList<Button>()
    private var note = ""
    private var noteLeft = 0f

    /** Where the row ends, for laying things out beside it. */
    val right: Float

    init {
        val w = if (wide) WIDE_W else NARROW_W
        val h = if (wide) 34f else NARROW_W
        for (i in 0 until Profile.SAVE_SLOTS) {
            val b = screen.addButton(x + i * (w + GAP), y, w, h, "") { tap(i) }
            b.small = true
            b.silent = true
            b.onHold = { store(i) }
            buttons += b
        }
        right = x + Profile.SAVE_SLOTS * (w + GAP) - GAP
    }

    private fun store(i: Int) {
        val level = current()
        if (level == null) {
            game.audio.play(Sfx.UI_NO)
            say("NOTHING TO STORE HERE")
            return
        }
        game.profile.store(i, level)
        game.audio.play(Sfx.UI_BUY)
        game.buzz()
        say("SLOT ${i + 1} NOW HOLDS ${level.label}")
    }

    private fun tap(i: Int) {
        val held = game.profile.saved[i]
        when {
            held == null -> {
                game.audio.play(Sfx.UI_TICK)
                say(if (current() == null) "SLOT ${i + 1} IS EMPTY" else "SLOT ${i + 1} IS EMPTY - PRESS AND HOLD TO STORE")
            }
            recall != null -> {
                game.audio.play(Sfx.UI_OK)
                recall.invoke(held)
            }
            else -> {
                game.audio.play(Sfx.UI_TICK)
                say("SLOT ${i + 1}: ${held.label}" + (if (current() == null) "" else " - HOLD TO REPLACE"))
            }
        }
    }

    private fun say(text: String) {
        note = text
        noteLeft = 2.6f
    }

    var visible: Boolean = true
        set(v) {
            field = v
            for (b in buttons) b.visible = v
        }

    /** Keeps the labels fresh. Call once a frame, before the screen draws its buttons. */
    fun update(dt: Float) {
        noteLeft -= dt
        for ((i, b) in buttons.withIndex()) {
            val held = game.profile.saved[i]
            b.label = if (wide) held?.label ?: "${i + 1}  EMPTY" else "${i + 1}"
            b.accent = if (held != null) Pal.GOLD else Pal.CYAN
            b.lit = held != null
        }
    }

    /**
     * Draws the row's caption - or, for a moment after a tap or a store, what just happened - at the
     * given spot.
     */
    fun caption(g: Gfx, idle: String, x: Float, y: Float, align: Int = Align.left) {
        if (!visible) return
        val fresh = noteLeft > 0f
        g.text(g.tiny, if (fresh) note else idle, x, y, if (fresh) Pal.GOLD else Pal.DIM, if (fresh) noteLeft.coerceAtMost(1f) else 1f, align)
    }

    private companion object {
        const val WIDE_W = 158f
        const val NARROW_W = 44f
        const val GAP = 6f
    }
}
