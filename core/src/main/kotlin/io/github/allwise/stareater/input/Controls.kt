package io.github.allwise.stareater.input

import com.badlogic.gdx.Input.Keys
import io.github.allwise.stareater.gfx.WorldRenderer
import io.github.allwise.stareater.sim.Dir
import io.github.allwise.stareater.sim.InputState
import kotlin.math.hypot

/**
 * Turns keys, mouse and touch into an [InputState].
 *
 * Keyboard: arrows / WASD to move, SPACE dashes, E fires the nova.
 * Touch: put a thumb down anywhere and drag - a floating stick. In the classic mode a quick tap
 * (no drag) steps once towards the tap. The two ability dials are tappable.
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
        state.moveX = mx.coerceIn(-1f, 1f)
        state.moveY = my.coerceIn(-1f, 1f)
        state.held = held
    }

    fun reset() {
        keysHeld.clear()
        stickPointer = -1
        stickOn = false
        stickDir = null
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
    }
}
