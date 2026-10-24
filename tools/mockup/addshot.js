const { chromium } = require('playwright');
const fs = require('fs');
const parts = JSON.parse(fs.readFileSync(__dirname + '/skins/walker_parts.json'));
// args: dir outDir name[:addons]...
const [dir, outDir, ...names] = process.argv.slice(2);
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await b.newPage({ viewport: { width: 1060, height: 260 } });
  p.on('pageerror', e => console.log('err:', e.message));
  p.on('console', m => { if (m.type() === 'error') console.log('console:', m.text()); });
  fs.mkdirSync(outDir, { recursive: true });
  for (const n of names) {
    const [tn, add] = n.split(':');
    const outfit = tn.split('_')[0];
    const q = new URLSearchParams({ tex: `${dir}/${tn}.png`, parts: JSON.stringify(parts[outfit]) });
    if (add) q.set('addons', outfit);
    await p.goto(`http://localhost:8765/sheet.html?${q}`);
    await p.waitForFunction(() => window.DONE === true, null, { timeout: 60000 });
    await p.locator('canvas').screenshot({ path: `${outDir}/${n.replace(':', '-')}.png` });
  }
  await b.close();
})();
