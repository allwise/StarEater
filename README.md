# Star Eater

You are the `o`. Eat the `*`. The `E`s and `S`s are coming for you.

The trick, same as in the original: **you can walk off one edge of the screen and come out of the
other, and the enemies never worked that out.** They take the plain route across the field, so a
well-timed wrap sends them trudging all the way back.

This version turns that idea into a roguelite: every level rolls a random mix of rules, the
difficulty keeps climbing, every tenth level is a boss, and a permanent skill tree carries you a
little further each run. Every run has a six-letter seed key, so two people can play the very same
run and compare.

There are four ways to play, and they open one boss at a time:

| Mode | What it is | Opens |
|---|---|---|
| **Campaign** | Thirty levels, three bosses, and the skill tree to grow into. Beat the third boss and it is complete. | from the start |
| **Arcade Matrix** | No skill tree. Every level is the classic grid. | boss 1 |
| **Arcade Flow** | No skill tree. Every level glides: glide, scroller or orbit, never the grid. | boss 2 |
| **Endless** | No skill tree. One level, dealt once, that never ends: each quota is a wave, and each wave is harder. | boss 3 |

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
| Move | Arrow keys or WASD | Drag anywhere (a floating stick), or tilt the phone |
| Step once (classic levels) | Tap a direction | Quick tap towards where you want to go |
| Top button: Overdrive *(skill)*, or pocket 1 | `SPACE` or `1` | Tap it |
| Middle button: Nova pulse *(skill)*, or pocket 2 | `E` or `2` | Tap it |
| Bottom button: pocket 3 | `C` or `3` | Tap it |
| Pause | `ESC` or `P` | Back button |
| Music on/off | `M` | Volumes are in OPTIONS |
| Fullscreen | `F11` | |

Menus work with the mouse, a finger, or just arrows + `ENTER`.

The three action buttons run down one edge of the field - top, middle, bottom - on the right, or on
the left if the picture has been slid left to put the joystick pad on the right.

### Options

On the title screen and in the pause menu.

- **Music** and **sound** volume, ten steps each (0 is off).
- **Screen: left / centre / right.** The picture is always 16:9. On a screen wider than that, this
  slides it to one side so the spare width ends up under one thumb. The bar left over is marked out
  as a joystick pad, and the floating stick works and is drawn out there, so steering never has to
  cover the game.
- **Tilt steering** (phones and tablets): lean the device the way you want to go. However it is held
  when a level starts counts as level, and it re-centres after every pause. Three sensitivities. In
  classic levels a firm lean is a held direction.
- **Vibration** (where there is a motor): the small taps on the initials wheels and the slot reels.

## How a run works

(This is the campaign. The arcades and Endless differ as described further down.)

1. **Level 1 is always the original**: classic tile movement, no twists, `E` and `S`.
2. Every level after that rolls **1 to 5 rules**: one movement mode plus up to four modifiers. Early
   levels roll few rules, later levels roll more. The briefing deals them out on a **slot machine**:
   one reel for the movement mode, four for the twists, and a reel that lands on a blank is a twist
   you were spared. Which reel deals what, and the order they stop in, is shuffled every time; one
   press stops them all if you are in a hurry.
3. Each level also rolls its **roster** of enemy types from the ones unlocked so far.
4. Eat the level's **quota** of stars to clear it. The quota grows every level.
5. Every tenth level is a **boss**. The third one, on level 30, is the end of the campaign.
6. Get eaten and the run ends. Your **stardust** is banked and can be spent on the skill tree.

Every boss is a **checkpoint**: once you have beaten the one on level 10, a FROM LEVEL button
appears next to PLAY and new runs can start on level 11 (then 21, and so on) instead of level 1.

At the start of a level the `o` beams in, and wears a marker until you first move, so there is no
doubt which thing on the screen is you.

Within a level, enemies start slow and single, then keep arriving, speeding up and getting smarter
the longer you take. Across levels, all of those numbers scale up too: steeply over the first ten
levels, while everything is new, and only gently after that, so the back two thirds of the campaign
are a matter of practice rather than a wall.

In the dark levels (Dark Nebula, Lights Out) there are fewer enemies and they are a little slower,
and anything outside your light shows as a pair of eyes, so nothing arrives out of nowhere.

### Movement modes

| Mode | What it is |
|---|---|
| **Classic** | The original. Everything lives on tiles; one tap, one step. Wraps on all four edges. |
| **Glide** | Thrust and drift like a space shooter. Momentum carries you; wraps on all four edges. |
| **Scroller** | A top-down scroller. Stars stream down from the top; you fly freely but only the sides wrap. |
| **Orbit** | A solar system. A sun (or a black hole) sits in the middle and pulls on everything, stars ride their orbits, and you coast unless you burn. The field is bigger than the screen, so the camera follows you. From level 3. |

### Gravity

Things that pull come in two kinds, in orbit levels and in the Gravity Wells rule alike.

- **Suns** only pull. They come in three stages - a pale dwarf, a golden star, a red giant - each
  heavier than the last (the single strength the game used to have sat between stages two and
  three). Nothing happens if you touch one: fly straight through it.
- **Black holes** pull about like a middling sun, and the black disc is exactly the size of the part
  that kills. It is instant: no shield and no moment of invulnerability helps. Only a Second Wind
  gets you thrown back out, once.

### Modifier rules

| Rule | Effect | Modes |
|---|---|---|
| Dark Nebula | You only see what is near you. Distant stars still glimmer. | all |
| Debris Field | Walls. Smart enemies path around them, dim ones get stuck. | classic, glide |
| Shy Stars | Stars drift away as you approach. Cut them off with a wrap. The small star magnet only slows them; the big one gets a tug-of-war. | classic, glide |
| Fading Light | Stars burn out if you take too long. | classic, glide |
| Swarm | Twice the enemies, each a bit slower. | all |
| Countdown | A clock that only stars can top up. Run out and you take a hit. | all |
| Meteor Shower | A row or column flashes, then gets flattened. Rocks stun enemies too. | all |
| Restless | No brakes: you can steer but never stop. | classic, glide |
| Serpent | Every star grows your tail, and biting it hurts. | classic, glide |
| Gravity Wells | Suns that drag you off course and the odd black hole. One or two in glide; in the scroller up to three drift down the screen. | glide, scroller |
| Inversion | The edges are solid for you, and open for *them*. | all |
| Deep Space | The field is four screens big and the camera follows you. Arrows on the rim point at off-screen stars. It still wraps. | classic, glide |
| Lights Out | Pitch black apart from a small glow around you. Every star you eat lights up its patch for a few seconds. | all |

Dark Nebula and Lights Out are never rolled together.

### Enemies

| | Name | Behaviour | From level |
|---|---|---|---|
| `E` | Eater | Walks straight at you. Cannot wrap. | 1 |
| `S` | Stalker | Aims at where you are heading, to cut you off. | 1 |
| `B` | Bouncer | Ricochets around diagonally and ignores you. | 2 |
| `Z` | Zoomer | Prowls, lines you up, telegraphs, then charges. | 3 |
| `W` | Warper | The one that learned the trick: takes the wrapped route. | 4 |
| `H` | Hive | Slow, but hatches fast, short-lived mites (`e`). | 6 |

On classic (grid) levels the enemies are their letters, as in the original. Everywhere else they
are drawn as creatures built from the same strokes: the E's arms are jaws that chomp faster as it
closes in, the S slithers, the B rolls, the Z coils and springs, the W flaps, the H swells until a
mite pops out.

"Intelligence" is a single 0..1 dial per level. Dim enemies hesitate, wander and press their faces
against walls; sharp ones lead their target, path around debris and turn on a dime.

### Bosses

Every tenth level is one enormous letter and a couple of minions, with no other twists. There are
no guns in this game, so a boss is not shot down, it is out-eaten: **every star you eat hurts it**,
and the level's quota is its health bar. Bosses cannot be stunned (they are slowed instead) and get
faster as they are worn down.

| Boss | Trick |
|---|---|
| The Devourer (`E`) | Lumbers after you, telegraphs, then lunges. Coughs up mites. |
| The Leviathan (`S`) | A serpent whose whole trailing body is deadly. |
| The Warden (`W`) | Knows the wrap, calls a cross of rocks down on your tile, and **locks the edges**. |

They rotate: level 10, 20, 30, then round again, scaled up.

A boss briefing starts like any other, and then the boss takes the slot machine over: every reel
slams to a stop on its letter.

The Warden's **edge block** is announced so that you can get ready for it: a klaxon, the whole rim
of the field throbbing red, and a countdown across the top for a couple of seconds. Then the rim
turns into a wall of teeth and for six seconds *nobody* wraps, not you and not the Warden, with a
second countdown until it opens again. Any boss can use it: it is one call, `World.sealEdges`.

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

### The arcades and Endless

**Arcade Matrix**, **Arcade Flow** and **Endless** leave the skill tree at home. Your kit is whatever
you pick up, plus whatever is in your pockets. Power-ups turn up on the field every ten seconds or
so as tilted squares, and blink out again if you leave them lying.

| Power-up | Lasts | Effect |
|---|---|---|
| Thrusters | the run, stacks x4 | 8% faster each. |
| Frenzy | the run, stacks x3 | Combos last longer. |
| Lucky Stars | the run, stacks x3 | Rare stars turn up more often. |
| Shield | 20 s | Soaks one hit. Popped is popped. |
| Magnet | 15 s | Stars drift towards you. |
| Overdrive | 8 s | 30% faster, straight away. |
| Wrap Echo | 20 s | Wrapping leaves a decoy behind. |
| Supply Drop | at once | Puts a random item in an empty pocket. |

The clocks only run while you are playing, and carry over from one level to the next. What you are
carrying is shown bottom left: timed ones wear a ring that drains, permanent ones show a pip per
stack. These runs still bank stardust, and each mode has a high-score board of its own.

#### Pockets

You have **three pockets**, each holding one single-use item, and they are the three action buttons.
Before the run you pack them out of your banked stardust. After every fifth level there is a **pit
stop** to restock - but a pit stop only takes what *this run* has earned so far, so it is always a
choice between spending it now and banking it, and every purchase on a run puts the prices up a
quarter. Anything bought on a visit can be put back before you leave.

| Item | Price | Effect |
|---|---|---|
| Overdrive | 30 | 30% faster for six seconds. |
| Stun Blast | 40 | Freezes everything near you. |
| Wipe | 70 | Clears the field of enemies. Bosses only stagger. |
| Flare | 25 | Lights a dark level for twelve seconds, and dazzles the enemies (they lose you) for a few. |
| Shield Cell | 50 | Soaks one hit. Not on a clock: it waits until it is needed. |
| Star Pull | 45 | Every star on the field flies to you. |

A run that never uses anything from its pockets is a **pure run**, and gets a diamond on the board.

#### Endless

One arena - a way of moving, two twists, three kinds of hostile - dealt once from the seed and kept.
There is no clearing it: each quota of stars is a **wave**, and each wave makes the level's numbers
those of the next level up, in place, without stopping. The tune runs three per cent faster every
wave.

### Skill tree and the boards

Stardust buys permanent skills across three branches:

- **Hull** - shields that recharge each level, a once-per-run revive, in-level shield regrowth.
- **Engine** - speed, the *Overdrive* button (30% faster for six seconds, then a breather), and
  *Wrap Echo*: wrapping leaves an afterimage enemies chase.
- **Maw** - a star magnet, longer combos, rarer stars, more stardust, and the Nova Pulse stun.

There is a high-score board for each mode - **Campaign**, **Matrix**, **Flow**, **Endless** - and a
**Seed** board listing the campaign runs on one seed key, where everyone played identical levels.
Two marks do the work that more boards would: a gold star is a campaign run played with the complete
skill tree, and a diamond is an arcade or endless run that never used its pockets.

Initials go in on three wheels: slide up or down anywhere to turn the lit one, tap to the left or
right of them to move along (or just type).

The skills are deliberately utility rather than raw power - difficulty never stops scaling, so a
full tree raises the ceiling without removing the need to actually dodge.

### Seeds

Every run has a seed key: six characters such as `STA-R22` (no `I`, `O`, `0` or `1`, so it can be
read out loud). The key decides the whole run: which rules each level rolls, which enemies, the
wall layouts, where the boss is. The same key deals the same run to anyone.

- **PLAY** starts a run on a fresh random key. The key is shown in the briefing, in the HUD and on
  the game-over screen, so a good run can be replayed or handed to a friend.
- **SEEDED RUN** lets you type a key, take today's key (the same for everybody that day), or roll a
  random one, and shows your best scores on it.
- After a run, **SAME SEED** replays it.

There are five **saved-seed slots**, which work like the preset buttons on a car radio. They sit
beside the button on every briefing, on the game-over screen and on the SEEDED RUN screen: press
and hold one to keep the seed in hand, tap it on the SEEDED RUN screen to bring that key back.

Scores are stored on the device only; comparing with a friend means comparing screens.

### Sound

All of it is synthesised by the game at start-up: pulse, triangle, saw and noise voices in
`core/.../audio/Synth.kt`, with the effects and the six looping tunes written as code in
`Sounds.kt`. The two arcades and Endless each have a tune of their own, all three of them the
original tune dressed differently: tightened into runs of sixteenths for Matrix, slowed and held for
Flow, looped over a galloping bass for Endless. Tunes are streamed from memory a block at a time rather than handed to the platform's
music player, because that is the only way to get a loop with no hiccup at the seam on a phone. After a run, the title tune comes back an octave down and low-passed, as if from the
next room, once the game-over jingle has had its say. There are no audio files in the repository. `./gradlew core:test` also drops every
effect and tune as a WAV into `core/build/synth-preview/`, to listen to without launching the game.

## Project layout

```
core/      All game code. No platform dependencies beyond libGDX.
  sim/       The simulation: pure Kotlin, no rendering or input. Unit-tested and bot-testable.
  meta/      Skill tree, save data, high scores.
  gfx/       Drawing toolkit, the world renderer (with its camera) and the creature animations.
  audio/     The chip synth, the tunes and effects, and playback.
  input/     Keyboard / touch / tilt -> InputState.
  ui/        Screens.
lwjgl3/    Desktop launcher.
android/   Android launcher.
assets/    The font. Everything else is drawn in code.
art/       The icon, as an SVG.
tools/     The script that renders the icons from it.
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
and return it from `Movement.create`. If the mode wants a field bigger than the screen, set the
spec's `width` and `height` in `LevelGen.build`; the camera takes care of the rest.

### Adding a boss

Subclass `BossType` in `core/.../sim/Bosses.kt`, add it to `Enemies.bosses`, and give it a shape in
`Creatures.kt`.

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
rasterises its font at the device's real resolution. It keeps the screen awake while it is open
(tilt steering means nobody touches it for minutes), holds the screen the way up it is for as long
as a level is being played (so leaning the phone to steer cannot flip the picture), and asks for
one permission, `VIBRATE`.

The icon is one drawing, `art/icon.svg`: the E after the o, the o after the star, in flat shapes that
still read at 48 pixels. Android's launcher icons (adaptive, plain and round) and the desktop window
icons are all rendered from it. After changing the drawing, render them again (needs Node and
Playwright):

```bash
node tools/make_icons.cjs
```

## Developer switches

A demo string drops the game straight into a state, optionally with the bot playing and
screenshots being saved. It uses a throwaway save, so it never touches real progress.

```bash
./gradlew lwjgl3:run -Pdemo="screen=play;level=9;move=SCROLL;mods=gravity,meteors;skills=all;bot=1"
```

| Key | Meaning |
|---|---|
| `screen` | `menu`, `intro`, `play`, `over`, `skills`, `scores`, `seed`, `help`, `options`, `pack`, `pitstop` |
| `level` | Level number to scale difficulty for |
| `move` | `GRID`, `GLIDE`, `SCROLL` or `ORBIT` |
| `mods` | Comma-separated rule ids: `fog,walls,shy,decay,swarm,countdown,meteors,restless,tail,gravity,inversion,deep,lightsout` |
| `roster` | Comma-separated enemy ids: `eater,stalker,bouncer,zoomer,warper,hive` |
| `boss` | Jump into a boss level: `devourer`, `leviathan` or `warden` |
| `skills=all` | Start with the full skill tree |
| `mode` | `matrix`, `flow` or `endless` (the campaign otherwise) |
| `powers` | Comma-separated power-ups to start an arcade run with: `speed,frenzy,luck,shield,magnet,overdrive,echo` |
| `items` | Up to three pocket items: `overdrive,stun,wipe,flare,shield,pull` |
| `won=1` | With `screen=over`: show the campaign-complete ending |
| `align` | Screen position: `-1` left, `0` centre, `1` right |
| `checkpoint` | Pretend every boss up to this level is beaten, e.g. `20` (also fills two saved-seed slots) |
| `window` | Window size, e.g. `1600x720`, to try screens that are not 16:9 |
| `dust` | Starting stardust |
| `bot=1` | Let the autopilot play |
| `auto=1` | Skip level briefings automatically (handy with `bot=1`) |
| `shots`, `out` | Save screenshots at these times (seconds) into this directory, then exit |

## Ideas not built yet

- **Armed** - glide plus an actual gun: shots stun rather than kill, and stars are your ammo.
- **Online boards** - seed keys already make runs comparable; the scores just are not shared yet.
- **More bosses**, and boss levels that roll a modifier of their own.

## Credits

Font: [JetBrains Mono](https://www.jetbrains.com/lp/mono/), SIL Open Font License 1.1
(see `assets/fonts/OFL.txt`).
