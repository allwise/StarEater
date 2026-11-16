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

/**
 * Title screen. The bot plays random levels behind the menu as an attract mode.
 *
 * Two pages of one column each. The front one is only what there is to play - a mode shows up there
 * when its boss has been beaten, not before - plus the skill tree. Everything else is behind MORE;
 * pass [more] to open on that page, which is where the screens reached from it come back to.
 */
class MenuScreen(game: StarEaterGame, private var more: Boolean = false) : Screen(game) {
    private val renderer = WorldRenderer(game.gfx)
    private val rng = Random(System.nanoTime())
    private val input = InputState()
    private var bot = Bot()
    private var demo = newDemo()

    private lateinit var from: Button
    private var picker = false
    private val front = ArrayList<Button>()
    private val extras = ArrayList<Button>()

    /** What the next boss opens, said in a line under the front page rather than with a dead button. */
    private var hint = ""
    private var hintY = 0f

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
        val p = game.profile
        val w = 300f
        val x = Gfx.W / 2f - w / 2f
        var rows = 0
        fun next(): Float = TOP - rows++ * PITCH

        // The campaign shares its row with a start-level picker, once a boss has been beaten.
        picker = p.checkpoint > 0 && p.startLevels().size >= 2
        val y0 = next()
        front += button(x, y0, if (picker) w - 98f else w, 37f, "START") { game.go(IntroScreen(game, SeedKey.newRun(SeedKey.random(), level = p.startLevel))) }
        from = button(x + w - 90f, y0, 90f, 37f, "") {
            val levels = p.startLevels()
            p.startLevel = levels[(levels.indexOf(p.startLevel) + 1) % levels.size]
            p.save()
        }
        from.small = true

        // The other three modes open one boss at a time, and are not on the menu until they do.
        val modes = listOf(GameMode.MATRIX, GameMode.FLOW, GameMode.ENDLESS)
        for (mode in modes.filter { p.unlocked(it) }) {
            front += button(x, next(), w, 37f, mode.title) {
                game.go(PocketShop(game, SeedKey.newRun(SeedKey.random(), mode), pitStop = false))
            }.also { it.accent = Pal.GOLD }
        }
        front += button(x, next(), w, 37f, "SKILL TREE") { game.go(SkillScreen(game)) }
        front += button(x, next(), w, 37f, "MORE") { turn(true) }
        modes.firstOrNull { !p.unlocked(it) }?.let { hint = "BEAT BOSS ${it.ordinal} TO OPEN ${it.title}" }
        hintY = TOP - rows * PITCH + 26f

        rows = 0
        extras += button(x, next(), w, 37f, "SEEDED RUN") { game.go(SeedScreen(game)) }
        extras += button(x, next(), w, 37f, "HIGH SCORES") { game.go(ScoresScreen(game)) }
        extras += button(x, next(), w, 37f, "HOW TO PLAY") { game.go(HelpScreen(game)) }
        extras += button(x, next(), w, 37f, "OPTIONS") { game.go(OptionsScreen(game)) }
        if (Gdx.app.type == Application.ApplicationType.Desktop) extras += button(x, next(), w, 37f, "QUIT") { Gdx.app.exit() }
        extras += button(x, next(), w, 37f, "BACK") { turn(false) }
        turn(more)
    }

    /** Shows one page's buttons and hides the other's, with the focus on the top one. */
    private fun turn(toMore: Boolean) {
        more = toMore
        for (b in front) b.visible = !more
        for (b in extras) b.visible = more
        from.visible = !more && picker
        focus = buttons.indexOfFirst { it.usable }
    }

    override fun back() {
        if (more) turn(false) else if (Gdx.app.type == Application.ApplicationType.Desktop) Gdx.app.exit()
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
        // On a window wider than the picture the demo would end in a hard edge: melt it into the bars.
        if (g.spareLeft > 1f || g.spareRight > 1f) {
            for (i in 0 until FADE) {
                val a = 1f - (i + 0.5f) / FADE
                g.rect(i * 6f, 0f, 6f, Gfx.H, Pal.BG, a * a)
                g.rect(Gfx.W - (i + 1) * 6f, 0f, 6f, Gfx.H, Pal.BG, a * a)
            }
        }

        val cx = Gfx.W / 2f
        val bob = MathUtils.sin(age * 1.6f) * 3f
        g.glow(cx, 450f, 330f, Pal.CYAN, 0.16f)
        g.text(g.huge, "STAR EATER", cx, 490f + bob, Pal.TEXT, 1f, Align.center)
        g.text(g.small, "o  eats  *     E and S eat  o     the edges are a lie", cx, 408f, Pal.CYAN, 0.9f, Align.center)
        g.star(cx - 300f, 460f + bob, 20f, 8f, 5, 90f + age * 30f, Pal.GOLD)
        g.star(cx + 300f, 460f + bob, 20f, 8f, 5, 90f - age * 30f, Pal.GOLD)

        from.label = "FROM ${game.profile.startLevel}"
        drawButtons()
        if (!more && hint.isNotEmpty()) g.text(g.tiny, hint, cx, hintY, Pal.DIM, 0.9f, Align.center)

        val p = game.profile
        g.star(24f, 26f, 9f, 4f, 5, 90f, Pal.STAR)
        g.text(g.body, "${p.stardust} STARDUST", 42f, 34f, Pal.STAR)
        val skills = "${p.owned.size}/${Skill.values().size} SKILLS"
        g.text(g.small, if (p.mastered) "MASTERED - $skills" else skills, 42f, 58f, if (p.mastered) Pal.GOLD else Pal.DIM)
        if (p.bestLevel > 0) g.text(g.small, "BEST: LEVEL ${p.bestLevel}   RUNS: ${p.totalRuns}", Gfx.W - 20f, 40f, Pal.DIM, 1f, Align.right)
    }

    private companion object {
        /** The top button's y, and the step from one row to the next. */
        const val TOP = 322f
        const val PITCH = 43f

        /** Strips in the fade at each side of the demo. */
        const val FADE = 16
    }
}
