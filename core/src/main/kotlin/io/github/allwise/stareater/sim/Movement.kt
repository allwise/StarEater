package io.github.allwise.stareater.sim

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * How bodies actually move in a level. Enemy brains only say *where* they want to go; the movement
 * decides what that means - a tile step, or a steering force.
 */
abstract class Movement(val kind: MoveKind) {
    open val enemySpeedFactor = 1f

    abstract fun updatePlayer(w: World, input: InputState, dt: Float)
    abstract fun moveEnemy(w: World, e: Enemy, dt: Float)
    open fun updateWorld(w: World, dt: Float) {}

    companion object {
        fun create(kind: MoveKind): Movement = when (kind) {
            MoveKind.GRID -> GridMovement()
            MoveKind.GLIDE -> GlideMovement()
            MoveKind.SCROLL -> ScrollMovement()
        }
    }
}

/** The original: everything lives on tiles and moves one step at a time. */
class GridMovement : Movement(MoveKind.GRID) {

    override fun updatePlayer(w: World, input: InputState, dt: Float) {
        val p = w.player
        val interval = 1f / (PLAYER_RATE * w.loadout.speed * (if (w.restless) 0.7f else 1f))
        p.stepCd -= dt
        p.holdGate -= dt
        for (t in input.taps) if (p.queue.size < 2) p.queue += t

        // Velocity estimate (for enemies that predict): fades when you stand still.
        val fade = exp(-4f * dt)
        p.vx *= fade; p.vy *= fade

        if (p.stepCd > 0f) return
        var dir: Dir? = null
        var tapped = false
        val held = input.held
        if (p.queue.isNotEmpty()) {
            dir = p.queue.removeAt(0)
            tapped = true
        } else if (held != null && p.holdGate <= 0f) {
            dir = held
        } else if (w.restless && p.started) {
            dir = p.lastDir
        }
        if (dir == null) return
        step(w, dir, interval)
        p.stepCd = interval
        // A fresh tap waits a beat before auto-repeat kicks in, so one tap is reliably one step.
        p.holdGate = if (tapped) interval * 1.7f else interval * 0.9f
    }

    private fun step(w: World, dir: Dir, interval: Float) {
        val p = w.player
        p.lastDir = dir
        p.faceX = dir.dx.toFloat()
        p.faceY = dir.dy.toFloat()
        p.started = true
        var cx = floor(p.x).toInt() + dir.dx
        var cy = floor(p.y).toInt() + dir.dy
        var wrapped = false
        if (cx < 0 || cx >= w.cols) {
            if (!w.playerWrapX) return
            cx = Math.floorMod(cx, w.cols); wrapped = true
        }
        if (cy < 0 || cy >= w.rows) {
            if (!w.playerWrapY) return
            cy = Math.floorMod(cy, w.rows); wrapped = true
        }
        if (w.isWall(cx, cy)) return
        val ox = p.x
        val oy = p.y
        p.stepTo(cx + 0.5f, cy + 0.5f, min(0.09f, interval * 0.9f))
        p.vx = p.vx * 0.4f + dir.dx / interval * 0.6f
        p.vy = p.vy * 0.4f + dir.dy / interval * 0.6f
        if (wrapped) w.onPlayerWrap(ox, oy)
    }

    override fun moveEnemy(w: World, e: Enemy, dt: Float) {
        val speed = w.speedOf(e)
        if (speed <= 0f) return
        e.stepAcc += dt * speed
        var guard = 0
        while (e.stepAcc >= 1f && guard++ < 3) {
            e.stepAcc -= 1f
            val dur = min(0.12f, 0.8f / speed)
            if (e.ballistic) ballisticStep(w, e, dur) else chaseStep(w, e, dur)
        }
        if (e.stepAcc > 1f) e.stepAcc = 1f
    }

    private fun chaseStep(w: World, e: Enemy, dur: Float) {
        val dx = e.desireX
        val dy = e.desireY
        val ax = abs(dx)
        val ay = abs(dy)
        if (ax < 0.01f && ay < 0.01f) return
        // Dim enemies dither: sometimes they hesitate, sometimes they wander off.
        val blunder = 0.32f * (1f - w.iq) * (1f - w.iq)
        if (w.rng.nextFloat() < blunder) {
            if (w.rng.nextBoolean()) return
            val d = Dir.values()[w.rng.nextInt(4)]
            tryStep(w, e, d.dx, d.dy, dur)
            return
        }
        val sx = sign(dx).toInt()
        val sy = sign(dy).toInt()
        val xFirst = when {
            ax < 0.5f -> false
            ay < 0.5f -> true
            else -> w.rng.nextFloat() < ax / (ax + ay)
        }
        if (xFirst) {
            if (!tryStep(w, e, sx, 0, dur) && ay >= 0.5f) tryStep(w, e, 0, sy, dur)
        } else {
            if (!tryStep(w, e, 0, sy, dur) && ax >= 0.5f) tryStep(w, e, sx, 0, dur)
        }
    }

    private fun tryStep(w: World, e: Enemy, sx: Int, sy: Int, dur: Float): Boolean {
        if (sx == 0 && sy == 0) return false
        val cx = floor(e.x).toInt() + sx
        val cy = floor(e.y).toInt() + sy
        if (blocked(w, e, cx, cy)) return false
        val nx = Math.floorMod(cx, w.cols) + 0.5f
        val ny = Math.floorMod(cy, w.rows) + 0.5f
        for (o in w.enemies) if (o !== e && abs(o.x - nx) < 0.5f && abs(o.y - ny) < 0.5f) return false
        e.stepTo(nx, ny, dur)
        return true
    }

    private fun blocked(w: World, e: Enemy, cx: Int, cy: Int): Boolean {
        if ((cx < 0 || cx >= w.cols) && !e.wraps) return true
        if ((cy < 0 || cy >= w.rows) && !(e.wraps && w.wrapY)) return true
        return w.isWall(cx, cy)
    }

    private fun ballisticStep(w: World, e: Enemy, dur: Float) {
        val cx = floor(e.x).toInt()
        val cy = floor(e.y).toInt()
        var sx = axis(e.dirX)
        var sy = axis(e.dirY)
        val hitX = sx != 0 && blocked(w, e, cx + sx, cy)
        val hitY = sy != 0 && blocked(w, e, cx, cy + sy)
        if (hitX || hitY) {
            e.type.onBlocked(w, e, hitX, hitY)
            if (!e.ballistic) return
            sx = axis(e.dirX)
            sy = axis(e.dirY)
        }
        if (blocked(w, e, cx + sx, cy + sy)) {
            e.type.onBlocked(w, e, true, true)
            return
        }
        e.stepTo(Math.floorMod(cx + sx, w.cols) + 0.5f, Math.floorMod(cy + sy, w.rows) + 0.5f, dur)
        e.travel += 1f
    }

    private fun axis(v: Float): Int = if (v > 0.3f) 1 else if (v < -0.3f) -1 else 0

    companion object {
        /** Steps per second while a direction is held. */
        const val PLAYER_RATE = 8f
    }
}

/** Shared behaviour of the two free-movement modes. */
abstract class ContinuousMovement(
    kind: MoveKind,
    private val accel: Float,
    private val drag: Float,
    private val maxSpeed: Float,
) : Movement(kind) {

    override fun updatePlayer(w: World, input: InputState, dt: Float) {
        val p = w.player
        var ix = input.moveX
        var iy = input.moveY
        val il = hypot(ix, iy)
        if (il > 1f) { ix /= il; iy /= il }
        if (il > 0.2f) p.started = true

        val top = maxSpeed * w.loadout.speed
        p.vx += ix * accel * dt
        p.vy += iy * accel * dt
        val keep = exp(-(if (w.restless) 0.1f else drag) * dt)
        p.vx *= keep; p.vy *= keep

        var speed = hypot(p.vx, p.vy)
        if (speed > top) {
            p.vx *= top / speed; p.vy *= top / speed
            speed = top
        }
        if (w.restless && p.started) {
            val floorSpeed = top * 0.6f
            if (speed < floorSpeed) {
                if (speed < 0.05f) { p.vx = p.faceX * floorSpeed; p.vy = p.faceY * floorSpeed }
                else { p.vx *= floorSpeed / speed; p.vy *= floorSpeed / speed }
                speed = floorSpeed
            }
        }
        if (speed > 0.3f) { p.faceX = p.vx / speed; p.faceY = p.vy / speed }

        val ox = p.x
        val oy = p.y
        val hit = w.moveBody(p, p.vx * dt, p.vy * dt, 0.3f, w.playerWrapX, w.playerWrapY)
        // Restless ships bounce rather than stall against something solid.
        if (hit and 1 != 0) p.vx = if (w.restless) -p.vx else 0f
        if (hit and 2 != 0) p.vy = if (w.restless) -p.vy else 0f
        if (abs(p.x - ox) > w.w / 2f || abs(p.y - oy) > w.h / 2f) w.onPlayerWrap(ox, oy)
    }

    override fun moveEnemy(w: World, e: Enemy, dt: Float) {
        val speed = w.speedOf(e)
        if (e.ballistic) {
            e.vx = e.dirX * speed
            e.vy = e.dirY * speed
            val hit = w.moveBody(e, e.vx * dt, e.vy * dt, 0.3f, e.wraps, e.wraps)
            e.travel += speed * dt
            if (hit != 0) e.type.onBlocked(w, e, hit and 1 != 0, hit and 2 != 0)
            return
        }
        var tvx = 0f
        var tvy = 0f
        val len = hypot(e.desireX, e.desireY)
        if (len > 0.001f && speed > 0f) {
            // Dim enemies weave about instead of flying a clean line.
            val wobble = sin(w.time * 2.3f + e.seed) * (1f - w.iq) * 0.7f
            val c = cos(wobble)
            val s = sin(wobble)
            val ux = e.desireX / len
            val uy = e.desireY / len
            tvx = (ux * c - uy * s) * speed
            tvy = (ux * s + uy * c) * speed
        }
        // Keep the pack from collapsing into one blob.
        for (o in w.enemies) {
            if (o === e || o.spawnT > 0f) continue
            val ddx = e.x - o.x
            val ddy = e.y - o.y
            val d2 = ddx * ddx + ddy * ddy
            if (d2 < 0.81f && d2 > 0.0001f) {
                val d = sqrt(d2)
                val push = (0.9f - d) * 5f
                tvx += ddx / d * push
                tvy += ddy / d * push
            }
        }
        val agility = (3f + 5f * w.iq) * max(speed, 1.5f)
        var ax = tvx - e.vx
        var ay = tvy - e.vy
        val al = hypot(ax, ay)
        val maxDv = agility * dt
        if (al > maxDv) { ax *= maxDv / al; ay *= maxDv / al }
        e.vx += ax; e.vy += ay
        val hit = w.moveBody(e, e.vx * dt, e.vy * dt, 0.3f, e.wraps, e.wraps)
        if (hit and 1 != 0) e.vx = 0f
        if (hit and 2 != 0) e.vy = 0f
    }
}

/** Mode 2: thrust and drift, like a space shooter. */
class GlideMovement : ContinuousMovement(MoveKind.GLIDE, accel = 34f, drag = 1.8f, maxSpeed = 8.5f) {
    override val enemySpeedFactor = 1.1f
}

/** Mode 3: a top-down scroller. Stars stream down from the top; you only wrap sideways. */
class ScrollMovement : ContinuousMovement(MoveKind.SCROLL, accel = 70f, drag = 7f, maxSpeed = 9f) {
    private var starTimer = 0f

    override fun updateWorld(w: World, dt: Float) {
        val speed = w.spec.scrollSpeed
        w.scroll += speed * dt
        var i = w.stars.size - 1
        while (i >= 0) {
            val s = w.stars[i]
            s.y -= speed * dt
            if (s.y < -0.8f) w.stars.removeAt(i)
            i--
        }
        starTimer -= dt
        if (starTimer <= 0f) {
            // Space the stream so about `starsOnField` stars are on screen at once.
            starTimer = (w.h / speed) / w.spec.starsOnField * (0.6f + 0.8f * w.rng.nextFloat())
            w.spawnStar()
        }
    }
}
