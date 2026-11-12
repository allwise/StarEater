package io.github.allwise.stareater.ui

import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.utils.Align
import io.github.allwise.stareater.StarEaterGame
import io.github.allwise.stareater.audio.Audio
import io.github.allwise.stareater.audio.Sfx
import io.github.allwise.stareater.audio.Tune
import io.github.allwise.stareater.sim.Fx
import io.github.allwise.stareater.sim.StarType
import io.github.allwise.stareater.gfx.Gfx
import io.github.allwise.stareater.gfx.Pal
import io.github.allwise.stareater.gfx.WorldRenderer
import io.github.allwise.stareater.input.Controls
import io.github.allwise.stareater.sim.Bot
import io.github.allwise.stareater.sim.LevelSpec
import io.github.allwise.stareater.sim.MoveKind
import io.github.allwise.stareater.sim.RunState
import io.github.allwise.stareater.sim.World

/** The game itself: one level, from first step to cleared-or-eaten. */
class PlayScreen(game: StarEaterGame, private val run: RunState, private val spec: LevelSpec) : Screen(game) {
    private val world = World(spec, game.profile.loadout(), run, run.seed xor (run.level * 104729L))
    private val renderer = WorldRenderer(game.gfx)
    private val controls = Controls()
    private val bot: Bot? = if (game.devBot) Bot() else null
    private var paused = false

    private var lastTick = 0

    override fun show() {
        game.audio.tune(
            when {
                spec.boss != null -> Tune.BOSS
                spec.move == MoveKind.GRID -> Tune.CLASSIC
                spec.move == MoveKind.GLIDE -> Tune.GLIDE
                spec.move == MoveKind.SCROLL -> Tune.SCROLL
                else -> Tune.ORBIT
            },
        )
        controls.dashEnabled = world.loadout.dash
        controls.novaEnabled = world.loadout.nova
        button(Gfx.W / 2f - 140f, 250f, 280f, 44f, "RESUME") { paused = false }
        button(Gfx.W / 2f - 140f, 196f, 280f, 44f, "END RUN") { game.go(GameOverScreen(game, run)) }
    }

    fun pauseGame() {
        if (world.phase == World.Phase.PLAYING) {
            paused = true
            controls.reset()
        }
    }

    override fun back() {
        if (paused) paused = false else pauseGame()
    }

    override fun render(dt: Float) {
        age += dt
        if (!paused) {
            controls.playerX = world.player.x * WorldRenderer.T
            controls.playerY = world.player.y * WorldRenderer.T
            controls.update(dt)
            val input = controls.state
            bot?.drive(world, input)
            world.update(dt, input)
            input.clearEdges()
            sounds()
            renderer.consume(world)
        }
        renderer.draw(world, if (paused) 0f else dt)
        renderer.hud(world, game.keyHints)
        stick()
        banner()

        when (world.phase) {
            World.Phase.CLEARED -> if (world.phaseTime > 1.7f) {
                run.level++
                run.prevMove = spec.move
                game.go(IntroScreen(game, run))
            }
            World.Phase.DEAD -> if (world.phaseTime > 1.6f) game.go(GameOverScreen(game, run))
            else -> {}
        }

        if (paused) {
            g.rect(0f, 0f, Gfx.W, Gfx.H, Pal.BG, 0.75f)
            g.text(g.big, "PAUSED", Gfx.W / 2f, 350f, Pal.TEXT, 1f, Align.center)
            drawButtons()
        }
    }

    /** Gives this frame's events a voice. Must run before the renderer swallows them. */
    private fun sounds() {
        val audio = game.audio
        for (e in world.fx) when (e) {
            is Fx.StarEaten -> {
                // The pickup blip climbs a scale with the combo.
                audio.play(Sfx.EAT, Audio.comboPitch(e.combo))
                if (e.type == StarType.NOVA) audio.play(Sfx.BLAST) else if (e.type.value >= 3) audio.play(Sfx.EAT_RARE)
            }
            is Fx.ShieldHit -> audio.play(Sfx.SHIELD)
            is Fx.Revive -> audio.play(Sfx.REVIVE)
            is Fx.Death -> {
                audio.play(Sfx.DEATH)
                audio.tune(null)
            }
            is Fx.Dash -> audio.play(Sfx.DASH)
            is Fx.Nova -> audio.play(Sfx.BLAST, 1.2f, 0.7f)
            is Fx.Wrap -> audio.play(Sfx.WRAP)
            is Fx.EnemySpawn -> audio.play(Sfx.SPAWN)
            is Fx.Stun -> audio.play(Sfx.STUN)
            is Fx.Alert -> audio.play(Sfx.ALERT)
            is Fx.MeteorWarn -> audio.play(Sfx.WARN)
            is Fx.TimeUp -> audio.play(Sfx.UI_NO)
            is Fx.BossHit -> audio.play(Sfx.BOSS_HIT)
            is Fx.BossDead -> audio.play(Sfx.BOSS_DEAD)
            is Fx.Cleared -> audio.play(Sfx.CLEAR)
            is Fx.StarLost, is Fx.EnemyGone -> {}
        }
        // The countdown clock ticks out its last five seconds.
        if (world.timeLeft in 0f..5f) {
            val second = world.timeLeft.toInt()
            if (second != lastTick) { lastTick = second; audio.play(Sfx.TICK) }
        }
    }

    /** The floating stick, shown only while a finger (or the mouse) is down. */
    private fun stick() {
        if (!controls.stickOn || paused) return
        g.ring(controls.stickOX, controls.stickOY, Controls.STICK_RANGE, 2f, Pal.CYAN, 0.25f)
        g.circle(controls.stickX, controls.stickY, 16f, Pal.CYAN, 0.3f)
    }

    private fun banner() {
        val cx = Gfx.W / 2f
        val cy = WorldRenderer.FIELD_H / 2f
        when (world.phase) {
            World.Phase.CLEARED -> {
                val a = (world.phaseTime * 4f).coerceAtMost(1f)
                g.glow(cx, cy, 300f, Pal.STAR, 0.25f * a)
                g.text(g.huge, "CLEAR!", cx, cy + 40f, Pal.STAR, a, Align.center)
                g.text(g.body, "+${world.clearBonus} BONUS", cx, cy - 28f, Pal.TEXT, a, Align.center)
            }
            World.Phase.DEAD -> {
                val a = ((world.phaseTime - 0.3f) * 3f).coerceIn(0f, 1f)
                g.text(g.huge, "EATEN", cx, cy + 34f, Pal.RED, a, Align.center)
            }
            else -> if (!world.player.started && !paused) {
                // Until the first move, remind the player what this level's movement is.
                val a = (age * 3f).coerceAtMost(1f) * 0.9f
                g.text(g.body, spec.move.title, cx, cy + 96f, Pal.CYAN, a, Align.center)
                val how = when {
                    !game.keyHints -> if (spec.move == MoveKind.GRID) "tap or drag to step" else "drag to fly"
                    spec.move == MoveKind.GRID -> "arrows / WASD: one tap, one step"
                    else -> "arrows / WASD to fly"
                }
                g.text(g.small, how, cx, cy + 70f, Pal.DIM, a, Align.center)
            }
        }
    }

    override fun keyDown(key: Int): Boolean {
        if (paused) return super.keyDown(key)
        if (key == Keys.ESCAPE || key == Keys.BACK || key == Keys.P) { pauseGame(); return true }
        return controls.keyDown(key)
    }

    override fun keyUp(key: Int) = controls.keyUp(key)

    override fun touchDown(x: Float, y: Float, pointer: Int) {
        if (paused) super.touchDown(x, y, pointer) else controls.touchDown(x, y, pointer)
    }

    override fun touchUp(x: Float, y: Float, pointer: Int) {
        if (paused) super.touchUp(x, y, pointer) else controls.touchUp(x, y, pointer)
    }

    override fun touchDragged(x: Float, y: Float, pointer: Int) {
        if (!paused) controls.touchDragged(x, y, pointer)
    }

    override fun mouseMoved(x: Float, y: Float) {
        if (paused) super.mouseMoved(x, y)
    }
}
