package io.github.allwise.stareater.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Align
import io.github.allwise.stareater.StarEaterGame
import io.github.allwise.stareater.audio.Sfx
import io.github.allwise.stareater.gfx.Gfx
import io.github.allwise.stareater.gfx.Pal
import io.github.allwise.stareater.meta.Profile

/**
 * Volumes, where the picture sits, and - on a phone - tilt steering and vibration.
 * Reached from the title screen and from the pause menu; [onBack] returns to whichever it was.
 */
class OptionsScreen(game: StarEaterGame, private val onBack: () -> Unit) : Screen(game) {
    private val align = ArrayList<Button>()
    private val sens = ArrayList<Button>()
    private var tilt: Button? = null
    private var buzz: Button? = null
    private var rowTilt = 0f
    private var rowBuzz = 0f

    override fun show() {
        val p = game.profile
        button(16f, Gfx.H - 50f, 110f, 36f, "BACK") { back() }.small = true

        // A muted channel reads as zero, so the first press of + starts from the bottom.
        stepper(ROW_MUSIC, { if (p.musicOn) p.musicVol else 0 }) {
            p.musicVol = it
            // Turning it up is as good as switching it back on.
            p.musicOn = it > 0
            game.audio.volumeChanged()
        }
        stepper(ROW_SOUND, { if (p.sfxOn) p.sfxVol else 0 }) {
            p.sfxVol = it
            p.sfxOn = it > 0
            game.audio.play(Sfx.UI_OK)
        }.forEach { it.silent = true }

        for ((i, name) in listOf("LEFT", "CENTRE", "RIGHT").withIndex()) {
            align += button(CONTROLS + i * 132f, ROW_SCREEN - 20f, 124f, 40f, name) {
                p.screenAlign = i - 1
                game.gfx.align = p.screenAlign
            }
        }

        // The last two rows only exist where the hardware does.
        var y = ROW_SCREEN - 76f
        if (game.canTilt) {
            rowTilt = y
            tilt = button(CONTROLS, y - 20f, 124f, 40f, "") { p.tilt = !p.tilt }
            for ((i, name) in listOf("GENTLE", "NORMAL", "TWITCHY").withIndex()) {
                sens += button(CONTROLS + 132f + i * 88f, y - 20f, 84f, 40f, name) { p.tiltSens = i }.also { it.small = true }
            }
            y -= 62f
        }
        if (game.canBuzz) {
            rowBuzz = y
            buzz = button(CONTROLS, y - 20f, 124f, 40f, "") {
                p.haptics = !p.haptics
                game.buzz()
            }
        }
    }

    /** A minus and a plus button either side of a ten-step bar. */
    private fun stepper(y: Float, get: () -> Int, set: (Int) -> Unit): List<Button> = listOf(
        button(CONTROLS, y - 20f, 48f, 40f, "-") { set(get() - 1) },
        button(CONTROLS + 340f, y - 20f, 48f, 40f, "+") { set(get() + 1) },
    )

    override fun back() {
        game.profile.save()
        onBack()
    }

    override fun render(dt: Float) {
        age += dt
        val p = game.profile
        g.rect(0f, 0f, Gfx.W, Gfx.H, Pal.BG)
        title("OPTIONS")

        label(ROW_MUSIC, "MUSIC", if (p.musicOn) "" else if (game.keyHints) "muted - M brings it back" else "muted")
        bar(ROW_MUSIC, if (p.musicOn) p.musicVol else 0, Pal.CYAN)
        label(ROW_SOUND, "SOUND", "")
        bar(ROW_SOUND, if (p.sfxOn) p.sfxVol else 0, Pal.GOLD)

        label(ROW_SCREEN, "SCREEN", "on a wide screen: leave a side free for your thumb")
        for ((i, b) in align.withIndex()) b.accent = if (i - 1 == p.screenAlign) Pal.GOLD else Pal.CYAN
        marker(align[p.screenAlign + 1])

        tilt?.let {
            label(rowTilt, "TILT STEERING", "lean the phone to steer - level is however you hold it at the start")
            it.label = if (p.tilt) "ON" else "OFF"
            for (b in sens) b.enabled = p.tilt
            if (p.tilt) marker(sens[p.tiltSens])
        }
        buzz?.let {
            label(rowBuzz, "VIBRATION", "")
            it.label = if (p.haptics) "ON" else "OFF"
        }
        drawButtons()
    }

    private fun label(y: Float, name: String, note: String) {
        g.text(g.body, name, LABELS, y + 8f, Pal.TEXT)
        if (note.isNotEmpty()) g.paragraph(g.tiny, note, LABELS, y - 16f, CONTROLS - LABELS - 24f, Pal.DIM)
    }

    private fun bar(y: Float, value: Int, c: Color) {
        val x0 = CONTROLS + 60f
        for (i in 0 until Profile.VOLUME_MAX) {
            val on = i < value
            g.rect(x0 + i * 27f, y - 12f, 21f, 24f, if (on) c else Pal.PANEL_HI, if (on) 1f else 0.8f)
        }
        // A notch at the level the game was mixed for.
        g.rect(x0 + Profile.VOLUME_DEFAULT * 27f - 4f, y - 20f, 2f, 5f, Pal.DIM)
        g.text(g.tiny, if (value == 0) "OFF" else value.toString(), CONTROLS + 404f, y + 5f, Pal.DIM, 1f, Align.left)
    }

    /** Underlines the chosen one of a row of either/or buttons. */
    private fun marker(b: Button) {
        g.rect(b.x, b.y - 6f, b.w, 3f, Pal.GOLD)
    }

    private companion object {
        const val LABELS = 120f
        const val CONTROLS = 430f
        const val ROW_MUSIC = 420f
        const val ROW_SOUND = 350f
        const val ROW_SCREEN = 274f
    }
}
