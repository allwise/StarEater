package io.github.allwise.stareater

import io.github.allwise.stareater.input.Controls
import io.github.allwise.stareater.sim.Dir
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tilt steering, fed with made-up accelerometer readings. The sensor reports, in the device's own
 * axes, which way "away from the ground" is - so a phone lying flat reads (0, 0, 9.8).
 */
class InputTest {
    private val g = 9.81f

    /**
     * A phone held in landscape (display rotation 90: its top edge is to the player's left), leaned
     * back by [pitch] degrees from flat and with its right-hand side dropped by [roll] degrees.
     * In that hold the device's +x points up the screen and its +y points to screen-left.
     */
    private fun feed(c: Controls, pitch: Float, roll: Float, frames: Int = 40, rotation: Int = 90, sensitivity: Float = 1f) {
        val p = Math.toRadians(pitch.toDouble())
        val r = Math.toRadians(roll.toDouble())
        val up = (sin(p) * cos(r)).toFloat() * g
        val left = sin(r).toFloat() * g
        val z = (cos(p) * cos(r)).toFloat() * g
        repeat(frames) {
            when (rotation) {
                90 -> c.tilt(up, left, z, 90, sensitivity, 1f / 60f)
                270 -> c.tilt(-up, -left, z, 270, sensitivity, 1f / 60f)
                else -> c.tilt(-left, up, z, 0, sensitivity, 1f / 60f)
            }
            c.update(1f / 60f)
        }
    }

    @Test
    fun howeverItIsHeldAtTheStartIsLevel() {
        for (hold in listOf(0f, 35f, 60f)) {
            val c = Controls()
            feed(c, hold, 0f)
            assertEquals(0f, c.state.moveX, 0.001f, "held at $hold")
            assertEquals(0f, c.state.moveY, 0.001f, "held at $hold")
        }
    }

    @Test
    fun leaningSteersTheWayYouLean() {
        val c = Controls()
        feed(c, 40f, 0f)
        feed(c, 40f, 20f)
        assertTrue(c.state.moveX > 0.9f, "right side down should fly right, got ${c.state.moveX}")
        assertEquals(0f, c.state.moveY, 0.05f)
        feed(c, 40f, -20f)
        assertTrue(c.state.moveX < -0.9f, "left side down should fly left, got ${c.state.moveX}")
        // Tipping the top edge away from you (flatter) is up; pulling it towards you is down.
        feed(c, 20f, 0f)
        assertTrue(c.state.moveY > 0.9f, "top edge away should fly up, got ${c.state.moveY}")
        feed(c, 60f, 0f)
        assertTrue(c.state.moveY < -0.9f, "top edge towards you should fly down, got ${c.state.moveY}")
    }

    @Test
    fun theSameLeanWorksWhicheverWayUpThePhoneIs() {
        for (rotation in listOf(0, 90, 270)) {
            val c = Controls()
            feed(c, 40f, 0f, rotation = rotation)
            feed(c, 25f, 15f, rotation = rotation)
            assertTrue(c.state.moveX > 0.5f && c.state.moveY > 0.5f, "rotation $rotation: got ${c.state.moveX}, ${c.state.moveY}")
        }
    }

    @Test
    fun aSmallWobbleIsIgnoredAndSensitivityScalesTheRest() {
        val still = Controls()
        feed(still, 40f, 0f)
        feed(still, 41f, 1.5f)
        assertEquals(0f, still.state.moveX, 0.001f)
        assertEquals(0f, still.state.moveY, 0.001f)

        val gentle = Controls()
        feed(gentle, 40f, 0f, sensitivity = 0.65f)
        feed(gentle, 40f, 9f, sensitivity = 0.65f)
        val twitchy = Controls()
        feed(twitchy, 40f, 0f, sensitivity = 1.6f)
        feed(twitchy, 40f, 9f, sensitivity = 1.6f)
        assertTrue(twitchy.state.moveX > gentle.state.moveX + 0.3f, "${twitchy.state.moveX} vs ${gentle.state.moveX}")
    }

    @Test
    fun aFirmLeanHoldsADirectionForTheClassicMode() {
        val c = Controls()
        feed(c, 40f, 0f)
        assertEquals(null, c.state.held)
        c.state.clearEdges()
        feed(c, 40f, 20f, frames = 30)
        assertEquals(Dir.RIGHT, c.state.held)
        assertEquals(listOf(Dir.RIGHT), c.state.taps.toList(), "one lean, one fresh step - not one per frame")
        // Recentring (a pause, a new level) forgets the lean until it has a new idea of level.
        c.recentre()
        feed(c, 40f, 20f, frames = 5)
        assertEquals(0f, c.state.moveX, 0.001f)
    }
}
