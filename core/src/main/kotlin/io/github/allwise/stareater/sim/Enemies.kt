package io.github.allwise.stareater.sim

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sign

/**
 * One kind of enemy. A type is a stateless "brain": every frame it reads the world and writes what it
 * wants onto the [Enemy] (a desired direction and a pace), and the level's [Movement] turns that into
 * actual motion. That split is what lets every enemy work in every movement mode.
 *
 * To add an enemy: subclass, implement [think], and add it to [Enemies.all].
 */
abstract class EnemyType(
    val id: String,
    val glyph: Char,
    val title: String,
    val blurb: String,
    /** Speed relative to the level's base enemy speed. */
    val speed: Float,
    /** First level this type may show up on. */
    val minLevel: Int,
    /** How often the director picks it when it is on the roster. */
    val weight: Float,
    /** Knows about (and uses) the screen wrap. */
    val wrapAware: Boolean = false,
    /** False for things that are only ever spawned by other enemies. */
    val inRoster: Boolean = true,
) {
    open fun init(w: World, e: Enemy) {}

    abstract fun think(w: World, e: Enemy, dt: Float)

    /** Called by the movement when a ballistic enemy runs into a wall or the edge of the field. */
    open fun onBlocked(w: World, e: Enemy, hitX: Boolean, hitY: Boolean) {}

    /** Head for a point the direct way (or the wrapped way, for enemies that understand wrapping). */
    protected fun chase(w: World, e: Enemy, tx: Float, ty: Float) {
        e.desireX = w.dx(e.x, tx, e.wraps)
        e.desireY = w.dy(e.y, ty, e.wraps)
        // Smart enough enemies path around walls instead of pressing their face against them.
        if (w.hasWalls && w.iq >= 0.3f && w.decoy == null) w.flowSteer(e)
    }
}

/** E - walks straight at you. Never wraps, so the far edge of the screen is your escape hatch. */
object Eater : EnemyType("eater", 'E', "EATER", "Walks straight at you. Cannot wrap.", 1f, 1, 1.2f) {
    override fun think(w: World, e: Enemy, dt: Float) {
        e.pace = 1f
        chase(w, e, w.targetX, w.targetY)
    }
}

/** S - aims at where you are going rather than where you are. */
object Stalker : EnemyType("stalker", 'S', "STALKER", "Heads you off: aims where you are going.", 0.9f, 1, 1f) {
    override fun think(w: World, e: Enemy, dt: Float) {
        e.pace = 1f
        val p = w.player
        val d = hypot(w.dx(e.x, w.targetX, e.wraps), w.dy(e.y, w.targetY, e.wraps))
        if (w.decoy != null || d < 2.5f) {
            chase(w, e, w.targetX, w.targetY)
            return
        }
        val lead = (0.25f + 0.9f * w.iq) * minOf(1f, d / 6f)
        val tx = (p.x + p.vx * lead).coerceIn(0.5f, w.w - 0.5f)
        val ty = (p.y + p.vy * lead).coerceIn(0.5f, w.h - 0.5f)
        chase(w, e, tx, ty)
    }
}

/** B - does not care about you at all, which somehow makes it worse. */
object Bouncer : EnemyType("bouncer", 'B', "BOUNCER", "Ricochets around diagonally. Ignores you.", 1.2f, 2, 0.6f) {
    override fun init(w: World, e: Enemy) {
        e.ballistic = true
        e.dirX = if (w.rng.nextBoolean()) DIAG else -DIAG
        e.dirY = if (w.rng.nextBoolean()) DIAG else -DIAG
    }

    override fun think(w: World, e: Enemy, dt: Float) {
        e.pace = 1f
    }

    override fun onBlocked(w: World, e: Enemy, hitX: Boolean, hitY: Boolean) {
        if (hitX) e.dirX = -e.dirX
        if (hitY) e.dirY = -e.dirY
    }

    private const val DIAG = 0.7071f
}

/** Z - prowls slowly, then lines you up and charges. */
object Zoomer : EnemyType("zoomer", 'Z', "ZOOMER", "Lines you up, winds up, then charges.", 1f, 3, 0.7f) {
    const val PROWL = 0
    const val AIM = 1
    const val CHARGE = 2
    const val REST = 3

    override fun init(w: World, e: Enemy) {
        e.timer = 1.5f
    }

    override fun think(w: World, e: Enemy, dt: Float) {
        e.timer -= dt
        when (e.state) {
            PROWL -> {
                e.pace = 0.55f
                chase(w, e, w.targetX, w.targetY)
                if (e.timer > 0f) return
                val dx = w.dx(e.x, w.targetX, e.wraps)
                val dy = w.dy(e.y, w.targetY, e.wraps)
                val grid = w.spec.move == MoveKind.GRID
                val dist = hypot(dx, dy)
                val linedUp = if (grid) (abs(dx) < 0.5f || abs(dy) < 0.5f) && dist in 1.5f..14f else dist in 2f..11f
                if (!linedUp) return
                if (grid) {
                    e.dirX = if (abs(dx) >= abs(dy)) sign(dx) else 0f
                    e.dirY = if (abs(dx) >= abs(dy)) 0f else sign(dy)
                } else {
                    e.dirX = dx / dist
                    e.dirY = dy / dist
                }
                e.state = AIM
                e.timer = 0.75f - 0.3f * w.iq
            }
            AIM -> {
                e.pace = 0f
                e.desireX = 0f; e.desireY = 0f
                e.vx = 0f; e.vy = 0f
                if (e.timer <= 0f) {
                    e.state = CHARGE
                    e.ballistic = true
                    e.travel = 0f
                }
            }
            CHARGE -> {
                e.pace = 3.2f
                if (e.travel >= 13f) rest(e)
            }
            else -> {
                e.pace = 0f
                e.desireX = 0f; e.desireY = 0f
                if (e.timer <= 0f) {
                    e.state = PROWL
                    e.timer = max(0.8f, 2.4f - 1.4f * w.iq)
                }
            }
        }
    }

    override fun onBlocked(w: World, e: Enemy, hitX: Boolean, hitY: Boolean) = rest(e)

    private fun rest(e: Enemy) {
        e.state = REST
        e.ballistic = false
        e.timer = 1f
        e.vx = 0f; e.vy = 0f
    }
}

/** W - the one that worked out the trick. Takes the shortest route *including* the wrap. */
object Warper : EnemyType("warper", 'W', "WARPER", "Knows the wrap trick. Takes the true shortest route.", 0.8f, 4, 0.7f, wrapAware = true) {
    override fun think(w: World, e: Enemy, dt: Float) {
        e.pace = 1f
        chase(w, e, w.targetX, w.targetY)
    }
}

/** H - slow and fat, but keeps hatching mites. */
object Hive : EnemyType("hive", 'H', "HIVE", "Slow, but hatches fast little mites.", 0.35f, 6, 0.45f) {
    override fun init(w: World, e: Enemy) {
        e.timer = 3f
    }

    override fun think(w: World, e: Enemy, dt: Float) {
        e.pace = 1f
        chase(w, e, w.targetX, w.targetY)
        e.timer -= dt
        if (e.timer <= 0f) {
            e.timer = max(2.5f, 6f - 3f * w.iq)
            if (w.enemies.count { it.type === Mite } < 4) w.spawnEnemyAt(Mite, e.x, e.y, 0.35f)
        }
    }
}

/** e - a hive's short-lived, over-caffeinated offspring. */
object Mite : EnemyType("mite", 'e', "MITE", "Fast, dumb, and gone in a few seconds.", 1.25f, 6, 0f, inRoster = false) {
    override fun init(w: World, e: Enemy) {
        e.life = 7f
    }

    override fun think(w: World, e: Enemy, dt: Float) {
        e.pace = 1f
        e.desireX = w.dx(e.x, w.targetX, e.wraps)
        e.desireY = w.dy(e.y, w.targetY, e.wraps)
    }
}

object Enemies {
    val all: List<EnemyType> = listOf(Eater, Stalker, Bouncer, Zoomer, Warper, Hive, Mite)
    val roster: List<EnemyType> = all.filter { it.inRoster }

    /** Types that actually hunt you. Every level gets at least one. */
    val hunters: List<EnemyType> = listOf(Eater, Stalker, Warper)

    fun byId(id: String): EnemyType = all.first { it.id == id }
}
