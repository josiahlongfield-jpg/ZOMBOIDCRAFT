// node run.js data.json outdir [stride] [camjson]
const { chromium } = require('playwright'); const fs = require('fs'), http = require('http'), path = require('path');
const D = __dirname, FILE = process.argv[2], OUT = process.argv[3], STRIDE = +(process.argv[4] || 1), CAM = process.argv[5] ? JSON.parse(process.argv[5]) : null;
const srv = http.createServer((q, res) => { const p = path.resolve(D, decodeURIComponent(q.url.split('?')[0]).replace(/^\/+/, '') || 'page.html');
  fs.readFile(p, (e, b) => { if (e) { res.writeHead(404); return res.end(); } res.writeHead(200, { 'content-type': p.endsWith('.html') ? 'text/html' : p.endsWith('.png') ? 'image/png' : 'application/json' }); res.end(b); }); }).listen(8771);
(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const b = await chromium.launch({ args: ['--use-gl=swiftshader', '--enable-unsafe-swiftshader'] });
  const pg = await b.newPage({ viewport: { width: 960, height: 540 } });
  pg.on('pageerror', e => console.error('ERR', e.message)); pg.on('console', m => console.log('LOG', m.text()));
  await pg.goto('http://localhost:8771/page.html'); await pg.waitForFunction(() => window.ready, null, { timeout: 120000 });
  const n = await pg.evaluate(f => window.load(f), FILE);
  const cv = await pg.$('canvas');
  for (let i = 0; i < n; i += STRIDE) for (const v of ['fp', 'tp']) {
    await pg.evaluate(([v, i, c]) => window.draw(v, i, c), [v, i, CAM]);
    await cv.screenshot({ path: path.join(OUT, `${v}_${String(i).padStart(4, '0')}.png`) });
  }
  await b.close(); srv.close();
})().catch(e => { console.error(e); process.exit(1); });
