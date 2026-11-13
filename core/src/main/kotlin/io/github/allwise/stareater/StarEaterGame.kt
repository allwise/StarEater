package io.github.allwise.stareater

import com.badlogic.gdx.Application
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.InputProcessor
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.ScreenUtils
import io.github.allwise.stareater.audio.Audio
import io.github.allwise.stareater.gfx.Gfx
import io.github.allwise.stareater.meta.KeyValueStore
import io.github.allwise.stareater.meta.MemoryStore
import io.github.allwise.stareater.meta.Profile
import io.github.allwise.stareater.meta.SavedLevel
import io.github.allwise.stareater.meta.Skill
import io.github.allwise.stareater.sim.Arcade
import io.github.allwise.stareater.sim.Enemies
import io.github.allwise.stareater.sim.LevelGen
import io.github.allwise.stareater.sim.MoveKind
import io.github.allwise.stareater.sim.PowerUp
import io.github.allwise.stareater.sim.RunState
import io.github.allwise.stareater.ui.GameOverScreen
import io.github.allwise.stareater.ui.HelpScreen
import io.github.allwise.stareater.ui.IntroScreen
import io.github.allwise.stareater.ui.MenuScreen
import io.github.allwise.stareater.ui.OptionsScreen
import io.github.allwise.stareater.ui.PlayScreen
import io.github.allwise.stareater.ui.ScoresScreen
import io.github.allwise.stareater.ui.Screen
import io.github.allwise.stareater.ui.SeedScreen
import io.github.allwise.stareater.ui.SkillScreen
import java.util.zip.Deflater

/**
 * Developer switches, parsed from a `key=value;key=value` string (see the README). Lets a build be
 * dropped straight into a given screen or rule set, driven by the bot, and screenshotted.
 */
class DevOptions(spec: String) {
    private val map = spec.split(';').mapNotNull { part ->
        val i = part.indexOf('=')
        if (i <= 0) null else part.substring(0, i).trim() to part.substring(i + 1).trim()
    }.toMap()

    val screen: String = map["screen"] ?: "menu"
    val level: Int = map["level"]?.toIntOrNull() ?: 1
    val move: MoveKind? = map["move"]?.let { m -> MoveKind.values().firstOrNull { it.name.equals(m, true) } }
    val mods: List<String>? = map["mods"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
    val roster: List<String>? = map["roster"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
    val boss: String? = map["boss"]
    val bot: Boolean = map["bot"] == "1"
    val auto: Boolean = map["auto"] == "1"
    val allSkills: Boolean = map["skills"] == "all"
    val dust: Int = map["dust"]?.toIntOrNull() ?: -1
    val seed: Long = map["seed"]?.toLongOrNull() ?: 1L
    val arcade: Boolean = map["arcade"] == "1"
    val powers: List<String> = map["powers"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    val align: Int = map["align"]?.toIntOrNull() ?: 0

    /** Pretend every boss up to this level has been beaten (and fill a couple of saved slots). */
    val checkpoint: Int = map["checkpoint"]?.toIntOrNull() ?: 0

    /** Window size as `1600x720`, for trying out screens that are not 16:9. */
    val window: Pair<Int, Int>? = map["window"]?.split('x')?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.size == 2 }?.let { it[0] to it[1] }
    val shots: List<Float> = map["shots"]?.split(',')?.mapNotNull { it.trim().toFloatOrNull() } ?: emptyList()
    val out: String = map["out"] ?: "."
}

class PrefsStore : KeyValueStore {
    private val prefs = Gdx.app.getPreferences("stareater")
    override fun get(key: String): String? = if (prefs.contains(key)) prefs.getString(key) else null
    override fun put(key: String, value: String) { prefs.putString(key, value) }
    override fun flush() = prefs.flush()
}

class StarEaterGame(private val dev: DevOptions? = null) : ApplicationAdapter(), InputProcessor {
    lateinit var gfx: Gfx
        private set
    lateinit var profile: Profile
        private set
    lateinit var audio: Audio
        private set
    private var screen: Screen? = null
    private val point = Vector2()
    private var devTime = 0f
    private var shotIndex = 0

    val devBot: Boolean get() = dev?.bot == true
    val devAuto: Boolean get() = dev?.auto == true

    /** True where there is a keyboard to hint at. */
    val keyHints: Boolean get() = Gdx.app.type == Application.ApplicationType.Desktop

    /** True on a device that can be steered by tilting it. */
    val canTilt: Boolean by lazy { Gdx.input.isPeripheralAvailable(Input.Peripheral.Accelerometer) }

    /** True where there is a motor to tap the player's fingers with. */
    val canBuzz: Boolean by lazy {
        Gdx.input.isPeripheralAvailable(Input.Peripheral.HapticFeedback) || Gdx.input.isPeripheralAvailable(Input.Peripheral.Vibrator)
    }

    /** A tiny tap through the vibration motor, where there is one and the player has not turned it off. */
    fun buzz() {
        if (!profile.haptics || !canBuzz) return
        try {
            if (Gdx.input.isPeripheralAvailable(Input.Peripheral.HapticFeedback)) Gdx.input.vibrate(Input.VibrationType.LIGHT)
            else Gdx.input.vibrate(12)
        } catch (e: Exception) {
            // No permission, no motor, an odd device: the game is fine without it.
        }
    }

    override fun create() {
        gfx = Gfx()
        gfx.resize(Gdx.graphics.width, Gdx.graphics.height)
        // Dev runs use a throwaway save so they never touch real progress.
        profile = Profile(if (dev != null) MemoryStore() else PrefsStore())
        if (dev != null) profile.screenAlign = dev.align
        gfx.align = profile.screenAlign
        audio = Audio(profile)
        audio.load()
        Gdx.input.inputProcessor = this
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        if (dev != null) startDev(dev) else go(MenuScreen(this))
    }

    private fun startDev(d: DevOptions) {
        if (d.dust >= 0) profile.grantDust(d.dust)
        for (boss in 10..d.checkpoint step 10) profile.beatBoss(boss)
        if (d.checkpoint > 0) {
            profile.store(0, SavedLevel("STAR22", 7))
            profile.store(2, SavedLevel("MQQN99", 14, arcade = true))
        }
        if (d.allSkills) {
            profile.grantDust(Skill.totalCost)
            var bought = true
            while (bought) {
                bought = false
                for (s in Skill.values()) if (profile.buy(s)) bought = true
            }
        }
        val run = RunState(d.seed, "DEVRUN")
        run.level = d.level
        if (d.arcade) {
            run.arcade = Arcade().also { a -> for (p in d.powers) PowerUp.values().firstOrNull { it.name.equals(p, true) }?.let { a.grant(it) } }
        }
        val spec = if (d.boss != null) {
            LevelGen.bossLevel(d.level, kotlin.random.Random(d.seed), Enemies.byId(d.boss), d.move)
        } else if (d.move != null || d.mods != null || d.roster != null) {
            val roster = d.roster?.map { Enemies.byId(it) } ?: Enemies.roster.filter { it.minLevel <= d.level }
            LevelGen.build(d.level, d.move ?: MoveKind.GRID, d.mods ?: emptyList(), roster)
        } else null
        when (d.screen) {
            "play" -> go(PlayScreen(this, run, spec ?: IntroScreen.roll(run)))
            "intro" -> go(IntroScreen(this, run, spec))
            "over" -> {
                val over = RunState(d.seed, "STAR22")
                if (d.arcade) over.arcade = Arcade()
                over.level = 9; over.score = 12340; over.dust = 87f; over.starsEaten = 64; over.bestCombo = 6
                profile.addScore("ACE", 30000, 12, true, 1L, "MQQN99")
                profile.addScore("BOB", 9000, 6, false, 2L, "STAR22")
                go(GameOverScreen(this, over))
            }
            "skills" -> go(SkillScreen(this))
            "scores" -> {
                val names = listOf("KIM", "ACE", "ZED", "BOB", "EVE", "SAM", "MAX")
                for ((i, n) in names.withIndex()) profile.addScore(n, 42000 - i * 5300, 14 - i, i % 2 == 0, i.toLong(), if (i % 3 == 0) "STAR22" else "MQQN99")
                profile.lastSeed = "STAR22"
                go(ScoresScreen(this))
            }
            "help" -> go(HelpScreen(this))
            "options" -> go(OptionsScreen(this) { go(MenuScreen(this)) })
            "seed" -> {
                profile.addScore("KIM", 8200, 7, false, 1L, "STAR22")
                profile.addScore("ACE", 15100, 11, true, 2L, "STAR22")
                profile.lastSeed = "STAR22"
                go(SeedScreen(this))
            }
            else -> go(MenuScreen(this))
        }
    }

    /** Switches screen. Pass [show] = false to return to a screen that is already set up. */
    fun go(next: Screen, show: Boolean = true) {
        screen = next
        if (show) next.show() else next.resume()
    }

    override fun resize(width: Int, height: Int) {
        if (width > 0 && height > 0) gfx.resize(width, height)
    }

    override fun render() {
        // Dev runs step at a fixed rate so bot runs and screenshots are repeatable.
        val dt = if (dev != null) 1f / 60f else minOf(Gdx.graphics.deltaTime, 1f / 30f)
        audio.update(dt)
        ScreenUtils.clear(0f, 0f, 0f, 1f)
        gfx.begin()
        screen?.render(dt)
        gfx.overlay()
        screen?.overlay()
        gfx.end()
        if (dev != null) devFrame(dev, dt)
    }

    private fun devFrame(d: DevOptions, dt: Float) {
        devTime += dt
        if (shotIndex < d.shots.size && devTime >= d.shots[shotIndex]) {
            val pm = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.backBufferWidth, Gdx.graphics.backBufferHeight)
            // The framebuffer's alpha channel is leftover blend state, not coverage: make the shot opaque.
            val px = pm.pixels
            for (i in 3 until px.limit() step 4) px.put(i, 0xFF.toByte())
            PixmapIO.writePNG(Gdx.files.absolute("${d.out}/shot_$shotIndex.png"), pm, Deflater.DEFAULT_COMPRESSION, true)
            pm.dispose()
            shotIndex++
            if (shotIndex >= d.shots.size) Gdx.app.exit()
        }
    }

    override fun pause() {
        if (::profile.isInitialized) profile.save()
        if (::audio.isInitialized) audio.pause()
        (screen as? PlayScreen)?.pauseGame()
    }

    override fun resume() {
        if (::audio.isInitialized) audio.resume()
    }

    override fun dispose() {
        audio.dispose()
        gfx.dispose()
    }

    // --- Input: translate to canvas coordinates and hand to the current screen --------------

    private fun canvas(x: Int, y: Int): Vector2 = gfx.viewport.unproject(point.set(x.toFloat(), y.toFloat()))

    override fun keyDown(keycode: Int): Boolean {
        if (keycode == Input.Keys.F11) {
            if (Gdx.graphics.isFullscreen) Gdx.graphics.setWindowedMode(1280, 720)
            else Gdx.graphics.setFullscreenMode(Gdx.graphics.displayMode)
            return true
        }
        if (keycode == Input.Keys.M && screen?.typing != true) {
            audio.toggleMusic()
            return true
        }
        return screen?.keyDown(keycode) ?: false
    }
    override fun keyUp(keycode: Int): Boolean { screen?.keyUp(keycode); return true }
    override fun keyTyped(character: Char): Boolean { screen?.keyTyped(character); return true }

    override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val p = canvas(screenX, screenY)
        screen?.touchDown(p.x, p.y, pointer)
        return true
    }

    override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
        val p = canvas(screenX, screenY)
        screen?.touchUp(p.x, p.y, pointer)
        return true
    }

    override fun touchCancelled(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean =
        touchUp(screenX, screenY, pointer, button)

    override fun touchDragged(screenX: Int, screenY: Int, pointer: Int): Boolean {
        val p = canvas(screenX, screenY)
        screen?.touchDragged(p.x, p.y, pointer)
        return true
    }

    override fun mouseMoved(screenX: Int, screenY: Int): Boolean {
        val p = canvas(screenX, screenY)
        screen?.mouseMoved(p.x, p.y)
        return true
    }

    override fun scrolled(amountX: Float, amountY: Float): Boolean = false

}
