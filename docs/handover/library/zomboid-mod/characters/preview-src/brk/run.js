const { chromium } = require('playwright');
(async () => { const b = await chromium.launch(); const p = await b.newPage();
  p.on('console', m => { if (!/willRead/.test(m.text())) console.log(m.text()); }); p.on('pageerror', e => console.log('PAGEERR', e.message));
  await p.goto('file://' + process.argv[2]); await p.waitForFunction(() => document.title === 'done', null, { timeout: 300000 }); await b.close(); })();
