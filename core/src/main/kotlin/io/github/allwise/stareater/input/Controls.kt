package io.github.allwise.stareater.input

import com.badlogic.gdx.Input.Keys
import io.github.allwise.stareater.gfx.WorldRenderer
import io.github.allwise.stareater.sim.Dir
import io.github.allwise.stareater.sim.InputState
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Turns keys, mouse and touch into an [InputState].
 *
 * Keyboard: arrows / WASD to move, SPACE dashes, E fires the nova.
 * Touch: put a thumb down anywhere and drag - a floating stick. In the classic mode a quick tap
 * (no drag) steps once towards the tap. The two ability dials are tappable.
 * Tilt: lean the device the way you want to go. However it is being held when the level starts
 * counts as level, so it works slouched on a sofa as well as sat at a table.
 */
class Controls {
    val state = InputState()

    /** Where the player is on the canvas, so a tap can be read as "step that way". */
    var playerX = 0f
    var playerY = 0f
    var dashEnabled = false
    var novaEnabled = false

    // Floating stick, in canvas pixels. Public so the screen can draw it.
    var stickOn = false
        private set
    var stickOX = 0f
        private set
    var stickOY = 0f
        private set
    var stickX = 0f
        private set
    var stickY = 0f
        private set
    private var stickPointer = -1
    private var stickAge = 0f
    private var stickMoved = false
    private var stickDir: Dir? = null
    private val keysHeld = ArrayList<Dir>(4)

    // Tilt steering: the lean, -1..1 on each axis, after the dead zone.
    var tiltX = 0f
        private set
    var tiltY = 0f
        private set
    private var tiltOn = false
    private var tiltDir: Dir? = null
    private var neutralPitch = 0f
    private var calibration = 0

    fun keyDown(key: Int): Boolean {
        val dir = dirOf(key)
        if (dir != null) {
            keysHeld.remove(dir)
            keysHeld += dir
            state.taps += dir
            return true
        }
        when (key) {
            Keys.SPACE, Keys.Z, Keys.SHIFT_LEFT, Keys.SHIFT_RIGHT -> state.dash = true
            Keys.E, Keys.X, Keys.CONTROL_LEFT, Keys.CONTROL_RIGHT -> state.nova = true
            else -> return false
        }
        return true
    }

    fun keyUp(key: Int) {
        dirOf(key)?.let { keysHeld.remove(it) }
    }

    fun touchDown(x: Float, y: Float, pointer: Int) {
        if (dashEnabled && hypot(x - WorldRenderer.DASH_X, y - WorldRenderer.DIAL_Y) < BUTTON_REACH) { state.dash = true; return }
        if (novaEnabled && hypot(x - WorldRenderer.NOVA_X, y - WorldRenderer.DIAL_Y) < BUTTON_REACH) { state.nova = true; return }
        if (stickPointer >= 0) return
        stickPointer = pointer
        stickOn = true
        stickOX = x; stickOY = y
        stickX = x; stickY = y
        stickAge = 0f
        stickMoved = false
        stickDir = null
    }

    fun touchDragged(x: Float, y: Float, pointer: Int) {
        if (pointer != stickPointer) return
        stickX = x; stickY = y
        val dx = x - stickOX
        val dy = y - stickOY
        val len = hypot(dx, dy)
        if (len > STICK_RANGE) {
            // Let the stick's anchor trail the thumb, so reversing never needs a long haul back.
            stickOX = x - dx / len * STICK_RANGE
            stickOY = y - dy / len * STICK_RANGE
        }
        if (len > STEP_THRESHOLD) {
            stickMoved = true
            val dir = Dir.of(dx, dy)
            if (dir != stickDir) {
                stickDir = dir
                state.taps += dir
            }
        } else if (len < DEADZONE) {
            stickDir = null
        }
    }

    fun touchUp(x: Float, y: Float, pointer: Int) {
        if (pointer != stickPointer) return
        // A tap that never became a drag: step once towards it.
        if (!stickMoved && stickAge < TAP_TIME) {
            val dx = x - playerX
            val dy = y - playerY
            if (hypot(dx, dy) > 8f) state.taps += Dir.of(dx, dy)
        }
        stickPointer = -1
        stickOn = false
        stickDir = null
    }

    /**
     * Feeds in one accelerometer reading, in the device's own axes as libGDX reports them (m/s²).
     * @param rotation how far the display is turned from the device's natural way up: 0, 90, 180 or 270
     * @param sensitivity 1 is normal; higher needs less of a lean
     */
    fun tilt(ax: Float, ay: Float, az: Float, rotation: Int, sensitivity: Float, dt: Float) {
        val g = sqrt(ax * ax + ay * ay + az * az)
        if (g < 2f) return // in free fall, or the sensor has nothing to say yet
        // The same reading, along the screen's left and up instead of the device's.
        val left: Float
        val up: Float
        when (rotation) {
            90 -> { left = ay; up = ax }
            180 -> { left = ax; up = -ay }
            270 -> { left = -ay; up = -ax }
            else -> { left = -ax; up = ay }
        }
        // Roll: how far the right-hand side has dropped. Pitch: 0 flat on a table, 90 degrees upright.
        val roll = asin((left / g).coerceIn(-1f, 1f))
        val pitch = atan2(up, az)
        tiltOn = true
        if (calibration < CALIBRATION_FRAMES) {
            // Average the first few readings: that is "level" for this level.
            neutralPitch = if (calibration == 0) pitch else neutralPitch + (pitch - neutralPitch) / (calibration + 1)
            calibration++
            tiltX = 0f; tiltY = 0f
            return
        }
        val full = FULL_TILT / sensitivity
        val k = 1f - exp(-dt * TILT_SMOOTHING)
        tiltX += (curve(roll / full) - tiltX) * k
        tiltY += (curve((neutralPitch - pitch) / full) - tiltY) * k

        // For the classic mode: a firm lean is a direction held down.
        val lean = hypot(tiltX, tiltY)
        if (lean > TILT_HOLD) {
            val dir = Dir.of(tiltX, tiltY)
            if (dir != tiltDir) {
                tiltDir = dir
                state.taps += dir
            }
        } else if (lean < TILT_RELEASE) {
            tiltDir = null
        }
    }

    /** Tilt steering is off (or there is no sensor). */
    fun noTilt() {
        tiltOn = false
        tiltX = 0f; tiltY = 0f
        tiltDir = null
    }

    /** Takes however the device is held right now as the new "level". */
    fun recentre() {
        calibration = 0
        tiltX = 0f; tiltY = 0f
        tiltDir = null
    }

    private fun curve(v: Float): Float {
        val m = abs(v)
        if (m <= TILT_DEADZONE) return 0f
        return ((m - TILT_DEADZONE) / (1f - TILT_DEADZONE)).coerceAtMost(1f) * sign(v)
    }

    /** Refreshes the continuous parts of the state. Call once per frame before the sim update. */
    fun update(dt: Float) {
        stickAge += dt
        var mx = 0f
        var my = 0f
        for (d in keysHeld) { mx += d.dx; my += d.dy }
        var held: Dir? = keysHeld.lastOrNull()
        if (stickOn) {
            val dx = stickX - stickOX
            val dy = stickY - stickOY
            val len = hypot(dx, dy)
            if (len > DEADZONE) {
                val f = ((len - DEADZONE) / (STICK_RANGE - DEADZONE)).coerceAtMost(1f)
                mx += dx / len * f
                my += dy / len * f
            }
            if (held == null) held = stickDir
        }
        if (tiltOn) {
            mx += tiltX
            my += tiltY
            if (held == null) held = tiltDir
        }
        state.moveX = mx.coerceIn(-1f, 1f)
        state.moveY = my.coerceIn(-1f, 1f)
        state.held = held
    }

    fun reset() {
        keysHeld.clear()
        stickPointer = -1
        stickOn = false
        stickDir = null
        recentre()
        state.clearEdges()
        state.moveX = 0f; state.moveY = 0f; state.held = null
    }

    private fun dirOf(key: Int): Dir? = when (key) {
        Keys.UP, Keys.W -> Dir.UP
        Keys.DOWN, Keys.S -> Dir.DOWN
        Keys.LEFT, Keys.A -> Dir.LEFT
        Keys.RIGHT, Keys.D -> Dir.RIGHT
        else -> null
    }

    companion object {
        const val STICK_RANGE = 56f
        private const val DEADZONE = 10f
        private const val STEP_THRESHOLD = 20f
        private const val TAP_TIME = 0.3f
        private const val BUTTON_REACH = WorldRenderer.DIAL_R + 12f

        /** Radians of lean for full speed at normal sensitivity: about 17 degrees. */
        private const val FULL_TILT = 0.3f
        private const val TILT_DEADZONE = 0.12f
        private const val TILT_SMOOTHING = 14f
        private const val TILT_HOLD = 0.5f
        private const val TILT_RELEASE = 0.3f
        private const val CALIBRATION_FRAMES = 12
    }
}
