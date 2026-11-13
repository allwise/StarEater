package io.github.allwise.stareater.gfx

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.glutils.HdpiUtils
import com.badlogic.gdx.graphics.glutils.PixmapTextureData
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.viewport.FitViewport
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object Pal {
    val BG = Color.valueOf("060914")
    val PANEL = Color.valueOf("0c1228")
    val PANEL_HI = Color.valueOf("17204a")
    val LINE = Color.valueOf("2a3769")
    val LATTICE = Color.valueOf("26315e")
    val WALL = Color.valueOf("2b3562")
    val WALL_HI = Color.valueOf("4a5a9c")
    val TEXT = Color.valueOf("e9efff")
    val DIM = Color.valueOf("7f8cb8")
    val PLAYER = Color.valueOf("eafcff")
    val CYAN = Color.valueOf("4fe3ff")
    val SHIELD = Color.valueOf("38f5c8")
    val GOLD = Color.valueOf("ffc533")
    val STAR = Color.valueOf("ffe773")
    val PINK = Color.valueOf("ff5fd2")
    val ORANGE = Color.valueOf("ff8a3c")
    val RED = Color.valueOf("ff4d5e")
    val GREEN = Color.valueOf("7dff7a")
    val PURPLE = Color.valueOf("b07bff")
    val STUN = Color.valueOf("6d7aa3")
    val WHITE: Color = Color.WHITE
}

/**
 * The game's whole drawing toolkit: a fixed 960x540 virtual canvas, a handful of primitives and the
 * fonts. It flips between the sprite batch and the shape renderer on demand so callers can just draw.
 */
class Gfx : Disposable {
    val viewport = FitViewport(W, H)
    private val batch = SpriteBatch()
    private val shapes = ShapeRenderer()
    private val layout = GlyphLayout()
    private val glowTex: Texture
    private val glowPixmap: Pixmap
    private val tmp = Color()

    lateinit var tiny: BitmapFont
    lateinit var small: BitmapFont
    lateinit var body: BitmapFont
    lateinit var big: BitmapFont
    lateinit var huge: BitmapFont
    lateinit var glyph: BitmapFont
    private var fontScale = 0f

    private var mode = NONE
    private var additive = false

    /** Where the canvas sits when the window is wider than it: -1 left, 0 centre, 1 right. */
    var align = 0
        set(v) {
            field = v
            place()
        }
    private var screenW = 1
    private var screenH = 1
    private val wide = Matrix4()

    init {
        val size = 128
        val pm = Pixmap(size, size, Pixmap.Format.RGBA8888)
        pm.blending = Pixmap.Blending.None
        for (y in 0 until size) for (x in 0 until size) {
            val dx = (x + 0.5f - size / 2f) / (size / 2f)
            val dy = (y + 0.5f - size / 2f) / (size / 2f)
            val d = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtMost(1f)
            val a = (1f - d) * (1f - d)
            pm.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, a))
        }
        // Managed, so Android can rebuild it from the pixmap if the GL context is ever lost.
        glowTex = Texture(PixmapTextureData(pm, null, false, false, true))
        glowPixmap = pm
        glowTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    }

    fun resize(width: Int, height: Int) {
        screenW = width
        screenH = height
        viewport.update(width, height, true)
        place()
        // Fonts are rasterised at the real pixel size so text stays crisp on any screen.
        // (backBufferHeight / height covers high-DPI desktops, where a logical pixel is several real ones.)
        val density = Gdx.graphics.backBufferHeight.toFloat() / max(1, Gdx.graphics.height)
        val scale = max(0.5f, viewport.screenHeight / H * density)
        if (fontScale == 0f || abs(scale - fontScale) / fontScale > 0.08f) buildFonts(scale)
    }

    /** Canvas pixels of window showing to the left and to the right of the canvas: the bars. */
    val spareLeft: Float get() = viewport.screenX * W / viewport.screenWidth.coerceAtLeast(1)
    val spareRight: Float get() = (screenW - viewport.screenX - viewport.screenWidth) * W / viewport.screenWidth.coerceAtLeast(1)

    /** Slides the canvas along whatever spare width the window has, keeping clear of notches. */
    private fun place() {
        val free = screenW - viewport.screenWidth
        if (free <= 0) return
        viewport.screenX = when {
            align < 0 -> min(Gdx.graphics.safeInsetLeft, free)
            align > 0 -> max(free - Gdx.graphics.safeInsetRight, 0)
            else -> free / 2
        }
    }

    private fun buildFonts(scale: Float) {
        if (fontScale != 0f) disposeFonts()
        fontScale = scale
        val gen = FreeTypeFontGenerator(Gdx.files.internal("fonts/JetBrainsMono-Bold.ttf"))
        fun make(size: Int, chars: String = FreeTypeFontGenerator.DEFAULT_CHARS): BitmapFont {
            val p = FreeTypeFontGenerator.FreeTypeFontParameter()
            p.size = max(6, (size * scale).roundToInt())
            p.characters = chars
            p.minFilter = Texture.TextureFilter.Linear
            p.magFilter = Texture.TextureFilter.Linear
            val f = gen.generateFont(p)
            f.data.setScale(1f / scale)
            f.setUseIntegerPositions(false)
            return f
        }
        tiny = make(12)
        small = make(15)
        body = make(20)
        big = make(30)
        huge = make(68, "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 !?.-+:#")
        glyph = make(29)
        gen.dispose()
    }

    private fun disposeFonts() {
        tiny.dispose(); small.dispose(); body.dispose(); big.dispose(); huge.dispose(); glyph.dispose()
    }

    // --- Frame / mode handling ---------------------------------------------------------------

    fun begin() {
        viewport.apply()
        batch.projectionMatrix = viewport.camera.combined
        shapes.projectionMatrix = viewport.camera.combined
        mode = NONE
    }

    fun end() {
        when (mode) {
            BATCH -> batch.end()
            SHAPES -> shapes.end()
        }
        mode = NONE
    }

    /**
     * From here until [end], draws over the whole window - the bars beside the canvas included -
     * still in canvas coordinates. For things that follow a finger, which may well be out there.
     */
    fun overlay() {
        end()
        HdpiUtils.glViewport(0, 0, screenW, screenH)
        val unit = W / viewport.screenWidth
        wide.setToOrtho2D(-viewport.screenX * unit, -viewport.screenY * unit, screenW * unit, screenH * unit)
        batch.projectionMatrix = wide
        shapes.projectionMatrix = wide
    }

    private fun useShapes(add: Boolean = false) {
        if (mode == SHAPES && additive == add) return
        end()
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, if (add) GL20.GL_ONE else GL20.GL_ONE_MINUS_SRC_ALPHA)
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        mode = SHAPES
        additive = add
    }

    private fun useBatch(add: Boolean = false) {
        if (mode == BATCH && additive == add) return
        end()
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, if (add) GL20.GL_ONE else GL20.GL_ONE_MINUS_SRC_ALPHA)
        batch.begin()
        mode = BATCH
        additive = add
    }

    // --- Primitives --------------------------------------------------------------------------

    fun rect(x: Float, y: Float, w: Float, h: Float, c: Color, a: Float = 1f) {
        useShapes()
        shapes.setColor(c.r, c.g, c.b, c.a * a)
        shapes.rect(x, y, w, h)
    }

    fun frame(x: Float, y: Float, w: Float, h: Float, t: Float, c: Color, a: Float = 1f) {
        rect(x, y, w, t, c, a)
        rect(x, y + h - t, w, t, c, a)
        rect(x, y + t, t, h - 2 * t, c, a)
        rect(x + w - t, y + t, t, h - 2 * t, c, a)
    }

    fun line(x1: Float, y1: Float, x2: Float, y2: Float, width: Float, c: Color, a: Float = 1f, add: Boolean = false) {
        useShapes(add)
        shapes.setColor(c.r, c.g, c.b, c.a * a)
        shapes.rectLine(x1, y1, x2, y2, width)
    }

    fun circle(x: Float, y: Float, r: Float, c: Color, a: Float = 1f, add: Boolean = false) {
        useShapes(add)
        shapes.setColor(c.r, c.g, c.b, c.a * a)
        shapes.circle(x, y, r, max(10, (r * 1.2f).toInt() + 8))
    }

    fun triangle(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, c: Color, a: Float = 1f) {
        useShapes()
        shapes.setColor(c.r, c.g, c.b, c.a * a)
        shapes.triangle(x1, y1, x2, y2, x3, y3)
    }

    /** An arc of a ring, drawn as thick segments. Angles in degrees, counter-clockwise from +x. */
    fun ring(x: Float, y: Float, r: Float, width: Float, c: Color, a: Float = 1f, from: Float = 0f, sweep: Float = 360f, add: Boolean = false) {
        if (sweep <= 0f) return
        useShapes(add)
        shapes.setColor(c.r, c.g, c.b, c.a * a)
        val steps = max(4, (abs(sweep) / 360f * max(18f, r * 1.1f)).toInt())
        var px = x + MathUtils.cosDeg(from) * r
        var py = y + MathUtils.sinDeg(from) * r
        for (i in 1..steps) {
            val ang = from + sweep * i / steps
            val nx = x + MathUtils.cosDeg(ang) * r
            val ny = y + MathUtils.sinDeg(ang) * r
            shapes.rectLine(px, py, nx, ny, width)
            px = nx; py = ny
        }
    }

    /** A star polygon with [points] tips. */
    fun star(x: Float, y: Float, outer: Float, inner: Float, points: Int, rotDeg: Float, c: Color, a: Float = 1f) {
        useShapes()
        shapes.setColor(c.r, c.g, c.b, c.a * a)
        val step = 180f / points
        var px = x + MathUtils.cosDeg(rotDeg) * outer
        var py = y + MathUtils.sinDeg(rotDeg) * outer
        for (i in 1..points * 2) {
            val r = if (i % 2 == 0) outer else inner
            val ang = rotDeg + step * i
            val nx = x + MathUtils.cosDeg(ang) * r
            val ny = y + MathUtils.sinDeg(ang) * r
            shapes.triangle(x, y, px, py, nx, ny)
            px = nx; py = ny
        }
    }

    /** A soft additive blob of light. */
    fun glow(x: Float, y: Float, r: Float, c: Color, a: Float = 1f) {
        if (a <= 0.003f) return
        useBatch(true)
        batch.setColor(c.r, c.g, c.b, c.a * a)
        batch.draw(glowTex, x - r, y - r, r * 2f, r * 2f)
    }

    fun text(font: BitmapFont, s: CharSequence, x: Float, y: Float, c: Color, a: Float = 1f, align: Int = Align.left) {
        useBatch()
        font.setColor(c.r, c.g, c.b, c.a * a)
        font.draw(batch, s, x, y, 0f, align, false)
    }

    /** Word-wrapped text. Returns the height it took up. */
    fun paragraph(font: BitmapFont, s: CharSequence, x: Float, y: Float, width: Float, c: Color, a: Float = 1f, align: Int = Align.left): Float {
        useBatch()
        font.setColor(c.r, c.g, c.b, c.a * a)
        return font.draw(batch, s, x, y, width, align, true).height
    }

    /** One character centred on a point - how the E's and S's get on screen. */
    fun glyphAt(font: BitmapFont, ch: Char, cx: Float, cy: Float, c: Color, a: Float = 1f) {
        useBatch()
        font.setColor(c.r, c.g, c.b, c.a * a)
        val h = if (ch.isLowerCase()) font.xHeight else font.capHeight
        font.draw(batch, GLYPHS[ch.code and 0x7f], cx, cy + h / 2f, 0f, Align.center, false)
    }

    fun width(font: BitmapFont, s: CharSequence): Float {
        layout.setText(font, s)
        return layout.width
    }

    fun mix(a: Color, b: Color, t: Float): Color = tmp.set(a).lerp(b, t)

    override fun dispose() {
        batch.dispose()
        shapes.dispose()
        glowTex.dispose()
        glowPixmap.dispose()
        if (fontScale != 0f) disposeFonts()
    }

    companion object {
        const val W = 960f
        const val H = 540f
        private const val NONE = 0
        private const val BATCH = 1
        private const val SHAPES = 2
        private val GLYPHS = Array(128) { it.toChar().toString() }
    }
}
