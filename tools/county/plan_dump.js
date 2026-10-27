// Reads the county plan page (the Artifact's HTML, saved in project files as
// zomboid-mod/plan/grayhaven-plan-draftN.html) and writes its data as JSON for plan_export.py.
//
//   node tools/county/plan_dump.js PLAN.html OUT.json
//
// The page keeps everything inside one closure, so a line is slipped in before it closes that
// copies each table onto window. Needs the preinstalled Playwright and Chromium.
const fs = require('fs');
const path = require('path');
const os = require('os');
const { chromium } = require('playwright');

const NAMES = ['B', 'COAST', 'CORE', 'DISTRICTS', 'FARM', 'FENCE_LINE', 'FERRIES', 'FOREST', 'ISLANDS', 'ISLE', 'LAKES',
  'MARSH', 'MIL_AREAS', 'MOUNTAINS', 'NAVY_LINE', 'P', 'PIERS', 'RAIL', 'RIVERS', 'RIVER_S', 'ROADS', 'SEA', 'SEA_S', 'SHIPS',
  'SPRAWL', 'SUBWAY', 'TOWNS_URBAN', 'WALL', 'WALL_S', 'WRECKS'];

(async () => {
  const [src, out] = process.argv.slice(2);
  let html = fs.readFileSync(src, 'utf8');
  const end = html.lastIndexOf('})();');
  if (end < 0) throw new Error('no closing })(); in ' + src);
  const grab = 'window.__plan = {};' + NAMES.map(n => `try { window.__plan['${n}'] = ${n}; } catch (e) {}`).join('');
  html = html.slice(0, end) + grab + html.slice(end);
  const tmp = path.join(os.tmpdir(), 'plan_dump_' + process.pid + '.html');
  fs.writeFileSync(tmp, html);
  const browser = await chromium.launch();
  const page = await browser.newPage();
  page.on('pageerror', e => console.error('page error:', e.message));
  await page.goto('file://' + tmp);
  await page.waitForTimeout(1500);
  const plan = await page.evaluate(() => JSON.parse(JSON.stringify(window.__plan || {})));
  await browser.close();
  fs.unlinkSync(tmp);
  const missing = NAMES.filter(n => !(n in plan));
  if (missing.length) throw new Error('missing from the page: ' + missing.join(', '));
  fs.writeFileSync(out, JSON.stringify(plan));
  console.log('wrote', out, Object.keys(plan).length, 'tables,', plan.P.length, 'places');
})();
