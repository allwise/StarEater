package io.github.allwise.stareater.meta

import io.github.allwise.stareater.sim.GameMode
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
    /** Each mode has a board of its own: the scores are not comparable. */
    val mode: GameMode = GameMode.CAMPAIGN,
    /** An arcade or endless run that never used anything out of its pockets. */
    val pure: Boolean = false,
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

    /** The day (see [SeedKey.today]) a daily run was last started, or -1 for never. */
    var dailyDay = -1L
        private set

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

    /** The highest boss level beaten so far (10, 20, ...). Beating one opens a start on the level after it. */
    var checkpoint = 0
        private set

    /** Where new runs begin: level 1, or the level after any boss already beaten. */
    var startLevel = 1
        set(v) { field = if (v in startLevels()) v else 1 }

    /**
     * Five seed keys kept for coming back to, in slots that work like the preset buttons on a car
     * radio: hold one to store, tap it to recall.
     */
    val saved = arrayOfNulls<String>(SAVE_SLOTS)
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
        dailyDay = store.get(K_DAILY)?.toLongOrNull() ?: -1L
        musicOn = store.get(K_MUSIC) != "0"
        sfxOn = store.get(K_SFX) != "0"
        musicVol = store.get(K_MUSIC_VOL)?.toIntOrNull() ?: VOLUME_DEFAULT
        sfxVol = store.get(K_SFX_VOL)?.toIntOrNull() ?: VOLUME_DEFAULT
        screenAlign = store.get(K_ALIGN)?.toIntOrNull() ?: 0
        tilt = store.get(K_TILT) == "1"
        tiltSens = store.get(K_TILT_SENS)?.toIntOrNull() ?: 1
        haptics = store.get(K_HAPTICS) != "0"
        checkpoint = (store.get(K_CHECKPOINT)?.toIntOrNull() ?: 0).coerceAtLeast(0)
        // A save from before bosses were recorded: having reached level 11 means the first boss fell, and so on.
        checkpoint = maxOf(checkpoint, minOf(GameMode.CAMPAIGN_LENGTH, (bestLevel - 1).coerceAtLeast(0) / CHECKPOINT_EVERY * CHECKPOINT_EVERY))
        startLevel = store.get(K_START)?.toIntOrNull() ?: 1
        store.get(K_SAVED)?.split(',')?.forEachIndexed { i, text ->
            // (An earlier version also wrote a level after a colon; only the key is kept now.)
            val key = text.substringBefore(':')
            if (i < SAVE_SLOTS && SeedKey.isValid(key)) saved[i] = key
        }
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
                    // Field 7 used to be a plain arcade flag ("1"), from before the arcade was split in two.
                    val mode = when (val m = f.getOrNull(6) ?: "") { "1" -> GameMode.FLOW; "", "0" -> GameMode.CAMPAIGN; else -> GameMode.byId(m) }
                    scores += ScoreEntry(f[0], score, level, f[3] == "1", time, seed, mode, f.getOrNull(7) == "1")
                }
            }
        }
        sortScores()
    }

    fun loadout(): Loadout = Skill.loadout(owned)

    /** True until today's daily run has been started. */
    fun dailyWaiting(today: Long): Boolean = dailyDay != today

    /** Notes that today's daily run has been started. */
    fun startedDaily(today: Long) {
        dailyDay = today
        save()
    }

    fun canBuy(s: Skill): Boolean = s !in owned && stardust >= s.cost && (s.requires == null || s.requires in owned)

    fun unlocked(s: Skill): Boolean = s.requires == null || s.requires in owned

    fun buy(s: Skill): Boolean {
        if (!canBuy(s)) return false
        stardust -= s.cost
        owned += s
        save()
        return true
    }

    /** Banks the proceeds of a finished run. [level] is how far a campaign run got; 0 for the other modes. */
    fun finishRun(dust: Int, level: Int) {
        stardust += dust
        totalRuns++
        if (level > bestLevel) bestLevel = level
        save()
    }

    /** Every level a campaign run may start on, lowest first: 1, and the level after each boss beaten short of the last. */
    fun startLevels(): List<Int> =
        (0..checkpoint / CHECKPOINT_EVERY).map { it * CHECKPOINT_EVERY + 1 }.filter { it < GameMode.CAMPAIGN_LENGTH }

    /** Matrix opens with the first boss, Flow with the second, Endless with the third. */
    fun unlocked(mode: GameMode): Boolean = checkpoint >= mode.unlockedBy

    /** Takes stardust out of the bank, if there is enough. */
    fun spend(amount: Int): Boolean {
        if (amount > stardust) return false
        stardust -= amount
        save()
        return true
    }

    /** Records a beaten boss. Returns the level that just became a starting point, or 0 if it already was. */
    fun beatBoss(level: Int): Int {
        if (level <= checkpoint || level % CHECKPOINT_EVERY != 0) return 0
        checkpoint = level
        save()
        return level + 1
    }

    fun store(slot: Int, key: String) {
        saved[slot] = key
        save()
    }

    fun grantDust(amount: Int) {
        stardust += amount
        save()
    }

    /** Top scores of one mode, best first. With [masteredOnly], only runs played with the full skill tree. */
    fun topScores(mode: GameMode = GameMode.CAMPAIGN, masteredOnly: Boolean = false, limit: Int = BOARD_SIZE): List<ScoreEntry> =
        scores.asSequence().filter { it.mode == mode && (!masteredOnly || it.mastered) }.take(limit).toList()

    /** Top campaign scores set on one particular seed key. */
    fun seedScores(seed: String, limit: Int = BOARD_SIZE): List<ScoreEntry> =
        scores.asSequence().filter { it.mode == GameMode.CAMPAIGN && it.seed == seed }.take(limit).toList()

    /** 1-based rank a score would get on its mode's board, or 0 if it would not make it. */
    fun rankFor(score: Int, mode: GameMode = GameMode.CAMPAIGN): Int {
        if (score <= 0) return 0
        val rank = scores.count { it.mode == mode && it.score >= score } + 1
        return if (rank <= BOARD_SIZE) rank else 0
    }

    /** 1-based rank a campaign score would get among runs on the same seed, or 0. */
    fun seedRankFor(seed: String, score: Int): Int {
        if (score <= 0 || seed.isEmpty()) return 0
        val rank = scores.count { it.mode == GameMode.CAMPAIGN && it.seed == seed && it.score >= score } + 1
        return if (rank <= BOARD_SIZE) rank else 0
    }

    /** The best score ever set in a mode, or 0. */
    fun bestScore(mode: GameMode): Int = scores.firstOrNull { it.mode == mode }?.score ?: 0

    fun addScore(
        name: String, score: Int, level: Int, mastered: Boolean, nowMs: Long, seed: String = "",
        mode: GameMode = GameMode.CAMPAIGN, pure: Boolean = false,
    ): ScoreEntry {
        val entry = ScoreEntry(name, score, level, mastered, nowMs, seed, mode, pure)
        scores += entry
        sortScores()
        // Keep enough history that the smaller boards are not starved by open-board entries, and
        // never drop the best score on any seed.
        var i = scores.size - 1
        while (scores.size > KEEP && i >= 0) {
            val e = scores[i]
            val campaign = e.mode == GameMode.CAMPAIGN
            val bestOnSeed = campaign && e.seed.isNotEmpty() && scores.first { it.mode == GameMode.CAMPAIGN && it.seed == e.seed } === e
            // The other modes' boards are small next to the campaign's: keep them whole.
            val onItsBoard = !campaign && scores.count { it.mode == e.mode && it.score >= e.score } <= BOARD_SIZE
            if (!bestOnSeed && !onItsBoard) scores.removeAt(i)
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
        checkpoint = 0
        startLevel = 1
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
            it.checkpoint = checkpoint; it.startLevel = startLevel; it.dailyDay = dailyDay
            saved.copyInto(it.saved)
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
        store.put(K_DAILY, dailyDay.toString())
        store.put(K_MUSIC, if (musicOn) "1" else "0")
        store.put(K_SFX, if (sfxOn) "1" else "0")
        store.put(K_MUSIC_VOL, musicVol.toString())
        store.put(K_SFX_VOL, sfxVol.toString())
        store.put(K_ALIGN, screenAlign.toString())
        store.put(K_TILT, if (tilt) "1" else "0")
        store.put(K_TILT_SENS, tiltSens.toString())
        store.put(K_HAPTICS, if (haptics) "1" else "0")
        store.put(K_CHECKPOINT, checkpoint.toString())
        store.put(K_START, startLevel.toString())
        store.put(K_SAVED, saved.joinToString(",") { it ?: "" })
        store.put(K_SKILLS, owned.joinToString(",") { it.name })
        store.put(K_SCORES, scores.joinToString("\n") { "${it.name}|${it.score}|${it.level}|${if (it.mastered) 1 else 0}|${it.epochMs}|${it.seed}|${it.mode.id}|${if (it.pure) 1 else 0}" })
        store.flush()
    }

    companion object {
        const val BOARD_SIZE = 10
        const val VOLUME_MAX = 10
        const val VOLUME_DEFAULT = 8
        const val SAVE_SLOTS = 5

        /** Same as the gap between bosses: every boss is a checkpoint. */
        const val CHECKPOINT_EVERY = 10
        private const val K_CHECKPOINT = "checkpoint"
        private const val K_START = "startLevel"
        private const val K_SAVED = "savedLevels" // the name is from when a level was kept as well
        private const val K_MUSIC_VOL = "musicVol"
        private const val K_SFX_VOL = "sfxVol"
        private const val K_ALIGN = "screenAlign"
        private const val K_TILT = "tilt"
        private const val K_TILT_SENS = "tiltSens"
        private const val K_HAPTICS = "haptics"
        private const val KEEP = 120
        private const val K_SEED = "lastSeed"
        private const val K_DAILY = "dailyDay"
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
