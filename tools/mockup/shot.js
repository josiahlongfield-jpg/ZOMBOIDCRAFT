const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await b.newPage({ viewport: { width: 1600, height: 900 } });
  p.on('console', m => console.log('console:', m.text()));
  p.on('pageerror', e => console.log('err:', e.message));
  for (const mode of ['dusk', 'night']) {
    await p.goto(`http://localhost:8765/index.html?mode=${mode}`);
    await p.waitForFunction(() => window.DONE === true, null, { timeout: 120000 });
    await p.waitForTimeout(500);
    await p.screenshot({ path: `${mode}.png` });
    console.log('shot', mode);
  }
  await b.close();
})();
