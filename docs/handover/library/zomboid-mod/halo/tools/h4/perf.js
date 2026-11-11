const {chromium}=require('playwright'); const fs=require('fs');
const OUT=process.argv[2]; const a3=process.argv[3]; const plan=JSON.parse(a3.startsWith('@')?fs.readFileSync(a3.slice(1)):a3); const body=process.argv[4]||'body_m093.json';
(async()=>{const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
const p=await b.newPage({viewport:{width:1280,height:720}}); p.on('pageerror',e=>console.log('err',e.message)); p.on('console',m=>{if(m.text().includes('fail'))console.log(m.text())});
await p.goto('http://localhost:8765/index.html?w=1280&h=720&bg=0b1220'); await p.waitForFunction('window.READY===true'); await p.evaluate(f=>window.loadAnim(f),body); await p.waitForTimeout(2500);
fs.mkdirSync(OUT,{recursive:true});
for(let i=0;i<plan.length;i++){ const o=plan[i];
 const u=await p.evaluate(([v])=>window.frame(v.t||1,v),[o]);
 fs.writeFileSync(`${OUT}/f${String(o.i??i).padStart(4,'0')}.png`,Buffer.from(u.split(',')[1],'base64')); if(i%60===0) console.log(i); }
await b.close(); console.log('done');})();
