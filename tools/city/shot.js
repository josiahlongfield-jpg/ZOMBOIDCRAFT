// Renders preview.html shots with headless Chromium: node shot.js URL_BASE OUT_PREFIX shot[,shot] [extra query]
const { chromium } = require('playwright');
(async () => {
  const [base, prefix, shots = 'street', extra = ''] = process.argv.slice(2);
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--js-flags=--max-old-space-size=8192'] });
  for (const shot of shots.split(',')) {
    const p = await b.newPage({ viewport: { width: 1440, height: 1080 } });
    p.on('pageerror', (e) => console.log('err:', e.message));
    p.on('console', (m) => { if (m.type() === 'error' || m.type() === 'warning') console.log('console:', m.text().slice(0, 400)); });
    const t = Date.now();
    await p.goto(`${base}/preview.html?shot=${shot}&${extra}`);
    await p.waitForFunction(() => window.DONE === true, null, { timeout: 900000, polling: 1000 });
    await p.screenshot({ path: `${prefix}-${shot}.png`, timeout: 600000 });
    console.log('shot', shot, ((Date.now() - t) / 1000).toFixed(0) + 's');
    await p.close();
  }
  await b.close();
})();
