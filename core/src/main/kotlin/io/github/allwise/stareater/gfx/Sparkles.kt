package io.github.allwise.stareater.gfx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils

/** Screen-space fireworks for the menus: high scores deserve a bit of a party. */
class Sparkles(private val g: Gfx) {
    private class P(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float, val size: Float, val color: Color, val star: Boolean)

    private val items = ArrayList<P>()
    private var timer = 0f
    private var shot = 0

    /** A burst of sparks at a point on the canvas. */
    fun burst(x: Float, y: Float, c: Color, n: Int = 34, speed: Float = 240f) {
        repeat(n) {
            val a = MathUtils.random(MathUtils.PI2)
            val s = speed * MathUtils.random(0.25f, 1f)
            val life = MathUtils.random(0.6f, 1.3f)
            if (items.size < 500) items += P(x, y, MathUtils.cos(a) * s, MathUtils.sin(a) * s, life, life, MathUtils.random(2f, 4.5f), c, it % 6 == 0)
        }
    }

    /** Keeps a show going: a new rocket every so often, somewhere in the upper part of the screen. */
    fun celebrate(dt: Float, every: Float = 0.45f) {
        timer -= dt
        if (timer > 0f) return
        timer = every * MathUtils.random(0.6f, 1.4f)
        burst(MathUtils.random(80f, Gfx.W - 80f), MathUtils.random(250f, Gfx.H - 40f), WorldRenderer.PARTY[shot++ % WorldRenderer.PARTY.size])
    }

    fun draw(dt: Float) {
        var i = items.size - 1
        while (i >= 0) {
            val p = items[i]
            p.life -= dt
            if (p.life <= 0f) {
                items.removeAt(i)
            } else {
                p.vy -= 160f * dt // a little gravity, so bursts droop like the real thing
                p.vx *= 1f - 1.6f * dt; p.vy *= 1f - 1.6f * dt
                p.x += p.vx * dt; p.y += p.vy * dt
                val t = p.life / p.maxLife
                if (p.star) g.star(p.x, p.y, p.size * 2.2f * t, p.size * 0.9f * t, 5, p.life * 300f, p.color, t)
                else g.circle(p.x, p.y, p.size * (0.4f + 0.6f * t), p.color, t, add = true)
            }
            i--
        }
    }
}
