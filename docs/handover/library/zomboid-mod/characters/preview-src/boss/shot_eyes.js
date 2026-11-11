const { chromium } = require('playwright');
(async () => {
  const b = await chromium.launch();
  const url = 'file://' + process.argv[2];
  const base = [0.35, 11.5];
  const h = base[0], d = base[1];
  const views = [['eyes', h - 0.5, 0.12]];
  for (const [name, yaw, pitch] of views) {
    const p = await b.newPage({ viewport: { width: 1600, height: 900 } });
    p.on('pageerror', e => console.log('ERR', e.message));
    await p.goto(url + `#v=hunt&m=still&p=0&t=0&a=free&yaw=${yaw}&pitch=${pitch}&tx=-3.3&ty=3.0&tz=0&d=${4.2.toFixed(2)}`);
    await p.waitForFunction(() => document.title === 'done', null, { timeout: 90000 });
    await p.evaluate(() => { document.querySelectorAll('body *').forEach(e => { if (e.tagName !== 'CANVAS' && !e.querySelector('canvas')) e.style.visibility = 'hidden'; }); });
    const c = await p.$('canvas');
    await c.screenshot({ path: name + '.png' });
    console.log(name);
    await p.close();
  }
  await b.close();
})();
