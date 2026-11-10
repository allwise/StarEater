package io.github.allwise.stareater.sim

import kotlin.math.floor
import kotlin.math.hypot

/**
 * A simple autopilot: greedy for stars, allergic to enemies. It is nowhere near a good player, but
 * it is enough to soak-test every rule combination and to drive attract-mode / screenshots.
 */
class Bot {
    private var last: Dir? = null

    fun drive(w: World, out: InputState) {
        val p = w.player
        val grid = w.spec.move == MoveKind.GRID
        val look = if (grid) 1f else 1.6f
        var bestScore = Float.MAX_VALUE
        var bestX = 0f
        var bestY = 0f
        for (c in CANDIDATES) {
            if (grid && c[0] != 0f && c[1] != 0f) continue
            var x = p.x + c[0] * look
            var y = p.y + c[1] * look
            if (x < 0f || x >= w.w) { if (!w.playerWrapX) continue; x = Geo.wrap(x, w.w) }
            if (y < 0f || y >= w.h) { if (!w.playerWrapY) continue; y = Geo.wrap(y, w.h) }
            if (w.isWallAt(x, y)) continue
            val score = danger(w, x, y) * 8f + starDistance(w, x, y) * 0.3f + (if (c[0] == 0f && c[1] == 0f) 0.4f else 0f)
            if (score < bestScore) { bestScore = score; bestX = c[0]; bestY = c[1] }
        }
        out.moveX = bestX
        out.moveY = bestY
        val dir = if (bestX == 0f && bestY == 0f) null else Dir.of(bestX, bestY)
        out.held = dir
        if (dir != null && dir != last) out.taps += dir
        last = dir

        var nearest = Float.MAX_VALUE
        for (e in w.enemies) if (e.active) nearest = minOf(nearest, w.dist(e.x, e.y, p.x, p.y))
        if (nearest < 2.2f) out.nova = true
        if (nearest < 1.6f) out.dash = true
    }

    private fun danger(w: World, x: Float, y: Float): Float {
        var d = 0f
        for (e in w.enemies) {
            if (e.stun > 0.4f || e.spawnT > 0.4f) continue
            val dist = w.dist(e.x, e.y, x, y)
            d += 1f / (dist * dist + 0.05f)
        }
        for (m in w.meteors) {
            val sameLine = if (m.horizontal) floor(y).toInt() == m.line else floor(x).toInt() == m.line
            if (sameLine) d += 0.6f
        }
        for (well in w.wells) {
            val dist = w.dist(well.x, well.y, x, y)
            d += 2f / (dist * dist + 0.05f)
        }
        for (i in 2 until w.tail.size) {
            val t = w.tail[i]
            if (w.dist(t.x, t.y, x, y) < 0.7f) d += 6f
        }
        return d
    }

    private fun starDistance(w: World, x: Float, y: Float): Float {
        var best = 40f
        for (s in w.stars) {
            if (!s.lit) continue
            val d = hypot(w.dx(x, s.x, w.playerWrapX), w.dy(y, s.y, w.playerWrapY))
            if (d < best) best = d
        }
        return best
    }

    private companion object {
        val CANDIDATES = arrayOf(
            floatArrayOf(0f, 0f), floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f), floatArrayOf(0f, 1f), floatArrayOf(0f, -1f),
            floatArrayOf(0.7f, 0.7f), floatArrayOf(-0.7f, 0.7f), floatArrayOf(0.7f, -0.7f), floatArrayOf(-0.7f, -0.7f),
        )
    }
}
