// Exports the Harvestman's game model from the preview page (preview/, the same files as the approved artifact).
//   node export.js 0.5 near.json && node export.js 0.25 far.json && python3 pack.py near.json far.json
// The first argument thins the meshes (the preview's #q setting); export.js.part is injected into the page before its
// offline check and leaves the triangles in window.__exp. Needs Playwright with Chromium.
const { chromium } = require('playwright');
const fs = require('fs'), path = require('path');
(async () => {
  const q = process.argv[2] || '0.5', out = process.argv[3] || 'near.json', dir = path.join(__dirname, 'preview');
  const order = ['r6_head.css.html', 'r6_body.html', 'r6_a.js', 'r6_b.js', 'r6_c.js', 'r7_room.js', 'r6_d.js'];
  let page = order.map(f => fs.readFileSync(path.join(dir, f), 'utf8')).join('');
  const at = page.indexOf('// offline check: run the walk');
  page = page.slice(0, at) + fs.readFileSync(path.join(__dirname, 'export.js.part'), 'utf8') + '\n' + page.slice(at);
  const tmp = path.join(require('os').tmpdir(), 'harvestman-export.html');
  fs.writeFileSync(tmp, page);
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await b.newPage();
  await p.goto('file://' + tmp + '#m=still&q=' + q);
  await p.waitForFunction(() => window.__probe, null, { timeout: 120000 });
  console.log(await p.evaluate(() => window.__probe));
  fs.writeFileSync(out, JSON.stringify(await p.evaluate(() => window.__exp)));
  await b.close();
})();
