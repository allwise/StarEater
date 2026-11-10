package io.github.allwise.stareater.sim

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * A modifier that changes how a level plays. Each rule is self-contained: it gets hooks into the
 * world and keeps its own state, so a level is simply "one movement mode + any compatible rules".
 *
 * To add a rule: subclass, then register a [RuleInfo] for it in [Rules.all].
 */
abstract class Rule {
    open fun setup(w: World) {}
    open fun update(w: World, dt: Float) {}
    open fun onStarSpawn(w: World, s: Star) {}
    open fun onStarEaten(w: World, s: Star) {}
}

class RuleInfo(
    val id: String,
    val title: String,
    val blurb: String,
    val minLevel: Int,
    /** Movement modes the rule makes sense in. */
    val moves: Set<MoveKind>,
    /** Adjusts the level's numbers before the world is built. */
    val tune: (LevelSpec) -> Unit = {},
    val create: () -> Rule,
)

object Rules {
    private val ALL_MOVES = MoveKind.values().toSet()
    private val FLAT = setOf(MoveKind.GRID, MoveKind.GLIDE)
    private val FLOATY = setOf(MoveKind.GLIDE, MoveKind.SCROLL)

    val all: List<RuleInfo> = listOf(
        RuleInfo("fog", "DARK NEBULA", "You only see what is close. Stars still glimmer.", 2, ALL_MOVES) { FogRule() },
        RuleInfo("walls", "DEBRIS FIELD", "Wreckage blocks the way - for them too.", 2, FLAT) { WallsRule() },
        RuleInfo("shy", "SHY STARS", "Stars drift away when you get close.", 2, FLAT) { ShyStarsRule() },
        RuleInfo("decay", "FADING LIGHT", "Stars burn out if you dawdle.", 2, FLAT) { DecayRule() },
        RuleInfo(
            "swarm", "SWARM", "Twice the enemies, but slower.", 2, ALL_MOVES,
            tune = {
                it.enemyStart *= 2
                it.enemyMax = (it.enemyMax * 1.7f).toInt()
                it.enemySpeed *= 0.72f
                it.spawnInterval *= 0.55f
            },
        ) { object : Rule() {} },
        RuleInfo("countdown", "COUNTDOWN", "The clock is running. Every star buys time.", 3, ALL_MOVES) { CountdownRule() },
        RuleInfo("meteors", "METEOR SHOWER", "Rows and columns flash, then get flattened.", 3, ALL_MOVES) { MeteorRule() },
        RuleInfo("restless", "RESTLESS", "No brakes. You can steer, you cannot stop.", 3, FLAT) { RestlessRule() },
        RuleInfo("tail", "SERPENT", "Every star grows your tail. Do not bite it.", 4, FLAT) { TailRule() },
        RuleInfo("gravity", "BLACK HOLE", "Something heavy is pulling everything in.", 4, FLOATY) { GravityRule() },
        RuleInfo("inversion", "INVERSION", "The edges are solid for you - and open for them.", 5, ALL_MOVES) { InversionRule() },
    )

    fun byId(id: String): RuleInfo = all.first { it.id == id }
}

class FogRule : Rule() {
    override fun setup(w: World) {
        w.fogRadius = max(3.8f, 5.6f - 0.08f * w.spec.number)
    }
}

class WallsRule : Rule() {
    override fun setup(w: World) {
        val target = min(70, 30 + w.spec.number * 2)
        repeat(25) {
            w.walls.fill(false)
            var placed = 0
            var guard = 0
            while (placed < target && guard++ < 400) {
                val horizontal = w.rng.nextBoolean()
                val len = 2 + w.rng.nextInt(4)
                val sx = w.rng.nextInt(w.cols)
                val sy = w.rng.nextInt(w.rows)
                for (i in 0 until len) {
                    val cx = sx + if (horizontal) i else 0
                    val cy = sy + if (horizontal) 0 else i
                    if (cx >= w.cols || cy >= w.rows) break
                    // Keep a pocket of free space around the player's starting cell.
                    if (abs(cx - w.cols / 2) <= 2 && abs(cy - w.rows / 2) <= 2) continue
                    val idx = cy * w.cols + cx
                    if (!w.walls[idx]) {
                        w.walls[idx] = true
                        placed++
                    }
                }
            }
            if (allReachable(w)) {
                w.hasWalls = true
                return
            }
        }
        w.walls.fill(false) // Could not make a fair layout: play it open instead.
    }

    /** Every free cell must be reachable without wrapping, or non-wrapping enemies could be sealed in. */
    private fun allReachable(w: World): Boolean {
        val seen = BooleanArray(w.walls.size)
        val queue = IntArray(w.walls.size)
        var head = 0
        var tail = 0
        val start = (w.rows / 2) * w.cols + w.cols / 2
        seen[start] = true
        queue[tail++] = start
        while (head < tail) {
            val c = queue[head++]
            val cx = c % w.cols
            val cy = c / w.cols
            for (d in Dir.values()) {
                val nx = cx + d.dx
                val ny = cy + d.dy
                if (nx < 0 || ny < 0 || nx >= w.cols || ny >= w.rows) continue
                val n = ny * w.cols + nx
                if (seen[n] || w.walls[n]) continue
                seen[n] = true
                queue[tail++] = n
            }
        }
        return tail == w.walls.count { !it }
    }
}

class ShyStarsRule : Rule() {
    override fun update(w: World, dt: Float) {
        val p = w.player
        for (s in w.stars) {
            val dx = w.dx(p.x, s.x, true)
            val dy = w.dy(p.y, s.y, true)
            val d = hypot(dx, dy)
            if (d > RANGE || d < 0.01f) continue
            val step = SPEED * (1f - d / RANGE * 0.6f) * dt
            val nx = Geo.wrap(s.x + dx / d * step, w.w)
            val ny = (s.y + dy / d * step).let { if (w.wrapY) Geo.wrap(it, w.h) else it.coerceIn(0.5f, w.h - 0.5f) }
            if (w.isWallAt(nx, ny)) continue
            s.x = nx; s.y = ny
        }
    }

    private companion object {
        const val RANGE = 4.2f
        const val SPEED = 4.2f
    }
}

class DecayRule : Rule() {
    override fun onStarSpawn(w: World, s: Star) {
        if (s.ttl <= 0f) s.ttl = max(4.5f, 9f - 0.12f * w.spec.number) + w.rng.nextFloat() * 2f
    }
}

class CountdownRule : Rule() {
    override fun setup(w: World) {
        w.timeLeft = START
    }

    override fun update(w: World, dt: Float) {
        if (!w.player.started) return // The clock starts when you do.
        w.timeLeft -= dt
        if (w.timeLeft <= 0f) {
            w.fx += Fx.TimeUp()
            w.player.invuln = 0f // Time running out always costs you.
            w.hitPlayer()
            w.timeLeft = 10f
        }
    }

    override fun onStarEaten(w: World, s: Star) {
        w.timeLeft = min(CAP, w.timeLeft + 2.2f + 0.3f * s.type.value)
    }

    private companion object {
        const val START = 15f
        const val CAP = 20f
    }
}

class MeteorRule : Rule() {
    private var timer = 3f

    override fun update(w: World, dt: Float) {
        timer -= dt
        if (timer <= 0f) {
            timer = max(1.3f, 4.2f - 0.14f * w.spec.number) * (0.7f + 0.6f * w.rng.nextFloat())
            val horizontal = w.rng.nextBoolean()
            val dir = if (w.rng.nextBoolean()) 1 else -1
            val line = w.rng.nextInt(if (horizontal) w.rows else w.cols)
            val span = if (horizontal) w.w else w.h
            w.meteors += Meteor(horizontal, line, dir, WARN, if (dir > 0) -1f else span + 1f)
        }
        var i = w.meteors.size - 1
        while (i >= 0) {
            val m = w.meteors[i]
            if (m.warn > 0f) {
                m.warn -= dt
            } else {
                m.pos += m.dir * SPEED * dt
                val span = if (m.horizontal) w.w else w.h
                if (m.pos < -1.5f || m.pos > span + 1.5f) {
                    w.meteors.removeAt(i)
                } else {
                    if (hypot(w.player.x - m.x, w.player.y - m.y) < 0.75f) w.hitPlayer()
                    // Rocks are not picky about who they flatten.
                    for (e in w.enemies) {
                        if (e.active && hypot(e.x - m.x, e.y - m.y) < 0.75f) w.stun(e, 2.5f)
                    }
                }
            }
            i--
        }
    }

    private companion object {
        const val WARN = 1.3f
        const val SPEED = 15f
    }
}

class RestlessRule : Rule() {
    override fun setup(w: World) {
        w.restless = true
    }
}

class TailRule : Rule() {
    private var lastX = 0f
    private var lastY = 0f
    private var spacing = 1f

    override fun setup(w: World) {
        w.tailActive = true
        lastX = w.player.x
        lastY = w.player.y
        spacing = if (w.spec.move == MoveKind.GRID) 1f else 0.5f
    }

    override fun update(w: World, dt: Float) {
        val p = w.player
        val grid = w.spec.move == MoveKind.GRID
        val moved = hypot(w.dx(lastX, p.x, true), w.dy(lastY, p.y, true))
        if (moved >= spacing - 0.01f) {
            w.tail.add(0, Pt(lastX, lastY))
            lastX = p.x; lastY = p.y
            val segments = 3 + w.collected
            val maxLen = min(if (grid) 36 else 60, if (grid) segments else segments * 2)
            while (w.tail.size > maxLen) w.tail.removeAt(w.tail.size - 1)
        }
        if (p.invuln > 0f) return
        // The freshest few points are right behind you; only the older ones can bite.
        val from = if (grid) 0 else 4
        val reach = if (grid) 0.5f else 0.38f
        for (i in from until w.tail.size) {
            val t = w.tail[i]
            if (hypot(w.dx(t.x, p.x, true), w.dy(t.y, p.y, true)) < reach) {
                w.hitPlayer()
                // Mercy: lose the back half so you are not immediately tangled again.
                while (w.tail.size > i / 2 + 1) w.tail.removeAt(w.tail.size - 1)
                return
            }
        }
    }
}

class GravityRule : Rule() {
    override fun setup(w: World) {
        val count = if (w.spec.number >= 9) 2 else 1
        repeat(count) {
            var x = 0f
            var y = 0f
            for (attempt in 0 until 40) {
                x = 3f + w.rng.nextFloat() * (w.w - 6f)
                y = 3f + w.rng.nextFloat() * (w.h - 6f)
                val farFromPlayer = hypot(x - w.player.x, y - w.player.y) > 7f
                val farFromOthers = w.wells.all { hypot(x - it.x, y - it.y) > 8f }
                if (farFromPlayer && farFromOthers) break
            }
            w.wells += Well(x, y, if (w.spec.move == MoveKind.SCROLL) 34f else 22f)
        }
    }

    override fun update(w: World, dt: Float) {
        val p = w.player
        for (well in w.wells) {
            if (w.spec.move == MoveKind.SCROLL) {
                well.y -= w.spec.scrollSpeed * 0.6f * dt
                if (well.y < -3f) {
                    well.y = w.h + 3f
                    well.x = 3f + w.rng.nextFloat() * (w.w - 6f)
                }
            }
            val dx = w.dx(p.x, well.x, true)
            val dy = w.dy(p.y, well.y, true)
            val d = hypot(dx, dy)
            if (d < REACH && d > 0.01f) {
                val a = well.pull / max(d, 1.2f) * (1f - d / REACH)
                p.vx += dx / d * a * dt
                p.vy += dy / d * a * dt
            }
            if (d < 0.6f && p.invuln <= 0f) {
                w.hitPlayer()
                // Spit the player back out so they are not stuck in the middle.
                val ux = if (d > 0.01f) -dx / d else 1f
                val uy = if (d > 0.01f) -dy / d else 0f
                p.vx = ux * 9f; p.vy = uy * 9f
            }
            for (e in w.enemies) {
                if (!e.active) continue
                val ex = well.x - e.x
                val ey = well.y - e.y
                val ed = hypot(ex, ey)
                if (ed < REACH && ed > 0.3f) {
                    val a = well.pull * 0.35f / max(ed, 1.2f) * (1f - ed / REACH)
                    e.vx += ex / ed * a * dt
                    e.vy += ey / ed * a * dt
                }
            }
        }
    }

    private companion object {
        const val REACH = 9f
    }
}

class InversionRule : Rule() {
    override fun setup(w: World) {
        w.playerWrapX = false
        w.playerWrapY = false
        w.enemiesWrap = true
    }
}
