const {chromium}=require('playwright'); const fs=require('fs');
const G=JSON.parse(fs.readFileSync('/tmp/claude-0/web/gface.json'));
const OUT=process.argv[2], A0=+process.argv[3], A1=+process.argv[4];
const IDLE='vin_m90_solo_defend_idle_var1'; const cl=x=>Math.max(0,Math.min(1,x));
const ORDER=['over_here','up_here','wait_chief','you_think','you_cant','just_wanna','pick_up','reuse'].map(k=>'vin_m90_solo_defend_'+k);
const plan=[]; let g=0; for(const k of ORDER){ const F=G[k].F; for(let i=-18;i<F+14;i++) plan.push({k,i,g:g++}); }
if(process.argv[5]==='seg'){ const seg=[]; let s=0; for(const k of ORDER){ const n=G[k].F+32; seg.push([k.replace('vin_m90_solo_defend_',''),G[k].line,s,s+n-1]); s+=n; } fs.writeFileSync('/tmp/claude-0/web/greel_seg.json',JSON.stringify(seg)); console.log(JSON.stringify(seg)); process.exit(0); }
(async()=>{const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
const p=await b.newPage({viewport:{width:1280,height:720}}); p.on('pageerror',e=>console.log('err',e.message));
await p.goto('http://localhost:8765/index.html?w=1280&h=720&bg=0b1220&full=1&lk=1.2,0.5,0.6&ex=1.15&grad=0.7'); await p.waitForFunction('window.READY===true');
await p.evaluate(()=>window.loadAnim('anim_v7.json')); await p.waitForTimeout(1500);
fs.mkdirSync(OUT,{recursive:true});
for(const {k,i,g} of plan){ if(g<A0||g>=A1) continue; const D=G[k], F=D.F, fi=Math.max(0,Math.min(F-1,i));
  const CU=process.argv[5]==='cu'; const o={t:2.2+g/30, yaw:CU?0.12:0.22, dist:CU?0.72:2.15, ty:CU?1.6:1.3, cy:CU?1.62:1.42, anim:{shot:IDLE,frame:(g+100)%685}, anim2:{shot:k,frame:fi}};
  o.mix=Math.min(cl((i+12)/12),cl((F+12-i)/12)); o.mix=o.mix*o.mix*(3-2*o.mix);
  if(i>=0&&i<F){ const face={}; for(const c in D.face) face[c]=D.face[c][i]; o.face=face; o.jaw=D.jaw[i]; for(const h in D.head) o[h]=D.head[h][i]; }
  const u=await p.evaluate(([v])=>window.frame(v.t,v),[o]);
  fs.writeFileSync(`${OUT}/f${String(g).padStart(4,'0')}.png`,Buffer.from(u.split(',')[1],'base64')); }
await b.close(); console.log('done',plan.length);})();
