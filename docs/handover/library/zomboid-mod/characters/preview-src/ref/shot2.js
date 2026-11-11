const { chromium } = require('playwright');
(async () => { const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 800, height: 600 } });
  p.on('console', m => console.log('console', m.text())); p.on('pageerror', e => console.log('ERR', e.message));
  await p.goto('file://' + __dirname + '/' + (process.argv[4] || 'ref.html') + '?' + process.argv[2], { waitUntil: 'commit' }); await p.waitForFunction(() => document.title === 'done', null, { timeout: 300000 });
  const d = await p.evaluate(() => document.getElementById('out').toDataURL('image/png'));
  require('fs').writeFileSync(process.argv[3], Buffer.from(d.split(',')[1], 'base64')); await b.close(); })();
