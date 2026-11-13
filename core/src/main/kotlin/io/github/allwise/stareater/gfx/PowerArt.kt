package io.github.allwise.stareater.gfx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import io.github.allwise.stareater.sim.PowerUp

/**
 * The arcade power-ups, drawn from primitives. Deliberately not letters (letters bite) and not
 * star-shaped (stars are food): every one sits in the same tilted square, so "a square thing" always
 * means "grab it".
 */
class PowerArt(private val g: Gfx) {

    /** A capsule as it lies on the field. [size] is the half-width of its frame in pixels. */
    fun capsule(kind: PowerUp, x: Float, y: Float, size: Float, time: Float, a: Float) {
        val c = color(kind)
        val bob = MathUtils.sin(time * 4f) * 2f
        val r = size * (1f + 0.06f * MathUtils.sin(time * 6f))
        g.glow(x, y + bob, size * 3f, c, 0.75f * a)
        // The frame: a diamond, with a second one turning slowly behind it.
        diamond(x, y + bob, r * 1.25f, time * 50f, 1.5f, c, 0.35f * a)
        g.star(x, y + bob, r, r * 0.72f, 4, 0f, Pal.PANEL, 0.92f * a)
        diamond(x, y + bob, r, 0f, 2.5f, c, a)
        icon(kind, x, y + bob, size * 0.5f, time, a)
    }

    private fun diamond(x: Float, y: Float, r: Float, rot: Float, width: Float, c: Color, a: Float) {
        var px = x + MathUtils.cosDeg(rot) * r
        var py = y + MathUtils.sinDeg(rot) * r
        for (i in 1..4) {
            val nx = x + MathUtils.cosDeg(rot + i * 90f) * r
            val ny = y + MathUtils.sinDeg(rot + i * 90f) * r
            g.line(px, py, nx, ny, width, c, a)
            px = nx; py = ny
        }
    }

    /** Just the symbol, about [s] pixels from centre to edge. Also used small, in the HUD. */
    fun icon(kind: PowerUp, x: Float, y: Float, s: Float, time: Float, a: Float) {
        val c = color(kind)
        val w = (s * 0.3f).coerceAtLeast(1.5f)
        when (kind) {
            // Two chevrons: go faster.
            PowerUp.SPEED -> for (i in 0..1) {
                val ox = x - s * 0.55f + i * s * 0.75f
                g.line(ox, y + s * 0.8f, ox + s * 0.6f, y, w, c, a)
                g.line(ox + s * 0.6f, y, ox, y - s * 0.8f, w, c, a)
            }
            // An hourglass: more time on the combo clock.
            PowerUp.FRENZY -> {
                g.triangle(x - s * 0.75f, y + s * 0.9f, x + s * 0.75f, y + s * 0.9f, x, y, c, a)
                g.triangle(x - s * 0.75f, y - s * 0.9f, x + s * 0.75f, y - s * 0.9f, x, y, c, a)
            }
            // A four-leaf clover.
            PowerUp.LUCK -> {
                for (i in 0 until 4) {
                    val ang = 45f + i * 90f
                    g.circle(x + MathUtils.cosDeg(ang) * s * 0.5f, y + MathUtils.sinDeg(ang) * s * 0.5f, s * 0.45f, c, a)
                }
                g.circle(x, y, s * 0.25f, Pal.WHITE, a * 0.8f)
            }
            // The same broken ring the player wears.
            PowerUp.SHIELD -> g.ring(x, y, s * 0.85f, w, c, a, time * 90f, 290f)
            // A horseshoe magnet, with its two bright tips.
            PowerUp.MAGNET -> {
                g.ring(x, y - s * 0.1f, s * 0.65f, w * 1.2f, c, a, 180f, 180f)
                g.rect(x - s * 0.65f - w * 0.6f, y - s * 0.1f, w * 1.2f, s * 0.9f, c, a)
                g.rect(x + s * 0.65f - w * 0.6f, y - s * 0.1f, w * 1.2f, s * 0.9f, c, a)
                g.rect(x - s * 0.65f - w * 0.6f, y + s * 0.5f, w * 1.2f, s * 0.35f, Pal.WHITE, a)
                g.rect(x + s * 0.65f - w * 0.6f, y + s * 0.5f, w * 1.2f, s * 0.35f, Pal.WHITE, a)
            }
            // An arrow with speed lines.
            PowerUp.DASH -> {
                g.line(x - s * 0.9f, y, x + s * 0.3f, y, w, c, a)
                g.triangle(x + s, y, x + s * 0.2f, y + s * 0.65f, x + s * 0.2f, y - s * 0.65f, c, a)
                g.line(x - s * 0.9f, y + s * 0.6f, x - s * 0.3f, y + s * 0.6f, w * 0.6f, c, a * 0.6f)
                g.line(x - s * 0.9f, y - s * 0.6f, x - s * 0.3f, y - s * 0.6f, w * 0.6f, c, a * 0.6f)
            }
            // You, and the ghost of you.
            PowerUp.ECHO -> {
                g.ring(x - s * 0.35f, y, s * 0.5f, w * 0.7f, c, a * 0.45f)
                g.ring(x + s * 0.35f, y, s * 0.5f, w, c, a)
            }
            // A blast.
            PowerUp.NOVA -> {
                g.star(x, y, s, s * 0.4f, 8, time * 40f, c, a)
                g.circle(x, y, s * 0.3f, Pal.WHITE, a)
            }
        }
    }

    companion object {
        fun color(kind: PowerUp): Color = when (kind) {
            PowerUp.SPEED -> Pal.GREEN
            PowerUp.FRENZY -> Pal.PINK
            PowerUp.LUCK -> Pal.GOLD
            PowerUp.SHIELD -> Pal.SHIELD
            PowerUp.MAGNET -> Pal.PURPLE
            PowerUp.DASH -> Pal.CYAN
            PowerUp.ECHO -> Pal.TEXT
            PowerUp.NOVA -> Pal.ORANGE
        }
    }
}
