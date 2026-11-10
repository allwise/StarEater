package io.github.allwise.stareater.ui

import com.badlogic.gdx.Input.Keys
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.utils.Align
import io.github.allwise.stareater.StarEaterGame
import io.github.allwise.stareater.gfx.Gfx
import io.github.allwise.stareater.gfx.Pal
import io.github.allwise.stareater.meta.Branch
import io.github.allwise.stareater.meta.Skill
import kotlin.math.abs

/** The permanent upgrade tree. Three branches; buy from the top down. */
class SkillScreen(game: StarEaterGame) : Screen(game) {
    private var selected = Skill.AEGIS_1
    private var wipeArmed = 0f
    private lateinit var buy: Button
    private lateinit var wipe: Button

    override fun show() {
        button(16f, Gfx.H - 50f, 110f, 36f, "BACK") { back() }.small = true
        buy = button(Gfx.W - 216f, 26f, 200f, 46f, "BUY") { purchase() }
        buy.accent = Pal.GOLD
        wipe = button(Gfx.W - 126f, Gfx.H - 50f, 110f, 36f, "WIPE SAVE") {
            if (wipeArmed > 0f) { game.profile.reset(); wipeArmed = 0f } else wipeArmed = 3f
        }
        wipe.small = true
        wipe.accent = Pal.RED
        focus = 1
        selected = Skill.values().firstOrNull { game.profile.canBuy(it) } ?: Skill.AEGIS_1
    }

    override fun back() = game.go(MenuScreen(game))

    private fun purchase() {
        game.profile.buy(selected)
    }

    private fun nodeX(s: Skill): Float {
        val centre = 180f + s.branch.ordinal * 300f
        return when (s.col) {
            0 -> centre - 72f
            1 -> centre + 72f
            else -> centre
        }
    }

    private fun nodeY(s: Skill): Float = 418f - s.tier * 76f

    private fun branchColor(b: Branch): Color = when (b) {
        Branch.HULL -> Pal.SHIELD
        Branch.ENGINE -> Pal.CYAN
        Branch.MAW -> Pal.GOLD
    }

    override fun render(dt: Float) {
        age += dt
        wipeArmed = (wipeArmed - dt).coerceAtLeast(0f)
        wipe.label = if (wipeArmed > 0f) "SURE?" else "WIPE SAVE"
        val p = game.profile
        g.rect(0f, 0f, Gfx.W, Gfx.H, Pal.BG)
        title("SKILL TREE")
        g.star(Gfx.W / 2f - 78f, Gfx.H - 62f, 8f, 3.4f, 5, 90f, Pal.STAR)
        g.text(g.small, "${p.stardust} STARDUST", Gfx.W / 2f - 62f, Gfx.H - 56f, Pal.STAR)
        if (p.mastered) g.text(g.small, "MASTERED", Gfx.W / 2f + 90f, Gfx.H - 56f, Pal.GOLD)

        for (b in Branch.values()) {
            val x = 180f + b.ordinal * 300f
            g.rect(x - 142f, 132f, 284f, 330f, Pal.PANEL, 0.6f)
            g.text(g.tiny, b.title, x, 454f, branchColor(b), 0.9f, Align.center)
        }

        // Links first, so the nodes sit on top of them.
        for (s in Skill.values()) {
            val req = s.requires ?: continue
            val lit = req in p.owned
            g.line(nodeX(req), nodeY(req) - NODE_H / 2f, nodeX(s), nodeY(s) + NODE_H / 2f, if (lit) 3f else 2f, if (lit) branchColor(s.branch) else Pal.LINE, if (lit) 0.9f else 0.7f)
        }
        for (s in Skill.values()) node(s)

        // Detail panel for the selected skill.
        val s = selected
        val c = branchColor(s.branch)
        g.rect(16f, 16f, Gfx.W - 32f, 100f, Pal.PANEL)
        g.rect(16f, 16f, 4f, 100f, c)
        g.text(g.body, s.title, 34f, 104f, c)
        g.text(g.small, s.blurb, 34f, 74f, Pal.TEXT)
        val owned = s in p.owned
        val status = when {
            owned -> "OWNED"
            !p.unlocked(s) -> "LOCKED - NEEDS ${s.requires?.title}"
            p.stardust < s.cost -> "COSTS ${s.cost} - YOU NEED ${s.cost - p.stardust} MORE"
            else -> "COSTS ${s.cost} STARDUST"
        }
        g.text(g.tiny, status, 34f, 42f, if (owned) Pal.GREEN else if (p.canBuy(s)) Pal.STAR else Pal.DIM)
        buy.visible = !owned
        buy.enabled = p.canBuy(s)
        buy.label = if (owned) "OWNED" else "BUY  ${s.cost}"
        drawButtons()
    }

    private fun node(s: Skill) {
        val p = game.profile
        val x = nodeX(s) - NODE_W / 2f
        val y = nodeY(s) - NODE_H / 2f
        val c = branchColor(s.branch)
        val owned = s in p.owned
        val open = p.unlocked(s)
        val can = p.canBuy(s)
        val sel = s == selected
        when {
            owned -> g.rect(x, y, NODE_W, NODE_H, c, 0.28f)
            else -> g.rect(x, y, NODE_W, NODE_H, Pal.PANEL_HI, if (open) 0.9f else 0.4f)
        }
        val edge = when {
            sel -> Pal.WHITE
            owned -> c
            can -> g.mix(Pal.LINE, c, 0.5f + 0.5f * MathUtils.sin(age * 5f))
            else -> Pal.LINE
        }
        g.frame(x, y, NODE_W, NODE_H, if (sel) 3f else 2f, edge)
        val a = if (owned || open) 1f else 0.45f
        g.text(g.tiny, s.title, nodeX(s), nodeY(s) + 13f, if (owned) Pal.TEXT else c, a, Align.center)
        g.text(g.tiny, if (owned) "OWNED" else s.cost.toString(), nodeX(s), nodeY(s) - 5f, if (owned) c else if (can) Pal.STAR else Pal.DIM, a, Align.center)
    }

    /** Moves the selection to the nearest node in a direction. */
    private fun moveSelection(dx: Int, dy: Int) {
        var best: Skill? = null
        var bestScore = Float.MAX_VALUE
        for (s in Skill.values()) {
            if (s == selected) continue
            val ox = nodeX(s) - nodeX(selected)
            val oy = nodeY(s) - nodeY(selected)
            val along = ox * dx + oy * dy
            if (along <= 1f) continue
            val score = along + (abs(ox * dy) + abs(oy * dx)) * 2f
            if (score < bestScore) { bestScore = score; best = s }
        }
        if (best != null) selected = best
    }

    override fun keyDown(key: Int): Boolean {
        when (key) {
            Keys.UP, Keys.W -> moveSelection(0, 1)
            Keys.DOWN, Keys.S -> moveSelection(0, -1)
            Keys.LEFT, Keys.A -> moveSelection(-1, 0)
            Keys.RIGHT, Keys.D -> moveSelection(1, 0)
            Keys.ENTER, Keys.NUMPAD_ENTER, Keys.SPACE -> purchase()
            else -> return super.keyDown(key)
        }
        return true
    }

    override fun touchDown(x: Float, y: Float, pointer: Int) {
        for (s in Skill.values()) {
            if (abs(x - nodeX(s)) <= NODE_W / 2f && abs(y - nodeY(s)) <= NODE_H / 2f) {
                // Tapping the already-selected node buys it.
                if (s == selected) purchase() else selected = s
                return
            }
        }
        super.touchDown(x, y, pointer)
    }

    private companion object {
        const val NODE_W = 132f
        const val NODE_H = 46f
    }
}
