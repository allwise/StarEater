package io.github.allwise.stareater.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Align
import io.github.allwise.stareater.StarEaterGame
import io.github.allwise.stareater.audio.Sfx
import io.github.allwise.stareater.gfx.Creatures
import io.github.allwise.stareater.gfx.Gfx
import io.github.allwise.stareater.gfx.Pal
import io.github.allwise.stareater.gfx.WorldRenderer
import io.github.allwise.stareater.meta.SavedLevel
import io.github.allwise.stareater.meta.SeedKey
import io.github.allwise.stareater.sim.Enemy
import io.github.allwise.stareater.sim.EnemyType
import io.github.allwise.stareater.sim.Leviathan
import io.github.allwise.stareater.sim.LevelGen
import io.github.allwise.stareater.sim.LevelSpec
import io.github.allwise.stareater.sim.MoveKind
import io.github.allwise.stareater.sim.Rules
import io.github.allwise.stareater.sim.RunState
import kotlin.math.floor
import kotlin.math.max

/**
 * The briefing before each level, dealt by a slot machine: one reel for the movement mode and four
 * for the twists, stopping one after another. A reel that lands on a blank is a twist you were spared.
 *
 * The level is already decided before the first reel moves (it depends only on the seed), so the
 * machine is pure theatre - and can be skipped with one press.
 *
 * On a boss level the reels start out the same, and then the boss takes the machine over.
 */
class IntroScreen(game: StarEaterGame, private val run: RunState, preset: LevelSpec? = null) : Screen(game) {
    private val spec = preset ?: roll(run)
    private val boss = spec.boss
    private val creatures = Creatures(game.gfx)
    private val models = HashMap<EnemyType, Enemy>()

    /** @param turn this reel's place in the order they stop in, which is not left to right */
    private class Reel(val index: Int, val turn: Int, var result: String, var pool: List<String>, var stopAt: Float) {
        var landed = false
        var landedAt = 0f
        var lastTick = 0
        val blank: Boolean get() = result == BLANK
    }

    private val reels = ArrayList<Reel>()
    private var landings = 0
    private lateinit var slots: SavedSlots
    private lateinit var go: Button
    private val goLabel = if (run.level == 1) "START" else if (boss != null) "FIGHT" else "NEXT LEVEL"

    /** The machine's own clock; skipping jumps it forward. */
    private var clock = 0f
    private var settleAt = 0f
    private var settled = false
    private var hijacked = false
    private var jolt = 0f

    override fun show() {
        go = button(Gfx.W / 2f - 150f, 34f, 300f, 46f, "SKIP") { if (settled) start() else skip() }
        go.silent = true

        val moves = MoveKind.values().map { it.title }
        // Plenty of blanks on the twist reels, so a blank does not look like a miracle.
        val twists = Rules.all.map { it.title } + List(5) { BLANK }
        // The reels stop in whatever order they feel like, the way real ones do.
        val order = (0 until LevelSpec.MAX_RULES).shuffled()
        for (i in 0 until LevelSpec.MAX_RULES) {
            val result = if (i == 0) spec.move.title else spec.modifiers.getOrNull(i - 1)?.let { Rules.byId(it).title } ?: BLANK
            // On a boss level nothing is going to land on its own: the reels just run until the boss arrives.
            val stopAt = if (boss != null) FOREVER else FIRST_STOP + order[i] * STOP_GAP
            reels += Reel(i, order[i], result, if (i == 0) moves else twists, stopAt)
        }
        val last = (reels.size - 1)
        settleAt = (if (boss != null) HIJACK_AT + HIJACK_STOP + last * HIJACK_GAP else FIRST_STOP + last * STOP_GAP) + 0.25f

        // Five slots beside the button: hold one to put this level by for later.
        slots = SavedSlots(this, game, go.x + go.w + 14f, 35f, wide = false, current = { if (SeedKey.isValid(run.key)) SavedLevel(run.key, run.level, run.arcade != null) else null })
    }

    private fun start() {
        game.audio.play(Sfx.UI_OK)
        game.go(PlayScreen(game, run, spec))
    }

    /** One press stops every reel where it was always going to stop. */
    private fun skip() {
        if (boss != null && !hijacked) hijack()
        clock = max(clock, settleAt)
        for (r in reels) if (!r.landed) { r.landed = true; r.landedAt = clock - 0.3f }
        game.audio.play(Sfx.REEL_STOP)
        game.buzz()
    }

    /** The boss grabs the machine: every reel gets its letter, and slams to a stop on it. */
    private fun hijack() {
        val b = boss ?: return
        hijacked = true
        jolt = 1f
        val letter = b.glyph.toString()
        for (r in reels) {
            r.result = letter
            r.pool = listOf(letter)
            r.stopAt = HIJACK_AT + HIJACK_STOP + r.turn * HIJACK_GAP
        }
        game.audio.play(Sfx.BOSS_INTRO)
        game.audio.play(Sfx.SEAL_WARN, 0.8f, 0.6f)
        game.buzz()
    }

    /** Backing out of a briefing ends the run (and still banks what you earned). */
    override fun back() = game.go(GameOverScreen(game, run))

    // ----------------------------------------------------------------------------------------
    // The machine
    // ----------------------------------------------------------------------------------------

    /** How many rows a reel still has to turn before it stops; a little negative while it bounces. */
    private fun remaining(r: Reel): Float {
        val u = r.stopAt - clock
        if (u > BRAKE) return SPEED * (u - BRAKE) + SPEED * BRAKE / 2f
        if (u > 0f) return SPEED * u * u / (2f * BRAKE)
        val s = -u / BOUNCE
        return if (s >= 1f) 0f else -0.24f * MathUtils.sin(s * MathUtils.PI) * (1f - s)
    }

    /** What is printed on row [k] of a reel. Row 0 is where it stops; the rest is shuffled filler. */
    private fun entry(r: Reel, k: Int): String {
        if (k == 0) return r.result
        var i = Math.floorMod(k * 7 + r.index * 3 + (k * k) % 5, r.pool.size)
        // Never the answer right next to the answer: it would look like a near miss on itself.
        if (r.pool[i] == r.result && r.pool.size > 1) i = (i + 1) % r.pool.size
        return r.pool[i]
    }

    private fun update(dt: Float) {
        clock += dt
        jolt = max(0f, jolt - dt * 2.2f)
        if (boss != null && !hijacked && clock >= HIJACK_AT) hijack()
        // Only the next reel to stop ticks, or five of them would be a buzz rather than a rattle.
        val next = reels.filter { !it.landed }.minByOrNull { it.stopAt }
        for (r in reels) {
            if (r.landed) continue
            if (clock >= r.stopAt) {
                r.landed = true
                r.landedAt = clock
                // Each landing rings a little higher than the last, wherever on the machine it is.
                val nth = landings++
                if (hijacked) {
                    game.audio.play(Sfx.BOSS_HIT, 0.7f + 0.06f * nth)
                    jolt = max(jolt, 0.5f)
                } else if (r.blank) {
                    game.audio.play(Sfx.REEL_STOP, 0.7f, 0.5f)
                } else {
                    game.audio.play(Sfx.REEL_STOP, 1f + 0.12f * nth)
                }
                game.buzz()
            } else if (r === next) {
                val row = floor(remaining(r)).toInt()
                if (row != r.lastTick) {
                    r.lastTick = row
                    game.audio.play(Sfx.UI_TICK, 1.5f, 0.7f)
                }
            }
        }
        if (!settled && clock >= settleAt) {
            settled = true
            go.label = goLabel
            go.accent = if (boss != null) Pal.RED else Pal.CYAN
        }
    }

    private fun machine() {
        val shakeX = if (jolt > 0f) MathUtils.random(-6f, 6f) * jolt else 0f
        val shakeY = if (jolt > 0f) MathUtils.random(-4f, 4f) * jolt else 0f
        val x0 = LEFT + shakeX
        val y0 = MACHINE_Y + shakeY
        val trim = if (hijacked) Pal.RED else Pal.LINE

        g.rect(x0 - 8f, y0 - 8f, FULL + 16f, MACHINE_H + 16f, Pal.PANEL)
        g.frame(x0 - 8f, y0 - 8f, FULL + 16f, MACHINE_H + 16f, 2f, trim)
        if (hijacked) g.glow(Gfx.W / 2f, y0 + MACHINE_H / 2f, 520f, Pal.RED, 0.22f + 0.2f * jolt)

        for (r in reels) {
            val x = x0 + r.index * (REEL_W + REEL_GAP)
            val cx = x + REEL_W / 2f
            val cy = y0 + MACHINE_H / 2f
            val won = r.landed && !r.blank
            val c = when {
                hijacked -> Pal.RED
                r.index == 0 -> Pal.CYAN
                else -> Pal.GOLD
            }
            g.rect(x, y0, REEL_W, MACHINE_H, Pal.BG)
            if (won) {
                // A reel that landed on something lights up, with a flash as it hits.
                val hit = (1f - (clock - r.landedAt) / 0.35f).coerceIn(0f, 1f)
                g.rect(x, cy - ROW / 2f, REEL_W, ROW, c, 0.14f + 0.5f * hit)
            }
            g.frame(x, y0, REEL_W, MACHINE_H, 2f, if (won) c else Pal.LINE, if (r.landed && r.blank) 0.5f else 1f)

            val turn = remaining(r)
            val base = floor(turn).toInt()
            for (k in base - 1..base + 2) {
                val dy = (turn - k) * ROW
                // Rows fade out towards the top and bottom of the window, like the curve of a drum.
                val a = 1f - kotlin.math.abs(dy) / (MACHINE_H / 2f - 4f)
                if (a <= 0f) continue
                val text = entry(r, k)
                if (hijacked) {
                    g.glyphAt(g.big, text[0], cx, cy + dy, Pal.RED, a)
                } else {
                    val here = r.landed && k == 0
                    val tint = if (text == BLANK) Pal.DIM else if (here) c else Pal.TEXT
                    g.text(g.small, text, cx, cy + dy + g.small.capHeight / 2f, tint, a * (if (text == BLANK) 0.6f else 1f), Align.center)
                }
            }
            // A bulb over each reel: flickering while it turns, steady once it has landed.
            val lit = if (r.landed) (if (r.blank) 0.25f else 1f) else 0.35f + 0.65f * MathUtils.random()
            g.circle(cx, y0 + MACHINE_H + 8f, 4f, if (r.landed && r.blank) Pal.DIM else c, lit)
        }
        // The payline markers.
        val py = y0 + MACHINE_H / 2f
        g.triangle(x0 - 8f, py + 7f, x0 - 8f, py - 7f, x0 + 2f, py, trim)
        g.triangle(x0 + FULL + 8f, py + 7f, x0 + FULL + 8f, py - 7f, x0 + FULL - 2f, py, trim)
    }

    // ----------------------------------------------------------------------------------------
    // Frame
    // ----------------------------------------------------------------------------------------

    override fun render(dt: Float) {
        age += dt
        if (game.devAuto && age > 0.4f) { game.go(PlayScreen(game, run, spec)); return }
        update(dt)
        g.rect(0f, 0f, Gfx.W, Gfx.H, Pal.BG)
        val cx = Gfx.W / 2f
        // Nobody knows it is a boss level until the boss says so.
        val red = hijacked
        g.glow(cx, 520f, 420f, if (red) Pal.RED else Pal.CYAN, 0.12f)
        g.text(g.big, if (red) "LEVEL ${spec.number}  -  BOSS" else "LEVEL ${spec.number}", cx, 528f, if (red) Pal.RED else Pal.TEXT, 1f, Align.center)
        val goal = if (red) "EAT ${spec.quota} STARS TO BRING IT DOWN" else "EAT ${spec.quota} STARS"
        val goalW = g.width(g.small, goal)
        g.star(cx - goalW / 2f - 14f, 487f, 8f, 3.4f, 5, 90f, Pal.STAR)
        g.text(g.small, goal, cx - goalW / 2f, 493f, Pal.STAR)
        if (run.key.isNotEmpty()) g.text(g.tiny, "SEED ${SeedKey.pretty(run.key)}", Gfx.W - LEFT, 524f, Pal.DIM, 1f, Align.right)
        if (run.arcade != null) g.text(g.small, "ARCADE", LEFT, 526f, Pal.GOLD)

        machine()

        // Each reel deals its card underneath as it lands: the mode across the top, twists in a grid.
        val half = (FULL - 16f) / 2f
        val first = reels[0]
        if (first.landed && (boss == null || settled)) {
            card(LEFT, CARD_Y, FULL, CARD_H, spec.move.title, spec.move.blurb, true, clock - (if (boss != null) settleAt else first.landedAt))
        }
        for ((i, id) in spec.modifiers.withIndex()) {
            val r = reels.getOrNull(i + 1) ?: break
            if (!r.landed) continue
            val info = Rules.byId(id)
            val x = LEFT + (i % 2) * (half + 16f)
            val y = CARD_Y - (CARD_H + 6f) * (1 + i / 2)
            card(x, y, half, CARD_H, info.title, info.blurb, false, clock - r.landedAt)
        }

        val rows = (spec.modifiers.size + 1) / 2
        var rosterTop = CARD_Y - 16f - rows * (CARD_H + 6f)
        if (boss != null) {
            rosterTop = CARD_Y - 142f
            if (settled) bossCard(boss, CARD_Y - 130f, clock - settleAt)
        } else if (spec.modifiers.isEmpty()) {
            rosterTop -= 30f
            if (settled) g.text(g.small, "No twists. Just you, the stars, and things with teeth.", cx, CARD_Y - 16f, Pal.DIM, ((clock - settleAt) * 3f).coerceAtMost(1f), Align.center)
        }

        // Who is out there.
        g.text(g.tiny, if (red) "AND ITS FRIENDS" else "HOSTILES", LEFT, rosterTop, Pal.DIM)
        for ((i, t) in spec.roster.withIndex()) {
            val x = LEFT + (i % 2) * (half + 16f)
            val y = rosterTop - 28f - (i / 2) * 38f
            val c = WorldRenderer.enemyColor(t)
            g.glow(x + 12f, y - 2f, 22f, c, 0.6f)
            // Letters on the grid, creatures everywhere else - same as in the level itself.
            if (spec.move == MoveKind.GRID) g.glyphAt(g.glyph, t.glyph, x + 12f, y - 2f, c) else portrait(t, x + 12f, y - 2f, 0.9f, 1f)
            g.text(g.small, t.title, x + 34f, y + 8f, c)
            g.text(g.tiny, t.blurb, x + 34f, y - 8f, Pal.DIM)
        }

        slots.update(dt)
        drawButtons()
        slots.caption(g, "HOLD TO SAVE THIS LEVEL", slots.right, 24f, Align.right)
        val hint = when {
            !settled -> if (game.keyHints) "ENTER stops the reels" else "tap to stop the reels"
            game.keyHints -> "ENTER to go   -   ESC ends the run"
            else -> "tap to go"
        }
        g.text(g.tiny, hint, cx, 22f, Pal.DIM, 0.6f + 0.4f * MathUtils.sin(age * 4f), Align.center)
    }

    /** The star of the show gets a card of its own, and arrives with a thump. */
    private fun bossCard(boss: EnemyType, y: Float, since: Float) {
        val t = (since / 0.3f).coerceIn(0f, 1f)
        val pop = 1f + 0.5f * MathUtils.sin(t * MathUtils.PI) * (1f - t)
        val c = WorldRenderer.enemyColor(boss)
        g.rect(LEFT, y, FULL, 122f, Pal.PANEL, t)
        g.rect(LEFT, y, 4f, 122f, Pal.RED, t)
        g.frame(LEFT, y, FULL, 122f, 1f, Pal.LINE, t)
        g.glow(LEFT + 90f, y + 61f, 110f * pop, c, 0.5f * t)
        portrait(boss, LEFT + 90f, y + 61f, 2.6f * pop, t)
        g.text(g.big, boss.title, LEFT + 190f, y + 98f, c, t)
        g.paragraph(g.small, boss.blurb, LEFT + 190f, y + 58f, FULL - 220f, Pal.TEXT, t)
    }

    private fun portrait(type: EnemyType, x: Float, y: Float, scale: Float, a: Float) {
        val model = models.getOrPut(type) { Enemy(type) }
        val c = WorldRenderer.enemyColor(type)
        if (type === Leviathan) creatures.serpentHead(x, y, 0f, 13f * scale, age, c, a)
        else creatures.draw(model, x, y, 0f, scale, age, 0.4f, c, a)
    }

    private fun card(x: Float, y: Float, w: Float, h: Float, title: String, blurb: String, primary: Boolean, since: Float) {
        val t = (since / 0.25f).coerceIn(0f, 1f)
        if (t <= 0f) return
        val slide = (1f - t) * 20f
        val c: Color = if (primary) Pal.CYAN else Pal.GOLD
        g.rect(x, y + slide, w, h, Pal.PANEL, t)
        g.rect(x, y + slide, 4f, h, c, t)
        g.frame(x, y + slide, w, h, 1f, Pal.LINE, t)
        g.text(if (primary) g.body else g.small, title, x + 16f, y + h - 9f + slide, c, t)
        g.text(g.tiny, blurb, x + 16f, y + 18f + slide, Pal.TEXT, t * 0.85f)
    }

    /** A tap anywhere stops the reels; only the button itself moves on. */
    override fun touchDown(x: Float, y: Float, pointer: Int) {
        if (!settled && buttons.none { it.usable && it.contains(x, y) }) skip() else super.touchDown(x, y, pointer)
    }

    companion object {
        /** Depends only on the run's seed and level, so a level never re-rolls if you revisit its briefing. */
        fun roll(run: RunState): LevelSpec = LevelGen.forRun(run)

        private const val BLANK = "- - -"
        private const val LEFT = 80f
        private const val FULL = Gfx.W - 2 * LEFT
        private const val REEL_GAP = 8f
        private const val REEL_W = (FULL - REEL_GAP * (LevelSpec.MAX_RULES - 1)) / LevelSpec.MAX_RULES
        private const val MACHINE_Y = 372f
        private const val MACHINE_H = 88f
        private const val ROW = 30f
        private const val CARD_Y = 306f
        private const val CARD_H = 50f

        /** Rows per second at full spin, and how long a reel takes to brake and to settle. */
        private const val SPEED = 15f
        private const val BRAKE = 0.5f
        private const val BOUNCE = 0.22f
        private const val FIRST_STOP = 0.8f
        private const val STOP_GAP = 0.38f
        private const val FOREVER = 1000f
        private const val HIJACK_AT = 1f
        private const val HIJACK_STOP = 0.4f
        private const val HIJACK_GAP = 0.11f
    }
}
