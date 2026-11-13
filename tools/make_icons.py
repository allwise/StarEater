"""
Cuts the Android launcher icons and the desktop window icons out of the one source image,
core/src/main/resources/images/StarEaterIcon1.png. Run it again after changing that image:

    python3 tools/make_icons.py

Needs Pillow (pip install pillow).
"""
from pathlib import Path
from PIL import Image, ImageDraw

root = Path(__file__).resolve().parent.parent
src = Image.open(root / "core/src/main/resources/images/StarEaterIcon1.png").convert("RGB")
res = root / "android/src/main/res"

def fit(size):
    return src.resize((size, size), Image.LANCZOS)

# Legacy launcher icons (Android 7.x), square and round.
for name, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
    d = res / f"mipmap-{name}"
    d.mkdir(parents=True, exist_ok=True)
    fit(px).save(d / "ic_launcher.png", optimize=True)
    big = fit(px * 4).convert("RGBA")
    mask = Image.new("L", big.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, big.size[0] - 1, big.size[1] - 1), fill=255)
    big.putalpha(mask)
    big.resize((px, px), Image.LANCZOS).save(d / "ic_launcher_round.png", optimize=True)
    # Adaptive foreground: a 108dp layer of which launchers show the middle 72dp or less.
    # The art fills that middle 72dp; its black background carries on into the black layer behind.
    layer = px * 108 // 48
    art = layer * 72 // 108
    fg = Image.new("RGBA", (layer, layer), (0, 0, 0, 0))
    fg.paste(fit(art).convert("RGBA"), ((layer - art) // 2, (layer - art) // 2))
    fg.save(d / "ic_launcher_foreground.png", optimize=True)

# Desktop window icons.
icons = root / "lwjgl3/src/main/resources/icons"
icons.mkdir(parents=True, exist_ok=True)
for px in (128, 32, 16):
    fit(px).save(icons / f"icon{px}.png", optimize=True)
