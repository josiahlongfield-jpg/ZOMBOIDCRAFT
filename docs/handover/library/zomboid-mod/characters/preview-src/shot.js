const { chromium } = require('playwright');
(async () => { const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 2048, height: 1024 } });
  p.on('console', m => console.log('console', m.text())); p.on('pageerror', e => console.log('ERR', e.message));
  await p.goto('file://' + process.argv[2]); await p.waitForFunction(() => document.title === 'done', null, { timeout: 60000 });
  await p.screenshot({ path: process.argv[3] }); await b.close(); })();
