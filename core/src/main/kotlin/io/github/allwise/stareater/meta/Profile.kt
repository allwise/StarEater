package io.github.allwise.stareater.meta

import io.github.allwise.stareater.sim.Loadout

/** The tiny bit of persistence the game needs, so core never touches a platform API directly. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun flush()
}

class MemoryStore : KeyValueStore {
    private val map = HashMap<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String) { map[key] = value }
    override fun flush() {}
}

class ScoreEntry(
    val name: String, val score: Int, val level: Int, val mastered: Boolean, val epochMs: Long,
    val seed: String = "",
    /** Arcade runs have their own board: no skill tree, so they are not comparable with the rest. */
    val arcade: Boolean = false,
)

/** Everything that survives between runs: stardust, skills, settings and the high-score boards. */
class Profile(private val store: KeyValueStore) {
    var stardust = 0
        private set
    var totalRuns = 0
        private set
    var bestLevel = 0
        private set
    var initials = "AAA"

    /** Key of the seed most recently played or looked up. */
    var lastSeed = ""
    var musicOn = true
    var sfxOn = true

    /** Volumes, 0 to [VOLUME_MAX]. [VOLUME_DEFAULT] is the level the game was mixed at. */
    var musicVol = VOLUME_DEFAULT
        set(v) { field = v.coerceIn(0, VOLUME_MAX) }
    var sfxVol = VOLUME_DEFAULT
        set(v) { field = v.coerceIn(0, VOLUME_MAX) }

    /** Where the picture sits on a screen wider than the game: -1 left, 0 centre, 1 right. */
    var screenAlign = 0
        set(v) { field = v.coerceIn(-1, 1) }

    /** Steer by tilting the device, and how eager that is (0 gentle, 1 normal, 2 twitchy). */
    var tilt = false
    var tiltSens = 1
        set(v) { field = v.coerceIn(0, 2) }

    /** Little taps through the vibration motor. */
    var haptics = true
    val owned: MutableSet<Skill> = LinkedHashSet()
    private val scores = ArrayList<ScoreEntry>()

    /** Fully kitted out. Mastered runs compete on a level playing field. */
    val mastered: Boolean get() = owned.size == Skill.values().size

    init {
        stardust = store.get(K_DUST)?.toIntOrNull() ?: 0
        totalRuns = store.get(K_RUNS)?.toIntOrNull() ?: 0
        bestLevel = store.get(K_BEST)?.toIntOrNull() ?: 0
        initials = store.get(K_NAME)?.takeIf { it.length == 3 } ?: "AAA"
        lastSeed = store.get(K_SEED)?.takeIf { SeedKey.isValid(it) } ?: ""
        musicOn = store.get(K_MUSIC) != "0"
        sfxOn = store.get(K_SFX) != "0"
        musicVol = store.get(K_MUSIC_VOL)?.toIntOrNull() ?: VOLUME_DEFAULT
        sfxVol = store.get(K_SFX_VOL)?.toIntOrNull() ?: VOLUME_DEFAULT
        screenAlign = store.get(K_ALIGN)?.toIntOrNull() ?: 0
        tilt = store.get(K_TILT) == "1"
        tiltSens = store.get(K_TILT_SENS)?.toIntOrNull() ?: 1
        haptics = store.get(K_HAPTICS) != "0"
        store.get(K_SKILLS)?.split(',')?.forEach { id ->
            Skill.values().firstOrNull { it.name == id }?.let { owned += it }
        }
        store.get(K_SCORES)?.lineSequence()?.forEach { line ->
            val f = line.split('|')
            if (f.size >= 5) {
                val score = f[1].toIntOrNull()
                val level = f[2].toIntOrNull()
                val time = f[4].toLongOrNull()
                val seed = f.getOrNull(5) ?: ""
                if (score != null && level != null && time != null) {
                    scores += ScoreEntry(f[0], score, level, f[3] == "1", time, seed, f.getOrNull(6) == "1")
                }
            }
        }
        sortScores()
    }

    fun loadout(): Loadout = Skill.loadout(owned)

    fun canBuy(s: Skill): Boolean = s !in owned && stardust >= s.cost && (s.requires == null || s.requires in owned)

    fun unlocked(s: Skill): Boolean = s.requires == null || s.requires in owned

    fun buy(s: Skill): Boolean {
        if (!canBuy(s)) return false
        stardust -= s.cost
        owned += s
        save()
        return true
    }

    /** Banks the proceeds of a finished run. */
    fun finishRun(dust: Int, level: Int) {
        stardust += dust
        totalRuns++
        if (level > bestLevel) bestLevel = level
        save()
    }

    fun grantDust(amount: Int) {
        stardust += amount
        save()
    }

    /** Top scores, best first. With [masteredOnly], only runs played with the full skill tree. */
    fun topScores(masteredOnly: Boolean, limit: Int = BOARD_SIZE): List<ScoreEntry> =
        scores.asSequence().filter { !it.arcade && (!masteredOnly || it.mastered) }.take(limit).toList()

    /** Top scores set on one particular seed key. */
    fun seedScores(seed: String, limit: Int = BOARD_SIZE): List<ScoreEntry> =
        scores.asSequence().filter { !it.arcade && it.seed == seed }.take(limit).toList()

    /** Top scores of arcade runs. */
    fun arcadeScores(limit: Int = BOARD_SIZE): List<ScoreEntry> =
        scores.asSequence().filter { it.arcade }.take(limit).toList()

    /** 1-based rank a score would get on the open board (or the arcade one), or 0 if it would not make it. */
    fun rankFor(score: Int, arcade: Boolean = false): Int {
        if (score <= 0) return 0
        val rank = scores.count { it.arcade == arcade && it.score >= score } + 1
        return if (rank <= BOARD_SIZE) rank else 0
    }

    /** 1-based rank a score would get among runs on the same seed, or 0. */
    fun seedRankFor(seed: String, score: Int): Int {
        if (score <= 0 || seed.isEmpty()) return 0
        val rank = scores.count { !it.arcade && it.seed == seed && it.score >= score } + 1
        return if (rank <= BOARD_SIZE) rank else 0
    }

    fun addScore(
        name: String, score: Int, level: Int, mastered: Boolean, nowMs: Long, seed: String = "", arcade: Boolean = false,
    ): ScoreEntry {
        val entry = ScoreEntry(name, score, level, mastered, nowMs, seed, arcade)
        scores += entry
        sortScores()
        // Keep enough history that the smaller boards are not starved by open-board entries, and
        // never drop the best score on any seed.
        var i = scores.size - 1
        while (scores.size > KEEP && i >= 0) {
            val e = scores[i]
            val bestOnSeed = !e.arcade && e.seed.isNotEmpty() && scores.first { !it.arcade && it.seed == e.seed } === e
            // The arcade board is small next to the others: keep it whole.
            val onArcadeBoard = e.arcade && scores.count { it.arcade && it.score >= e.score } <= BOARD_SIZE
            if (!bestOnSeed && !onArcadeBoard) scores.removeAt(i)
            i--
        }
        initials = name
        save()
        return entry
    }

    fun reset() {
        stardust = 0
        totalRuns = 0
        bestLevel = 0
        owned.clear()
        scores.clear()
        save()
    }

    private fun sortScores() {
        scores.sortWith(compareByDescending<ScoreEntry> { it.score }.thenBy { it.epochMs })
    }

    /** Writes this profile into another store (export / tests). */
    fun copyInto(target: KeyValueStore) {
        Profile(target).also {
            it.stardust = stardust; it.totalRuns = totalRuns; it.bestLevel = bestLevel
            it.initials = initials; it.lastSeed = lastSeed; it.musicOn = musicOn; it.sfxOn = sfxOn
            it.musicVol = musicVol; it.sfxVol = sfxVol; it.screenAlign = screenAlign
            it.tilt = tilt; it.tiltSens = tiltSens; it.haptics = haptics
            it.owned += owned; it.scores += scores
            it.save()
        }
    }

    fun save() {
        store.put(K_DUST, stardust.toString())
        store.put(K_RUNS, totalRuns.toString())
        store.put(K_BEST, bestLevel.toString())
        store.put(K_NAME, initials)
        store.put(K_SEED, lastSeed)
        store.put(K_MUSIC, if (musicOn) "1" else "0")
        store.put(K_SFX, if (sfxOn) "1" else "0")
        store.put(K_MUSIC_VOL, musicVol.toString())
        store.put(K_SFX_VOL, sfxVol.toString())
        store.put(K_ALIGN, screenAlign.toString())
        store.put(K_TILT, if (tilt) "1" else "0")
        store.put(K_TILT_SENS, tiltSens.toString())
        store.put(K_HAPTICS, if (haptics) "1" else "0")
        store.put(K_SKILLS, owned.joinToString(",") { it.name })
        store.put(K_SCORES, scores.joinToString("\n") { "${it.name}|${it.score}|${it.level}|${if (it.mastered) 1 else 0}|${it.epochMs}|${it.seed}|${if (it.arcade) 1 else 0}" })
        store.flush()
    }

    companion object {
        const val BOARD_SIZE = 10
        const val VOLUME_MAX = 10
        const val VOLUME_DEFAULT = 8
        private const val K_MUSIC_VOL = "musicVol"
        private const val K_SFX_VOL = "sfxVol"
        private const val K_ALIGN = "screenAlign"
        private const val K_TILT = "tilt"
        private const val K_TILT_SENS = "tiltSens"
        private const val K_HAPTICS = "haptics"
        private const val KEEP = 120
        private const val K_SEED = "lastSeed"
        private const val K_MUSIC = "music"
        private const val K_SFX = "sfx"
        private const val K_DUST = "stardust"
        private const val K_RUNS = "runs"
        private const val K_BEST = "bestLevel"
        private const val K_NAME = "initials"
        private const val K_SKILLS = "skills"
        private const val K_SCORES = "scores"
    }
}
