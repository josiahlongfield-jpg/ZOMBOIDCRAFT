const {chromium}=require('playwright'); const fs=require('fs');
const OUT=process.argv[2], A0=+process.argv[3], A1=+process.argv[4];
const SEG=[['any:idle',120,i=>i%60],['vin_m90_solo_defend_idle_var1',300,i=>i],[null,120,i=>i]];
(async()=>{const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
const p=await b.newPage({viewport:{width:720,height:720}}); p.on('pageerror',e=>console.log('err',e.message));
await p.goto('http://localhost:8765/index.html?w=720&h=720&bg=0b1220&full=1&lk=1.2,0.5,0.6&ex=1.15&grad=0.7'); await p.waitForFunction('window.READY===true');
await p.evaluate(()=>Promise.all([window.loadAnim('anim_v7.json'),window.loadAnim('anim_base.json')])); await p.waitForTimeout(1500);
fs.mkdirSync(OUT,{recursive:true}); let g=0;
for(const [shot,n,fn] of SEG){ for(let i=0;i<n;i++,g++){ if(g<A0||g>=A1) continue;
  const o={t:2.2+i/30, yaw:0.3, dist:3.1, ty:0.98, cy:1.15}; if(shot) o.anim={shot,frame:fn(i)};
  const u=await p.evaluate(([v])=>window.frame(v.t,v),[o]);
  fs.writeFileSync(`${OUT}/f${String(g).padStart(4,'0')}.png`,Buffer.from(u.split(',')[1],'base64')); } }
await b.close(); console.log('done',g);})();
