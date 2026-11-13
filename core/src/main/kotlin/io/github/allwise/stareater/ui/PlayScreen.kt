package io.github.allwise.stareater.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
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
    // An arcade run brings its own kit; otherwise it is whatever the skill tree has bought.
    private val world = World(spec, run.arcade?.loadout ?: game.profile.loadout(), run, run.seed xor (run.level * 104729L))
    private val renderer = WorldRenderer(game.gfx)
    private val controls = Controls()
    private val bot: Bot? = if (game.devBot) Bot() else null
    private var paused = false

    private var lastTick = 0
    private var klaxon = 0f

    /** Set when beating this level's boss opened a new place to start from. */
    private var unlocked = 0
    private var frameDt = 0f

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
        // The player beams in: a soft whoop to go with it.
        game.audio.play(Sfx.WRAP, 0.7f, 0.8f)
        button(Gfx.W / 2f - 140f, 268f, 280f, 44f, "RESUME") { paused = false }
        button(Gfx.W / 2f - 140f, 214f, 280f, 44f, "OPTIONS") { game.go(OptionsScreen(game) { game.go(this, show = false) }) }
        button(Gfx.W / 2f - 140f, 160f, 280f, 44f, "END RUN") { game.go(GameOverScreen(game, run)) }
    }

    /** Back from the options: still paused, and whatever was changed in there now applies. */
    override fun resume() {
        controls.reset()
    }

    private val tilting: Boolean get() = game.profile.tilt && game.canTilt

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
        frameDt = dt
        if (!paused) {
            controls.playerX = world.player.x * WorldRenderer.T
            controls.playerY = world.player.y * WorldRenderer.T
            // In arcade mode the dash and the nova come and go with the power-ups.
            controls.dashEnabled = world.loadout.dash
            controls.novaEnabled = world.loadout.nova
            if (tilting) {
                val i = Gdx.input
                // libGDX reports the sensor in a phone's axes on every device: on one whose natural way
                // up is landscape (most tablets) it turns the reading a quarter, so do the same here.
                val turn = if (i.nativeOrientation == Input.Orientation.Landscape) 90 else 0
                controls.tilt(i.accelerometerX, i.accelerometerY, i.accelerometerZ, (i.rotation + turn) % 360, TILT_SENSITIVITY[game.profile.tiltSens], dt)
            } else {
                controls.noTilt()
            }
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
            g.text(g.big, "PAUSED", Gfx.W / 2f, 368f, Pal.TEXT, 1f, Align.center)
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
            is Fx.BossDead -> {
                audio.play(Sfx.BOSS_DEAD)
                // A boss beaten is a checkpoint: from now on a run can begin on the level after it.
                unlocked = game.profile.beatBoss(spec.number)
            }
            is Fx.Cleared -> audio.play(Sfx.CLEAR)
            is Fx.Powered -> {
                audio.play(Sfx.POWER_UP)
                game.buzz()
            }
            is Fx.PowerLost -> audio.play(Sfx.POWER_DOWN)
            is Fx.EdgeWarn -> klaxon = 0f
            is Fx.EdgeSeal -> {
                audio.play(Sfx.SEAL)
                game.buzz()
            }
            is Fx.EdgeOpen -> audio.play(Sfx.UNSEAL)
            is Fx.StarLost, is Fx.EnemyGone -> {}
        }
        // The edge-block klaxon keeps honking for as long as the warning lasts.
        if (world.sealWarn > 0f) {
            klaxon -= frameDt
            if (klaxon <= 0f) { klaxon = 0.9f; audio.play(Sfx.SEAL_WARN) }
        }
        // The countdown clock ticks out its last five seconds.
        if (world.timeLeft in 0f..5f) {
            val second = world.timeLeft.toInt()
            if (second != lastTick) { lastTick = second; audio.play(Sfx.TICK) }
        }
    }

    /**
     * The floating stick, shown only while a finger (or the mouse) is down. Drawn in the overlay
     * pass because the thumb may well be out in the bar beside the picture - which is the point of
     * being able to move the picture over.
     */
    override fun overlay() {
        if (paused) return
        // With the picture slid to one side, the bar left over is the joystick: say so.
        val align = game.gfx.align
        if (align < 0) pad(Gfx.W, game.gfx.spareRight) else if (align > 0) pad(-game.gfx.spareLeft, game.gfx.spareLeft)
        if (!controls.stickOn) return
        g.ring(controls.stickOX, controls.stickOY, Controls.STICK_RANGE, 2f, Pal.CYAN, 0.25f)
        g.circle(controls.stickX, controls.stickY, 16f, Pal.CYAN, 0.3f)
    }

    /** Marks the spare strip beside the picture as the place to steer from: a pad with a stick at rest in it. */
    private fun pad(x: Float, width: Float) {
        if (width < 70f) return
        val x0 = x + 8f
        val w = width - 16f
        val y0 = 10f
        val h = Gfx.H - 20f
        val cx = x0 + w / 2f
        val cy = y0 + h * 0.42f
        g.rect(x0, y0, w, h, Pal.PANEL, 0.35f)
        g.frame(x0, y0, w, h, 1.5f, Pal.LINE, 0.7f)
        // Corner brackets, so it reads as a control and not as a border.
        val arm = minOf(22f, w / 4f)
        for (sx in intArrayOf(0, 1)) for (sy in intArrayOf(0, 1)) {
            val px = x0 + sx * w
            val py = y0 + sy * h
            val dx = if (sx == 0) arm else -arm
            val dy = if (sy == 0) arm else -arm
            g.line(px, py, px + dx, py, 3f, Pal.CYAN, 0.6f)
            g.line(px, py, px, py + dy, 3f, Pal.CYAN, 0.6f)
        }
        g.text(g.tiny, "STEER", cx, y0 + h - 14f, Pal.DIM, 0.9f, Align.center)
        // A finger down takes over: the live stick is drawn where the thumb actually is.
        if (controls.stickOn) return
        val r = minOf(Controls.STICK_RANGE, w / 2f - 14f)
        val breathe = 0.5f + 0.5f * kotlin.math.sin(age * 2.2f)
        g.ring(cx, cy, r, 2f, Pal.CYAN, 0.3f + 0.15f * breathe)
        g.circle(cx, cy, r * 0.3f, Pal.CYAN, 0.22f + 0.1f * breathe)
        for (i in 0 until 4) {
            val ux = kotlin.math.cos(i * 1.5708f)
            val uy = kotlin.math.sin(i * 1.5708f)
            val tx = cx + ux * (r + 12f)
            val ty = cy + uy * (r + 12f)
            g.triangle(tx + ux * 7f, ty + uy * 7f, tx - uy * 6f, ty + ux * 6f, tx + uy * 6f, ty - ux * 6f, Pal.CYAN, 0.35f)
        }
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
                if (unlocked > 0) g.text(g.small, "CHECKPOINT  -  NEW RUNS CAN NOW START AT LEVEL $unlocked", cx, cy - 62f, Pal.CYAN, a, Align.center)
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
                    tilting -> if (spec.move == MoveKind.GRID) "tilt to step  -  this is level" else "tilt to fly  -  this is level"
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

    private companion object {
        /** Gentle, normal, twitchy. */
        val TILT_SENSITIVITY = floatArrayOf(0.65f, 1f, 1.6f)
    }
}
