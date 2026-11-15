package io.github.allwise.stareater.sim

/**
 * The game's modes.
 *
 *  - The campaign is the way in: thirty levels, three bosses, a skill tree to grow into.
 *  - The two arcades leave the tree at home. Matrix is tile by tile, every level; Flow is everything
 *    that glides.
 *  - Endless is one level that never ends, and never stops getting worse.
 */
enum class GameMode(val title: String, val id: String) {
    CAMPAIGN("CAMPAIGN", "C"),
    MATRIX("ARCADE MATRIX", "M"),
    FLOW("ARCADE FLOW", "F"),
    ENDLESS("ENDLESS", "E");

    /** No skill tree: the kit is what gets picked up, plus what is in the pockets. */
    val arcade: Boolean get() = this != CAMPAIGN

    /** The boss whose defeat opens this mode: level 10 for Matrix, 20 for Flow, 30 for Endless. */
    val unlockedBy: Int get() = ordinal * LevelSpec.BOSS_EVERY

    companion object {
        /** The campaign is over when this level's boss goes down. */
        const val CAMPAIGN_LENGTH = 30

        fun byId(id: String): GameMode = values().firstOrNull { it.id == id } ?: CAMPAIGN
    }
}

/**
 * Things to carry in your pockets on an arcade or endless run: bought with stardust beforehand, or
 * at the pit stop every fifth level. One tap, one use, gone.
 */
enum class Item(val title: String, val blurb: String, val price: Int) {
    OVERDRIVE("OVERDRIVE", "30% faster for six seconds.", 30),
    STUN("STUN BLAST", "Freezes everything near you.", 40),
    WIPE("WIPE", "Clears the field. Bosses only stagger.", 70),
    FLARE("FLARE", "Lights the dark and dazzles them.", 25),
    SHIELD("SHIELD CELL", "Soaks one hit. Waits until needed.", 50),
    PULL("STAR PULL", "Every star on the field flies to you.", 45);

    companion object {
        const val POCKETS = 3

        /** A pit stop comes after every level that divides by this. */
        const val SHOP_EVERY = 5

        /** What [item] costs once [bought] things have already been bought on this run: each one puts prices up a quarter. */
        fun priceOf(item: Item, bought: Int): Int = Math.round(item.price * (1f + 0.25f * bought))
    }
}
