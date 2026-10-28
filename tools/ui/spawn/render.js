// node render.js county.svg county.png: draws the map SVG to a 768 px PNG with the pre-installed Playwright.
const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const svg = fs.readFileSync(process.argv[2], 'utf8').replace(/width="\d+" height="\d+"/, 'width="768" height="768"');
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 768, height: 768 } });
  await page.setContent('<html><body style="margin:0">' + svg + '</body></html>');
  await page.screenshot({ path: process.argv[3] });
  await browser.close();
})();
