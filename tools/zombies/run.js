const { chromium } = require('playwright'); const fs = require('fs');
(async () => { const b = await chromium.launch(); 
  for (const o of process.argv.slice(2)) {
    const p = await b.newPage(); p.on('pageerror', e => console.log('ERR', e.message)); p.on('console', m => { if (!m.text().includes('willRead')) console.log(m.text()); });
    await p.goto('file://' + __dirname + '/export.html?o=' + o, { waitUntil: 'commit' }); await p.waitForFunction(() => document.title === 'done', null, { timeout: 300000 });
    const r = await p.evaluate(() => window.__RESULT); const png = r.atlas.split(',')[1]; delete r.atlas;
    fs.writeFileSync(__dirname + '/out/' + o + '.json', JSON.stringify(r)); fs.writeFileSync(__dirname + '/out/' + o + '.png', Buffer.from(png, 'base64'));
    console.log(o, JSON.stringify(r.stats), r.ah, 'body', r.body.length); await p.close(); }
  await b.close(); })();
