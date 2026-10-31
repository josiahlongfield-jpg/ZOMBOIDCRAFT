const { chromium } = require('playwright'); const fs = require('fs');
(async () => {
  const b = await chromium.launch(); const p = await b.newPage();
  await p.goto('http://127.0.0.1:8765/bake.html');
  await p.waitForFunction(() => document.title === 'done', null, { timeout: 300000 });
  const o = await p.evaluate(() => window.OUT);
  if (o.err) { console.log(o.err); process.exit(1); }
  fs.writeFileSync('bake.json', JSON.stringify(o));
  console.log(JSON.stringify({ box: o.box, head: o.head, pelvis: o.pelvis, scale: o.scale, leftIsL: o.leftIsL, clips: o.clips.length }));
  await b.close();
})();
