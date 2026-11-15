package io.github.allwise.stareater.ui

import com.badlogic.gdx.utils.Align
import io.github.allwise.stareater.StarEaterGame
import io.github.allwise.stareater.audio.Sfx
import io.github.allwise.stareater.audio.Tune
import io.github.allwise.stareater.gfx.Gfx
import io.github.allwise.stareater.gfx.Pal
import io.github.allwise.stareater.gfx.PowerArt
import io.github.allwise.stareater.sim.Item
import io.github.allwise.stareater.sim.RunState

/**
 * Where the three pockets get filled.
 *
 * Before an arcade or endless run it is a packing list paid for out of the bank. As a pit stop,
 * every fifth level, it only takes what the run has earned so far - so the choice is to spend it now
 * or bank it - and every purchase puts the prices up a quarter, so no run can simply be bought.
 */
class PocketShop(game: StarEaterGame, private val run: RunState, private val pitStop: Boolean) : Screen(game) {
    private val art = PowerArt(game.gfx)
    private val shelf = ArrayList<Button>()
    private val pockets = ArrayList<Button>()

    /** What each pocket's item cost if it was bought on this visit, so it can be handed back; 0 if it was there already. */
    private val paid = IntArray(Item.POCKETS)
    private var note = ""
    private var noteLeft = 0f

    private val purse: Int get() = if (pitStop) run.dust.toInt() else game.profile.stardust

    private fun price(item: Item): Int = if (pitStop) Item.priceOf(item, run.bought) else item.price

    override fun show() {
        if (!pitStop) game.audio.tune(Tune.MENU)
        for ((i, item) in Item.values().withIndex()) {
            val x = SHELF_X + (i % 2) * (CARD_W + 20f)
            val y = SHELF_Y - (i / 2) * (CARD_H + 10f)
            shelf += button(x, y, CARD_W, CARD_H, "") { buy(item) }.also { it.silent = true }
        }
        for (i in 0 until Item.POCKETS) {
            val x = Gfx.W / 2f + (i - 1) * (POCKET_W + 14f) - POCKET_W / 2f
            pockets += button(x, POCKET_Y, POCKET_W, POCKET_H, "") { handBack(i) }.also { it.silent = true }
        }
        val go = button(Gfx.W / 2f - 150f, 24f, 300f, 46f, if (pitStop) "BACK OUT THERE" else "START") { leave() }
        go.accent = Pal.GOLD
        if (!pitStop) button(16f, Gfx.H - 50f, 110f, 36f, "BACK") { back() }.small = true
        focus = buttons.indexOf(go)
    }

    private fun buy(item: Item) {
        val free = run.pockets.indexOfFirst { it == null }
        val cost = price(item)
        when {
            free < 0 -> say("YOUR POCKETS ARE FULL")
            cost > purse -> say("NOT ENOUGH STARDUST")
            else -> {
                if (pitStop) {
                    run.dust -= cost
                    run.bought++
                } else {
                    game.profile.spend(cost)
                }
                run.pockets[free] = item
                paid[free] = cost
                game.audio.play(Sfx.UI_BUY)
                game.buzz()
                return
            }
        }
        game.audio.play(Sfx.UI_NO)
    }

    /** Anything bought on this visit can go back on the shelf for what it cost. */
    private fun handBack(i: Int) {
        val item = run.pockets[i] ?: return
        if (paid[i] <= 0) {
            game.audio.play(Sfx.UI_NO)
            say("${item.title} IS ALREADY YOURS - IT STAYS")
            return
        }
        if (pitStop) {
            run.dust += paid[i]
            run.bought--
        } else {
            game.profile.grantDust(paid[i])
        }
        run.pockets[i] = null
        paid[i] = 0
        game.audio.play(Sfx.UI_TICK)
    }

    private fun say(text: String) {
        note = text
        noteLeft = 2.2f
    }

    private fun leave() = game.go(IntroScreen(game, run))

    /** Backing out of the packing list puts everything back; backing out of a pit stop just carries on. */
    override fun back() {
        if (pitStop) {
            leave()
            return
        }
        for (i in 0 until Item.POCKETS) if (paid[i] > 0) handBack(i)
        game.go(MenuScreen(game))
    }

    override fun render(dt: Float) {
        age += dt
        noteLeft -= dt
        g.rect(0f, 0f, Gfx.W, Gfx.H, Pal.BG)
        val cx = Gfx.W / 2f
        g.glow(cx, 520f, 420f, Pal.GOLD, 0.1f)
        title(if (pitStop) "PIT STOP" else "PACK YOUR POCKETS")
        val sub = if (pitStop) "${run.mode.title}  -  LEVEL ${run.level} IS NEXT" else "${run.mode.title}  -  THREE POCKETS, ONE USE EACH"
        g.text(g.small, sub, cx, 478f, Pal.CYAN, 1f, Align.center)

        // The purse: what there is to spend here.
        val money = "$purse"
        val label = if (pitStop) "EARNED THIS RUN  -  SPEND IT NOW OR BANK IT" else "STARDUST IN THE BANK"
        val mw = g.width(g.body, money)
        g.star(cx - mw / 2f - 16f, 442f, 9f, 4f, 5, 90f, Pal.STAR)
        g.text(g.body, money, cx - mw / 2f, 450f, Pal.STAR)
        g.text(g.tiny, label, cx, 424f, Pal.DIM, 1f, Align.center)

        val full = run.pockets.none { it == null }
        for ((i, item) in Item.values().withIndex()) shelf[i].enabled = !full && price(item) <= purse
        drawButtons()

        for ((i, item) in Item.values().withIndex()) {
            val b = shelf[i]
            val a = if (b.enabled) 1f else 0.45f
            val c = PowerArt.color(item)
            g.glow(b.x + 34f, b.cy, 30f, c, 0.4f * a)
            art.item(item, b.x + 34f, b.cy, 15f, age, a)
            g.text(g.small, item.title, b.x + 68f, b.y + b.h - 11f, c, a)
            g.text(g.tiny, item.blurb, b.x + 68f, b.y + 22f, Pal.TEXT, 0.85f * a)
            val cost = price(item)
            g.text(g.body, "$cost", b.x + b.w - 14f, b.y + b.h - 10f, if (cost <= purse) Pal.STAR else Pal.RED, a, Align.right)
        }

        g.text(g.tiny, "YOUR POCKETS", cx, POCKET_Y + POCKET_H + 18f, Pal.DIM, 1f, Align.center)
        for (i in 0 until Item.POCKETS) {
            val b = pockets[i]
            val item = run.pockets[i]
            if (item == null) {
                g.text(g.tiny, "EMPTY", b.cx, b.cy + 5f, Pal.DIM, 0.6f, Align.center)
            } else {
                art.item(item, b.x + 26f, b.cy, 12f, age, 1f)
                g.text(g.tiny, item.title, b.x + 48f, b.cy + 5f, PowerArt.color(item))
                if (paid[i] > 0) g.text(g.tiny, "tap to put back", b.cx, b.y - 4f, Pal.DIM, 0.7f, Align.center)
            }
        }

        val idle = if (pitStop && run.bought > 0) "EVERY PURCHASE ON A RUN PUTS THE PRICES UP A QUARTER" else ""
        val fresh = noteLeft > 0f
        g.text(g.tiny, if (fresh) note else idle, cx, 88f, if (fresh) Pal.GOLD else Pal.DIM, if (fresh) noteLeft.coerceAtMost(1f) else 1f, Align.center)
    }

    private companion object {
        const val CARD_W = 400f
        const val CARD_H = 62f
        const val SHELF_X = 70f
        const val SHELF_Y = 336f
        const val POCKET_W = 190f
        const val POCKET_H = 42f
        const val POCKET_Y = 112f
    }
}
