const { chromium } = require('playwright');
const fs = require('fs');
const parts = JSON.parse(fs.readFileSync(__dirname + '/skins/walker_parts.json'));
const outfits = process.argv[2] ? process.argv[2].split(',') : Object.keys(parts);
const skins = ['pale', 'olive', 'dark'];
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await b.newPage({ viewport: { width: 1060, height: 260 } });
  p.on('pageerror', e => console.log('err:', e.message));
  fs.mkdirSync(__dirname + '/rows', { recursive: true });
  for (let i = 0; i < outfits.length; i++) {
    const o = outfits[i], s = skins[i % 3];
    const q = new URLSearchParams({ tex: `skins/${o}_${s}.png`, parts: JSON.stringify(parts[o]) });
    await p.goto(`http://localhost:8765/sheet.html?${q}`);
    await p.waitForFunction(() => window.DONE === true, null, { timeout: 60000 });
    await p.locator('canvas').screenshot({ path: `${__dirname}/rows/${o}.png` });
    console.log('row', o, s);
  }
  await b.close();
})();
