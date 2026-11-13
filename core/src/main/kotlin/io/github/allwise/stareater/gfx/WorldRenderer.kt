package io.github.allwise.stareater.gfx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Align
import io.github.allwise.stareater.meta.SeedKey
import io.github.allwise.stareater.sim.Arcade
import io.github.allwise.stareater.sim.Body
import io.github.allwise.stareater.sim.Bouncer
import io.github.allwise.stareater.sim.Capsule
import io.github.allwise.stareater.sim.Devourer
import io.github.allwise.stareater.sim.Eater
import io.github.allwise.stareater.sim.Enemy
import io.github.allwise.stareater.sim.EnemyType
import io.github.allwise.stareater.sim.Fx
import io.github.allwise.stareater.sim.Geo
import io.github.allwise.stareater.sim.Hive
import io.github.allwise.stareater.sim.Leviathan
import io.github.allwise.stareater.sim.LevelSpec
import io.github.allwise.stareater.sim.Mite
import io.github.allwise.stareater.sim.MoveKind
import io.github.allwise.stareater.sim.PowerUp
import io.github.allwise.stareater.sim.Rules
import io.github.allwise.stareater.sim.Stalker
import io.github.allwise.stareater.sim.Star
import io.github.allwise.stareater.sim.StarType
import io.github.allwise.stareater.sim.Sun
import io.github.allwise.stareater.sim.Warden
import io.github.allwise.stareater.sim.Warper
import io.github.allwise.stareater.sim.World
import io.github.allwise.stareater.sim.Zoomer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Draws a [World] through a camera, and owns everything that is purely for show: particles,
 * shockwaves, score pop-ups, lightning, fireworks.
 *
 * The view is always 30x15 tiles. When the world is that size the camera sits still (the classic
 * look); when it is bigger the camera follows the player around the torus.
 */
class WorldRenderer(private val g: Gfx) {
    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float, val size: Float, val color: Color)
    private class Ring(val x: Float, val y: Float, val to: Float, var life: Float, val maxLife: Float, val color: Color)
    private class Floater(val text: String, val x: Float, var y: Float, var life: Float, val color: Color)
    private class Shard(var x: Float, var y: Float, var vx: Float, var vy: Float, var age: Float, val color: Color)
    private class Bolt(val x0: Float, val y0: Float, val x1: Float, val y1: Float, var life: Float, val color: Color)
    private class Pop(val text: String, var life: Float, val color: Color)
    private class Firework(val x: Float, val y: Float, var delay: Float, val color: Color)

    private val creatures = Creatures(g)
    private val powerArt = PowerArt(g)
    private val particles = ArrayList<Particle>()
    private val rings = ArrayList<Ring>()
    private val floaters = ArrayList<Floater>()
    private val shards = ArrayList<Shard>()
    private val bolts = ArrayList<Bolt>()
    private val pops = ArrayList<Pop>()
    private val fireworks = ArrayList<Firework>()
    private val rnd = Random(7)
    private val specks = FloatArray(SPECKS * 4) { rnd.nextFloat() }
    private val nebula = FloatArray(NEBULAE * 4)
    private val nebulaColors = arrayOfNulls<Color>(NEBULAE)
    private val tmp = Color()

    private var time = 0f
    private var frameDt = 0f
    private var shake = 0f
    private var flash = 0f
    private val flashColor = Color(Color.WHITE)
    private var thrustAcc = 0f
    private var eatPulse = 0f
    private var hudPulse = 0f
    private var scoreBump = 0f
    private var lastPop = 1
    private var meteorTime = 0f
    private var streakT = 4f
    private var streakAge = 1f
    private var streakX = 0f
    private var streakY = 0f
    private var sealSlam = 0f
    private var world: World? = null

    // Camera: centre of the view in tiles, and the size of the world it is looking at.
    private var camX = VIEW_COLS / 2f
    private var camY = VIEW_ROWS / 2f
    private var ww = VIEW_COLS.toFloat()
    private var wh = VIEW_ROWS.toFloat()
    private var follow = false
    private var wrapsY = true

    /** Screen shake offset this frame. */
    private var ox = 0f
    private var oy = 0f

    // ----------------------------------------------------------------------------------------
    // Events -> effects
    // ----------------------------------------------------------------------------------------

    /** Turns the sim's events into effects. Call once per frame, before [draw]. */
    fun consume(w: World) {
        for (e in w.fx) when (e) {
            is Fx.StarEaten -> {
                val c = starColor(e.type)
                burst(e.x, e.y, min(30, 8 + e.type.value * 2), c, 3f + 0.25f * e.type.value)
                ring(e.x, e.y, 1.1f + 0.18f * e.type.value, 0.35f, c)
                floaters += Floater(if (e.combo > 1) "+${e.points} x${e.combo}" else "+${e.points}", e.x, e.y + 0.45f, 0.9f, c)
                // A few shards fly up to the quota counter.
                if (world === w) {
                    repeat(min(6, 1 + e.type.value)) {
                        val a = rnd.nextFloat() * MathUtils.PI2
                        val s = 160f + 220f * rnd.nextFloat()
                        if (shards.size < 60) shards += Shard(px(e.x), py(e.y), MathUtils.cos(a) * s, MathUtils.sin(a) * s, 0f, c)
                    }
                }
                eatPulse = 1f
                scoreBump = 1f
                if (e.combo <= 1) lastPop = 1
                if (e.combo > lastPop && (e.combo == 3 || e.combo == 5 || e.combo == 7 || e.combo == World.MAX_COMBO)) {
                    lastPop = e.combo
                    pops += Pop(if (e.combo == World.MAX_COMBO) "MAX COMBO!" else "COMBO x${e.combo}", 1.1f, comboColor(e.combo))
                    flashWith(comboColor(e.combo), 0.12f)
                }
            }
            is Fx.StarLost -> burst(e.x, e.y, 5, Pal.DIM, 1.3f)
            is Fx.ShieldHit -> {
                shake = 7f
                ring(e.x, e.y, 3.5f, 0.45f, Pal.SHIELD)
                flashWith(Pal.SHIELD, 0.18f)
                lastPop = 1
            }
            is Fx.Revive -> {
                shake = 12f
                ring(e.x, e.y, 9f, 0.7f, Pal.GOLD)
                flashWith(Pal.GOLD, 0.4f)
                pops += Pop("SECOND WIND!", 1.3f, Pal.GOLD)
            }
            is Fx.Death -> {
                shake = 16f
                burst(e.x, e.y, 60, Pal.PLAYER, 7f)
                burst(e.x, e.y, 30, Pal.RED, 4.5f)
                ring(e.x, e.y, 5f, 0.6f, Pal.RED)
                ring(e.x, e.y, 2.5f, 0.4f, Pal.PLAYER)
                flashWith(Pal.RED, 0.45f)
            }
            is Fx.Dash -> {
                val ddx = w.dx(e.x0, e.x1, true)
                val ddy = w.dy(e.y0, e.y1, true)
                for (i in 0..12) {
                    val t = i / 12f
                    spark(e.x0 + ddx * t, e.y0 + ddy * t, 0f, 0f, 0.4f, 4.5f, Pal.CYAN)
                }
                ring(e.x1, e.y1, 1.2f, 0.25f, Pal.CYAN)
            }
            is Fx.Nova -> {
                ring(e.x, e.y, e.radius, 0.5f, Pal.GOLD)
                ring(e.x, e.y, e.radius * 0.6f, 0.4f, Pal.WHITE)
                burst(e.x, e.y, 24, Pal.GOLD, 8f)
                flashWith(Pal.GOLD, 0.15f)
            }
            is Fx.Wrap -> {
                burst(e.x0, e.y0, 8, Pal.CYAN, 2f)
                burst(e.x1, e.y1, 8, Pal.CYAN, 2f)
                ring(e.x1, e.y1, 1.4f, 0.3f, Pal.CYAN)
            }
            is Fx.EnemySpawn -> {}
            is Fx.EnemyGone -> burst(e.x, e.y, 6, Pal.RED, 1.6f)
            is Fx.Stun -> burst(e.x, e.y, 6, Pal.GOLD, 1.8f)
            is Fx.Alert -> ring(e.x, e.y, 1.6f, 0.3f, Pal.RED)
            is Fx.MeteorWarn -> {}
            is Fx.TimeUp -> flashWith(Pal.RED, 0.35f)
            is Fx.BossHit -> {
                bolts += Bolt(e.x, e.y, e.bossX, e.bossY, 0.28f, Pal.WHITE)
                burst(e.bossX, e.bossY, 14, Pal.WHITE, 5f)
                shake = max(shake, 4f)
            }
            is Fx.BossDead -> {
                shake = 22f
                flashWith(Pal.WHITE, 0.6f)
                for (i in 0 until 3) ring(e.x, e.y, 5f + i * 4f, 0.6f + i * 0.2f, if (i == 1) Pal.GOLD else Pal.WHITE)
                burst(e.x, e.y, 90, Pal.GOLD, 10f)
                burst(e.x, e.y, 60, Pal.RED, 7f)
                repeat(9) { fireworks += Firework(e.x + rnd.nextFloat() * 8f - 4f, e.y + rnd.nextFloat() * 6f - 3f, 0.15f * it, PARTY[it % PARTY.size]) }
                pops += Pop("BOSS DOWN!", 1.6f, Pal.GOLD)
            }
            is Fx.Cleared -> {
                flashWith(Pal.WHITE, 0.3f)
                val p = w.player
                burst(p.x, p.y, 40, Pal.STAR, 7f)
                ring(p.x, p.y, 8f, 0.7f, Pal.STAR)
                repeat(7) { fireworks += Firework(p.x + rnd.nextFloat() * 16f - 8f, p.y + rnd.nextFloat() * 8f - 4f, 0.12f + 0.18f * it, PARTY[it % PARTY.size]) }
            }
            is Fx.Powered -> {
                val c = PowerArt.color(e.kind)
                burst(e.x, e.y, 26, c, 6f)
                ring(e.x, e.y, 2.6f, 0.45f, c)
                ring(e.x, e.y, 1.4f, 0.3f, Pal.WHITE)
                flashWith(c, 0.14f)
                val stack = w.arcade?.stacks(e.kind) ?: 0
                pops += Pop(if (e.kind.permanent) "${e.kind.title}  x$stack" else e.kind.title, 1.1f, c)
            }
            is Fx.PowerLost -> {
                val p = w.player
                ring(p.x, p.y, 1.6f, 0.35f, PowerArt.color(e.kind))
            }
            is Fx.EdgeWarn -> {
                flashWith(Pal.RED, 0.22f)
                shake = max(shake, 5f)
            }
            is Fx.EdgeSeal -> {
                shake = 14f
                flashWith(Pal.ORANGE, 0.4f)
                sealSlam = 1f
                pops += Pop("EDGES LOCKED", 1.2f, Pal.ORANGE)
            }
            is Fx.EdgeOpen -> {
                flashWith(Pal.CYAN, 0.25f)
                pops += Pop("EDGES OPEN", 1.1f, Pal.CYAN)
            }
        }
        w.fx.clear()
    }

    private fun flashWith(c: Color, amount: Float) {
        flashColor.set(c)
        flash = max(flash, amount)
    }

    /** Sparks flying outwards. Positions in tiles, [speed] in tiles per second. */
    private fun burst(x: Float, y: Float, n: Int, c: Color, speed: Float) {
        repeat(n) {
            val a = rnd.nextFloat() * MathUtils.PI2
            val s = speed * (0.3f + 0.7f * rnd.nextFloat())
            spark(x, y, MathUtils.cos(a) * s, MathUtils.sin(a) * s, 0.35f + 0.45f * rnd.nextFloat(), 2f + 2.2f * rnd.nextFloat(), c)
        }
    }

    private fun spark(x: Float, y: Float, vx: Float, vy: Float, life: Float, size: Float, c: Color) {
        if (particles.size >= MAX_PARTICLES) return
        particles += Particle(x, y, vx, vy, life, life, size, c)
    }

    private fun ring(x: Float, y: Float, radius: Float, life: Float, c: Color) {
        rings += Ring(x, y, radius, life, life, c)
    }

    // ----------------------------------------------------------------------------------------
    // Camera
    // ----------------------------------------------------------------------------------------

    private fun camera(w: World, dt: Float) {
        ww = w.w
        wh = w.h
        wrapsY = w.wrapY
        follow = w.cols != VIEW_COLS || w.rows != VIEW_ROWS
        val fresh = world !== w
        if (fresh) {
            world = w
            seedNebula(w)
            shards.clear(); bolts.clear(); pops.clear(); fireworks.clear()
            lastPop = 1
        }
        if (!follow) {
            camX = ww / 2f
            camY = wh / 2f
            return
        }
        // Look a little ahead of where the player is going.
        val p = w.player
        val tx = bx(w, p) + (p.vx * 0.22f).coerceIn(-2.5f, 2.5f)
        val ty = by(w, p) + (p.vy * 0.22f).coerceIn(-1.5f, 1.5f)
        if (fresh) {
            camX = Geo.wrap(tx, ww); camY = Geo.wrap(ty, wh)
        } else {
            // The shortest way round the torus, so crossing the seam is perfectly smooth.
            val k = 1f - exp(-5f * dt)
            camX = Geo.wrap(camX + Geo.wrapDelta(tx - camX, ww) * k, ww)
            camY = Geo.wrap(camY + Geo.wrapDelta(ty - camY, wh) * k, wh)
        }
    }

    /** Tile coordinate -> canvas pixel, via the camera. */
    private fun px(x: Float): Float = (Geo.wrapDelta(x - camX, ww) + VIEW_COLS / 2f) * T + ox

    private fun py(y: Float): Float =
        ((if (wrapsY) Geo.wrapDelta(y - camY, wh) else y - camY) + VIEW_ROWS / 2f) * T + oy

    /** Where a body is right now in tiles, including its hop animation. */
    private fun bx(w: World, b: Body): Float =
        if (b.tween >= 1f) b.x else b.x - w.dx(b.px, b.x, true) * (1f - ease(b.tween))

    private fun by(w: World, b: Body): Float =
        if (b.tween >= 1f) b.y else b.y - w.dy(b.py, b.y, true) * (1f - ease(b.tween))

    private fun ease(t: Float): Float = 1f - (1f - t) * (1f - t)

    /**
     * Runs [draw] at the on-screen position of a point in the world - and, when the world is exactly
     * as big as the view, again at the wrapped copy that pokes in from the opposite edge.
     */
    private inline fun at(x: Float, y: Float, margin: Float, draw: (Float, Float) -> Unit) {
        val sx = px(x)
        val sy = py(y)
        val wpx = ww * T
        val hpx = wh * T
        val dx = if (sx < margin && sx + wpx < FIELD_W + margin) wpx else if (sx > FIELD_W - margin && sx - wpx > -margin) -wpx else 0f
        val dy = if (!wrapsY) 0f else if (sy < margin && sy + hpx < FIELD_H + margin) hpx else if (sy > FIELD_H - margin && sy - hpx > -margin) -hpx else 0f
        val inX = sx > -margin && sx < FIELD_W + margin
        val inY = sy > -margin && sy < FIELD_H + margin
        if (inX && inY) draw(sx, sy)
        if (dx != 0f && inY) draw(sx + dx, sy)
        if (dy != 0f && inX) draw(sx, sy + dy)
        if (dx != 0f && dy != 0f) draw(sx + dx, sy + dy)
    }

    /** How visible something is, given the level's darkness and whatever is lighting it. */
    private fun vis(w: World, x: Float, y: Float): Float {
        if (w.fogRadius <= 0f) return 1f
        var v = ((w.fogRadius + 0.8f - w.dist(x, y, w.player.x, w.player.y)) / 1.6f).coerceIn(0f, 1f)
        for (f in w.flares) {
            if (v >= 1f) break
            val r = f.radius * min(1f, f.life / 2f)
            v = max(v, ((r + 0.8f - w.dist(x, y, f.x, f.y)) / 1.6f).coerceIn(0f, 1f))
        }
        return v
    }

    // ----------------------------------------------------------------------------------------
    // Frame
    // ----------------------------------------------------------------------------------------

    fun draw(w: World, dt: Float, dim: Float = 0f) {
        time += dt
        frameDt = dt
        shake = max(0f, shake - dt * 30f)
        flash = max(0f, flash - dt * 1.4f)
        eatPulse = max(0f, eatPulse - dt * 5f)
        hudPulse = max(0f, hudPulse - dt * 4f)
        scoreBump = max(0f, scoreBump - dt * 4f)
        sealSlam = max(0f, sealSlam - dt * 2.5f)
        ox = if (shake > 0f) (rnd.nextFloat() - 0.5f) * shake else 0f
        oy = if (shake > 0f) (rnd.nextFloat() - 0.5f) * shake else 0f
        camera(w, dt)

        background(w, dt)
        lights(w)
        edges(w)
        walls(w)
        meteors(w, dt)
        wells(w)
        w.sun?.let { sun(w, it) }
        tail(w)
        decoy(w)
        for (s in w.stars) star(w, s)
        for (c in w.capsules) capsule(w, c)
        for (e in w.enemies) enemy(w, e, dt)
        if (w.phase != World.Phase.DEAD) player(w, dt)
        effects(dt)
        if (follow) indicators(w)
        if (flash > 0f) g.rect(0f, 0f, FIELD_W, FIELD_H, flashColor, flash)
        if (w.sealing) sealNotice(w)
        pops(dt)
        if (dim > 0f) g.rect(0f, 0f, Gfx.W, Gfx.H, Pal.BG, dim)
    }

    // ----------------------------------------------------------------------------------------
    // Backdrop
    // ----------------------------------------------------------------------------------------

    private fun seedNebula(w: World) {
        val r = Random(w.spec.number * 131L + w.spec.move.ordinal * 17L + w.spec.modifiers.size)
        // Each level gets its own two-tone sky; boss levels burn red.
        val a = if (w.spec.boss != null) BOSS_SKY else SKIES[r.nextInt(SKIES.size)]
        val b = if (w.spec.boss != null) SKIES[3] else SKIES[r.nextInt(SKIES.size)]
        for (i in 0 until NEBULAE) {
            nebula[i * 4] = r.nextFloat()
            nebula[i * 4 + 1] = r.nextFloat()
            nebula[i * 4 + 2] = 230f + 230f * r.nextFloat()
            nebula[i * 4 + 3] = r.nextFloat() * 6.28f
            nebulaColors[i] = if (i % 2 == 0) a else b
        }
    }

    private fun background(w: World, dt: Float) {
        g.rect(0f, 0f, Gfx.W, Gfx.H, Pal.BG)
        val scroll = if (w.spec.move == MoveKind.SCROLL) w.scroll * T else 0f
        val camPxX = if (follow) camX * T else 0f
        val camPxY = if (follow) camY * T else 0f

        // Nebula clouds: big soft blobs, far away, glowing a little hotter as the combo climbs.
        val heat = (if (w.fogRadius > 0f) 0.45f else 1f) * (1f + 0.07f * (w.combo - 1))
        val spanX = FIELD_W + 700f
        val spanY = FIELD_H + 700f
        for (i in 0 until NEBULAE) {
            val c = nebulaColors[i] ?: continue
            val phase = nebula[i * 4 + 3]
            var x = nebula[i * 4] * spanX - camPxX * 0.12f + MathUtils.sin(time * 0.07f + phase) * 40f
            var y = nebula[i * 4 + 1] * spanY - (camPxY + scroll) * 0.12f + MathUtils.cos(time * 0.05f + phase) * 30f
            x = ((x % spanX) + spanX) % spanX - 350f
            y = ((y % spanY) + spanY) % spanY - 350f
            val breathe = 0.85f + 0.15f * MathUtils.sin(time * 0.4f + phase * 3f)
            g.glow(x, y, nebula[i * 4 + 2], c, 0.3f * heat * breathe)
        }

        // Distant stars, in layers. They slide past as the camera (or the scroller) moves.
        for (i in 0 until SPECKS) {
            val depth = 0.25f + specks[i * 4 + 2] * 0.75f
            var x = specks[i * 4] * FIELD_W - camPxX * depth * 0.3f
            var y = specks[i * 4 + 1] * FIELD_H - (camPxY * 0.5f + scroll) * depth * 0.6f
            x = ((x % FIELD_W) + FIELD_W) % FIELD_W
            y = ((y % FIELD_H) + FIELD_H) % FIELD_H
            val twinkle = 0.55f + 0.45f * MathUtils.sin(time * (0.6f + specks[i * 4 + 3] * 1.8f) + i)
            val size = 1f + depth * 1.6f
            g.rect(x + ox * 0.3f, y + oy * 0.3f, size, size, if (i % 9 == 0) Pal.CYAN else Pal.DIM, 0.55f * depth * twinkle)
        }

        // Now and then, a shooting star.
        streakT -= dt
        if (streakT <= 0f) {
            streakT = 5f + 6f * rnd.nextFloat()
            streakAge = 0f
            streakX = rnd.nextFloat() * FIELD_W
            streakY = FIELD_H * (0.4f + 0.6f * rnd.nextFloat())
        }
        if (streakAge < STREAK_TIME) {
            streakAge += dt
            val t = streakAge / STREAK_TIME
            val hx = streakX + 300f * t
            val hy = streakY - 140f * t
            g.line(hx - 80f, hy + 37f, hx, hy, 2f, Pal.TEXT, 0.55f * (1f - t), add = true)
        }

        // The lattice: dots on the tile corners, so the grid is visible without shouting.
        if (w.spec.move == MoveKind.GRID) {
            val x0 = MathUtils.floor(camX - VIEW_COLS / 2f)
            val y0 = MathUtils.floor(camY - VIEW_ROWS / 2f)
            for (cy in y0..y0 + VIEW_ROWS + 1) for (cx in x0..x0 + VIEW_COLS + 1) {
                val a = if (w.fogRadius > 0f) 0.2f + 0.8f * vis(w, cx.toFloat(), cy.toFloat()) else 1f
                val sx = (cx - camX + VIEW_COLS / 2f) * T + ox
                val sy = (cy - camY + VIEW_ROWS / 2f) * T + oy
                g.rect(sx - 1f, sy - 1f, 2f, 2f, Pal.LATTICE, a)
            }
        }
    }

    /** In a dark level: a pool of light around the player, and around each flare. */
    private fun lights(w: World) {
        if (w.fogRadius <= 0f) return
        val p = w.player
        at(bx(w, p), by(w, p), w.fogRadius * T * 1.6f) { x, y ->
            g.glow(x, y, w.fogRadius * T * 1.9f, FOG_LIGHT, 0.5f)
        }
        for (f in w.flares) {
            val fade = min(1f, f.life / 2f)
            at(f.x, f.y, f.radius * T * 1.5f) { x, y ->
                g.glow(x, y, f.radius * T * 1.6f * fade, FLARE_LIGHT, 0.17f * fade)
            }
        }
    }

    /** Field edges: faint and dashed where you can wrap, solid and angry where you cannot. */
    private fun edges(w: World) {
        val wpx = ww * T
        val hpx = wh * T
        val x0 = px(0f)
        val y0 = if (wrapsY) py(0f) else oy
        val sealing = w.sealing
        for (k in -1..1) {
            val x = x0 + k * wpx
            if (x > -70f && x < FIELD_W + 70f) {
                if (sealing) barrier(w, x, 0f, x, FIELD_H) else edge(w.playerWrapX, x, 0f, x, FIELD_H)
            }
        }
        if (!wrapsY) {
            edge(false, 0f, y0, FIELD_W, y0)
            edge(false, 0f, y0 + hpx, FIELD_W, y0 + hpx)
        } else {
            for (k in -1..1) {
                val y = y0 + k * hpx
                if (y > -70f && y < FIELD_H + 70f) {
                    if (sealing) barrier(w, 0f, y, FIELD_W, y) else edge(w.playerWrapY, 0f, y, FIELD_W, y)
                }
            }
        }
    }

    /**
     * An edge under an edge block. While the warning runs it throbs red, faster and brighter as the
     * moment comes; once shut it is a wall of light with teeth, so there is no mistaking it for an
     * edge you can still slip through.
     */
    private fun barrier(w: World, x1: Float, y1: Float, x2: Float, y2: Float) {
        val horizontal = y1 == y2
        if (w.sealWarn > 0f) {
            val t = 1f - w.sealWarn / w.sealWarnTotal
            val pulse = 0.5f + 0.5f * MathUtils.sin(time * (9f + 22f * t))
            val a = (0.35f + 0.65f * t) * (0.35f + 0.65f * pulse)
            g.line(x1, y1, x2, y2, 130f * (0.4f + 0.6f * t), Pal.RED, 0.10f * a)
            g.line(x1, y1, x2, y2, 46f, Pal.RED, 0.22f * a)
            g.line(x1, y1, x2, y2, 14f + 8f * t, Pal.ORANGE, 0.9f * a)
            // Still open for now: keep the portal dashes under the glow.
            edge(true, x1, y1, x2, y2)
            return
        }
        val leaving = min(1f, w.sealTime / 0.8f)
        val slam = 1f + 1.4f * sealSlam
        g.line(x1, y1, x2, y2, 120f * slam, Pal.ORANGE, 0.10f * leaving)
        g.line(x1, y1, x2, y2, 50f * slam, Pal.ORANGE, 0.22f * leaving)
        g.line(x1, y1, x2, y2, 22f, Pal.RED, 0.95f * leaving)
        g.line(x1, y1, x2, y2, 8f, Pal.STAR, leaving)
        // Teeth marching along it, on both faces.
        val gap = 40f
        val crawl = (time * 30f) % gap
        val n = ((if (horizontal) x2 - x1 else y2 - y1) / gap).toInt() + 2
        for (i in -1 until n) {
            val d = i * gap + crawl
            if (horizontal) {
                g.triangle(x1 + d - 9f, y1 + 10f, x1 + d + 9f, y1 + 10f, x1 + d, y1 + 26f, Pal.RED, leaving)
                g.triangle(x1 + d - 9f, y1 - 10f, x1 + d + 9f, y1 - 10f, x1 + d, y1 - 26f, Pal.RED, leaving)
            } else {
                g.triangle(x1 + 10f, y1 + d - 9f, x1 + 10f, y1 + d + 9f, x1 + 26f, y1 + d, Pal.RED, leaving)
                g.triangle(x1 - 10f, y1 + d - 9f, x1 - 10f, y1 + d + 9f, x1 - 26f, y1 + d, Pal.RED, leaving)
            }
        }
    }

    /** The edge block spelled out across the top of the field: a countdown in, then a countdown out. */
    private fun sealNotice(w: World) {
        val cx = FIELD_W / 2f
        val y = FIELD_H - 34f
        val warning = w.sealWarn > 0f
        val c = if (warning) Pal.RED else Pal.ORANGE
        val left = if (warning) w.sealWarn else w.sealTime
        val frac = (if (warning) w.sealWarn / w.sealWarnTotal else w.sealTime / w.sealTotal).coerceIn(0f, 1f)
        val blink = if (warning) 0.65f + 0.35f * MathUtils.sin(time * 18f) else 1f
        val text = if (warning) "EDGES LOCKING IN %.1f".format(max(0f, left)) else "EDGES LOCKED  %.1f".format(max(0f, left))
        g.rect(cx - 190f, y - 30f, 380f, 42f, Pal.BG, 0.7f)
        g.frame(cx - 190f, y - 30f, 380f, 42f, 2f, c, blink)
        g.text(g.body, text, cx, y + 2f, c, blink, Align.center)
        g.rect(cx - 180f, y - 25f, 360f * frac, 5f, c, blink)
    }

    private fun edge(open: Boolean, x1: Float, y1: Float, x2: Float, y2: Float) {
        if (!open) {
            g.line(x1, y1, x2, y2, 5f, Pal.ORANGE, 0.75f)
            return
        }
        val dashes = 24
        // The dashes crawl, so a wrap edge reads as a portal rather than a wall.
        val crawl = (time * 0.4f) % 1f
        for (i in 0 until dashes) {
            val a = (i + crawl) / dashes
            val b = (i + crawl + 0.4f) / dashes
            g.line(x1 + (x2 - x1) * a, y1 + (y2 - y1) * a, x1 + (x2 - x1) * min(1f, b), y1 + (y2 - y1) * min(1f, b), 3f, Pal.CYAN, 0.3f)
        }
    }

    private fun walls(w: World) {
        if (!w.hasWalls) return
        val x0 = MathUtils.floor(camX - VIEW_COLS / 2f) - 1
        val y0 = MathUtils.floor(camY - VIEW_ROWS / 2f) - 1
        for (cy in y0..y0 + VIEW_ROWS + 2) for (cx in x0..x0 + VIEW_COLS + 2) {
            if (!w.isWall(cx, cy)) continue
            val a = if (w.fogRadius > 0f) 0.1f + 0.9f * vis(w, cx + 0.5f, cy + 0.5f) else 1f
            val x = (cx - camX + VIEW_COLS / 2f) * T + ox
            val y = (cy - camY + VIEW_ROWS / 2f) * T + oy
            g.rect(x + 1f, y + 1f, T - 2f, T - 2f, Pal.WALL, a)
            g.rect(x + 1f, y + T - 4f, T - 2f, 3f, Pal.WALL_HI, a)
            g.rect(x + 1f, y + 1f, 3f, T - 5f, Pal.WALL_HI, a * 0.5f)
        }
    }

    private fun meteors(w: World, dt: Float) {
        meteorTime += dt
        for (m in w.meteors) {
            if (m.warn > 0f) {
                // Warning: the doomed lane pulses, faster as impact gets closer.
                val pulse = 0.5f + 0.5f * MathUtils.sin(time * (10f + (1.3f - m.warn) * 14f))
                val a = 0.08f + 0.16f * pulse
                if (m.horizontal) {
                    val y = py(m.line + 0.5f)
                    g.rect(0f, y - T / 2f, FIELD_W, T, Pal.RED, a)
                    arrow(if (m.dir > 0) 12f else FIELD_W - 12f, y, m.dir * 9f, 0f, 0.5f + 0.5f * pulse)
                } else {
                    val x = px(m.line + 0.5f)
                    g.rect(x - T / 2f, 0f, T, FIELD_H, Pal.RED, a)
                    arrow(x, if (m.dir > 0) 12f else FIELD_H - 12f, 0f, m.dir * 9f, 0.5f + 0.5f * pulse)
                }
            } else {
                val tx = if (m.horizontal) -m.dir.toFloat() else 0f
                val ty = if (m.horizontal) 0f else -m.dir.toFloat()
                at(m.x, m.y, 80f) { x, y ->
                    for (i in 1..6) g.circle(x + tx * i * 9f, y + ty * i * 9f, 11f - i * 1.5f, Pal.ORANGE, 0.5f - i * 0.07f)
                    g.glow(x, y, 38f, Pal.ORANGE, 0.9f)
                    g.circle(x, y, 11f, Pal.ORANGE)
                    g.circle(x - 3f, y + 3f, 4f, Pal.STAR, 0.8f)
                }
                if (rnd.nextFloat() < 0.6f) spark(m.x, m.y, tx * 3f + rnd.nextFloat() - 0.5f, ty * 3f + rnd.nextFloat() - 0.5f, 0.4f, 3f, Pal.ORANGE)
            }
        }
    }

    private fun arrow(x: Float, y: Float, dx: Float, dy: Float, a: Float) {
        g.triangle(x + dx, y + dy, x - dx - dy, y - dy - dx, x - dx + dy, y - dy + dx, Pal.RED, a)
    }

    private fun wells(w: World) {
        for (well in w.wells) {
            at(well.x, well.y, 130f) { x, y -> blackHole(x, y, 1f) }
        }
    }

    private fun blackHole(x: Float, y: Float, scale: Float) {
        g.glow(x, y, 120f * scale, Pal.PURPLE, 0.45f)
        for (i in 0 until 4) {
            // Rings fall inwards, forever.
            val t = ((time * 0.35f + i / 4f) % 1f)
            val r = (70f * (1f - t) + 14f) * scale
            g.ring(x, y, r, 2.5f, Pal.PURPLE, 0.55f * t, time * 40f + i * 50f, 300f)
        }
        g.circle(x, y, 15f * scale, Color.BLACK)
        g.ring(x, y, 15f * scale, 3f, Pal.PINK, 0.9f)
    }

    /** The middle of an orbit level, and the tracks the stars ride around it. */
    private fun sun(w: World, s: Sun) {
        val cx = px(s.x)
        val cy = py(s.y)
        for (star in w.stars) {
            if (star.orbitR > 0f) g.ring(cx, cy, star.orbitR * T, 1.5f, Pal.DIM, 0.16f)
        }
        val r = s.radius * T
        if (s.blackHole) {
            blackHole(cx, cy, r / 15f * 0.9f)
            return
        }
        val pulse = 1f + 0.04f * MathUtils.sin(time * 3f)
        g.glow(cx, cy, r * 6f, Pal.ORANGE, 0.5f)
        g.glow(cx, cy, r * 3f, Pal.STAR, 0.8f)
        // Corona: flickering rays.
        for (i in 0 until 14) {
            val ang = i * (360f / 14f) + time * 12f
            val len = r * (1.35f + 0.3f * MathUtils.sin(time * 5f + i * 1.7f))
            g.line(
                cx + MathUtils.cosDeg(ang) * r * 0.9f, cy + MathUtils.sinDeg(ang) * r * 0.9f,
                cx + MathUtils.cosDeg(ang) * len, cy + MathUtils.sinDeg(ang) * len, 3f, Pal.GOLD, 0.7f, add = true,
            )
        }
        g.circle(cx, cy, r * pulse, Pal.ORANGE)
        g.circle(cx, cy, r * 0.8f * pulse, Pal.GOLD)
        g.circle(cx - r * 0.15f, cy + r * 0.15f, r * 0.5f, Pal.STAR)
    }

    private fun tail(w: World) {
        if (!w.tailActive) return
        val n = w.tail.size
        for (i in n - 1 downTo 0) {
            val t = w.tail[i]
            val f = 1f - i / (n + 4f)
            val a = vis(w, t.x, t.y)
            if (a <= 0f) continue
            at(t.x, t.y, 16f) { x, y ->
                g.circle(x, y, 4f + 5f * f, Pal.SHIELD, a * (0.35f + 0.5f * f))
            }
        }
    }

    private fun decoy(w: World) {
        val d = w.decoy ?: return
        val a = min(1f, d.life) * (0.5f + 0.3f * MathUtils.sin(time * 30f))
        at(d.x, d.y, 40f) { x, y ->
            g.glow(x, y, 40f, Pal.CYAN, a)
            g.ring(x, y, 9f, 4f, Pal.CYAN, a)
            g.ring(x, y, 18f + 4f * MathUtils.sin(time * 8f), 2f, Pal.CYAN, a * 0.6f)
        }
    }

    // ----------------------------------------------------------------------------------------
    // Stars
    // ----------------------------------------------------------------------------------------

    private fun star(w: World, s: Star) {
        val c = starColor(s.type)
        var a = vis(w, s.x, s.y)
        // In the dark, far stars still glimmer so you know which way to head.
        if (w.fogRadius > 0f) a = max(a, 0.2f + 0.12f * MathUtils.sin(time * 5f + s.x * 3f))
        var scale = min(1f, s.age * 5f)
        // Pop in with a little overshoot.
        if (s.age < 0.35f) scale *= 1f + 0.5f * MathUtils.sin(s.age / 0.35f * MathUtils.PI)
        if (s.ttl > 0f) {
            val left = s.ttl - s.age
            if (left < 2f) {
                if (MathUtils.sin(time * (14f + (2f - left) * 12f)) < -0.2f) a *= 0.25f
                scale *= 0.6f + 0.2f * left
            }
        }
        if (!s.lit) a *= 0.16f
        val spin = time * 50f + s.x * 37f
        val pulse = 1f + 0.12f * MathUtils.sin(time * 5f + s.y * 2f)
        at(s.x, s.y, 50f) { x, y ->
            when (s.type) {
                StarType.COMMON -> {
                    g.glow(x, y, 22f * scale, c, a * 0.7f)
                    val r = 8.5f * scale * pulse
                    for (i in 0 until 3) {
                        val ang = spin * 0.4f + i * 60f
                        val dx = MathUtils.cosDeg(ang) * r
                        val dy = MathUtils.sinDeg(ang) * r
                        g.line(x - dx, y - dy, x + dx, y + dy, 3f, c, a)
                    }
                    g.circle(x, y, 2f * scale, Pal.WHITE, a)
                }
                StarType.GOLD -> {
                    g.glow(x, y, 34f * scale, c, a * 0.9f)
                    glint(x, y, 22f * scale, c, a * 0.5f, time * 20f)
                    g.star(x, y, 13f * scale * pulse, 5.6f * scale * pulse, 5, 90f + MathUtils.sin(time * 2f) * 12f, c, a)
                    g.circle(x, y, 3f * scale, Pal.WHITE, a * 0.8f)
                }
                StarType.PULSAR -> {
                    g.glow(x, y, 30f * scale, c, a * 0.9f)
                    g.star(x, y, 14f * scale, 3.2f * scale, 4, spin, c, a)
                    val t = (s.age % Star.PULSAR_PERIOD) / Star.PULSAR_PERIOD
                    g.ring(x, y, 6f + 16f * t, 2f, c, a * (1f - t))
                }
                StarType.COMET -> {
                    val vx = if (s.orbitR > 0f) -MathUtils.sin(s.orbitA) * s.orbitW * s.orbitR else s.vx
                    val vy = if (s.orbitR > 0f) MathUtils.cos(s.orbitA) * s.orbitW * s.orbitR else s.vy
                    for (i in 1..6) g.circle(x - vx * i * 3.2f, y - vy * i * 3.2f, 6.5f - i, c, a * (0.5f - i * 0.07f))
                    g.glow(x, y, 28f * scale, c, a * 0.9f)
                    g.star(x, y, 11f * scale, 4f * scale, 4, 45f, c, a)
                    g.circle(x, y, 3.5f * scale, Pal.WHITE, a)
                }
                StarType.NOVA -> {
                    g.glow(x, y, 50f * scale, c, a)
                    glint(x, y, 30f * scale, Pal.WHITE, a * 0.6f, -time * 30f)
                    g.star(x, y, 16f * scale * pulse, 5f * scale, 8, spin * 0.6f, c, a)
                    g.circle(x, y, 5f * scale, Pal.WHITE, a)
                    if (s.ttl > 0f) g.ring(x, y, 21f, 2f, Pal.WHITE, a * 0.8f, 90f, 360f * (1f - s.age / s.ttl))
                }
            }
        }
    }

    /** A four-point lens flare. */
    private fun glint(x: Float, y: Float, r: Float, c: Color, a: Float, rot: Float) {
        for (i in 0 until 2) {
            val ang = rot + i * 90f
            val dx = MathUtils.cosDeg(ang) * r
            val dy = MathUtils.sinDeg(ang) * r
            g.line(x - dx, y - dy, x + dx, y + dy, 1.5f, c, a, add = true)
        }
    }

    /** An arcade power-up on the field. */
    private fun capsule(w: World, c: Capsule) {
        var a = max(vis(w, c.x, c.y), if (w.fogRadius > 0f) 0.25f else 0f)
        a *= min(1f, c.age * 4f)
        // Blink out over the last three seconds.
        if (c.ttl > 0f) {
            val left = c.ttl - c.age
            if (left < 3f && MathUtils.sin(time * (10f + (3f - left) * 8f)) < 0f) a *= 0.3f
        }
        at(c.x, c.y, 50f) { x, y -> powerArt.capsule(c.kind, x, y, 15f, time + c.x, a) }
    }

    // ----------------------------------------------------------------------------------------
    // Enemies
    // ----------------------------------------------------------------------------------------

    private fun enemy(w: World, e: Enemy, dt: Float) {
        val boss = e.type.boss
        // The serpent's body is long: draw it even when the head is out of sight.
        if (e.type === Leviathan) serpentBody(w, e)
        val vis = if (boss) max(0.35f, vis(w, e.x, e.y)) else vis(w, e.x, e.y)
        if (vis <= 0f) return
        val base = enemyColor(e.type)
        val ex = bx(w, e)
        val ey = by(w, e)
        val scale = if (boss) BOSS_SCALE else if (e.type === Mite) 0.6f else 1f
        val margin = 30f * scale
        // Letters on the grid, creatures everywhere else. Bosses are always creatures.
        val alive = boss || w.spec.move != MoveKind.GRID

        if (e.spawnT > 0f) {
            // Materialising: a closing ring gives fair warning before it can bite.
            val t = min(1f, e.spawnT)
            val blink = vis * (0.25f + 0.25f * MathUtils.sin(time * 22f))
            at(ex, ey, margin + 40f) { x, y ->
                g.ring(x, y, (10f + 26f * t) * scale, 2.5f, base, vis * (1f - t * 0.6f), time * 200f, 280f)
                if (alive) creatures.draw(e, x, y, e.heading, scale, 0f, 0f, base, blink)
                else g.glyphAt(g.glyph, e.type.glyph, x, y, base, blink)
            }
            return
        }

        // Face the way it is going. On the grid there is no velocity, so read the last hop instead.
        var hx = e.vx
        var hy = e.vy
        if (e.ballistic) { hx = e.dirX; hy = e.dirY }
        else if (w.spec.move == MoveKind.GRID) { hx = w.dx(e.px, e.x, true); hy = w.dy(e.py, e.y, true) }
        if ((e.type === Zoomer && e.state == Zoomer.AIM) || (e.type === Devourer && e.state == Devourer.AIM)) { hx = e.dirX; hy = e.dirY }
        if (hx * hx + hy * hy > 0.02f) {
            val target = MathUtils.atan2(hy, hx)
            var diff = target - e.heading
            while (diff > MathUtils.PI) diff -= MathUtils.PI2
            while (diff < -MathUtils.PI) diff += MathUtils.PI2
            e.heading += diff * (1f - exp(-10f * dt))
        }
        val stunned = e.stun > 0f
        if (!stunned) e.animT += dt * (if (e.slow > 0f) 0.4f else 1f)
        val hunger = (1f - w.dist(e.x, e.y, w.player.x, w.player.y) / 8f).coerceIn(0f, 1f)

        var a = vis
        if (e.life in 0f..1.5f) a *= e.life / 1.5f
        val winding = (e.type === Zoomer && e.state == Zoomer.AIM) || (e.type === Devourer && e.state == Devourer.AIM)
        if (winding) {
            // Telegraph the charge lane.
            val len = (if (boss) 10f else 13f) * T
            val width = if (boss) 80f else 10f
            val pulse = 0.5f + 0.5f * MathUtils.sin(time * 40f)
            at(ex, ey, len) { x, y ->
                g.line(x, y, x + e.dirX * len, y + e.dirY * len, width, base, a * (0.1f + 0.12f * pulse))
                g.line(x, y, x + e.dirX * len, y + e.dirY * len, 2f, base, a * 0.6f)
            }
        }
        val c = when {
            stunned -> Pal.STUN
            e.hurt > 0f -> g.mix(base, Pal.WHITE, min(1f, e.hurt * 3f))
            e.slow > 0f -> g.mix(base, Pal.STUN, 0.6f)
            else -> base
        }
        tmp.set(c)
        at(ex, ey, margin + 20f) { x, y ->
            g.glow(x, y, (if (stunned) 18f else 30f) * scale, tmp, a * (if (stunned) 0.4f else 0.75f))
            if (alive) {
                if (e.type === Leviathan) creatures.serpentHead(x, y, e.heading, 1.15f * T, e.animT, tmp, a)
                else creatures.draw(e, x, y, e.heading, scale, e.animT, hunger, tmp, a)
            } else {
                // A letter with a little life in it: it squashes as it lands each step.
                val bounce = if (e.tween < 1f) MathUtils.sin(e.tween * MathUtils.PI) * 3f else 0f
                g.glyphAt(g.glyph, e.type.glyph, x, y + bounce, tmp, a)
            }
            if (stunned || e.slow > 0f) {
                for (i in 0 until 3) {
                    val ang = time * 260f + i * 120f
                    g.circle(x + MathUtils.cosDeg(ang) * 14f * scale, y + 10f * scale + MathUtils.sinDeg(ang) * 4f, 2f, Pal.GOLD, a)
                }
            }
        }
    }

    private fun serpentBody(w: World, e: Enemy) {
        val trail = e.trail ?: return
        val n = trail.size
        val base = if (e.hurt > 0f) Pal.WHITE else enemyColor(e.type)
        for (i in n - 1 downTo 0) {
            val t = trail[i]
            val f = 1f - i / (n + 2f)
            val a = max(0.35f, vis(w, t.x, t.y))
            val r = (0.35f + 0.6f * f) * T
            val wobble = MathUtils.sin(time * 6f - i * 0.6f) * 2f
            at(t.x, t.y, r + 10f) { x, y ->
                g.circle(x, y + wobble, r, if (i % 2 == 0) base else Pal.RED, a)
                g.circle(x, y + wobble, r * 0.55f, Pal.BG, a * 0.35f)
            }
        }
    }

    // ----------------------------------------------------------------------------------------
    // Player
    // ----------------------------------------------------------------------------------------

    private fun player(w: World, dt: Float) {
        val p = w.player
        val tint = comboColor(w.combo)
        // Exhaust in the free-flight modes: sells the glide. On the grid, a puff per hop.
        if (w.phase == World.Phase.PLAYING) {
            if (w.spec.move != MoveKind.GRID) {
                val speed = hypot(p.vx, p.vy)
                thrustAcc += dt * speed * 5f
                while (thrustAcc >= 1f) {
                    thrustAcc -= 1f
                    spark(p.x - p.faceX * 0.3f, p.y - p.faceY * 0.3f, -p.vx * 0.2f + rnd.nextFloat() - 0.5f, -p.vy * 0.2f + rnd.nextFloat() - 0.5f, 0.3f, 3f, tint)
                }
            } else if (p.tween < 1f && p.tween - dt / p.tweenDur <= 0f) {
                repeat(3) { spark(p.px, p.py, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, 0.3f, 2.5f, tint) }
            }
        }
        var a = 1f
        if (p.invuln > 0f && MathUtils.sin(time * 40f) < 0f) a = 0.35f
        val gulp = 1f + 0.4f * eatPulse
        at(bx(w, p), by(w, p), 40f) { x, y ->
            g.glow(x, y, 38f + 22f * eatPulse, tint, (0.6f + 0.3f * eatPulse) * a)
            g.circle(x, y, 8.5f * gulp, Pal.BG, a)
            g.ring(x, y, 8.5f * gulp, 4.5f, Pal.PLAYER, a)
            for (i in 0 until p.shields) {
                g.ring(x, y, 15f + i * 4.5f, 2f, Pal.SHIELD, 0.85f * a, time * (90f + i * 40f), 290f)
            }
        }
    }

    // ----------------------------------------------------------------------------------------
    // Effects
    // ----------------------------------------------------------------------------------------

    private fun effects(dt: Float) {
        var i = fireworks.size - 1
        while (i >= 0) {
            val f = fireworks[i]
            f.delay -= dt
            if (f.delay <= 0f) {
                fireworks.removeAt(i)
                burst(f.x, f.y, 26, f.color, 6f)
                burst(f.x, f.y, 10, Pal.WHITE, 3f)
                ring(f.x, f.y, 2.6f, 0.45f, f.color)
            }
            i--
        }
        i = rings.size - 1
        while (i >= 0) {
            val r = rings[i]
            r.life -= dt
            if (r.life <= 0f) rings.removeAt(i)
            else {
                val t = 1f - r.life / r.maxLife
                val e = 1f - (1f - t) * (1f - t)
                g.ring(px(r.x), py(r.y), r.to * T * e, 4f * (1f - t) + 1f, r.color, (1f - t) * 0.9f, add = true)
            }
            i--
        }
        i = particles.size - 1
        while (i >= 0) {
            val p = particles[i]
            p.life -= dt
            if (p.life <= 0f) particles.removeAt(i)
            else {
                p.x += p.vx * dt; p.y += p.vy * dt
                p.vx *= 1f - 2.5f * dt; p.vy *= 1f - 2.5f * dt
                val t = p.life / p.maxLife
                val sx = px(p.x)
                val sy = py(p.y)
                if (sx > -8f && sx < FIELD_W + 8f && sy > -8f && sy < FIELD_H + 8f) {
                    g.circle(sx, sy, p.size * (0.4f + 0.6f * t), p.color, t, add = true)
                }
            }
            i--
        }
        i = bolts.size - 1
        while (i >= 0) {
            val b = bolts[i]
            b.life -= dt
            if (b.life <= 0f) bolts.removeAt(i) else lightning(b)
            i--
        }
        i = floaters.size - 1
        while (i >= 0) {
            val f = floaters[i]
            f.life -= dt
            if (f.life <= 0f) floaters.removeAt(i)
            else {
                f.y += 1.1f * dt
                val sx = px(f.x).coerceIn(30f, FIELD_W - 30f)
                val sy = min(py(f.y), FIELD_H - 4f)
                g.text(g.small, f.text, sx, sy, f.color, min(1f, f.life * 3f), Align.center)
            }
            i--
        }
    }

    /** A jagged arc from a point to the boss, redrawn differently every frame. */
    private fun lightning(b: Bolt) {
        val x0 = px(b.x0)
        val y0 = py(b.y0)
        // Take the short way round, like everything else on the torus.
        val x1 = x0 + Geo.wrapDelta(b.x1 - b.x0, ww) * T
        val y1 = y0 + (if (wrapsY) Geo.wrapDelta(b.y1 - b.y0, wh) else b.y1 - b.y0) * T
        val a = b.life / 0.28f
        val steps = 9
        var lx = x0
        var ly = y0
        for (s in 1..steps) {
            val t = s / steps.toFloat()
            val jitter = if (s == steps) 0f else 16f
            val nx = x0 + (x1 - x0) * t + (rnd.nextFloat() - 0.5f) * jitter
            val ny = y0 + (y1 - y0) * t + (rnd.nextFloat() - 0.5f) * jitter
            g.line(lx, ly, nx, ny, 5f, Pal.CYAN, a * 0.35f, add = true)
            g.line(lx, ly, nx, ny, 2f, b.color, a, add = true)
            lx = nx; ly = ny
        }
    }

    /** Arrows on the rim of the view pointing at things worth knowing about that are off-screen. */
    private fun indicators(w: World) {
        for (s in w.stars) if (s.lit) pointer(s.x, s.y, starColor(s.type), 7f)
        for (c in w.capsules) pointer(c.x, c.y, PowerArt.color(c.kind), 9f)
        w.boss?.let { pointer(it.x, it.y, Pal.RED, 11f) }
        w.sun?.let { pointer(it.x, it.y, Pal.GOLD, 9f) }
    }

    private fun pointer(x: Float, y: Float, c: Color, size: Float) {
        val dx = Geo.wrapDelta(x - camX, ww)
        val dy = if (wrapsY) Geo.wrapDelta(y - camY, wh) else y - camY
        val halfW = VIEW_COLS / 2f
        val halfH = VIEW_ROWS / 2f
        if (abs(dx) <= halfW && abs(dy) <= halfH) return
        // Slide along the ray from the centre until it meets the rim.
        val k = min((halfW - 0.6f) / max(abs(dx), 0.001f), (halfH - 0.6f) / max(abs(dy), 0.001f))
        val sx = FIELD_W / 2f + dx * k * T
        val sy = FIELD_H / 2f + dy * k * T
        val len = hypot(dx, dy)
        val ux = dx / len
        val uy = dy / len
        val a = (0.35f + 0.65f * (1f - (len - halfH) / 24f)).coerceIn(0.3f, 1f) * (0.8f + 0.2f * MathUtils.sin(time * 6f))
        g.triangle(sx + ux * size, sy + uy * size, sx - ux * size * 0.5f - uy * size * 0.7f, sy - uy * size * 0.5f + ux * size * 0.7f, sx - ux * size * 0.5f + uy * size * 0.7f, sy - uy * size * 0.5f - ux * size * 0.7f, c, a)
    }

    /** Big announcements across the middle of the field. */
    private fun pops(dt: Float) {
        var i = pops.size - 1
        while (i >= 0) {
            val p = pops[i]
            p.life -= dt
            if (p.life <= 0f) pops.removeAt(i)
            else {
                val age = 1.1f - p.life
                val a = min(1f, p.life * 2.5f)
                val y = FIELD_H * 0.74f + min(age, 0.15f) / 0.15f * 14f + age * 10f
                g.glow(FIELD_W / 2f, y - 12f, 220f, p.color, 0.22f * a)
                g.text(g.big, p.text, FIELD_W / 2f, y, p.color, a, Align.center)
            }
            i--
        }
    }

    // ----------------------------------------------------------------------------------------
    // HUD
    // ----------------------------------------------------------------------------------------

    /** The status strip above the field, plus the ability dials. */
    fun hud(w: World, keyHints: Boolean) {
        val top = Gfx.H
        g.rect(0f, FIELD_H, Gfx.W, top - FIELD_H, Pal.PANEL)
        g.rect(0f, FIELD_H, Gfx.W, 2f, if (w.combo > 1) comboColor(w.combo) else Pal.LINE)

        // Left: level, shields, active rules.
        g.text(g.big, "LV ${w.spec.number}", 14f, top - 8f, Pal.TEXT)
        val lvW = g.width(g.big, "LV ${w.spec.number}")
        var ix = 14f + lvW + 20f
        for (i in 0 until w.loadout.shields) {
            g.ring(ix, top - 20f, 7f, 2.5f, Pal.SHIELD, if (i < w.player.shields) 1f else 0.2f)
            ix += 20f
        }
        if (w.loadout.secondWind) {
            val a = if (w.run.secondWindUsed) 0.2f else 1f
            g.rect(ix - 2f, top - 28f, 4f, 16f, Pal.GOLD, a)
            g.rect(ix - 8f, top - 22f, 16f, 4f, Pal.GOLD, a)
        }
        val tags = StringBuilder(w.spec.move.title)
        for (m in w.spec.modifiers) tags.append("  +  ").append(Rules.byId(m).title)
        g.text(g.tiny, tags, 14f, FIELD_H + 18f, Pal.CYAN)
        var tx = 14f + g.width(g.tiny, tags) + 24f
        if (w.arcade != null) {
            g.text(g.tiny, "ARCADE", tx, FIELD_H + 18f, Pal.GOLD)
            tx += g.width(g.tiny, "ARCADE") + 24f
        }
        if (w.run.key.isNotEmpty()) g.text(g.tiny, "SEED ${SeedKey.pretty(w.run.key)}", tx, FIELD_H + 18f, Pal.DIM, 0.8f)
        w.arcade?.let { kit(it) }

        // Middle: the quota - or, on a boss level, how much boss is left.
        val qx = 318f
        val boss = w.spec.boss
        val frac = (w.collected.toFloat() / w.spec.quota).coerceIn(0f, 1f)
        val label = boss?.title ?: "${w.collected} / ${w.spec.quota}"
        if (boss == null) g.star(qx, top - 20f, 8f * (1f + 0.6f * hudPulse), 3.4f * (1f + 0.6f * hudPulse), 5, 90f, Pal.STAR)
        g.text(g.body, label, if (boss == null) qx + 16f else qx - 8f, top - 11f, if (boss != null) Pal.RED else Pal.TEXT)
        val bx = (if (boss == null) qx + 30f else qx + 6f) + g.width(g.body, label)
        val bw = 220f
        val by = top - 26f
        g.rect(bx, by, bw, 12f, Pal.PANEL_HI)
        if (boss != null) g.rect(bx, by, bw * (1f - frac), 12f, Pal.RED)
        else g.rect(bx, by, bw * frac, 12f, Pal.STAR)
        if (hudPulse > 0f) g.rect(bx, by, bw, 12f, Pal.WHITE, 0.45f * hudPulse)
        g.frame(bx - 1f, by - 1f, bw + 2f, 14f, 1f, Pal.LINE)
        if (w.timeLeft >= 0f) {
            val urgent = w.timeLeft < 5f
            val c = if (urgent && MathUtils.sin(time * 16f) > 0f) Pal.RED else if (urgent) Pal.ORANGE else Pal.TEXT
            g.text(g.body, "%.1fs".format(max(0f, w.timeLeft)), bx + bw + 16f, top - 11f, c)
        }

        // Right: score and combo.
        g.text(g.big, w.run.score.toString(), Gfx.W - 14f, top - 8f + 4f * scoreBump, g.mix(Pal.TEXT, Pal.GOLD, scoreBump), 1f, Align.right)
        if (w.combo > 1) {
            val t = (w.comboTimer / w.comboWindow).coerceIn(0f, 1f)
            val c = comboColor(w.combo)
            g.text(g.small, "COMBO x${w.combo}", Gfx.W - 14f, FIELD_H + 22f, c, 1f, Align.right)
            g.rect(Gfx.W - 14f - 90f * t, FIELD_H + 5f, 90f * t, 3f, c)
        }

        // Ability dials, bottom right of the field. They double as touch buttons.
        if (w.loadout.dash) {
            val ready = 1f - (w.player.dashCd / w.dashCooldown).coerceIn(0f, 1f)
            dial(DASH_X, DIAL_Y, ready, "DASH", if (keyHints) "SPACE" else "", Pal.CYAN)
        }
        if (w.loadout.nova) {
            val charge = w.player.novaCharge.toFloat() / World.NOVA_COST
            dial(NOVA_X, DIAL_Y, charge, "NOVA", if (keyHints) "E" else "", Pal.GOLD)
        }

        // Shards from eaten stars, homing in on the quota counter.
        var i = shards.size - 1
        while (i >= 0) {
            val s = shards[i]
            s.age += frameDt
            val tx = qx
            val ty = top - 20f
            val dx = tx - s.x
            val dy = ty - s.y
            val d = hypot(dx, dy)
            if (d < 14f || s.age > 1.4f) {
                shards.removeAt(i)
                hudPulse = 1f
            } else {
                // Drift outwards first, then get pulled in harder and harder.
                val pull = 600f + 5000f * s.age
                s.vx += dx / d * pull * frameDt; s.vy += dy / d * pull * frameDt
                val damp = 1f - 3.5f * frameDt
                s.vx *= damp; s.vy *= damp
                s.x += s.vx * frameDt; s.y += s.vy * frameDt
                g.glow(s.x, s.y, 12f, s.color, 0.7f)
                g.circle(s.x, s.y, 2.5f, Pal.WHITE, 0.9f)
            }
            i--
        }
    }

    /** Arcade mode: what you are carrying, bottom left. Timed ones wear a ring that drains. */
    private fun kit(a: Arcade) {
        var x = 30f
        val y = 30f
        for (k in PowerUp.values()) {
            if (k == PowerUp.NOVA || !a.active(k)) continue
            val c = PowerArt.color(k)
            g.circle(x, y, 17f, Pal.PANEL, 0.6f)
            if (k.timed) {
                val left = a.left(k)
                // Flicker for the last few seconds, so the end never comes as a surprise.
                val fading = left < 4f && MathUtils.sin(time * 14f) < 0f
                g.ring(x, y, 17f, 2f, Pal.LINE, 0.8f)
                g.ring(x, y, 17f, 3.5f, c, if (fading) 0.35f else 0.95f, 90f, -360f * (left / k.seconds).coerceIn(0f, 1f))
                powerArt.icon(k, x, y, 8f, time, if (fading) 0.45f else 1f)
            } else {
                g.ring(x, y, 17f, 2f, c, 0.6f)
                powerArt.icon(k, x, y + 2f, 7f, time, 1f)
                // One pip per stack.
                val n = a.stacks(k)
                for (i in 0 until n) g.rect(x - n * 3f + i * 6f + 0.5f, y - 13f, 4f, 3f, c)
            }
            x += 42f
        }
    }

    private fun dial(x: Float, y: Float, fill: Float, label: String, hint: String, c: Color) {
        val ready = fill >= 1f
        g.circle(x, y, DIAL_R, Pal.PANEL, 0.55f)
        g.ring(x, y, DIAL_R, 3f, Pal.LINE, 0.8f)
        g.ring(x, y, DIAL_R, 4f, c, if (ready) 0.7f + 0.3f * MathUtils.sin(time * 6f) else 0.5f, 90f, -360f * fill)
        g.text(g.tiny, label, x, y + 9f, if (ready) c else Pal.DIM, 1f, Align.center)
        if (hint.isNotEmpty()) g.text(g.tiny, hint, x, y - 5f, Pal.DIM, 0.8f, Align.center)
    }

    companion object {
        /** Pixels per tile on the virtual canvas. */
        const val T = 32f
        const val VIEW_COLS = LevelSpec.FIELD_W
        const val VIEW_ROWS = LevelSpec.FIELD_H
        const val FIELD_W = 960f
        const val FIELD_H = 480f
        const val DIAL_R = 30f
        const val DIAL_Y = 48f
        const val DASH_X = 908f
        const val NOVA_X = 834f
        const val BOSS_SCALE = 3.4f
        private const val SPECKS = 110
        private const val STREAK_TIME = 0.4f
        private const val NEBULAE = 6
        private const val MAX_PARTICLES = 700
        private val FOG_LIGHT = Color.valueOf("3a55a8")
        private val FLARE_LIGHT = Color.valueOf("a8843a")
        private val BOSS_SKY = Color.valueOf("8c1f2a")
        private val SKIES = arrayOf(
            Color.valueOf("3b2a9c"), Color.valueOf("1d4fb8"), Color.valueOf("0f7f86"), Color.valueOf("8a2a86"), Color.valueOf("23339c"),
        )
        val PARTY = arrayOf(Pal.GOLD, Pal.CYAN, Pal.PINK, Pal.GREEN, Pal.ORANGE, Pal.PURPLE)

        fun starColor(t: StarType): Color = when (t) {
            StarType.COMMON -> Pal.STAR
            StarType.GOLD -> Pal.GOLD
            StarType.PULSAR -> Pal.PINK
            StarType.COMET -> Pal.CYAN
            StarType.NOVA -> Pal.ORANGE
        }

        /** The player (and the HUD) warm up from cyan through gold to pink as the combo climbs. */
        fun comboColor(combo: Int): Color = when {
            combo >= 7 -> Pal.PINK
            combo >= 5 -> Pal.ORANGE
            combo >= 3 -> Pal.GOLD
            else -> Pal.CYAN
        }

        fun enemyColor(t: EnemyType): Color = when (t) {
            Eater -> Pal.RED
            Stalker -> Pal.ORANGE
            Bouncer -> Pal.PURPLE
            Zoomer -> Pal.GREEN
            Warper -> Pal.SHIELD
            Hive -> Pal.PINK
            Mite -> Pal.PINK
            Devourer -> Pal.RED
            Leviathan -> Pal.ORANGE
            Warden -> Pal.SHIELD
            else -> Pal.RED
        }
    }
}
