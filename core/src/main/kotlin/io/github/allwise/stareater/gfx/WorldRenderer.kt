package io.github.allwise.stareater.gfx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Align
import io.github.allwise.stareater.sim.Body
import io.github.allwise.stareater.sim.Bouncer
import io.github.allwise.stareater.sim.Eater
import io.github.allwise.stareater.sim.Enemy
import io.github.allwise.stareater.sim.EnemyType
import io.github.allwise.stareater.sim.Fx
import io.github.allwise.stareater.sim.Hive
import io.github.allwise.stareater.sim.Mite
import io.github.allwise.stareater.sim.MoveKind
import io.github.allwise.stareater.sim.Rules
import io.github.allwise.stareater.sim.Stalker
import io.github.allwise.stareater.sim.Star
import io.github.allwise.stareater.sim.StarType
import io.github.allwise.stareater.sim.Warper
import io.github.allwise.stareater.sim.World
import io.github.allwise.stareater.sim.Zoomer
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Draws a [World] and owns the purely cosmetic stuff: particles, shockwave rings, score pop-ups. */
class WorldRenderer(private val g: Gfx) {
    private class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float, val size: Float, val color: Color)
    private class Ring(val x: Float, val y: Float, val to: Float, var life: Float, val maxLife: Float, val color: Color)
    private class Floater(val text: String, val x: Float, var y: Float, var life: Float, val color: Color)

    private val particles = ArrayList<Particle>()
    private val rings = ArrayList<Ring>()
    private val floaters = ArrayList<Floater>()
    private val rnd = Random(7)
    private val specks = FloatArray(SPECKS * 4) { rnd.nextFloat() }
    private var time = 0f
    private var shake = 0f
    private var flash = 0f
    private val flashColor = Color(Color.WHITE)
    private var thrustAcc = 0f
    private var world: World? = null

    /** Field-space pixel offset applied this frame (screen shake). */
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
                burst(e.x, e.y, 8 + e.type.value, c, 90f)
                floaters += Floater(if (e.combo > 1) "+${e.points} x${e.combo}" else "+${e.points}", e.x * T, e.y * T + 14f, 0.9f, c)
            }
            is Fx.StarLost -> burst(e.x, e.y, 5, Pal.DIM, 40f)
            is Fx.ShieldHit -> {
                shake = 7f
                ring(e.x, e.y, 3.5f, 0.45f, Pal.SHIELD)
                flashWith(Pal.SHIELD, 0.18f)
            }
            is Fx.Revive -> {
                shake = 12f
                ring(e.x, e.y, 9f, 0.7f, Pal.GOLD)
                flashWith(Pal.GOLD, 0.4f)
            }
            is Fx.Death -> {
                shake = 16f
                burst(e.x, e.y, 60, Pal.PLAYER, 220f)
                burst(e.x, e.y, 30, Pal.RED, 140f)
                ring(e.x, e.y, 5f, 0.6f, Pal.RED)
                flashWith(Pal.RED, 0.45f)
            }
            is Fx.Dash -> {
                val ddx = w.dx(e.x0, e.x1, true)
                val ddy = w.dy(e.y0, e.y1, true)
                for (i in 0..10) {
                    val t = i / 10f
                    spark(e.x0 + ddx * t, e.y0 + ddy * t, 0f, 0f, 0.35f, 4f, Pal.CYAN)
                }
            }
            is Fx.Nova -> ring(e.x, e.y, e.radius, 0.5f, Pal.GOLD)
            is Fx.Wrap -> {
                burst(e.x0, e.y0, 6, Pal.CYAN, 60f)
                burst(e.x1, e.y1, 6, Pal.CYAN, 60f)
            }
            is Fx.EnemyGone -> burst(e.x, e.y, 6, Pal.RED, 50f)
            is Fx.Stun -> burst(e.x, e.y, 5, Pal.GOLD, 50f)
            is Fx.TimeUp -> flashWith(Pal.RED, 0.35f)
            is Fx.Cleared -> {
                flashWith(Pal.WHITE, 0.3f)
                val p = w.player
                burst(p.x, p.y, 40, Pal.STAR, 200f)
                ring(p.x, p.y, 8f, 0.7f, Pal.STAR)
            }
            is Fx.EnemySpawn -> {}
        }
        w.fx.clear()
    }

    private fun flashWith(c: Color, amount: Float) {
        flashColor.set(c)
        flash = max(flash, amount)
    }

    private fun burst(x: Float, y: Float, n: Int, c: Color, speed: Float) {
        repeat(n) {
            val a = rnd.nextFloat() * MathUtils.PI2
            val s = speed * (0.3f + 0.7f * rnd.nextFloat())
            spark(x, y, MathUtils.cos(a) * s, MathUtils.sin(a) * s, 0.35f + 0.4f * rnd.nextFloat(), 2f + 2f * rnd.nextFloat(), c)
        }
    }

    private fun spark(x: Float, y: Float, vx: Float, vy: Float, life: Float, size: Float, c: Color) {
        if (particles.size >= MAX_PARTICLES) return
        particles += Particle(x * T, y * T, vx, vy, life, life, size, c)
    }

    private fun ring(x: Float, y: Float, radius: Float, life: Float, c: Color) {
        rings += Ring(x * T, y * T, radius * T, life, life, c)
    }

    // ----------------------------------------------------------------------------------------
    // Frame
    // ----------------------------------------------------------------------------------------

    fun draw(w: World, dt: Float, dim: Float = 0f) {
        world = w
        time += dt
        shake = max(0f, shake - dt * 30f)
        flash = max(0f, flash - dt * 1.4f)
        ox = if (shake > 0f) (rnd.nextFloat() - 0.5f) * shake else 0f
        oy = if (shake > 0f) (rnd.nextFloat() - 0.5f) * shake else 0f

        background(w)
        walls(w)
        meteors(w)
        wells(w)
        tail(w)
        decoy(w)
        for (s in w.stars) star(w, s)
        for (e in w.enemies) enemy(w, e)
        if (w.phase != World.Phase.DEAD) player(w, dt)
        effects(dt)
        if (flash > 0f) g.rect(0f, 0f, FIELD_W, FIELD_H, flashColor, flash)
        if (dim > 0f) g.rect(0f, 0f, Gfx.W, Gfx.H, Pal.BG, dim)
    }

    /** How visible something at a field position is, given the fog. */
    private fun vis(w: World, x: Float, y: Float): Float {
        if (w.fogRadius <= 0f) return 1f
        val d = w.dist(x, y, w.player.x, w.player.y)
        return ((w.fogRadius + 0.8f - d) / 1.6f).coerceIn(0f, 1f)
    }

    /** Runs [draw] at a pixel position and again at any wrapped copies that poke over an edge. */
    private inline fun wrapped(w: World, x: Float, y: Float, margin: Float, draw: (Float, Float) -> Unit) {
        val px = x + ox
        val py = y + oy
        draw(px, py)
        val wx = if (x < margin) FIELD_W else if (x > FIELD_W - margin) -FIELD_W else 0f
        val wy = if (!w.wrapY) 0f else if (y < margin) FIELD_H else if (y > FIELD_H - margin) -FIELD_H else 0f
        if (wx != 0f) draw(px + wx, py)
        if (wy != 0f) draw(px, py + wy)
        if (wx != 0f && wy != 0f) draw(px + wx, py + wy)
    }

    private fun sx(w: World, b: Body): Float {
        if (b.tween >= 1f) return b.x * T
        return (b.x - w.dx(b.px, b.x, true) * (1f - ease(b.tween))) * T
    }

    private fun sy(w: World, b: Body): Float {
        if (b.tween >= 1f) return b.y * T
        return (b.y - w.dy(b.py, b.y, true) * (1f - ease(b.tween))) * T
    }

    private fun ease(t: Float): Float = 1f - (1f - t) * (1f - t)

    // ----------------------------------------------------------------------------------------
    // Layers
    // ----------------------------------------------------------------------------------------

    private fun background(w: World) {
        g.rect(0f, 0f, Gfx.W, Gfx.H, Pal.BG)
        val p = w.player
        if (w.fogRadius > 0f) {
            wrapped(w, sx(w, p), sy(w, p), w.fogRadius * T * 1.5f) { x, y ->
                g.glow(x, y, w.fogRadius * T * 1.9f, FOG_LIGHT, 0.5f)
            }
        }
        // Distant stars. In the scroller they stream past at different depths.
        for (i in 0 until SPECKS) {
            val depth = 0.25f + specks[i * 4 + 2] * 0.75f
            val x = specks[i * 4] * FIELD_W
            var y = specks[i * 4 + 1] * FIELD_H
            if (w.spec.move == MoveKind.SCROLL) y = ((y - w.scroll * T * depth * 0.6f) % FIELD_H + FIELD_H) % FIELD_H
            val twinkle = 0.55f + 0.45f * MathUtils.sin(time * (0.6f + specks[i * 4 + 3] * 1.8f) + i)
            val size = 1f + depth * 1.4f
            g.rect(x + ox * 0.3f, y + oy * 0.3f, size, size, Pal.DIM, 0.5f * depth * twinkle)
        }
        // The lattice: dots on the tile corners, so you can see the grid without it shouting.
        if (w.spec.move == MoveKind.GRID) {
            for (cy in 0..w.rows) for (cx in 0..w.cols) {
                val a = if (w.fogRadius > 0f) 0.25f + 0.75f * vis(w, cx.toFloat(), cy.toFloat()) else 1f
                g.rect(cx * T - 1f + ox, cy * T - 1f + oy, 2f, 2f, Pal.LATTICE, a)
            }
        }
        // Edges: faint and dashed where you can wrap, solid and angry where you cannot.
        edge(w.playerWrapX, 0f, 0f, 0f, FIELD_H)
        edge(w.playerWrapX, FIELD_W, 0f, FIELD_W, FIELD_H)
        edge(w.playerWrapY, 0f, 0f, FIELD_W, 0f)
        edge(w.playerWrapY, 0f, FIELD_H, FIELD_W, FIELD_H)
    }

    private fun edge(open: Boolean, x1: Float, y1: Float, x2: Float, y2: Float) {
        if (!open) {
            g.line(x1 + ox, y1 + oy, x2 + ox, y2 + oy, 5f, Pal.ORANGE, 0.75f)
            return
        }
        val dashes = 24
        for (i in 0 until dashes) {
            val a = (i + 0.2f) / dashes
            val b = (i + 0.6f) / dashes
            g.line(x1 + (x2 - x1) * a + ox, y1 + (y2 - y1) * a + oy, x1 + (x2 - x1) * b + ox, y1 + (y2 - y1) * b + oy, 3f, Pal.CYAN, 0.3f)
        }
    }

    private fun walls(w: World) {
        if (!w.hasWalls) return
        for (cy in 0 until w.rows) for (cx in 0 until w.cols) {
            if (!w.walls[cy * w.cols + cx]) continue
            val a = if (w.fogRadius > 0f) 0.12f + 0.88f * vis(w, cx + 0.5f, cy + 0.5f) else 1f
            val x = cx * T + ox
            val y = cy * T + oy
            g.rect(x + 1f, y + 1f, T - 2f, T - 2f, Pal.WALL, a)
            g.rect(x + 1f, y + T - 4f, T - 2f, 3f, Pal.WALL_HI, a)
            g.rect(x + 1f, y + 1f, 3f, T - 5f, Pal.WALL_HI, a * 0.5f)
        }
    }

    private fun meteors(w: World) {
        for (m in w.meteors) {
            if (m.warn > 0f) {
                // Warning: the doomed lane pulses, faster as impact gets closer.
                val pulse = 0.5f + 0.5f * MathUtils.sin(time * (10f + (1.3f - m.warn) * 14f))
                val a = 0.08f + 0.16f * pulse
                if (m.horizontal) g.rect(0f, m.line * T + oy, FIELD_W, T, Pal.RED, a)
                else g.rect(m.line * T + ox, 0f, T, FIELD_H, Pal.RED, a)
                // Arrow heads showing which way it will come from.
                val ax = if (m.horizontal) (if (m.dir > 0) 10f else FIELD_W - 10f) else m.line * T + T / 2f
                val ay = if (m.horizontal) m.line * T + T / 2f else (if (m.dir > 0) 10f else FIELD_H - 10f)
                val dx = if (m.horizontal) m.dir * 9f else 0f
                val dy = if (m.horizontal) 0f else m.dir * 9f
                g.triangle(ax + dx + ox, ay + dy + oy, ax - dx - dy + ox, ay - dy - dx + oy, ax - dx + dy + ox, ay - dy + dx + oy, Pal.RED, 0.5f + 0.5f * pulse)
            } else {
                val x = m.x * T + ox
                val y = m.y * T + oy
                val tx = if (m.horizontal) -m.dir * 1f else 0f
                val ty = if (m.horizontal) 0f else -m.dir * 1f
                for (i in 1..6) g.circle(x + tx * i * 9f, y + ty * i * 9f, 11f - i * 1.5f, Pal.ORANGE, 0.5f - i * 0.07f)
                g.glow(x, y, 34f, Pal.ORANGE, 0.9f)
                g.circle(x, y, 11f, Pal.ORANGE)
                g.circle(x - 3f, y + 3f, 4f, Pal.STAR, 0.8f)
            }
        }
    }

    private fun wells(w: World) {
        for (well in w.wells) {
            wrapped(w, well.x * T, well.y * T, 80f) { x, y ->
                g.glow(x, y, 120f, Pal.PURPLE, 0.45f)
                for (i in 0 until 4) {
                    // Rings fall inwards, forever.
                    val t = ((time * 0.35f + i / 4f) % 1f)
                    val r = 70f * (1f - t) + 14f
                    g.ring(x, y, r, 2.5f, Pal.PURPLE, 0.55f * t, time * 40f + i * 50f, 300f)
                }
                g.circle(x, y, 15f, Color.BLACK)
                g.ring(x, y, 15f, 3f, Pal.PINK, 0.9f)
            }
        }
    }

    private fun tail(w: World) {
        if (!w.tailActive) return
        val n = w.tail.size
        for (i in n - 1 downTo 0) {
            val t = w.tail[i]
            val f = 1f - i / (n + 4f)
            val a = vis(w, t.x, t.y)
            if (a <= 0f) continue
            wrapped(w, t.x * T, t.y * T, 16f) { x, y ->
                g.circle(x, y, 4f + 5f * f, Pal.SHIELD, a * (0.35f + 0.5f * f))
            }
        }
    }

    private fun decoy(w: World) {
        val d = w.decoy ?: return
        val a = min(1f, d.life) * (0.5f + 0.3f * MathUtils.sin(time * 30f))
        val x = d.x * T + ox
        val y = d.y * T + oy
        g.glow(x, y, 40f, Pal.CYAN, a)
        g.ring(x, y, 9f, 4f, Pal.CYAN, a)
        g.ring(x, y, 18f + 4f * MathUtils.sin(time * 8f), 2f, Pal.CYAN, a * 0.6f)
    }

    private fun star(w: World, s: Star) {
        val c = starColor(s.type)
        var a = vis(w, s.x, s.y)
        // In the nebula, far stars still glimmer so you know which way to head.
        if (w.fogRadius > 0f) a = max(a, 0.2f + 0.12f * MathUtils.sin(time * 5f + s.x * 3f))
        var scale = min(1f, s.age * 5f)
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
        wrapped(w, s.x * T, s.y * T, 20f) { x, y ->
            when (s.type) {
                StarType.COMMON -> {
                    g.glow(x, y, 20f * scale, c, a * 0.7f)
                    val r = 8.5f * scale * pulse
                    for (i in 0 until 3) {
                        val ang = spin * 0.4f + i * 60f
                        val dx = MathUtils.cosDeg(ang) * r
                        val dy = MathUtils.sinDeg(ang) * r
                        g.line(x - dx, y - dy, x + dx, y + dy, 3f, c, a)
                    }
                }
                StarType.GOLD -> {
                    g.glow(x, y, 30f * scale, c, a * 0.9f)
                    g.star(x, y, 13f * scale * pulse, 5.6f * scale * pulse, 5, 90f + MathUtils.sin(time * 2f) * 12f, c, a)
                    g.circle(x, y, 3f * scale, Pal.WHITE, a * 0.8f)
                }
                StarType.PULSAR -> {
                    g.glow(x, y, 28f * scale, c, a * 0.9f)
                    g.star(x, y, 14f * scale, 3.2f * scale, 4, spin, c, a)
                    val t = (s.age % Star.PULSAR_PERIOD) / Star.PULSAR_PERIOD
                    g.ring(x, y, 6f + 14f * t, 2f, c, a * (1f - t))
                }
                StarType.COMET -> {
                    for (i in 1..5) g.circle(x - s.vx * i * 3.2f, y - s.vy * i * 3.2f, 6f - i, c, a * (0.5f - i * 0.08f))
                    g.glow(x, y, 26f * scale, c, a * 0.9f)
                    g.star(x, y, 11f * scale, 4f * scale, 4, 45f, c, a)
                    g.circle(x, y, 3.5f * scale, Pal.WHITE, a)
                }
                StarType.NOVA -> {
                    g.glow(x, y, 46f * scale, c, a)
                    g.star(x, y, 16f * scale * pulse, 5f * scale, 8, spin * 0.6f, c, a)
                    g.circle(x, y, 5f * scale, Pal.WHITE, a)
                    if (s.ttl > 0f) g.ring(x, y, 20f, 2f, Pal.WHITE, a * 0.8f, 90f, 360f * (1f - s.age / s.ttl))
                }
            }
        }
    }

    private fun enemy(w: World, e: Enemy) {
        val vis = vis(w, e.x, e.y)
        if (vis <= 0f) return
        val base = enemyColor(e.type)
        var px = sx(w, e)
        var py = sy(w, e)
        val margin = 20f

        if (e.spawnT > 0f) {
            // Materialising: a closing ring gives fair warning before it can bite.
            val t = min(1f, e.spawnT)
            wrapped(w, px, py, margin) { x, y ->
                g.ring(x, y, 10f + 26f * t, 2.5f, base, vis * (1f - t * 0.6f), time * 200f, 280f)
                g.glyphAt(g.glyph, e.type.glyph, x, y, base, vis * (0.25f + 0.25f * MathUtils.sin(time * 22f)))
            }
            return
        }

        var a = vis
        if (e.life in 0f..1.5f) a *= e.life / 1.5f
        if (e.type === Zoomer && e.state == Zoomer.AIM) {
            // Telegraph the charge lane.
            val len = 13f * T
            val pulse = 0.5f + 0.5f * MathUtils.sin(time * 40f)
            g.line(px + ox, py + oy, px + e.dirX * len + ox, py + e.dirY * len + oy, 10f, base, a * (0.12f + 0.14f * pulse))
            g.line(px + ox, py + oy, px + e.dirX * len + ox, py + e.dirY * len + oy, 2f, base, a * 0.6f)
            px += (rnd.nextFloat() - 0.5f) * 3f
            py += (rnd.nextFloat() - 0.5f) * 3f
        }
        val stunned = e.stun > 0f
        val c = if (stunned) Pal.STUN else base
        wrapped(w, px, py, margin) { x, y ->
            g.glow(x, y, if (stunned) 18f else 30f, c, a * (if (stunned) 0.4f else 0.75f))
            g.glyphAt(g.glyph, e.type.glyph, x, y, c, a)
            if (stunned) {
                for (i in 0 until 3) {
                    val ang = time * 260f + i * 120f
                    g.circle(x + MathUtils.cosDeg(ang) * 14f, y + 10f + MathUtils.sinDeg(ang) * 4f, 2f, Pal.GOLD, a)
                }
            }
        }
    }

    private fun player(w: World, dt: Float) {
        val p = w.player
        val px = sx(w, p)
        val py = sy(w, p)
        // Exhaust in the free-flight modes: sells the glide.
        if (w.spec.move != MoveKind.GRID && w.phase == World.Phase.PLAYING) {
            val speed = kotlin.math.hypot(p.vx, p.vy)
            thrustAcc += dt * speed * 5f
            while (thrustAcc >= 1f) {
                thrustAcc -= 1f
                spark(p.x - p.faceX * 0.3f, p.y - p.faceY * 0.3f, -p.vx * 6f + (rnd.nextFloat() - 0.5f) * 30f, -p.vy * 6f + (rnd.nextFloat() - 0.5f) * 30f, 0.3f, 3f, Pal.CYAN)
            }
        }
        var a = 1f
        if (p.invuln > 0f && MathUtils.sin(time * 40f) < 0f) a = 0.35f
        wrapped(w, px, py, 26f) { x, y ->
            g.glow(x, y, 38f, Pal.CYAN, 0.6f * a)
            g.circle(x, y, 8.5f, Pal.BG, a)
            g.ring(x, y, 8.5f, 4.5f, Pal.PLAYER, a)
            for (i in 0 until p.shields) {
                g.ring(x, y, 15f + i * 4.5f, 2f, Pal.SHIELD, 0.85f * a, time * (90f + i * 40f), 290f)
            }
        }
    }

    private fun effects(dt: Float) {
        var i = rings.size - 1
        while (i >= 0) {
            val r = rings[i]
            r.life -= dt
            if (r.life <= 0f) rings.removeAt(i)
            else {
                val t = 1f - r.life / r.maxLife
                val e = 1f - (1f - t) * (1f - t)
                g.ring(r.x + ox, r.y + oy, r.to * e, 4f * (1f - t) + 1f, r.color, (1f - t) * 0.9f, add = true)
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
                g.circle(p.x + ox, p.y + oy, p.size * (0.4f + 0.6f * t), p.color, t, add = true)
            }
            i--
        }
        i = floaters.size - 1
        while (i >= 0) {
            val f = floaters[i]
            f.life -= dt
            if (f.life <= 0f) floaters.removeAt(i)
            else {
                f.y += 34f * dt
                g.text(g.small, f.text, f.x.coerceIn(30f, FIELD_W - 30f), min(f.y, FIELD_H - 4f), f.color, min(1f, f.life * 3f), Align.center)
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
        g.rect(0f, FIELD_H, Gfx.W, 2f, Pal.LINE)

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

        // Middle: the quota.
        val qx = 318f
        val label = "${w.collected} / ${w.spec.quota}"
        g.star(qx, top - 20f, 8f, 3.4f, 5, 90f, Pal.STAR)
        g.text(g.body, label, qx + 16f, top - 11f, Pal.TEXT)
        val bx = qx + 30f + g.width(g.body, label)
        val bw = 220f
        val by = top - 26f
        val frac = (w.collected.toFloat() / w.spec.quota).coerceIn(0f, 1f)
        g.rect(bx, by, bw, 12f, Pal.PANEL_HI)
        g.rect(bx, by, bw * frac, 12f, Pal.STAR)
        g.frame(bx - 1f, by - 1f, bw + 2f, 14f, 1f, Pal.LINE)
        if (w.timeLeft >= 0f) {
            val urgent = w.timeLeft < 5f
            val c = if (urgent && MathUtils.sin(time * 16f) > 0f) Pal.RED else if (urgent) Pal.ORANGE else Pal.TEXT
            g.text(g.body, "%.1fs".format(max(0f, w.timeLeft)), bx + bw + 16f, top - 11f, c)
        }

        // Right: score and combo.
        g.text(g.big, w.run.score.toString(), Gfx.W - 14f, top - 8f, Pal.TEXT, 1f, Align.right)
        if (w.combo > 1) {
            val t = (w.comboTimer / w.comboWindow).coerceIn(0f, 1f)
            g.text(g.small, "COMBO x${w.combo}", Gfx.W - 14f, FIELD_H + 22f, Pal.GOLD, 1f, Align.right)
            g.rect(Gfx.W - 14f - 90f * t, FIELD_H + 5f, 90f * t, 3f, Pal.GOLD)
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
        const val FIELD_W = 960f
        const val FIELD_H = 480f
        const val DIAL_R = 30f
        const val DIAL_Y = 48f
        const val DASH_X = 908f
        const val NOVA_X = 834f
        private const val SPECKS = 80
        private const val MAX_PARTICLES = 500
        private val FOG_LIGHT = Color.valueOf("3a55a8")

        fun starColor(t: StarType): Color = when (t) {
            StarType.COMMON -> Pal.STAR
            StarType.GOLD -> Pal.GOLD
            StarType.PULSAR -> Pal.PINK
            StarType.COMET -> Pal.CYAN
            StarType.NOVA -> Pal.ORANGE
        }

        fun enemyColor(t: EnemyType): Color = when (t) {
            Eater -> Pal.RED
            Stalker -> Pal.ORANGE
            Bouncer -> Pal.PURPLE
            Zoomer -> Pal.GREEN
            Warper -> Pal.SHIELD
            Hive -> Pal.PINK
            Mite -> Pal.PINK
            else -> Pal.RED
        }
    }
}
