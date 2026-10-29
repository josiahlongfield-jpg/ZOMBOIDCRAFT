const { chromium } = require('playwright'); const fs = require('fs');
(async () => {
  const b = await chromium.launch(); const p = await b.newPage();
  await p.goto('http://127.0.0.1:8765/export.html');
  await p.waitForFunction(() => document.title === 'done', null, { timeout: 300000 });
  const o = await p.evaluate(() => window.OUT);
  if (o.err) { console.log(o.err); process.exit(1); }
  fs.writeFileSync('spider.glb', Buffer.from(o.b64, 'base64'));
  console.log(o.size, o.clips.length, o.clips.join(','));
  await b.close();
})();
