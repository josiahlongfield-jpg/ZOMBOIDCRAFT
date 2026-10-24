const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await b.newPage({ viewport: { width: 1600, height: 900 } });
  p.on('pageerror', e => console.log('err:', e.message));
  p.on('console', m => { if (m.type() === 'error') console.log('console:', m.text()); });
  for (const shot of ['wide', 'close']) {
    await p.goto(`http://localhost:8765/photon.html?shot=${shot}`);
    await p.waitForFunction(() => window.DONE === true, null, { timeout: 180000 });
    await p.screenshot({ path: `${__dirname}/hd-${shot}.png` });
    console.log('shot', shot);
  }
  await b.close();
})();
