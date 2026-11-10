# Star Eater

You are the `o`. Eat the `*`. The `E`s and `S`s are coming for you.

The trick, same as in the original: **you can walk off one edge of the screen and come out of the
other, and the enemies never worked that out.** They take the plain route across the field, so a
well-timed wrap sends them trudging all the way back.

This version turns that idea into a roguelite: every level rolls a random mix of rules, the
difficulty keeps climbing, and a permanent skill tree carries you a little further each run.

Built with **Kotlin + [libGDX](https://libgdx.com/)**. Runs on desktop today; the Android module
shares all of the game code.

## Running it

You need a JDK (17 or newer). Nothing else.

```bash
./gradlew lwjgl3:run
```

Build a single runnable jar (ends up in `lwjgl3/build/libs/StarEater-<version>-desktop.jar`):

```bash
./gradlew lwjgl3:dist
```

Run the tests:

```bash
./gradlew core:test
```

### Controls

| | Keyboard | Touch / mouse |
|---|---|---|
| Move | Arrow keys or WASD | Drag anywhere (a floating stick) |
| Step once (classic levels) | Tap a direction | Quick tap towards where you want to go |
| Dash *(skill)* | `SPACE` | Tap the DASH dial |
| Nova pulse *(skill)* | `E` | Tap the NOVA dial |
| Pause | `ESC` or `P` | Back button |
| Fullscreen | `F11` | |

Menus work with the mouse, a finger, or just arrows + `ENTER`.

## How a run works

1. **Level 1 is always the original**: classic tile movement, no twists, `E` and `S`.
2. Every level after that rolls **1 to 5 rules**: one movement mode plus up to four modifiers. Early
   levels roll few rules, later levels roll more.
3. Each level also rolls its **roster** of enemy types from the ones unlocked so far.
4. Eat the level's **quota** of stars to clear it. The quota grows every level.
5. Get eaten and the run ends. Your **stardust** is banked and can be spent on the skill tree.

Within a level, enemies start slow and single, then keep arriving, speeding up and getting smarter
the longer you take. Across levels, all of those numbers scale up too.

### Movement modes

| Mode | What it is |
|---|---|
| **Classic** | The original. Everything lives on tiles; one tap, one step. Wraps on all four edges. |
| **Glide** | Thrust and drift like a space shooter. Momentum carries you; wraps on all four edges. |
| **Scroller** | A top-down scroller. Stars stream down from the top; you fly freely but only the sides wrap. |

### Modifier rules

| Rule | Effect | Modes |
|---|---|---|
| Dark Nebula | You only see what is near you. Distant stars still glimmer. | all |
| Debris Field | Walls. Smart enemies path around them, dim ones get stuck. | classic, glide |
| Shy Stars | Stars drift away as you approach. Cut them off with a wrap. | classic, glide |
| Fading Light | Stars burn out if you take too long. | classic, glide |
| Swarm | Twice the enemies, each a bit slower. | all |
| Countdown | A clock that only stars can top up. Run out and you take a hit. | all |
| Meteor Shower | A row or column flashes, then gets flattened. Rocks stun enemies too. | all |
| Restless | No brakes: you can steer but never stop. | classic, glide |
| Serpent | Every star grows your tail, and biting it hurts. | classic, glide |
| Black Hole | A gravity well drags everything towards it. | glide, scroller |
| Inversion | The edges are solid for you, and open for *them*. | all |

### Enemies

| | Name | Behaviour | From level |
|---|---|---|---|
| `E` | Eater | Walks straight at you. Cannot wrap. | 1 |
| `S` | Stalker | Aims at where you are heading, to cut you off. | 1 |
| `B` | Bouncer | Ricochets around diagonally and ignores you. | 2 |
| `Z` | Zoomer | Prowls, lines you up, telegraphs, then charges. | 3 |
| `W` | Warper | The one that learned the trick: takes the wrapped route. | 4 |
| `H` | Hive | Slow, but hatches fast, short-lived mites (`e`). | 6 |

"Intelligence" is a single 0..1 dial per level. Dim enemies hesitate, wander and press their faces
against walls; sharp ones lead their target, path around debris and turn on a dime.

### Stars

| Star | Value | Notes |
|---|---|---|
| Star | 1 | The plain `*`. |
| Gold star | 3 | |
| Pulsar | 4 | Blinks. Can only be eaten while lit. |
| Comet | 5 | Keeps moving. |
| Nova | 10 | Burns out in a few seconds; eating it stuns everything nearby. |

Every star counts one towards the quota; the value is what goes into your score and your stardust.
Eat stars in quick succession to build a combo multiplier (up to x9).

### Skill tree and the two boards

Stardust buys permanent skills across three branches:

- **Hull** - shields that recharge each level, a once-per-run revive, in-level shield regrowth.
- **Engine** - speed, a blink dash, and *Wrap Echo*: wrapping leaves an afterimage enemies chase.
- **Maw** - a star magnet, longer combos, rarer stars, more stardust, and the Nova Pulse stun.

There are two high-score boards. **Open** takes every run. **Mastered** only takes runs played with
the complete tree, so everyone on it has identical kit and the only variable left is the player.
The skills are deliberately utility rather than raw power - difficulty never stops scaling, so a
full tree raises the ceiling without removing the need to actually dodge.

## Project layout

```
core/      All game code. No platform dependencies beyond libGDX.
  sim/       The simulation: pure Kotlin, no rendering or input. Unit-tested and bot-testable.
  meta/      Skill tree, save data, high scores.
  gfx/       Drawing toolkit and the world renderer.
  input/     Keyboard / touch -> InputState.
  ui/        Screens.
lwjgl3/    Desktop launcher.
android/   Android launcher.
assets/    The font. Everything else is drawn in code.
```

The simulation only ever sees an `InputState` and only ever emits entities plus a list of `Fx`
events, which is what lets the same code run under the unit tests, the bot, the desktop window
and a phone.

### Adding a rule

Rules are self-contained. Subclass `Rule` in `core/.../sim/Rules.kt`, override the hooks you need
(`setup`, `update`, `onStarSpawn`, `onStarEaten`), and register it in `Rules.all` with the level it
unlocks at and the movement modes it supports. The level generator, the briefing screen and the
soak test pick it up from there.

### Adding an enemy

Subclass `EnemyType` in `core/.../sim/Enemies.kt` and implement `think`: write a desired direction
and a pace onto the enemy. The level's movement mode turns that into tile steps or steering, so a
new enemy works in every mode without knowing about any of them. Add it to `Enemies.all`.

### Adding a movement mode

Add a `MoveKind`, implement `Movement` (how the player moves, how an enemy's desire becomes motion)
and return it from `Movement.create`.

## Android

The `android` module is only included in the build when an Android SDK is configured (a
`local.properties` with `sdk.dir=...`, or `ANDROID_HOME` set), so the desktop build needs no SDK.

```bash
./gradlew android:assembleDebug
```

```bash
./gradlew android:installDebug
```

The game is landscape, draws to a fixed 960x540 virtual canvas that scales to any screen, and
rasterises its font at the device's real resolution. It has no launcher icon yet.

## Developer switches

A demo string drops the game straight into a state, optionally with the bot playing and
screenshots being saved. It uses a throwaway save, so it never touches real progress.

```bash
./gradlew lwjgl3:run -Pdemo="screen=play;level=9;move=SCROLL;mods=gravity,meteors;skills=all;bot=1"
```

| Key | Meaning |
|---|---|
| `screen` | `menu`, `intro`, `play`, `over`, `skills`, `scores`, `help` |
| `level` | Level number to scale difficulty for |
| `move` | `GRID`, `GLIDE` or `SCROLL` |
| `mods` | Comma-separated rule ids: `fog,walls,shy,decay,swarm,countdown,meteors,restless,tail,gravity,inversion` |
| `roster` | Comma-separated enemy ids: `eater,stalker,bouncer,zoomer,warper,hive` |
| `skills=all` | Start with the full skill tree |
| `dust` | Starting stardust |
| `bot=1` | Let the autopilot play |
| `auto=1` | Skip level briefings automatically (handy with `bot=1`) |
| `shots`, `out` | Save screenshots at these times (seconds) into this directory, then exit |

## Ideas not built yet

- **Deep Space** - a field several screens wide with a follow camera. The wrap is still there, you
  just have to remember where.
- **Armed** - glide plus an actual gun: shots stun rather than kill, and stars are your ammo.
- **Orbit** - everything circles a central sun; you thrust to change orbit instead of steering.
- **Twins** - you steer two `o`s at once, mirrored. Either can eat, either can die.
- **Lights Out** - the field is dark and each star you eat lights its surroundings for a while.
- **Boss levels** - every tenth level, one very large letter.
- **Daily seed** - everyone gets the same level sequence for a day; a natural fit for the Mastered
  board and for online leaderboards.
- **Sound** - there is none yet.

## Credits

Font: [JetBrains Mono](https://www.jetbrains.com/lp/mono/), SIL Open Font License 1.1
(see `assets/fonts/OFL.txt`).
