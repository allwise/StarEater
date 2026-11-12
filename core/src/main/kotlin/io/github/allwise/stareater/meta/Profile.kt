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

class ScoreEntry(val name: String, val score: Int, val level: Int, val mastered: Boolean, val epochMs: Long, val seed: String = "")

/** Everything that survives between runs: stardust, skills and the high-score board. */
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
                if (score != null && level != null && time != null) scores += ScoreEntry(f[0], score, level, f[3] == "1", time, seed)
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
        scores.asSequence().filter { !masteredOnly || it.mastered }.take(limit).toList()

    /** Top scores set on one particular seed key. */
    fun seedScores(seed: String, limit: Int = BOARD_SIZE): List<ScoreEntry> =
        scores.asSequence().filter { it.seed == seed }.take(limit).toList()

    /** 1-based rank a score would get on the open board, or 0 if it would not make it. */
    fun rankFor(score: Int): Int {
        if (score <= 0) return 0
        val rank = scores.count { it.score >= score } + 1
        return if (rank <= BOARD_SIZE) rank else 0
    }

    /** 1-based rank a score would get among runs on the same seed, or 0. */
    fun seedRankFor(seed: String, score: Int): Int {
        if (score <= 0 || seed.isEmpty()) return 0
        val rank = scores.count { it.seed == seed && it.score >= score } + 1
        return if (rank <= BOARD_SIZE) rank else 0
    }

    fun addScore(name: String, score: Int, level: Int, mastered: Boolean, nowMs: Long, seed: String = ""): ScoreEntry {
        val entry = ScoreEntry(name, score, level, mastered, nowMs, seed)
        scores += entry
        sortScores()
        // Keep enough history that the smaller boards are not starved by open-board entries, and
        // never drop the best score on any seed.
        var i = scores.size - 1
        while (scores.size > KEEP && i >= 0) {
            val e = scores[i]
            val bestOnSeed = e.seed.isNotEmpty() && scores.first { it.seed == e.seed } === e
            if (!bestOnSeed) scores.removeAt(i)
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
        store.put(K_SKILLS, owned.joinToString(",") { it.name })
        store.put(K_SCORES, scores.joinToString("\n") { "${it.name}|${it.score}|${it.level}|${if (it.mastered) 1 else 0}|${it.epochMs}|${it.seed}" })
        store.flush()
    }

    companion object {
        const val BOARD_SIZE = 10
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
