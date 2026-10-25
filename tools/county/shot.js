// Renders view.html shots with headless Chromium: node shot.js URL_BASE OUT_PREFIX name=query [name=query ...]
// Each query is view.html's (mesh=DIR&cam=...); the shot is saved as OUT_PREFIX-name.png.
const { chromium } = require('playwright');
(async () => {
  const [base, prefix, ...shots] = process.argv.slice(2);
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--js-flags=--max-old-space-size=8192'] });
  for (const s of shots) {
    const at = s.indexOf('=');
    const name = s.slice(0, at), query = s.slice(at + 1);
    const w = /(?:^|&)w=(\d+)/.exec(query), h = /(?:^|&)h=(\d+)/.exec(query);
    const p = await b.newPage({ viewport: { width: w ? +w[1] : 1440, height: h ? +h[1] : 1080 } });
    p.on('pageerror', (e) => console.log('err:', e.message));
    p.on('console', (m) => { if (m.type() === 'error' || m.type() === 'warning') console.log('console:', m.text().slice(0, 400)); });
    const t = Date.now();
    await p.goto(`${base}/view.html?${query}`);
    await p.waitForFunction(() => window.DONE === true, null, { timeout: 900000, polling: 500 });
    await p.screenshot({ path: `${prefix}-${name}.png`, timeout: 600000 });
    console.log('shot', name, ((Date.now() - t) / 1000).toFixed(0) + 's');
    await p.close();
  }
  await b.close();
})();
