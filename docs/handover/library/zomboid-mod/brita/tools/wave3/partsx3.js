const {chromium}=require('playwright');
(async()=>{const b=await chromium.launch({args:['--use-gl=swiftshader','--enable-webgl','--ignore-gpu-blocklist']});const p=await b.newPage();
const errs=[];p.on('pageerror',e=>errs.push(e.message));
await p.goto('http://localhost:8763/brita-review.html');await p.waitForTimeout(5000);
const out=await p.evaluate(async()=>{const r={};
  for(let i=0;i<D.guns.length;i++){await show(i);await new Promise(z=>setTimeout(z,150));
    const g=D.guns[i]; r[g.id]={upright:!!g.upright,parts:partsGroup.children.map(c=>({matrix:Array.from(c.matrix.elements)})),fitted:fitted(g)};}
  return r;});
require('fs').writeFileSync(__dirname+'/parts_export3.json',JSON.stringify(out,null,1));
console.log(Object.entries(out).map(([k,v])=>k+':'+v.parts.length+' '+JSON.stringify(v.fitted)).join('\n'));console.log(errs.slice(0,5));await b.close()})();
