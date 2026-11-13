package io.github.allwise.stareater.ui

import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Align
import io.github.allwise.stareater.StarEaterGame
import io.github.allwise.stareater.audio.Sfx
import io.github.allwise.stareater.audio.Tune
import io.github.allwise.stareater.gfx.Gfx
import io.github.allwise.stareater.gfx.Pal
import io.github.allwise.stareater.gfx.Sparkles
import io.github.allwise.stareater.gfx.WorldRenderer
import io.github.allwise.stareater.meta.SeedKey
import io.github.allwise.stareater.sim.RunState
import kotlin.math.exp

/**
 * End of a run: bank the stardust, and take three initials if the score made a board.
 *
 * The initials are three wheels. Slide a finger up or down - anywhere - to turn the lit one, tap to
 * the left or right of them to move along (or tap a wheel to pick it). A keyboard can just type.
 */
class GameOverScreen(game: StarEaterGame, private val run: RunState) : Screen(game) {
    private val dust = run.dust.toInt()
    private val arcade = run.arcade != null
    private val mastered = !arcade && game.profile.mastered
    private val sparkles = Sparkles(game.gfx)
    private var rank = 0
    private var seedRank = 0
    private var entering = false
    private val name = CharArray(3)
    private var slot = 0

    /** Each wheel's position in letters; a whole number when it is at rest on one. */
    private val wheel = FloatArray(3)
    private var dragPointer = -1
    private var dragY = 0f
    private var dragFrom = 0f
    private var dragMoved = false
    private var dragOnSlot = false
    private var lastY = 0f
    private var lastT = 0f
    private var spinning = 0f

    override val typing: Boolean get() = entering

    override fun show() {
        val p = game.profile
        rank = p.rankFor(run.score, arcade)
        seedRank = if (arcade) 0 else p.seedRankFor(run.key, run.score)
        p.finishRun(dust, run.level)
        if (run.key.isNotEmpty()) p.lastSeed = run.key
        // A score is worth initials if it makes the open board or the board for its own seed.
        entering = rank > 0 || seedRank > 0
        p.initials.toCharArray().copyInto(name)
        for (i in 0 until 3) wheel[i] = (name[i] - 'A').toFloat()
        // The jingle gets the room to itself, then the quiet tune creeps in underneath.
        val salute = rank in 1..3 || seedRank == 1
        game.audio.play(if (salute) Sfx.HIGH_SCORE else Sfx.GAME_OVER)
        game.audio.tune(Tune.AFTER, after = if (salute) 2.7f else 1.5f, fadeIn = 2.5f)

        val y = 34f
        val w = 196f
        val x0 = Gfx.W / 2f - (w * 4 + 12f * 3) / 2f
        button(x0, y, w, 44f, "NEW RUN") { game.go(IntroScreen(game, SeedKey.newRun(SeedKey.random(), arcade))) }
        button(x0 + (w + 12f), y, w, 44f, "SAME SEED") { game.go(IntroScreen(game, SeedKey.newRun(run.key, arcade))) }.enabled = SeedKey.isValid(run.key)
        button(x0 + (w + 12f) * 2, y, w, 44f, "SKILL TREE") { game.go(SkillScreen(game)) }
        button(x0 + (w + 12f) * 3, y, w, 44f, "MENU") { game.go(MenuScreen(game)) }
        button(Gfx.W / 2f + 150f, SLOT_Y + 4f, 110f, 44f, "OK") { confirm() }.accent = Pal.GOLD
        refresh()
    }

    private fun refresh() {
        for ((i, b) in buttons.withIndex()) b.visible = if (i == 4) entering else !entering
        focus = if (entering) 4 else 0
    }

    private fun confirm() {
        if (!entering) return
        game.profile.addScore(String(name), run.score, run.level, mastered, System.currentTimeMillis(), run.key, arcade)
        entering = false
        dragPointer = -1
        refresh()
    }

    override fun back() {
        if (entering) confirm() else game.go(MenuScreen(game))
    }

    /** Turns a wheel by whole letters (the keyboard's way of doing it). */
    private fun spin(i: Int, by: Int) {
        slot = i
        spinning = 0f
        setWheel(i, MathUtils.round(wheel[i]) + by.toFloat())
    }

    /** Moves a wheel, and clicks - softly, in the ear and in the hand - each time a new letter comes up. */
    private fun setWheel(i: Int, pos: Float) {
        wheel[i] = pos
        val letter = 'A' + Math.floorMod(MathUtils.round(pos), 26)
        if (letter == name[i]) return
        name[i] = letter
        game.audio.play(Sfx.UI_TICK)
        game.buzz()
    }

    private fun select(i: Int) {
        val next = Math.floorMod(i, 3)
        if (next == slot) return
        // Leave the old wheel sitting squarely on its letter.
        wheel[slot] = MathUtils.round(wheel[slot]).toFloat()
        spinning = 0f
        slot = next
        game.audio.play(Sfx.UI_TICK, 1.35f)
        game.buzz()
    }

    /** A released wheel coasts, slows, and settles onto the nearest letter. */
    private fun coast(dt: Float) {
        if (dragPointer >= 0) return
        val pos = wheel[slot]
        if (kotlin.math.abs(spinning) > 1.5f) {
            setWheel(slot, pos + spinning * dt)
            spinning *= exp(-3.2f * dt)
        } else {
            spinning = 0f
            val rest = MathUtils.round(pos).toFloat()
            if (pos != rest) wheel[slot] = if (kotlin.math.abs(rest - pos) < 0.01f) rest else pos + (rest - pos) * (1f - exp(-20f * dt))
        }
    }

    override fun render(dt: Float) {
        age += dt
        if (entering) coast(dt)
        val party = rank > 0 || seedRank == 1
        g.rect(0f, 0f, Gfx.W, Gfx.H, Pal.BG)
        val cx = Gfx.W / 2f
        g.glow(cx, 470f, 360f, if (party) Pal.GOLD else Pal.RED, 0.14f)
        if (party) sparkles.celebrate(dt)
        sparkles.draw(dt)
        g.text(g.huge, "GAME OVER", cx, 518f, if (party) rainbow(age * 0.6f) else Pal.RED, 1f, Align.center)

        // The score counts up, because of course it does.
        val shown = (run.score * (age / 0.8f).coerceAtMost(1f)).toInt()
        g.text(g.big, shown.toString(), cx, 418f, Pal.TEXT, 1f, Align.center)
        g.text(g.tiny, "SCORE", cx, 382f, Pal.DIM, 1f, Align.center)

        stat(cx - 300f, "LEVEL", run.level.toString())
        stat(cx - 150f, "STARS", run.starsEaten.toString())
        stat(cx, "BEST COMBO", "x${run.bestCombo}")
        stat(cx + 150f, "STARDUST", "+$dust", Pal.STAR)
        stat(cx + 300f, "SEED", if (run.key.isEmpty()) "-" else SeedKey.pretty(run.key), Pal.CYAN)

        val pulse = 0.75f + 0.25f * MathUtils.sin(age * 6f)
        when {
            rank > 0 -> {
                val extra = (if (mastered) "  -  MASTERED" else "") + (if (seedRank > 0) "  -  #$seedRank ON THIS SEED" else "")
                g.text(g.body, "${if (arcade) "ARCADE HIGH SCORE!" else "HIGH SCORE!"}  RANK #$rank$extra", cx, 276f, rainbow(age * 0.9f), pulse, Align.center)
            }
            seedRank > 0 -> g.text(g.body, "#$seedRank ON SEED ${SeedKey.pretty(run.key)}", cx, 276f, Pal.CYAN, pulse, Align.center)
            else -> g.text(g.small, "${game.profile.stardust} stardust banked. Go spend it.", cx, 270f, Pal.DIM, 1f, Align.center)
        }

        if (entering) {
            val hint = if (game.keyHints) "TYPE YOUR INITIALS, THEN ENTER" else "SLIDE UP OR DOWN TO TURN  -  TAP LEFT OR RIGHT TO MOVE ALONG"
            g.text(g.tiny, hint, cx, 254f, Pal.DIM, 1f, Align.center)
            val cy = SLOT_Y + SLOT_H / 2f
            for (i in 0 until 3) {
                val x = slotX(i)
                val on = i == slot
                g.rect(x - SLOT_W / 2f, SLOT_Y, SLOT_W, SLOT_H, if (on) Pal.PANEL_HI else Pal.PANEL)
                g.frame(x - SLOT_W / 2f, SLOT_Y, SLOT_W, SLOT_H, 2f, if (on) Pal.GOLD else Pal.LINE)
                // The wheel: the letter in the window, and its neighbours fading away above and below.
                // Sliding up brings the next letter up from underneath.
                val pos = wheel[i]
                val at = MathUtils.round(pos)
                val off = pos - at
                for (k in -2..2) {
                    val dy = (off - k) * WHEEL_ROW
                    val fade = 1f - kotlin.math.abs(dy) / (WHEEL_ROW * 1.7f)
                    if (fade <= 0f) continue
                    val letter = 'A' + Math.floorMod(at + k, 26)
                    val centred = kotlin.math.abs(dy) < WHEEL_ROW / 2f
                    g.glyphAt(g.big, letter, x, cy + dy, if (centred) Pal.TEXT else Pal.DIM, if (centred) 1f else fade * (if (on) 0.8f else 0.3f))
                }
                if (on) {
                    val nudge = 2f * MathUtils.sin(age * 5f)
                    g.triangle(x - 8f, SLOT_Y + SLOT_H + 36f + nudge, x + 8f, SLOT_Y + SLOT_H + 36f + nudge, x, SLOT_Y + SLOT_H + 46f + nudge, Pal.GOLD)
                    g.triangle(x - 8f, SLOT_Y - 36f - nudge, x + 8f, SLOT_Y - 36f - nudge, x, SLOT_Y - 46f - nudge, Pal.GOLD)
                }
            }
        }
        drawButtons()
    }

    /** Cycles smoothly through the party palette. */
    private fun rainbow(t: Float): Color {
        val colors = WorldRenderer.PARTY
        val f = ((t % 1f) + 1f) % 1f * colors.size
        val i = f.toInt() % colors.size
        return g.mix(colors[i], colors[(i + 1) % colors.size], f - f.toInt())
    }

    private fun stat(x: Float, label: String, value: String, c: Color = Pal.TEXT) {
        g.text(g.body, value, x, 340f, c, 1f, Align.center)
        g.text(g.tiny, label, x, 312f, Pal.DIM, 1f, Align.center)
    }

    private fun slotX(i: Int): Float = Gfx.W / 2f - 60f + (i - 1) * (SLOT_W + 12f)

    override fun keyDown(key: Int): Boolean {
        if (!entering) return super.keyDown(key)
        when (key) {
            Keys.LEFT -> select(slot - 1)
            Keys.RIGHT -> select(slot + 1)
            Keys.UP -> spin(slot, 1)
            Keys.DOWN -> spin(slot, -1)
            Keys.BACKSPACE -> select(slot - 1)
            Keys.ENTER, Keys.NUMPAD_ENTER, Keys.ESCAPE, Keys.BACK -> { game.audio.play(Sfx.UI_OK); confirm() }
            else -> return false
        }
        return true
    }

    override fun keyTyped(c: Char) {
        if (!entering || !c.isLetter()) return
        val up = c.uppercaseChar()
        if (up !in 'A'..'Z') return
        spinning = 0f
        name[slot] = up
        wheel[slot] = (up - 'A').toFloat()
        slot = (slot + 1).coerceAtMost(2)
    }

    private fun slotAt(x: Float): Int = (0 until 3).firstOrNull { kotlin.math.abs(x - slotX(it)) <= SLOT_W / 2f + 6f } ?: -1

    override fun touchDown(x: Float, y: Float, pointer: Int) {
        // The buttons keep working as buttons; everywhere else on the screen is the wheel.
        if (!entering || buttons.any { it.usable && it.contains(x, y) }) {
            super.touchDown(x, y, pointer)
            return
        }
        if (dragPointer >= 0) return
        val hit = slotAt(x)
        dragOnSlot = hit >= 0
        if (dragOnSlot) select(hit)
        dragPointer = pointer
        dragY = y
        dragFrom = wheel[slot]
        dragMoved = false
        lastY = y
        lastT = age
        spinning = 0f
    }

    override fun touchDragged(x: Float, y: Float, pointer: Int) {
        if (pointer != dragPointer) return
        if (kotlin.math.abs(y - dragY) > DRAG_SLOP) dragMoved = true
        if (!dragMoved) return
        setWheel(slot, dragFrom + (y - dragY) / WHEEL_STEP)
        // Remember how fast the finger is going, for the flick at the end.
        val dt = age - lastT
        if (dt > 0.001f) {
            val v = (y - lastY) / WHEEL_STEP / dt
            spinning = if (dt > 0.12f) v else spinning * 0.5f + v * 0.5f
            lastY = y
            lastT = age
        }
    }

    override fun touchUp(x: Float, y: Float, pointer: Int) {
        if (pointer != dragPointer) {
            super.touchUp(x, y, pointer)
            return
        }
        dragPointer = -1
        if (dragMoved) {
            // A finger that stopped before lifting was placing the letter, not flicking it.
            if (age - lastT > 0.09f) spinning = 0f
            spinning = spinning.coerceIn(-MAX_FLICK, MAX_FLICK)
        } else if (!dragOnSlot) {
            // A plain tap beside the wheels: step to the next one on that side.
            select(if (x < Gfx.W / 2f - 60f) slot - 1 else slot + 1)
        }
    }

    private companion object {
        const val SLOT_W = 54f
        const val SLOT_H = 52f
        const val SLOT_Y = 138f

        /** Canvas pixels of finger travel per letter, and the spacing the letters are drawn at. */
        const val WHEEL_STEP = 26f
        const val WHEEL_ROW = 40f
        const val DRAG_SLOP = 7f
        const val MAX_FLICK = 40f
    }
}
