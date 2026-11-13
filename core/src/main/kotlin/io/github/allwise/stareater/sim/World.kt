package io.github.allwise.stareater.sim

import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * One level in progress. Pure simulation: no rendering, no input devices, no files. Feed it an
 * [InputState] every frame and read its entities and [fx] back out.
 */
class World(val spec: LevelSpec, val loadout: Loadout, val run: RunState, seed: Long) {
    enum class Phase { PLAYING, CLEARED, DEAD }

    val rng = Random(seed)
    val cols = spec.width
    val rows = spec.height
    val w = cols.toFloat()
    val h = rows.toFloat()

    val movement: Movement = Movement.create(spec.move)
    val rules: List<Rule> = spec.modifiers.map { Rules.byId(it).create() }

    val player = Player()
    val enemies = ArrayList<Enemy>()
    val stars = ArrayList<Star>()
    val meteors = ArrayList<Meteor>()
    val wells = ArrayList<Well>()
    val tail = ArrayList<Pt>()
    val walls = BooleanArray(cols * rows)
    val fx = ArrayList<Fx>()
    val flares = ArrayList<Flare>()
    val capsules = ArrayList<Capsule>()
    var decoy: Decoy? = null
    var sun: Sun? = null

    /** The big one, on boss levels. */
    var boss: Enemy? = null

    // --- Knobs that rules turn -------------------------------------------------------------
    var hasWalls = false
    var fogRadius = 0f
    var playerWrapX = true
    var playerWrapY = spec.move != MoveKind.SCROLL
    var enemiesWrap = false
    var restless = false
    var tailActive = false

    /** Set by Shy Stars: stars back away, and a star magnet has a fight on its hands. */
    var shyStars = false

    /** Seconds on the Countdown clock, or negative when there is no clock. */
    var timeLeft = -1f

    /** Arcade runs collect their kit off the field; null in a normal run. */
    val arcade: Arcade? = run.arcade

    // --- Edge block ------------------------------------------------------------------------
    /** Seconds of warning left before the edges shut. */
    var sealWarn = 0f
        private set

    /** Seconds the edges stay shut, counted once the warning is over. */
    var sealTime = 0f
        private set
    var sealWarnTotal = 1f
        private set
    var sealTotal = 1f
        private set

    /** True while an edge block is anywhere between its first warning and reopening. */
    val sealing: Boolean get() = sealWarn > 0f || sealTime > 0f

    /** True while the edges are actually shut. */
    val sealed: Boolean get() = sealWarn <= 0f && sealTime > 0f
    private var openWrapX = true
    private var openWrapY = true

    /** Whether the field wraps top-to-bottom at all. The scroller only wraps sideways. */
    val wrapY = spec.move != MoveKind.SCROLL

    // --- Live state ------------------------------------------------------------------------
    var phase = Phase.PLAYING
    var phaseTime = 0f
    var time = 0f
    var collected = 0
    var combo = 1
    var comboTimer = 0f
    var scroll = 0f
    var iq = spec.iqBase
    var speedMul = 1f
    private var spawnTimer = spec.spawnInterval
    private var capsuleTimer = FIRST_CAPSULE
    private var spotX = 0.5f
    private var spotY = 0.5f

    /** What the enemies are chasing: you, or your Wrap Echo. */
    val targetX: Float get() = decoy?.x ?: player.x
    val targetY: Float get() = decoy?.y ?: player.y

    val comboWindow: Float get() = COMBO_WINDOW * loadout.comboTime

    /** Score for clearing this level. Bosses pay out properly. */
    val clearBonus: Int get() = if (spec.boss != null) 500 * (spec.number / LevelSpec.BOSS_EVERY) else 50 * spec.number

    init {
        if (spec.move == MoveKind.SCROLL) player.place(cols / 2 + 0.5f, 3.5f)
        else player.place(cols / 2 + 0.5f, rows / 2 + 0.5f)
        player.shields = loadout.shields
        if (arcade?.novaHeld == true) player.novaCharge = NOVA_COST
        movement.setup(this)
        for (r in rules) r.setup(this)
        repeat(spec.starsOnField) { spawnStar(initial = true) }
        repeat(spec.enemyStart) { spawnEnemy(1.6f) }
        spec.boss?.let {
            pickSpawnSpot()
            boss = spawnEnemyAt(it, spotX, spotY, 2.6f)
        }
    }

    // ----------------------------------------------------------------------------------------
    // Frame update
    // ----------------------------------------------------------------------------------------

    fun update(dt: Float, input: InputState) {
        if (phase != Phase.PLAYING) {
            phaseTime += dt
            player.tick(dt)
            for (e in enemies) e.tick(dt)
            return
        }
        time += dt
        run.time += dt
        iq = min(1f, spec.iqBase + time * spec.iqRamp)
        speedMul = 1f + min(spec.speedRampMax, time * spec.speedRamp)

        val p = player
        p.invuln = max(0f, p.invuln - dt)
        p.dashCd = max(0f, p.dashCd - dt)
        p.echoCd = max(0f, p.echoCd - dt)
        if (loadout.shieldRegen && p.shields < loadout.shields) {
            p.shieldRegen += dt
            if (p.shieldRegen >= SHIELD_REGEN_TIME) {
                p.shieldRegen = 0f
                p.shields++
            }
        }

        arcade?.let { updateArcade(it, dt) }
        updateSeal(dt)
        movement.updatePlayer(this, input, dt)
        if (input.dash) dash()
        if (input.nova) nova()
        p.tick(dt)

        refreshFlow()
        movement.updateWorld(this, dt)
        for (r in rules) {
            r.update(this, dt)
            if (phase != Phase.PLAYING) return
        }
        updateMeteors(dt)
        if (phase != Phase.PLAYING) return

        decoy?.let {
            it.life -= dt
            if (it.life <= 0f) decoy = null
        }

        updateStars(dt)
        updateCapsules(dt)
        direct(dt)
        updateEnemies(dt)

        if (comboTimer > 0f) {
            comboTimer -= dt
            if (comboTimer <= 0f) combo = 1
        }

        if (phase == Phase.PLAYING && collected >= spec.quota) {
            phase = Phase.CLEARED
            phaseTime = 0f
            run.score += clearBonus
            run.dust += (3 + spec.number) * loadout.dustMul * (if (spec.boss != null) 3f else 1f)
            boss?.let {
                fx += Fx.BossDead(it.x, it.y)
                enemies.remove(it)
                boss = null
            }
            // Whatever the boss had locked dies with it.
            sealWarn = 0f
            sealTime = 0f
            fx += Fx.Cleared()
        }
    }

    // ----------------------------------------------------------------------------------------
    // Geometry
    // ----------------------------------------------------------------------------------------

    /** Signed x distance from [from] to [to]: the wrapped shortest way if [wrap], else the plain way. */
    fun dx(from: Float, to: Float, wrap: Boolean): Float = if (wrap) Geo.wrapDelta(to - from, w) else to - from

    fun dy(from: Float, to: Float, wrap: Boolean): Float = if (wrap && wrapY) Geo.wrapDelta(to - from, h) else to - from

    /** Distance across the torus. Used for anything physical: touching, pickup, blast radius. */
    fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float = hypot(dx(ax, bx, true), dy(ay, by, true))

    fun isWall(cx: Int, cy: Int): Boolean = hasWalls && walls[Math.floorMod(cy, rows) * cols + Math.floorMod(cx, cols)]

    fun isWallAt(x: Float, y: Float): Boolean = isWall(floor(x).toInt(), floor(y).toInt())

    private fun boxHitsWall(cx: Float, cy: Float, half: Float): Boolean {
        val x0 = floor(cx - half).toInt()
        val x1 = floor(cx + half).toInt()
        val y0 = floor(cy - half).toInt()
        val y1 = floor(cy + half).toInt()
        for (ty in y0..y1) for (tx in x0..x1) if (isWall(tx, ty)) return true
        return false
    }

    /**
     * Moves a body by a small delta, sliding along walls and field edges.
     * Returns a bitmask: 1 = stopped horizontally, 2 = stopped vertically.
     */
    fun moveBody(b: Body, ddx: Float, ddy: Float, radius: Float, wrapX: Boolean, wrapYAxis: Boolean): Int {
        var hit = 0
        val half = radius * 0.85f
        // A body that somehow starts inside a wall is allowed to walk out rather than being glued in.
        val solid = hasWalls && !boxHitsWall(b.x, b.y, half)
        var nx = b.x + ddx
        if (wrapX) nx = Geo.wrap(nx, w)
        else if (nx < radius) { nx = radius; hit = hit or 1 }
        else if (nx > w - radius) { nx = w - radius; hit = hit or 1 }
        if (solid && boxHitsWall(nx, b.y, half)) { nx = b.x; hit = hit or 1 }
        b.x = nx

        var ny = b.y + ddy
        if (wrapYAxis && wrapY) ny = Geo.wrap(ny, h)
        else if (ny < radius) { ny = radius; hit = hit or 2 }
        else if (ny > h - radius) { ny = h - radius; hit = hit or 2 }
        if (solid && boxHitsWall(b.x, ny, half)) { ny = b.y; hit = hit or 2 }
        b.y = ny
        b.px = b.x; b.py = b.y; b.tween = 1f
        return hit
    }

    // ----------------------------------------------------------------------------------------
    // Pathfinding (only when there are walls)
    // ----------------------------------------------------------------------------------------

    private var flowCell = -1
    private val flow = IntArray(cols * rows)
    private val flowWrapped = IntArray(cols * rows)
    private val bfsQueue = IntArray(cols * rows)

    private fun refreshFlow() {
        if (!hasWalls) return
        val cell = floor(player.y).toInt().coerceIn(0, rows - 1) * cols + floor(player.x).toInt().coerceIn(0, cols - 1)
        if (cell == flowCell) return
        flowCell = cell
        bfs(flow, cell, false)
        bfs(flowWrapped, cell, true)
    }

    private fun bfs(out: IntArray, start: Int, wrap: Boolean) {
        out.fill(UNREACHABLE)
        var head = 0
        var tailIdx = 0
        out[start] = 0
        bfsQueue[tailIdx++] = start
        while (head < tailIdx) {
            val c = bfsQueue[head++]
            val cx = c % cols
            val cy = c / cols
            for (d in DIRS) {
                var nx = cx + d.dx
                var ny = cy + d.dy
                if (nx < 0 || nx >= cols) { if (!wrap) continue; nx = Math.floorMod(nx, cols) }
                if (ny < 0 || ny >= rows) { if (!wrap) continue; ny = Math.floorMod(ny, rows) }
                val n = ny * cols + nx
                if (walls[n] || out[n] != UNREACHABLE) continue
                out[n] = out[c] + 1
                bfsQueue[tailIdx++] = n
            }
        }
    }

    /** Points the enemy's desire down the flow field towards the player, if that beats going direct. */
    fun flowSteer(e: Enemy) {
        if (flowCell < 0) return
        val field = if (e.wraps) flowWrapped else flow
        val cx = floor(e.x).toInt().coerceIn(0, cols - 1)
        val cy = floor(e.y).toInt().coerceIn(0, rows - 1)
        val here = field[cy * cols + cx]
        if (here <= 1 || here == UNREACHABLE) return
        var best = here
        var bx = 0
        var by = 0
        for (d in DIRS) {
            var nx = cx + d.dx
            var ny = cy + d.dy
            if (nx < 0 || nx >= cols) { if (!e.wraps) continue; nx = Math.floorMod(nx, cols) }
            if (ny < 0 || ny >= rows) { if (!e.wraps || !wrapY) continue; ny = Math.floorMod(ny, rows) }
            val v = field[ny * cols + nx]
            if (v < best) { best = v; bx = d.dx; by = d.dy }
        }
        if (bx == 0 && by == 0) return
        e.desireX = (cx + bx + 0.5f) - e.x
        e.desireY = (cy + by + 0.5f) - e.y
    }

    // ----------------------------------------------------------------------------------------
    // Stars
    // ----------------------------------------------------------------------------------------

    fun spawnStar(initial: Boolean = false) {
        val s = Star(rollStarType())
        if (spec.move == MoveKind.SCROLL) {
            val x = 0.5f + rng.nextInt(cols)
            val y = if (initial) h * 0.35f + rng.nextFloat() * h * 0.65f else h + 0.6f
            s.place(x, y)
            if (s.type == StarType.COMET) s.vx = if (rng.nextBoolean()) 2.2f else -2.2f
        } else if (spec.move == MoveKind.ORBIT && sun != null) {
            // Stars ride circular orbits, slower the further out they are.
            val centre = sun!!
            val maxR = min(w, h) / 2f - 1.5f
            var bestScore = -1f
            for (attempt in 0 until 12) {
                val r = 3f + rng.nextFloat() * (maxR - 3f)
                val a = rng.nextFloat() * 6.2832f
                val x = centre.x + kotlin.math.cos(a) * r
                val y = centre.y + kotlin.math.sin(a) * r
                var score = min(dist(x, y, player.x, player.y), 6f)
                for (o in stars) score = min(score, dist(x, y, o.x, o.y) + 2f)
                if (score > bestScore) { bestScore = score; s.orbitR = r; s.orbitA = a; s.place(x, y) }
                if (score >= 4f) break
            }
            // Comets go round the wrong way, and faster.
            val spin = if (s.type == StarType.COMET) -1.8f else 1f
            s.orbitW = kotlin.math.sqrt(centre.gm / (s.orbitR * s.orbitR * s.orbitR)) * 0.55f * spin
        } else {
            var bx = 0.5f
            var by = 0.5f
            var bestScore = -1f
            for (attempt in 0 until 24) {
                val cx = rng.nextInt(cols)
                val cy = rng.nextInt(rows)
                if (isWall(cx, cy)) continue
                val x = cx + 0.5f
                val y = cy + 0.5f
                var score = min(dist(x, y, player.x, player.y), 6f)
                for (o in stars) score = min(score, dist(x, y, o.x, o.y) + 2f)
                if (score > bestScore) { bestScore = score; bx = x; by = y }
                if (score >= 4f) break
            }
            s.place(bx, by)
            if (s.type == StarType.COMET) {
                val a = rng.nextFloat() * 6.2832f
                val sp = 2.4f + rng.nextFloat()
                s.vx = kotlin.math.cos(a) * sp
                s.vy = kotlin.math.sin(a) * sp
            }
        }
        if (s.type == StarType.NOVA) s.ttl = 8f
        for (r in rules) r.onStarSpawn(this, s)
        stars += s
    }

    private fun rollStarType(): StarType {
        val types = StarType.values()
        var total = 0f
        for (t in types) total += starWeight(t)
        var r = rng.nextFloat() * total
        for (t in types) {
            r -= starWeight(t)
            if (r <= 0f) return t
        }
        return StarType.COMMON
    }

    private fun starWeight(t: StarType): Float {
        val base = spec.starWeights[t.ordinal]
        return if (t == StarType.COMMON) base else base * (1f + loadout.luck)
    }

    private fun updateStars(dt: Float) {
        val p = player
        var i = stars.size - 1
        while (i >= 0) {
            val s = stars[i]
            s.age += dt
            if (s.vx != 0f || s.vy != 0f) {
                s.x = Geo.wrap(s.x + s.vx * dt, w)
                if (wrapY) s.y = Geo.wrap(s.y + s.vy * dt, h)
            }
            val d = dist(s.x, s.y, p.x, p.y)
            if (loadout.magnet > 0f && s.lit && d < loadout.magnet && d > 0.01f) {
                s.orbitR = 0f // the maw's pull beats the orbit
                val pull = magnetPull(s, d) * dt
                s.x = Geo.wrap(s.x + dx(s.x, p.x, true) / d * pull, w)
                s.y += dy(s.y, p.y, true) / d * pull
                if (wrapY) s.y = Geo.wrap(s.y, h)
            }
            if (s.ttl > 0f && s.age >= s.ttl) {
                fx += Fx.StarLost(s.x, s.y)
                stars.removeAt(i)
            } else if (s.lit && d < PICKUP) {
                stars.removeAt(i)
                eat(s)
            }
            i--
        }
        if (spec.move != MoveKind.SCROLL) {
            while (stars.size < spec.starsOnField) spawnStar()
        }
    }

    /**
     * How fast the star magnet reels a star in, in tiles per second. Against a shy star it is a
     * contest: the small maw only slows its retreat, the big one holds it in a tug-of-war that sways
     * back and forth and, given patience, comes your way.
     */
    private fun magnetPull(s: Star, d: Float): Float {
        if (!shyStars) return 3f + 9f * (1f - d / loadout.magnet)
        val flee = ShyStarsRule.flee(d)
        return if (loadout.magnet < STRONG_MAGNET) flee * 0.7f else flee * (1.04f + 0.22f * kotlin.math.sin(s.age * 1.8f))
    }

    private fun eat(s: Star) {
        combo = if (comboTimer > 0f) min(MAX_COMBO, combo + 1) else 1
        comboTimer = comboWindow
        run.bestCombo = max(run.bestCombo, combo)
        val points = (s.type.value * 10 * combo * (1f + 0.15f * (spec.number - 1))).toInt()
        collected++
        run.score += points
        run.starsEaten++
        run.dust += s.type.value * loadout.dustMul
        if (loadout.nova) player.novaCharge = min(NOVA_COST, player.novaCharge + 1)
        fx += Fx.StarEaten(s.x, s.y, s.type, points, combo)
        boss?.let {
            it.hurt = 0.45f
            fx += Fx.BossHit(s.x, s.y, it.x, it.y)
        }
        if (s.type == StarType.NOVA) shockwave(s.x, s.y, 6f, 2.5f)
        for (r in rules) r.onStarEaten(this, s)
    }

    // ----------------------------------------------------------------------------------------
    // Enemies
    // ----------------------------------------------------------------------------------------

    /** Speed of an enemy right now, in tiles per second. */
    fun speedOf(e: Enemy): Float =
        spec.enemySpeed * speedMul * e.type.speed * e.pace * movement.enemySpeedFactor * (if (e.slow > 0f) 0.45f else 1f)

    /** The director: enemies start single and keep arriving until the level's cap. */
    private fun direct(dt: Float) {
        spawnTimer -= dt
        if (spawnTimer > 0f) return
        spawnTimer = spec.spawnInterval
        if (enemies.count { it.type.inRoster } < spec.enemyMax) spawnEnemy(1.4f)
    }

    private fun spawnEnemy(warmup: Float) {
        var total = 0f
        for (t in spec.roster) total += t.weight
        var r = rng.nextFloat() * total
        var type = spec.roster.last()
        for (t in spec.roster) {
            r -= t.weight
            if (r <= 0f) { type = t; break }
        }
        pickSpawnSpot()
        spawnEnemyAt(type, spotX, spotY, warmup)
    }

    /** Finds somewhere fair for an enemy to appear: off at a distance, but not half a galaxy away. */
    private fun pickSpawnSpot() {
        var bx = 0.5f
        var by = 0.5f
        if (spec.move == MoveKind.SCROLL) {
            // Mostly from ahead; from behind if the player is hugging the top.
            by = if (player.y > h - 4.5f) 0.5f else h - 0.5f
            var best = -1f
            for (attempt in 0 until 8) {
                val x = 0.5f + rng.nextInt(cols)
                val d = kotlin.math.abs(x - player.x)
                if (d > best) { best = d; bx = x }
                if (d > 6f) break
            }
        } else {
            var best = -1f
            for (attempt in 0 until 30) {
                val cx = rng.nextInt(cols)
                val cy = rng.nextInt(rows)
                if (isWall(cx, cy)) continue
                val x = cx + 0.5f
                val y = cy + 0.5f
                val d = dist(x, y, player.x, player.y)
                // Ideal is 9 to 15 tiles out; nearer is unfair, further is a long boring walk.
                var score = if (d > 15f) 15f - (d - 15f) * 0.5f else d
                if (enemies.any { dist(x, y, it.x, it.y) < 1.5f }) score *= 0.3f
                sun?.let { if (dist(x, y, it.x, it.y) < it.radius + 2f) score = -1f }
                if (score > best) { best = score; bx = x; by = y }
                if (d in 9f..15f && score > 0f) break
            }
        }
        spotX = bx
        spotY = by
    }

    fun spawnEnemyAt(type: EnemyType, x: Float, y: Float, warmup: Float): Enemy {
        val e = Enemy(type)
        e.place(x, y)
        e.spawnT = warmup
        e.wraps = (type.wrapAware || enemiesWrap) && !sealed
        e.seed = rng.nextFloat() * 100f
        type.init(this, e)
        enemies += e
        fx += Fx.EnemySpawn(x, y)
        return e
    }

    private fun updateEnemies(dt: Float) {
        val p = player
        var i = enemies.size - 1
        while (i >= 0) {
            val e = enemies[i]
            e.tick(dt)
            if (e.slow > 0f) e.slow -= dt
            if (e.hurt > 0f) e.hurt -= dt
            if (e.life > 0f) {
                e.life -= dt
                if (e.life <= 0f) {
                    fx += Fx.EnemyGone(e.x, e.y)
                    enemies.removeAt(i)
                    i--
                    continue
                }
            }
            if (e.spawnT > 0f) {
                e.spawnT -= dt
            } else if (e.stun > 0f) {
                e.stun -= dt
                e.vx = 0f; e.vy = 0f
            } else {
                e.type.think(this, e, dt)
                movement.moveEnemy(this, e, dt)
                if (dist(e.x, e.y, p.x, p.y) < PLAYER_RADIUS + e.radius) hitPlayer()
            }
            i--
        }
    }

    fun stun(e: Enemy, seconds: Float) {
        if (e.spawnT > 0f) return
        if (e.type.boss) {
            // Too big to stop; it only slows down.
            if (e.slow <= 0f) fx += Fx.Stun(e.x, e.y)
            e.slow = max(e.slow, seconds)
            return
        }
        if (e.stun <= 0f) fx += Fx.Stun(e.x, e.y)
        e.stun = max(e.stun, seconds)
    }

    // ----------------------------------------------------------------------------------------
    // Meteors
    // ----------------------------------------------------------------------------------------

    /** Schedules a rock to sweep a row or column after a warning. */
    fun callMeteor(horizontal: Boolean, line: Int, dir: Int = if (rng.nextBoolean()) 1 else -1) {
        val span = if (horizontal) w else h
        val wrapped = Math.floorMod(line, if (horizontal) rows else cols)
        meteors += Meteor(horizontal, wrapped, dir, METEOR_WARN, if (dir > 0) -1f else span + 1f)
        fx += Fx.MeteorWarn()
    }

    private fun updateMeteors(dt: Float) {
        var i = meteors.size - 1
        while (i >= 0) {
            val m = meteors[i]
            if (m.warn > 0f) {
                m.warn -= dt
            } else {
                m.pos += m.dir * METEOR_SPEED * dt
                val span = if (m.horizontal) w else h
                if (m.pos < -1.5f || m.pos > span + 1.5f) {
                    meteors.removeAt(i)
                } else {
                    if (hypot(player.x - m.x, player.y - m.y) < 0.75f) hitPlayer()
                    // Rocks are not picky about who they flatten.
                    for (e in enemies) {
                        if (e.active && !e.type.boss && hypot(e.x - m.x, e.y - m.y) < 0.75f) stun(e, 2.5f)
                    }
                }
            }
            i--
        }
    }

    /** Stuns every enemy within [radius] of a point. */
    fun shockwave(x: Float, y: Float, radius: Float, seconds: Float) {
        for (e in enemies) if (dist(x, y, e.x, e.y) <= radius) stun(e, seconds)
        fx += Fx.Nova(x, y, radius)
    }

    // ----------------------------------------------------------------------------------------
    // Player
    // ----------------------------------------------------------------------------------------

    /**
     * The player takes a hit. A [fatal] one - a black hole - goes straight through shields and any
     * moment of invulnerability; only a Second Wind cheats it.
     */
    fun hitPlayer(fatal: Boolean = false) {
        val p = player
        if (phase != Phase.PLAYING || (p.invuln > 0f && !fatal)) return
        combo = 1
        comboTimer = 0f
        when {
            p.shields > 0 && !fatal -> {
                p.shields--
                p.shieldRegen = 0f
                // An arcade shield is one bubble: popped is popped.
                if (p.shields == 0) arcade?.spend(PowerUp.SHIELD)
                p.invuln = 1.6f
                fx += Fx.ShieldHit(p.x, p.y)
                shockwave(p.x, p.y, 3.5f, 1.3f)
            }
            loadout.secondWind && !run.secondWindUsed -> {
                run.secondWindUsed = true
                p.invuln = 2.6f
                fx += Fx.Revive(p.x, p.y)
                shockwave(p.x, p.y, 9f, 2.8f)
            }
            else -> {
                phase = Phase.DEAD
                phaseTime = 0f
                fx += Fx.Death(p.x, p.y)
            }
        }
    }

    /** Called by the movement whenever the player crosses an edge. */
    fun onPlayerWrap(fromX: Float, fromY: Float) {
        val p = player
        fx += Fx.Wrap(fromX, fromY, p.x, p.y)
        if (loadout.echo && p.echoCd <= 0f) {
            decoy = Decoy(fromX, fromY, ECHO_TIME)
            p.echoCd = ECHO_COOLDOWN
        }
    }

    val dashCooldown: Float get() = DASH_COOLDOWN * loadout.dashCdMul

    private fun dash() {
        val p = player
        if (!loadout.dash || p.dashCd > 0f) return
        val grid = spec.move == MoveKind.GRID
        val fromX = p.x
        val fromY = p.y
        var wrapped = false
        // Walk the dash in small hops so it stops at walls and edges instead of tunnelling.
        val hops = if (grid) DASH_CELLS else 14
        val hop = if (grid) 1f else DASH_TILES / hops
        for (n in 0 until hops) {
            var nx = p.x + p.faceX * hop
            var ny = p.y + p.faceY * hop
            if (nx < 0f || nx >= w) { if (!playerWrapX) break; nx = Geo.wrap(nx, w); wrapped = true }
            if (ny < 0f || ny >= h) { if (!playerWrapY) break; ny = Geo.wrap(ny, h); wrapped = true }
            if (hasWalls && boxHitsWall(nx, ny, PLAYER_RADIUS * 0.85f)) break
            // A dash goes clean through a sun, but pulls up short of a black hole.
            val centre = sun
            if (centre != null && centre.blackHole && hypot(nx - centre.x, ny - centre.y) < centre.radius + 0.4f) break
            p.x = nx; p.y = ny
            if (loadout.dashStun) for (e in enemies) if (dist(e.x, e.y, nx, ny) < 1.1f) stun(e, 2f)
            // Hoover up anything the dash passes through.
            var i = stars.size - 1
            while (i >= 0) {
                val s = stars[i]
                if (s.lit && dist(s.x, s.y, nx, ny) < PICKUP) { stars.removeAt(i); eat(s) }
                i--
            }
            i = capsules.size - 1
            while (i >= 0) {
                val c = capsules[i]
                if (dist(c.x, c.y, nx, ny) < PICKUP) { capsules.removeAt(i); collect(c) }
                i--
            }
        }
        p.px = p.x; p.py = p.y; p.tween = 1f
        p.started = true
        p.invuln = max(p.invuln, 0.35f)
        p.dashCd = dashCooldown
        fx += Fx.Dash(fromX, fromY, p.x, p.y)
        if (wrapped) onPlayerWrap(fromX, fromY)
    }

    private fun nova() {
        val p = player
        if (!loadout.nova || p.novaCharge < NOVA_COST) return
        p.novaCharge = 0
        arcade?.spend(PowerUp.NOVA)
        shockwave(p.x, p.y, 7.5f, 3f)
        meteors.clear()
    }

    // ----------------------------------------------------------------------------------------
    // Edge block
    // ----------------------------------------------------------------------------------------

    /**
     * Shuts every edge of the field: after [warn] seconds of warning nobody wraps, not you and not
     * them, for [hold] seconds. Asking again while one is already under way does nothing.
     */
    fun sealEdges(warn: Float, hold: Float) {
        if (sealing || phase != Phase.PLAYING) return
        sealWarn = warn
        sealWarnTotal = warn
        sealTime = hold
        sealTotal = hold
        fx += Fx.EdgeWarn()
    }

    private fun updateSeal(dt: Float) {
        if (sealWarn > 0f) {
            sealWarn -= dt
            if (sealWarn > 0f) return
            sealWarn = 0f
            openWrapX = playerWrapX
            openWrapY = playerWrapY
            playerWrapX = false
            playerWrapY = false
            for (e in enemies) e.wraps = false
            fx += Fx.EdgeSeal()
        } else if (sealTime > 0f) {
            sealTime -= dt
            if (sealTime > 0f) return
            sealTime = 0f
            playerWrapX = openWrapX
            playerWrapY = openWrapY
            for (e in enemies) e.wraps = e.type.wrapAware || enemiesWrap
            fx += Fx.EdgeOpen()
        }
    }

    // ----------------------------------------------------------------------------------------
    // Arcade power-ups
    // ----------------------------------------------------------------------------------------

    private fun updateArcade(a: Arcade, dt: Float) {
        // Like the countdown, the clocks only run once you have moved.
        if (!player.started) return
        a.tick(dt) { kind ->
            if (kind == PowerUp.SHIELD) player.shields = 0
            fx += Fx.PowerLost(kind)
        }
    }

    private fun updateCapsules(dt: Float) {
        val a = arcade ?: return
        val scrolling = spec.move == MoveKind.SCROLL
        var i = capsules.size - 1
        while (i >= 0) {
            val c = capsules[i]
            c.age += dt
            if (scrolling) c.y -= spec.scrollSpeed * dt
            if (dist(c.x, c.y, player.x, player.y) < PICKUP) {
                capsules.removeAt(i)
                collect(c)
            } else if ((c.ttl > 0f && c.age >= c.ttl) || c.y < -0.8f) {
                capsules.removeAt(i)
            }
            i--
        }
        capsuleTimer -= dt
        if (capsuleTimer > 0f) return
        capsuleTimer = CAPSULE_EVERY * (0.75f + 0.5f * rng.nextFloat())
        if (capsules.size < MAX_CAPSULES) spawnCapsule(a)
    }

    private fun spawnCapsule(a: Arcade) {
        val kinds = PowerUp.values()
        var total = 0f
        for (k in kinds) total += capsuleWeight(a, k)
        if (total <= 0f) return
        var r = rng.nextFloat() * total
        var kind = kinds.last { capsuleWeight(a, it) > 0f }
        for (k in kinds) {
            r -= capsuleWeight(a, k)
            if (r <= 0f && capsuleWeight(a, k) > 0f) { kind = k; break }
        }
        val c = Capsule(kind)
        if (spec.move == MoveKind.SCROLL) {
            c.place(0.5f + rng.nextInt(cols), h + 0.6f)
        } else {
            var bx = 0.5f
            var by = 0.5f
            var best = -1f
            for (attempt in 0 until 24) {
                val cx = rng.nextInt(cols)
                val cy = rng.nextInt(rows)
                if (isWall(cx, cy)) continue
                val x = cx + 0.5f
                val y = cy + 0.5f
                // Worth a detour, but not a trek: somewhere 5 to 11 tiles away.
                val d = dist(x, y, player.x, player.y)
                var score = if (d > 11f) 11f - (d - 11f) * 0.5f else d
                val centre = sun
                if (centre != null && dist(x, y, centre.x, centre.y) < centre.radius + 2.5f) score = -1f
                if (score > best) { best = score; bx = x; by = y }
                if (d in 5f..11f && score > 0f) break
            }
            c.place(bx, by)
            c.ttl = CAPSULE_LIFE
        }
        capsules += c
    }

    private fun capsuleWeight(a: Arcade, k: PowerUp): Float = when {
        !a.wants(k) -> 0f
        // An echo is no use where you cannot wrap.
        k == PowerUp.ECHO && !openToPlayer -> 0f
        // One that is already running is only a top-up: offer it less.
        k.timed && a.active(k) -> k.weight * 0.35f
        else -> k.weight
    }

    private val openToPlayer: Boolean get() = if (sealing) openWrapX || openWrapY else playerWrapX || playerWrapY

    private fun collect(c: Capsule) {
        val a = arcade ?: return
        a.grant(c.kind)
        when (c.kind) {
            PowerUp.SHIELD -> player.shields = 1
            PowerUp.NOVA -> player.novaCharge = NOVA_COST
            else -> {}
        }
        fx += Fx.Powered(c.x, c.y, c.kind)
    }

    companion object {
        const val UNREACHABLE = Int.MAX_VALUE
        const val PICKUP = 0.72f
        const val TOUCH = 0.6f
        const val PLAYER_RADIUS = 0.3f
        const val MAX_COMBO = 9
        const val COMBO_WINDOW = 3.2f
        const val NOVA_COST = 10
        const val DASH_COOLDOWN = 4f
        const val DASH_CELLS = 3
        const val DASH_TILES = 3.6f
        const val ECHO_TIME = 2.4f
        const val ECHO_COOLDOWN = 6f
        const val SHIELD_REGEN_TIME = 25f
        const val METEOR_WARN = 1.3f
        const val METEOR_SPEED = 15f

        /** A star magnet at least this wide can hold its own against a shy star. */
        const val STRONG_MAGNET = 3f
        const val FIRST_CAPSULE = 5f
        const val CAPSULE_EVERY = 11f
        const val CAPSULE_LIFE = 12f
        const val MAX_CAPSULES = 2
        private val DIRS = Dir.values()
    }
}
