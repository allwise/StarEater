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
import io.github.allwise.stareater.sim.Arcade
import io.github.allwise.stareater.sim.Bot
import io.github.allwise.stareater.sim.GameMode
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

    private lateinit var from: Button

    private fun newDemo(): World {
        bot = Bot()
        val level = 1 + rng.nextInt(9)
        // The attract mode shows off a bit of everything, bosses included.
        val spec = if (rng.nextInt(7) == 0) LevelGen.bossLevel(10 * (1 + rng.nextInt(3)), rng) else LevelGen.generate(level, rng)
        // Every third demo is an arcade one, so the power-ups get shown off too.
        val run = RunState(0)
        if (rng.nextInt(3) == 0) run.arcade = Arcade()
        return World(spec, run.arcade?.loadout ?: Skill.loadout(emptySet()), run, rng.nextLong())
    }

    override fun show() {
        game.audio.tune(Tune.MENU)
        // Two columns: the ways to play on the left, everything else on the right.
        val w = 275f
        val left = Gfx.W / 2f - w - 10f
        val right = Gfx.W / 2f + 10f
        val p = game.profile
        fun row(i: Int): Float = 322f - i * 43f

        // The campaign shares its row with a start-level picker, once a boss has been beaten.
        val wide = p.checkpoint == 0 || p.startLevels().size < 2
        button(left, row(0), if (wide) w else w - 98f, 37f, "CAMPAIGN") { game.go(IntroScreen(game, SeedKey.newRun(SeedKey.random(), level = p.startLevel))) }
        from = button(left + w - 90f, row(0), 90f, 37f, "") {
            val levels = p.startLevels()
            p.startLevel = levels[(levels.indexOf(p.startLevel) + 1) % levels.size]
            p.save()
        }
        from.small = true
        from.visible = !wide

        // The other three modes open one boss at a time.
        for ((i, mode) in listOf(GameMode.MATRIX, GameMode.FLOW, GameMode.ENDLESS).withIndex()) {
            val open = p.unlocked(mode)
            val b = button(left, row(i + 1), w, 37f, if (open) mode.title else "LOCKED - BEAT BOSS ${mode.ordinal}") {
                game.go(PocketShop(game, SeedKey.newRun(SeedKey.random(), mode), pitStop = false))
            }
            b.enabled = open
            b.small = !open
            b.accent = Pal.GOLD
        }
        button(left, row(4), w, 37f, "SEEDED RUN") { game.go(SeedScreen(game)) }

        button(right, row(0), w, 37f, "SKILL TREE") { game.go(SkillScreen(game)) }
        button(right, row(1), w, 37f, "HIGH SCORES") { game.go(ScoresScreen(game)) }
        button(right, row(2), w, 37f, "HOW TO PLAY") { game.go(HelpScreen(game)) }
        button(right, row(3), w, 37f, "OPTIONS") { game.go(OptionsScreen(game) { game.go(MenuScreen(game)) }) }
        if (Gdx.app.type == Application.ApplicationType.Desktop) button(right, row(4), w, 37f, "QUIT") { Gdx.app.exit() }
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
        g.glow(cx, 450f, 330f, Pal.CYAN, 0.16f)
        g.text(g.huge, "STAR EATER", cx, 490f + bob, Pal.TEXT, 1f, Align.center)
        g.text(g.small, "o  eats  *     E and S eat  o     the edges are a lie", cx, 408f, Pal.CYAN, 0.9f, Align.center)
        g.star(cx - 300f, 460f + bob, 20f, 8f, 5, 90f + age * 30f, Pal.GOLD)
        g.star(cx + 300f, 460f + bob, 20f, 8f, 5, 90f - age * 30f, Pal.GOLD)

        from.label = "FROM ${game.profile.startLevel}"
        drawButtons()

        val p = game.profile
        g.star(24f, 26f, 9f, 4f, 5, 90f, Pal.STAR)
        g.text(g.body, "${p.stardust} STARDUST", 42f, 34f, Pal.STAR)
        val skills = "${p.owned.size}/${Skill.values().size} SKILLS"
        g.text(g.small, if (p.mastered) "MASTERED - $skills" else skills, 42f, 58f, if (p.mastered) Pal.GOLD else Pal.DIM)
        if (p.bestLevel > 0) g.text(g.small, "BEST: LEVEL ${p.bestLevel}   RUNS: ${p.totalRuns}", Gfx.W - 20f, 40f, Pal.DIM, 1f, Align.right)
    }
}
