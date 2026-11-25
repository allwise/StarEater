# Play Store listing assets

| File | Play Console slot | Size |
|---|---|---|
| `icon-512.png` | App icon | 512x512, rendered from `art/icon.svg` |
| `feature-graphic.png` | Feature graphic | 1024x500 |
| `screenshot-*.jpg` | Phone screenshots (upload in this order) | 1920x1080 |

The screenshots are real frames from the game, taken with the demo switches (see the README's
"Developer switches"). To retake one after the game changes, run its line with
`xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew lwjgl3:run -Pdemo="...;window=1920x1080;phone=1;key=STAR22;out=DIR"`
and pick the best of the shots:

| Screenshot | Demo string (before the common part) | Shot used |
|---|---|---|
| 01 leviathan | `screen=play;level=20;boss=leviathan;move=glide;bot=1;skills=all;shots=4,7,10` | 1st |
| 02 arcade flow | `screen=play;level=9;mode=flow;move=scroll;mods=gravity,meteors;items=stun,wipe,flare;powers=magnet,speed;bot=1;shots=4,7,10` | 1st |
| 03 devourer | `screen=play;level=10;boss=devourer;move=glide;bot=1;skills=all;shots=4,7,10` | 2nd |
| 04 lights out + suns | `screen=play;level=16;move=glide;mods=gravity,lightsout;seed=3;bot=1;shots=5,8,11` | 2nd |
| 05 slot machine | `screen=intro;level=12;seed=7;shots=1.2,2,4` | 3rd |
| 06 classic grid | `screen=play;level=8;move=grid;mods=walls;roster=eater,stalker,warper,zoomer;bot=1;shots=5,8,11` | 3rd |
| 07 black hole | `screen=play;level=14;move=glide;mods=deep,gravity;seed=4;bot=1;skills=all;shots=4,7,10,13` | 2nd |
| 08 title | `screen=menu;checkpoint=10;dust=340;shots=2,4,6` | 3rd |

The feature graphic is the 2nd Leviathan shot, cropped to the field and mirrored so the serpent is on
the right, with the icon, the name and the tag line over it (made with a small Playwright page).
