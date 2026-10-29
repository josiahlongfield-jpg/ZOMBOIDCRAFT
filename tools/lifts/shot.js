// Renders tools/lifts/preview.html shots: serve a folder holding preview.html (as lift.html), blocks.json (tools/city), node_modules/three and tex -> src/main/resources/assets/zomboidcraft/textures on :8790, then
//   node shot.js OUT_PREFIX landing,opening,inside,shaft,escalator
const { chromium } = require('playwright');
(async () => {
  const [prefix, shots] = process.argv.slice(2);
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  for (const shot of shots.split(',')) {
    const [name, q = ''] = shot.split('?');
    const p = await b.newPage({ viewport: { width: 1600, height: 1000 } });
    p.on('pageerror', (e) => console.log('err:', e.message));
    p.on('console', (m) => console.log('console:', m.text().slice(0, 300)));
    await p.goto(`http://localhost:8790/lift.html?shot=${name}&${q}`);
    await p.waitForFunction(() => window.DONE === true, null, { timeout: 120000 });
    await p.screenshot({ path: `${prefix}-${name}${q ? '-' + q.replace(/[=&]/g, '') : ''}.png` });
    await p.close();
  }
  await b.close();
})();
