package io.github.allwise.stareater.meta

import io.github.allwise.stareater.sim.Loadout

enum class Branch(val title: String) { HULL("HULL"), ENGINE("ENGINE"), MAW("MAW") }

/**
 * The permanent skill tree. [col] and [tier] are the node's slot inside its branch (two columns wide;
 * a negative col means "centred"). Skills are bought with stardust and never lost.
 */
enum class Skill(
    val title: String,
    val blurb: String,
    val cost: Int,
    val branch: Branch,
    val col: Int,
    val tier: Int,
    val requires: Skill?,
    val apply: (Loadout) -> Unit,
) {
    AEGIS_1("AEGIS I", "A shield that soaks one hit. Recharges every level.", 40, Branch.HULL, -1, 0, null, { it.shields += 1 }),
    AEGIS_2("AEGIS II", "A second shield charge.", 140, Branch.HULL, 0, 1, AEGIS_1, { it.shields += 1 }),
    SECOND_WIND("SECOND WIND", "Once per run, cheat death with a huge stun blast.", 260, Branch.HULL, 1, 1, AEGIS_1, { it.secondWind = true }),
    RECHARGE("RECHARGE", "Shields slowly regrow during a level.", 320, Branch.HULL, 0, 2, AEGIS_2, { it.shieldRegen = true }),

    THRUST_1("THRUSTERS I", "Move 12% faster.", 30, Branch.ENGINE, -1, 0, null, { it.speed += 0.12f }),
    THRUST_2("THRUSTERS II", "Another 12% faster.", 110, Branch.ENGINE, 0, 1, THRUST_1, { it.speed += 0.12f }),
    DASH("BLINK DASH", "Dash a few tiles ahead, untouchable. Eats stars on the way.", 160, Branch.ENGINE, 1, 1, THRUST_1, { it.dash = true }),
    ECHO("WRAP ECHO", "Wrapping leaves an afterimage that enemies chase.", 240, Branch.ENGINE, 0, 2, THRUST_2, { it.echo = true }),
    PHASE_DASH("PHASE DASH", "Dash recharges faster and stuns what it passes.", 300, Branch.ENGINE, 1, 2, DASH, { it.dashCdMul = 0.6f; it.dashStun = true }),

    MAW_1("GRAVITY MAW I", "Nearby stars drift towards you.", 35, Branch.MAW, -1, 0, null, { it.magnet = 2.2f }),
    MAW_2("GRAVITY MAW II", "A much wider pull.", 120, Branch.MAW, 0, 1, MAW_1, { it.magnet = 3.8f }),
    FRENZY("FRENZY", "Combos last 50% longer.", 90, Branch.MAW, 1, 1, MAW_1, { it.comboTime = 1.5f }),
    SIEVE("STARDUST SIEVE", "Earn 30% more stardust.", 130, Branch.MAW, 0, 2, MAW_2, { it.dustMul = 1.3f }),
    LUCKY("LUCKY STARS", "Rare stars turn up far more often.", 180, Branch.MAW, 1, 2, FRENZY, { it.luck = 1.2f }),
    NOVA("NOVA PULSE", "Ten stars charge a blast that stuns everything nearby.", 280, Branch.MAW, -1, 3, LUCKY, { it.nova = true });

    companion object {
        val totalCost: Int = values().sumOf { it.cost }

        fun loadout(owned: Set<Skill>): Loadout {
            val l = Loadout()
            // Declaration order matters: tier II skills overwrite tier I values.
            for (s in values()) if (s in owned) s.apply(l)
            return l
        }
    }
}
