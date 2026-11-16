package io.github.allwise.stareater.ui

import io.github.allwise.stareater.StarEaterGame
import io.github.allwise.stareater.gfx.Gfx
import io.github.allwise.stareater.gfx.Pal

/**
 * The options, as reached from the title screen. The settings themselves are an [OptionsPanel];
 * the pause menu shows the same one without leaving the level.
 */
class OptionsScreen(game: StarEaterGame) : Screen(game) {
    private val panel = OptionsPanel(this, game)

    override fun show() {
        button(16f, Gfx.H - 50f, 110f, 36f, "BACK") { back() }.small = true
        panel.build()
    }

    override fun back() {
        game.profile.save()
        game.go(MenuScreen(game, more = true))
    }

    override fun render(dt: Float) {
        age += dt
        g.rect(0f, 0f, Gfx.W, Gfx.H, Pal.BG)
        title("OPTIONS")
        panel.draw()
        drawButtons()
    }
}
