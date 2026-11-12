package io.github.allwise.stareater.ui

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Align
import io.github.allwise.stareater.StarEaterGame
import io.github.allwise.stareater.audio.Tune
import io.github.allwise.stareater.meta.SeedKey
import io.github.allwise.stareater.gfx.Gfx
import io.github.allwise.stareater.gfx.Pal
import io.github.allwise.stareater.gfx.WorldRenderer
import io.github.allwise.stareater.meta.Skill
import io.github.allwise.stareater.sim.Bot
import io.github.allwise.stareater.sim.InputState
import io.github.allwise.stareater.sim.LevelGen
import io.github.allwise.stareater.sim.RunState
import io.github.allwise.stareater.sim.World
import kotlin.random.Random

/** Title screen. The bot plays random levels behind the menu as an attract mode. */
class MenuScreen(game: StarEaterGame) : Screen(game) {
    private val renderer = WorldRenderer(game.gfx)
    private val rng = Random(System.nanoTime())
    private val input = InputState()
    private var bot = Bot()
    private var demo = newDemo()

    private fun newDemo(): World {
        bot = Bot()
        val level = 1 + rng.nextInt(9)
        // The attract mode shows off a bit of everything, bosses included.
        val spec = if (rng.nextInt(7) == 0) LevelGen.bossLevel(10 * (1 + rng.nextInt(3)), rng) else LevelGen.generate(level, rng)
        return World(spec, Skill.loadout(emptySet()), RunState(0), rng.nextLong())
    }

    private lateinit var music: Button
    private lateinit var sound: Button

    override fun show() {
        game.audio.tune(Tune.MENU)
        val w = 280f
        val x = Gfx.W / 2f - w / 2f
        var y = 302f
        fun add(label: String, action: () -> Unit) {
            button(x, y, w, 40f, label, action)
            y -= 46f
        }
        add("PLAY") { game.go(IntroScreen(game, SeedKey.newRun(SeedKey.random()))) }
        add("SEEDED RUN") { game.go(SeedScreen(game)) }
        add("SKILL TREE") { game.go(SkillScreen(game)) }
        add("HIGH SCORES") { game.go(ScoresScreen(game)) }
        add("HOW TO PLAY") { game.go(HelpScreen(game)) }
        if (Gdx.app.type == Application.ApplicationType.Desktop) add("QUIT") { Gdx.app.exit() }
        music = button(Gfx.W - 252f, 14f, 114f, 30f, "") { game.audio.toggleMusic() }
        sound = button(Gfx.W - 130f, 14f, 114f, 30f, "") { game.audio.toggleSfx() }
        music.small = true
        sound.small = true
        sound.silent = true
    }

    override fun back() {
        if (Gdx.app.type == Application.ApplicationType.Desktop) Gdx.app.exit()
    }

    override fun render(dt: Float) {
        age += dt
        input.clearEdges()
        bot.drive(demo, input)
        demo.update(dt, input)
        renderer.consume(demo)
        renderer.draw(demo, dt)
        if (demo.phase != World.Phase.PLAYING && demo.phaseTime > 1.6f) demo = newDemo()
        g.rect(0f, 0f, Gfx.W, Gfx.H, Pal.BG, 0.72f)

        val cx = Gfx.W / 2f
        val bob = MathUtils.sin(age * 1.6f) * 3f
        g.glow(cx, 430f, 330f, Pal.CYAN, 0.16f)
        g.text(g.huge, "STAR EATER", cx, 470f + bob, Pal.TEXT, 1f, Align.center)
        g.text(g.small, "o  eats  *     E and S eat  o     the edges are a lie", cx, 388f, Pal.CYAN, 0.9f, Align.center)
        g.star(cx - 300f, 440f + bob, 20f, 8f, 5, 90f + age * 30f, Pal.GOLD)
        g.star(cx + 300f, 440f + bob, 20f, 8f, 5, 90f - age * 30f, Pal.GOLD)

        music.label = if (game.profile.musicOn) "MUSIC ON" else "MUSIC OFF"
        sound.label = if (game.profile.sfxOn) "SOUND ON" else "SOUND OFF"
        drawButtons()

        val p = game.profile
        g.star(24f, 26f, 9f, 4f, 5, 90f, Pal.STAR)
        g.text(g.body, "${p.stardust} STARDUST", 42f, 34f, Pal.STAR)
        val skills = "${p.owned.size}/${Skill.values().size} SKILLS"
        g.text(g.small, if (p.mastered) "MASTERED - $skills" else skills, 42f, 58f, if (p.mastered) Pal.GOLD else Pal.DIM)
        if (p.bestLevel > 0) g.text(g.small, "BEST: LEVEL ${p.bestLevel}   RUNS: ${p.totalRuns}", Gfx.W - 20f, 66f, Pal.DIM, 1f, Align.right)
    }
}
