# Star Eater

A roguelite take on an old terminal game: you are the `o`, you eat `*`, the letters eat you, and
the edges of the screen wrap for you but not (mostly) for them. Kotlin + libGDX; one `core` module
shared by a desktop launcher (`lwjgl3`) and an Android one (`android`).

`README.md` is the full description of the game and its rules. This file is the working notes:
how to build and check things, the traps, what is agreed, and where the work stands.

## Commands

```bash
./gradlew core:test                 # all the tests; also drops every sound as a WAV in core/build/synth-preview
./gradlew lwjgl3:run                # play it
./gradlew lwjgl3:dist               # one runnable jar in lwjgl3/build/libs
./gradlew android:assembleDebug     # needs an Android SDK; the module is skipped without one
node tools/make_icons.cjs           # re-render every icon from art/icon.svg (needs Playwright)
```

A demo string drops the game straight into a state and can save screenshots, which is how screens
get checked without playing to them (keys are listed in the README under "Developer switches"):

```bash
./gradlew lwjgl3:run -Pdemo="screen=play;mode=flow;level=6;mods=lightsout;items=stun,wipe,flare;bot=1;shots=2.5;out=/tmp/shots"
```

With no display, wrap it in `xvfb-run -a -s "-screen 0 1280x720x24"`. The game runs fine without a
sound card. To check audio for real rather than by reading code, OpenAL Soft can write what the
game plays to a file: set `ALSOFT_DRIVERS=wave` and point `ALSOFT_CONF` at a config with
`[wave] file = out.wav`.

Android cannot be built here without an SDK. `.github/workflows/build.yml` builds the tests, the
desktop jar and the debug APK on every push: after pushing, check that run.

## Releasing to Google Play

- Build the bundle Play wants with `./gradlew android:bundleRelease`; it lands in
  `android/build/outputs/bundle/release/`. It is signed with the *upload key* if
  `keystore.properties` (storeFile, storePassword, keyAlias, keyPassword) exists at the project root
  or the `STAREATER_KEYSTORE`, `_KEYSTORE_PASSWORD`, `STAREATER_KEY_ALIAS`, `_KEY_PASSWORD`
  variables are set; otherwise it comes out unsigned (that is what CI builds). None of it is in git.
- Google holds the app signing key (Play App Signing); we only own the upload key. Make it once:
  `keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 10000`
  and keep a copy in a password manager.
- Raise `appVersionCode` in `gradle.properties` for every upload; Play refuses a repeat.
- The package name `io.github.allwise.stareater` is permanent once the app exists in the Console.
- Free vs paid cannot be changed later (free stays free), hence the plan: a free app with a one-time
  in-app purchase that unlocks everything past level 10 (agreed, not built yet).
- targetSdk 36 turns on predictive back; the manifest opts out so the back button still reaches the
  game. Check pause-on-back on a phone after any targetSdk change.

## How the code is laid out

- `sim/` is the game itself and must stay pure Kotlin: no libGDX, no files, no clock. It takes an
  `InputState` each frame and gives back entities plus a list of `Fx` events. That is what lets the
  tests and the bot drive it. `meta/` (profile, skills, seed keys) is pure too, behind `KeyValueStore`.
- `gfx/WorldRenderer` turns a `World` and its `Fx` into pictures; `ui/PlayScreen.sounds()` turns the
  same `Fx` into sound. A new event needs a line in both.
- `input/Controls` is pure as well (tilt maths included): `PlayScreen` feeds it sensor readings.
- Everything is drawn in code on a fixed 960x540 canvas, y up. The field is the bottom 960x480; the
  HUD is the 60 px strip above it. There are no image or audio assets apart from the font and the icon.
- Sound is code too: `audio/Synth` is the instrument, `audio/Sounds` the recipes and the tunes,
  `audio/Audio` plays them. Tunes are streamed from memory by `LoopPlayer`, not played as files.

## Traps

- **Levels are a pure function of seed, mode and level number** (`LevelGen.forSeed`). Checkpoints,
  seed boards and "same key, same run" all rest on that. Adding or reordering a random draw in level
  generation, or in a rule's `setup`, silently changes every existing seed. Sometimes that is fine,
  but do it on purpose. (Last done 2026-11-17: glide levels with Deep Space take one extra draw for
  the Gravity Wells pairing, so their rosters changed; no other level did.)
- **Things saved by name**: `Skill` constants (`DASH` and `PHASE_DASH` are now Overdrive and
  Afterburner but keep their old names for that reason), `GameMode.id`, the profile's keys. Old saves
  must keep loading: `Profile` has small migrations for earlier formats, each with a test.
- **Text is fixed-pitch and does not wrap itself.** The tiny font is about 7.2 px a character. Rule
  blurbs must fit 52 characters, pocket-item blurbs 40. The help screen's left column is full: a line
  added means a line taken out, and the touch wording of CONTROLS is a line longer than the desktop
  one. Screenshot it after touching it.
- **The font only has ASCII.**
- **Tunes** are eight bars of sixteen steps; the tests check every bar sounds, the loop joins up, and
  no effect ends on a click. A tune that dies away before its last bar ends is a seam you can hear.
- **`LevelGen.scale` is called again on a live level** (Endless does it every wave), so rule `tune`
  functions must give the same result however many times they run.
- **The picture is only slid sideways on `PlayScreen`** (`StarEaterGame.go` sets `gfx.align`). Every
  other screen is centred and the window is cleared to `Pal.BG`, so a screen whose backdrop is not
  plain `Pal.BG` right to the canvas edge shows a seam on a wide phone. Check with `window=1600x720`.
- **The options live in `OptionsPanel`**, shown by both `OptionsScreen` and the pause menu in
  `PlayScreen`. A new setting goes there once; check it fits both (`screen=play;paused=1;phone=1`).
- **Darkness is drawn, not simulated**: `WorldRenderer.vis` decides what shows. The sim only carries
  the numbers (`fogRadius`, flares, `Well.light`, `Sun.light`).
- Buttons hide rather than disappear: screens index into `buttons` by position in places
  (`GameOverScreen.refresh`), so add new ones at the end.

## How the work goes

The owner plays on an Android phone and sends feedback as numbered lists; reply to the numbers. Three
things cannot be verified from a desk and should always be called out as untested when they change:
tilt steering, vibration, and how anything actually sounds. Prefer a screenshot over a description.
Keep `README.md` in step with the game in the same commit, and this file's status section too.

## Agreed design (so it is not argued again)

- Skill first. Stardust buys choices and bragging rights after the campaign, never permanent power.
  No paid continues.
- The campaign is the way in: 30 levels, 3 bosses, the skill tree. Boss 1 opens Arcade Matrix (grid
  only), boss 2 Arcade Flow (never the grid), boss 3 Endless.
- Arcade and Endless have no skill tree: field pick-ups plus three pockets of one-use items. The
  pit stop (every 5th level) spends only what the run has earned, and each purchase raises prices 25%.
- The title screen's front page is only what can be played now, plus the skill tree. The campaign
  button just says START (the other modes come later, once you are familiar). Locked modes
  are not listed, one line names the next unlock, and everything else lives behind MORE.
- The quota drops by 7 after each boss (not in Endless, and not the bosses' own); rules, enemies and
  speed keep climbing regardless. Level length should stay roughly flat across the campaign.
- Serpent: the tail stuns an enemy that touches it and is bitten off there (stun, not kill; a short
  grace afterwards so nothing can be stun-locked).
- Shy Stars against a star magnet: push in the far half of the magnet's reach, pull inside half.
- Gravity wells can fling you past top speed (up to +60%, fading; `Player.fling`). Dwarf suns pull
  1.6x what they did, suns 1.35x, giants 1.2x; black holes and the orbit sun are unchanged.
- Orbit levels: more stars (they only ride the inner part of a big field), 3/4 the enemies.
- A glide level with Deep Space gets Gravity Wells 60% of the time, and a deep field gets 2 more suns.
- The daily run is on the title screen's front page, always from level 1; its star radiates until
  today's has been started (`Profile.dailyDay`).
- Suns light dark levels (well: half its pull reach; orbit sun: a quarter of the field's height).
  Black holes never do: dark plus a black hole is meant to stay as hard as it is.
- The W's swim stroke changes its pace within a stroke, never its average speed.
- No more boards than modes. Marks instead: gold star = mastered campaign run, diamond = pure run
  (nothing used from the pockets).
- Overdrive replaced the dash everywhere. Suns only pull; black holes kill outright (a Second Wind
  is the one exception).
- Saved slots keep a seed key and nothing else.
- Cosmetics, when they come, must each be switchable off.
- Screen shake is a setting, off by default (the owner does not like it; some players do).
  Vibration is on by default; every feel is a named `Buzz` in `input/Haptics.kt`.
- With tilt steering on, touch only presses buttons: no floating stick, no tap-to-step, no pad.

## Status

**Publishing:** the owner is setting up a Norwegian sole proprietorship (ENK) first; the Play account
is personal for now (so Play's 12-testers-for-14-days closed test applies before production). Release
signing, the AAB build and a 16 KB alignment check are in place and unverified on a real upload.
Store listing assets are in `store/` (screenshots, feature graphic, 512 icon; how they were made is in
`store/README.md`). The privacy policy and a landing page are a static site in `site/`, meant for
Cloudflare on allwise.dev (the Cloudflare tools here can read the account but not deploy, so the owner
uploads it); its contact address is still the placeholder CONTACT_EMAIL. Still to build: the unlock purchase (Play Billing) with the level-10 demo cap, and Play Games
leaderboards, both behind a `Platform`-style interface with a desktop no-op.

*As of 2026-11-21.*

**Playtested (2026-11-15): "a lot of good feel". Tilt and sound: "works well as is" (2026-11-18).** Two rounds of small things came back and are
built and pushed, waiting on the next playtest:

- Menus centred on the whole screen instead of following the left/right setting; the two-page
  title screen with locked modes hidden; START instead of CAMPAIGN; the campaign's Overdrive and
  Nova buttons drawn as pictures like the pockets.
- The quota dropping by 7 after each boss because levels past 10 to 15 ran long (level 11 is now
  19 stars, 19 is 27, 21 is 22, 29 is 30).
- Balance: enemies crash into the Serpent tail; shy stars push at a distance and give in up close
  against a magnet; suns light dark levels.
- A pause button in the status strip, and the pause menu being the options (tilt re-centres on
  RESUME - untested on a device, as is everything about tilt).
- The W swims in strokes (pace only; drawn from stills, never watched moving on a phone).
- New icon (2026-11-21), picked from several drafts: the same E, o and star as a triangle chase with
  speed streaks (E bottom left after the o at the top, the o after the star at the right), plus a few
  more background stars. Rendered into every launcher and window icon by tools/make_icons.cjs.
- Round four (2026-11-18): vibration on game events (stars, crashes and more - none of it felt
  yet), screen shake made switchable and off by default, touch steering off while tilting.
- Round three (2026-11-17): the scroller's sideways speed with a part-pushed stick; the daily run
  on the front page, always from level 1; stronger wells that can fling you; orbit levels with more
  stars and fewer enemies; Deep Space and Gravity Wells paired up.

**Still not commented on, so still open for tuning:** item prices, enemy numbers and speed on
levels 12 to 20, the dark levels, and the Matrix, Flow and Endless tunes (never heard by ear).

**Next, in the agreed order** (do not start until the playtest feedback is in):

1. Scoring depth: a counted-up tally on the clear screen (stars, flawless x1.5, under-par time
   bonus, dare multiplier); pace against your best in the HUD; dares - optional handicaps that
   multiply score, entered by staking stardust, paying back with interest for beating your own
   best. Dares also apply to the campaign, with an award for completing it with one on.
2. Hooks: the daily seed with a streak (first run of the day counts; 14-day history); about 30
   trophies for skill feats, three of them rotating daily; cosmetics bought with stardust or earned
   by trophies, each one toggleable; a copyable share line.
3. Endless, finished: every 40 s or so a slot-machine reel spins live in a corner and adds or swaps
   a twist without pausing play, bumping the multiplier; the tune speeds up per reel rather than per
   wave; a pit stop of some kind. Rules have no teardown yet, so swapping one out needs that first.

**Parked:** online boards. Google Play Games Services would cover per-mode and daily boards for
free once the app is in the Play Console; per-seed boards would need a small server of our own.

**Loose ends:** `core/src/main/resources/images/StarEaterIcon1.png` (the earlier icon artwork) is no
longer used by anything but still ships inside the jar and the APK.
