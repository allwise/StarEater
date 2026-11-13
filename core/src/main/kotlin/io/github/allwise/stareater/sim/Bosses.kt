package io.github.allwise.stareater.sim

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sign

/**
 * Bosses: one very large letter per boss level. There are no guns in this game, so a boss is not
 * shot down - it is *out-eaten*. Every star you eat hurts it, and the level's quota is its health.
 * The more hurt it is, the angrier (faster) it gets.
 */
abstract class BossType(id: String, glyph: Char, title: String, blurb: String, speed: Float, wrapAware: Boolean = false) :
    EnemyType(id, glyph, title, blurb, speed, minLevel = 10, weight = 0f, wrapAware = wrapAware, inRoster = false) {
    override val radius: Float get() = 1.3f
    override val boss: Boolean get() = true

    /** 1 at full health, rising to 1.5 as the boss is worn down. */
    protected fun rage(w: World): Float = 1f + 0.5f * w.collected / max(1, w.spec.quota)
}

/** A giant E. Lumbers after you, then lunges - and keeps coughing up mites. */
object Devourer : BossType("devourer", 'E', "THE DEVOURER", "Lunges. Coughs up mites. Every star you eat hurts it.", 0.62f) {
    const val HUNT = 0
    const val AIM = 1
    const val LUNGE = 2
    const val REST = 3

    override fun init(w: World, e: Enemy) {
        e.timer = 4f
        e.travel = 5f // doubles as the mite timer while hunting
    }

    override fun think(w: World, e: Enemy, dt: Float) {
        e.timer -= dt
        when (e.state) {
            HUNT -> {
                e.pace = rage(w)
                chase(w, e, w.targetX, w.targetY)
                e.travel -= dt
                if (e.travel <= 0f) {
                    e.travel = 7f / rage(w)
                    if (w.enemies.count { it.type === Mite } < 3) w.spawnEnemyAt(Mite, e.x, e.y, 0.4f)
                }
                val dx = w.dx(e.x, w.targetX, false)
                val dy = w.dy(e.y, w.targetY, false)
                val dist = hypot(dx, dy)
                if (e.timer > 0f || dist !in 3f..11f) return
                if (w.spec.move == MoveKind.GRID) {
                    e.dirX = if (abs(dx) >= abs(dy)) sign(dx) else 0f
                    e.dirY = if (abs(dx) >= abs(dy)) 0f else sign(dy)
                } else {
                    e.dirX = dx / dist; e.dirY = dy / dist
                }
                e.state = AIM
                e.timer = 0.9f
                w.fx += Fx.Alert(e.x, e.y)
            }
            AIM -> {
                e.pace = 0f
                e.desireX = 0f; e.desireY = 0f
                e.vx = 0f; e.vy = 0f
                if (e.timer <= 0f) { e.state = LUNGE; e.ballistic = true; e.travel = 0f }
            }
            LUNGE -> {
                e.pace = 3.4f
                if (e.travel >= 9f) rest(e)
            }
            else -> {
                e.pace = 0f
                e.desireX = 0f; e.desireY = 0f
                if (e.timer <= 0f) { e.state = HUNT; e.timer = 3.5f / rage(w); e.travel = 3f }
            }
        }
    }

    override fun onBlocked(w: World, e: Enemy, hitX: Boolean, hitY: Boolean) = rest(e)

    private fun rest(e: Enemy) {
        e.state = REST
        e.ballistic = false
        e.timer = 1.3f
        e.vx = 0f; e.vy = 0f
    }
}

/** A giant S: a serpent whose whole body is deadly. Cuts you off, and wraps the long way round you. */
object Leviathan : BossType("leviathan", 'S', "THE LEVIATHAN", "A serpent. Its whole body bites. Every star you eat hurts it.", 0.95f) {
    const val SPACING = 0.7f

    override fun init(w: World, e: Enemy) {
        e.trail = ArrayList()
        e.trailX = e.x; e.trailY = e.y
    }

    override fun think(w: World, e: Enemy, dt: Float) {
        e.pace = rage(w)
        // Lead the target, like the little stalkers do - but never fully commit, so it sweeps past.
        val p = w.player
        val lead = 0.5f
        val tx = (w.targetX + p.vx * lead).coerceIn(1f, w.w - 1f)
        val ty = (w.targetY + p.vy * lead).coerceIn(1f, w.h - 1f)
        chase(w, e, tx, ty)

        val trail = e.trail ?: return
        if (hypot(w.dx(e.trailX, e.x, true), w.dy(e.trailY, e.y, true)) >= SPACING) {
            trail.add(0, Pt(e.trailX, e.trailY))
            e.trailX = e.x; e.trailY = e.y
            val length = (14 * rage(w)).toInt()
            while (trail.size > length) trail.removeAt(trail.size - 1)
        }
        for (t in trail) {
            if (w.dist(t.x, t.y, p.x, p.y) < 0.75f) { w.hitPlayer(); break }
        }
    }
}

/** A giant W. Knows the wrap, and calls rocks down on wherever you are standing. */
object Warden : BossType("warden", 'W', "THE WARDEN", "Wraps after you, calls down rocks, and locks the edges. Every star you eat hurts it.", 0.6f, wrapAware = true) {
    /** How long the edges flash before they shut, and how long they stay shut. */
    const val SEAL_WARN = 2.6f
    const val SEAL_HOLD = 6f

    override fun init(w: World, e: Enemy) {
        e.timer = 4f
        e.timer2 = 8f
    }

    override fun think(w: World, e: Enemy, dt: Float) {
        e.pace = rage(w)
        chase(w, e, w.targetX, w.targetY)
        e.timer -= dt
        if (e.timer <= 0f) {
            e.timer = 3.6f / rage(w)
            // A cross of rocks through the player's current tile.
            w.callMeteor(horizontal = true, line = w.player.y.toInt())
            w.callMeteor(horizontal = false, line = w.player.x.toInt())
            w.fx += Fx.Alert(e.x, e.y)
        }
        // The edge block: a jailer locks the doors. Announced well ahead, so you can get clear of
        // the rim (and of it) before the wrap stops working.
        e.timer2 -= dt
        if (e.timer2 <= 0f && !w.sealing) {
            e.timer2 = (SEAL_WARN + SEAL_HOLD) + 10f / rage(w)
            w.sealEdges(SEAL_WARN, SEAL_HOLD)
        }
    }
}
