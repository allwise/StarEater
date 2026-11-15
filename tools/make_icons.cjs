/*
 * Renders every icon the game ships from the one drawing, art/icon.svg:
 *
 *   - Android's adaptive launcher icon (a background layer and a foreground layer, at five densities)
 *   - the plain and round launcher icons older Androids use
 *   - the desktop window icons
 *
 * Run it again after changing the drawing:
 *
 *     node tools/make_icons.cjs
 *
 * It draws with a headless browser, so each size is rendered from the vector rather than shrunk
 * from a big bitmap. Needs Playwright with its Chromium (npm install playwright).
 */
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');
const svg = fs.readFileSync(path.join(root, 'art/icon.svg'), 'utf8').replace(/<\?xml[^>]*\?>/, '');

/** The drawing with one of its two layers taken out. */
const without = (layer) => svg.replace(new RegExp(`<g id="${layer}"[\\s\\S]*?\\n  </g>`), '');

/** The middle 72 of the 108 units: what a launcher actually shows of an adaptive icon. */
const cropped = svg.replace('viewBox="0 0 108 108"', 'viewBox="18 18 72 72"');

async function main() {
  const browser = await chromium.launch();
  const page = await browser.newPage({ deviceScaleFactor: 1 });

  /** Renders [source] at [px] square into [file], optionally masked to a rounded shape. */
  async function render(source, px, file, radius = '0') {
    const sized = source.replace('width="432" height="432"', `width="${px}" height="${px}"`);
    await page.setViewportSize({ width: px, height: px });
    await page.setContent(
      `<body style="margin:0;background:transparent"><div style="width:${px}px;height:${px}px;overflow:hidden;border-radius:${radius}">${sized}</div></body>`);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    await page.screenshot({ path: file, omitBackground: true });
  }

  const res = path.join(root, 'android/src/main/res');
  for (const [density, px] of Object.entries({ mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 })) {
    const dir = path.join(res, `mipmap-${density}`);
    // An adaptive layer is 108dp where a plain icon is 48dp.
    const layer = Math.round(px * 108 / 48);
    await render(without('art'), layer, path.join(dir, 'ic_launcher_background.png'));
    await render(without('background'), layer, path.join(dir, 'ic_launcher_foreground.png'));
    await render(cropped, px, path.join(dir, 'ic_launcher.png'), '18%');
    await render(cropped, px, path.join(dir, 'ic_launcher_round.png'), '50%');
  }

  const icons = path.join(root, 'lwjgl3/src/main/resources/icons');
  for (const px of [128, 32, 16]) await render(cropped, px, path.join(icons, `icon${px}.png`), '18%');

  await browser.close();
}

main().catch((e) => { console.error(e); process.exit(1); });
