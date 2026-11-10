package io.github.allwise.stareater.sim

import kotlin.math.abs

/** Wrap-around maths for the toroidal playfield. */
object Geo {
    /** Folds [v] into `[0, size)`. */
    fun wrap(v: Float, size: Float): Float {
        var r = v % size
        if (r < 0f) r += size
        if (r >= size) r = 0f
        return r
    }

    /** Shortest signed distance for a delta [d] on a loop of length [size]. */
    fun wrapDelta(d: Float, size: Float): Float {
        var r = d % size
        if (r > size / 2f) r -= size else if (r < -size / 2f) r += size
        return r
    }
}

enum class Dir(val dx: Int, val dy: Int) {
    UP(0, 1), DOWN(0, -1), LEFT(-1, 0), RIGHT(1, 0);

    companion object {
        /** Dominant axis of a vector. */
        fun of(dx: Float, dy: Float): Dir =
            if (abs(dx) >= abs(dy)) (if (dx >= 0f) RIGHT else LEFT) else (if (dy >= 0f) UP else DOWN)
    }
}

/** How the player (and everything else) moves. Exactly one per level. */
enum class MoveKind(val title: String, val blurb: String) {
    GRID("CLASSIC", "Tile by tile. One tap, one step - the original."),
    GLIDE("GLIDE", "Thrust and drift. Momentum is your friend and your enemy."),
    SCROLL("SCROLLER", "The sky streams past. The sides wrap, top and bottom do not."),
}

/**
 * One frame of player intent. Filled in by the platform input layer (or a bot) and consumed by the
 * simulation, which keeps the sim itself free of any keyboard / touch knowledge.
 */
class InputState {
    /** Analog direction, each axis in -1..1. Used by the continuous modes. */
    var moveX = 0f
    var moveY = 0f

    /** Fresh direction presses this frame. Used by the grid mode: one tap, one step. */
    val taps = ArrayList<Dir>(4)

    /** Direction currently held down, for key-repeat style stepping. */
    var held: Dir? = null

    /** Edge-triggered ability buttons. */
    var dash = false
    var nova = false

    fun clearEdges() {
        taps.clear()
        dash = false
        nova = false
    }
}
