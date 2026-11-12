package io.github.allwise.stareater.gfx

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import io.github.allwise.stareater.sim.Bouncer
import io.github.allwise.stareater.sim.Devourer
import io.github.allwise.stareater.sim.Eater
import io.github.allwise.stareater.sim.Enemy
import io.github.allwise.stareater.sim.Hive
import io.github.allwise.stareater.sim.Mite
import io.github.allwise.stareater.sim.Stalker
import io.github.allwise.stareater.sim.Warden
import io.github.allwise.stareater.sim.Warper
import io.github.allwise.stareater.sim.Zoomer

/**
 * The enemies as living things. Each one is still recognisably its letter, but drawn from strokes
 * so it can move: the E's arms are jaws, the S slithers, the W flaps.
 *
 * Shapes are described in a little local space where the letter spans roughly -1..1 and +x is
 * "forwards"; the pen rotates that to the creature's heading and scales it up.
 */
class Creatures(private val g: Gfx) {
    private var cx = 0f
    private var cy = 0f
    private var cos = 1f
    private var sin = 0f
    private var unit = UNIT
    private var stretch = 1f
    private var color: Color = Color.WHITE
    private var alpha = 1f

    /**
     * @param heading radians, direction of travel
     * @param scale 1 for a normal enemy; bosses are several times that
     * @param t the creature's own animation clock
     * @param hunger 0..1, how close it is to its dinner (things get more frantic)
     */
    fun draw(e: Enemy, x: Float, y: Float, heading: Float, scale: Float, t: Float, hunger: Float, c: Color, a: Float) {
        cx = x; cy = y
        unit = UNIT * scale
        stretch = 1f
        color = c
        alpha = a
        aim(heading)
        when (e.type) {
            Eater -> eater(t, hunger, teeth = false)
            Mite -> eater(t * 1.6f, 1f, teeth = false)
            Devourer -> {
                if (e.state == Devourer.AIM) {
                    // Winding up: jaws wide, whole body trembling.
                    cx += MathUtils.random(-2f, 2f); cy += MathUtils.random(-2f, 2f)
                    eaterJaws(-0.35f, 0.2f, teeth = true)
                } else {
                    eater(if (e.state == Devourer.LUNGE) t * 2.2f else t, hunger, teeth = true)
                }
            }
            Stalker -> snake(t, hunger)
            Bouncer -> {
                aim(t * 2.4f) // it rolls rather than faces anywhere
                bouncer(t)
            }
            Zoomer -> zoomer(e, t)
            Warper -> wings(t, 1f)
            Warden -> {
                wings(t * 0.6f, 1f)
                // Its pet rocks.
                for (i in 0 until 3) {
                    val ang = t * 1.7f + i * MathUtils.PI2 / 3f
                    g.circle(cx + MathUtils.cos(ang) * unit * 1.9f, cy + MathUtils.sin(ang) * unit * 1.9f, unit * 0.22f, Pal.ORANGE, a)
                }
            }
            Hive -> {
                aim(0f)
                hive(e, t)
            }
            else -> eater(t, hunger, teeth = false)
        }
    }

    /** The head of the Leviathan; its body is drawn from its trail by the world renderer. */
    fun serpentHead(x: Float, y: Float, heading: Float, radius: Float, t: Float, c: Color, a: Float) {
        cx = x; cy = y; unit = radius; stretch = 1f; color = c; alpha = a
        aim(heading)
        g.circle(x, y, radius, c, a)
        // Forked tongue, flicking.
        val flick = 0.5f + 0.5f * MathUtils.sin(t * 9f)
        color = Pal.RED
        seg(0.9f, 0f, 1.3f + 0.4f * flick, 0f, 0.14f)
        seg(1.3f + 0.4f * flick, 0f, 1.6f + 0.4f * flick, 0.22f, 0.1f)
        seg(1.3f + 0.4f * flick, 0f, 1.6f + 0.4f * flick, -0.22f, 0.1f)
        // Eyes.
        color = Pal.BG
        dot(0.35f, 0.45f, 0.2f)
        dot(0.35f, -0.45f, 0.2f)
        color = Pal.STAR
        dot(0.42f, 0.45f, 0.09f)
        dot(0.42f, -0.45f, 0.09f)
    }

    // --- The pen -----------------------------------------------------------------------------

    private fun aim(heading: Float) {
        cos = MathUtils.cos(heading)
        sin = MathUtils.sin(heading)
    }

    private fun tx(x: Float, y: Float): Float = cx + (x * stretch * cos - y * sin) * unit
    private fun ty(x: Float, y: Float): Float = cy + (x * stretch * sin + y * cos) * unit

    /** A stroke with rounded ends. */
    private fun seg(x1: Float, y1: Float, x2: Float, y2: Float, width: Float = STROKE) {
        val ax = tx(x1, y1)
        val ay = ty(x1, y1)
        val bx = tx(x2, y2)
        val by = ty(x2, y2)
        g.line(ax, ay, bx, by, width * unit, color, alpha)
        g.circle(ax, ay, width * unit / 2f, color, alpha)
        g.circle(bx, by, width * unit / 2f, color, alpha)
    }

    private fun dot(x: Float, y: Float, r: Float) = g.circle(tx(x, y), ty(x, y), r * unit, color, alpha)

    // --- E: the arms are jaws ----------------------------------------------------------------

    private fun eater(t: Float, hunger: Float, teeth: Boolean) {
        // Chomps faster, and wider, the closer dinner gets.
        val chomp = 0.5f + 0.5f * MathUtils.sin(t * (5f + 9f * hunger))
        val close = MathUtils.lerp(-0.25f - 0.12f * hunger, 0.36f, chomp * chomp)
        eaterJaws(close, 0.25f + 0.3f * (1f - chomp), teeth)
    }

    /** @param close jaw angle: negative is splayed open, positive is biting down. */
    private fun eaterJaws(close: Float, tongue: Float, teeth: Boolean) {
        val back = -0.6f
        val len = 1.4f
        val ex = back + len * MathUtils.cos(close)
        val ey = len * MathUtils.sin(close)
        seg(back, -1f, back, 1f)
        seg(back, 1f, ex, 1f - ey)
        seg(back, -1f, ex, -1f + ey)
        seg(back, 0f, tongue, 0f, STROKE * 0.75f)
        if (teeth) {
            val base = color
            color = Pal.TEXT
            for (i in 1..3) {
                val f = i / 3.6f
                val jx = back + (ex - back) * f
                val top = 1f - ey * f
                seg(jx, top - 0.08f, jx + 0.06f, top - 0.34f, 0.1f)
                seg(jx, -top + 0.08f, jx + 0.06f, -top + 0.34f, 0.1f)
            }
            color = base
        }
        val base = color
        color = Pal.TEXT
        dot(-0.12f, 0.5f, 0.15f)
        color = base
    }

    // --- S: it slithers ----------------------------------------------------------------------

    private fun snake(t: Float, hunger: Float) {
        val n = 10
        var lx = 0f
        var ly = 0f
        for (i in 0..n) {
            val u = i / n.toFloat()
            val x = 1.15f - 2.5f * u
            val y = MathUtils.sin(u * 5.6f - t * (7f + 5f * hunger)) * 0.46f * minOf(1f, u * 3f + 0.12f)
            if (i > 0) seg(lx, ly, x, y, MathUtils.lerp(0.44f, 0.14f, u))
            lx = x; ly = y
        }
        val headY = MathUtils.sin(-t * (7f + 5f * hunger)) * 0.46f * 0.12f
        dot(1.2f, headY, 0.3f)
        val base = color
        color = Pal.BG
        dot(1.28f, headY + 0.14f, 0.07f)
        dot(1.28f, headY - 0.14f, 0.07f)
        color = base
    }

    // --- B: it bounces, and its bumps wobble -------------------------------------------------

    private fun bouncer(t: Float) {
        seg(-0.55f, -1f, -0.55f, 1f)
        bump(0.5f, 0.62f + 0.16f * MathUtils.sin(t * 7f))
        bump(-0.5f, 0.72f + 0.16f * MathUtils.sin(t * 7f + MathUtils.PI))
    }

    private fun bump(centreY: Float, reach: Float) {
        val steps = 6
        var lx = -0.55f
        var ly = centreY + 0.5f
        for (i in 1..steps) {
            val phi = MathUtils.PI * i / steps
            val x = -0.55f + reach * MathUtils.sin(phi)
            val y = centreY + 0.5f * MathUtils.cos(phi)
            seg(lx, ly, x, y)
            lx = x; ly = y
        }
    }

    // --- Z: a bolt that coils and springs ----------------------------------------------------

    private fun zoomer(e: Enemy, t: Float) {
        when (e.state) {
            Zoomer.AIM -> {
                stretch = 0.6f
                cx += MathUtils.random(-1.5f, 1.5f); cy += MathUtils.random(-1.5f, 1.5f)
                bolt(0f)
            }
            Zoomer.CHARGE -> {
                stretch = 1.7f
                // Afterimages streaming out behind.
                val a = alpha
                val ox = cx
                val oy = cy
                for (i in 2 downTo 1) {
                    cx = ox - cos * unit * i * 1.1f; cy = oy - sin * unit * i * 1.1f
                    alpha = a * (0.4f - i * 0.12f)
                    bolt(0f)
                }
                cx = ox; cy = oy; alpha = a
                bolt(0f)
            }
            Zoomer.REST -> {
                alpha *= 0.6f
                bolt(0.25f)
            }
            else -> bolt(0.12f * MathUtils.sin(t * 4f))
        }
        stretch = 1f
    }

    /** The Z, lying along the heading so the diagonal reads as a lightning bolt. */
    private fun bolt(shear: Float) {
        seg(0.9f, 0.75f + shear, 0.9f, -0.75f + shear)
        seg(0.9f, -0.75f + shear, -0.9f, 0.75f - shear)
        seg(-0.9f, 0.75f - shear, -0.9f, -0.75f - shear)
    }

    // --- W: wings ----------------------------------------------------------------------------

    private fun wings(t: Float, size: Float) {
        val flap = MathUtils.sin(t * 8f)
        val span = (0.95f + 0.2f * flap) * size
        val sweep = 0.22f * flap
        // A ghost of itself, half a beat behind: it is never quite all here.
        val a = alpha
        alpha = a * 0.3f
        wingPath(span * 1.12f, -sweep, -0.35f)
        alpha = a
        wingPath(span, sweep, 0f)
    }

    private fun wingPath(span: Float, sweep: Float, shift: Float) {
        seg(-0.6f - sweep + shift, -1.15f * span, 0.5f + shift, -0.55f * span)
        seg(0.5f + shift, -0.55f * span, -0.2f + shift, 0f)
        seg(-0.2f + shift, 0f, 0.5f + shift, 0.55f * span)
        seg(0.5f + shift, 0.55f * span, -0.6f - sweep + shift, 1.15f * span)
    }

    // --- H: it breathes, and something is growing on the crossbar ----------------------------

    private fun hive(e: Enemy, t: Float) {
        val breath = 0.62f + 0.08f * MathUtils.sin(t * 3f)
        seg(-breath, -1f, -breath, 1f)
        seg(breath, -1f, breath, 1f)
        seg(-breath, 0f, breath, 0f)
        // The egg swells as the next mite gets ready.
        val ready = (1f - e.timer / 6f).coerceIn(0f, 1f)
        val base = color
        color = Pal.TEXT
        dot(0f, 0f, 0.12f + 0.3f * ready)
        color = base
        for (i in 0 until 3) {
            val ang = t * 2f + i * MathUtils.PI2 / 3f
            dot(MathUtils.cos(ang) * 1.35f, MathUtils.sin(ang) * 1.35f, 0.1f)
        }
    }

    companion object {
        /** Pixels per local unit at scale 1: a creature is about 24px tall. */
        const val UNIT = 12f
        private const val STROKE = 0.36f
    }
}
