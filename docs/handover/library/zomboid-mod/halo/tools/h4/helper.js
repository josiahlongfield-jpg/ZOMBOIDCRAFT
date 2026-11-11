const {chromium}=require('playwright'); const fs=require('fs');
const T=JSON.parse(fs.readFileSync('/tmp/claude-0/web/talk2.json'));
const ease=x=>x<0?0:x>1?1:x*x*(3-2*x), lerp=(a,b,t)=>a+(b-a)*t, cl=x=>Math.max(0,Math.min(1,x));
const OUT=process.argv[2], A0=+process.argv[3], A1=+process.argv[4];
const IDLE='vin_m90_solo_defend_idle_var1', TURN='any:turn_left_90', GEST='vin_m90_solo_defend_you_think';
const TS=150, TT=192, N=TT+T.n+30;
(async()=>{const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
const p=await b.newPage({viewport:{width:1280,height:720}}); p.on('pageerror',e=>console.log('err',e.message));
await p.goto('http://localhost:8765/index.html?w=1280&h=720&bg=0b1220&full=1&lk=1.2,0.5,0.6&ex=1.15&grad=0.7'); await p.waitForFunction('window.READY===true');
await p.evaluate(()=>Promise.all([window.loadAnim('anim_v7.json'),window.loadAnim('anim_base.json')])); await p.waitForTimeout(1500);
fs.mkdirSync(OUT,{recursive:true});
for(let i=A0;i<Math.min(A1,N);i++){ const t=i/30; const k=ease(t/1.4);
 const o={t, rez:lerp(-0.08,1.12,k), glow:0.7+0.3*k, anim:{shot:IDLE,frame:(i+40)%685}};
 const tu=cl((i-TS)/42); o.rot=-Math.PI/2*(1-ease(tu));
 if(i>=TS&&i<TT){ o.anim2={shot:TURN,frame:i-TS}; o.mix=Math.min(cl((i-TS)/8),cl((TT-i)/8)); }
 const c=ease(cl((i-110)/200)); o.yaw=0.15; o.dist=lerp(3.3,1.9,c); o.ty=lerp(0.95,1.36,c); o.cy=lerp(1.05,1.45,c);
 const kk=i-TT;
 if(kk>=0&&kk<T.n){ const face={}; for(const ch in T.face) face[ch]=T.face[ch][kk]; Object.assign(o,{jaw:T.jaw[kk],nod:T.nod[kk],tilt:T.tilt[kk],look:T.look[kk],eyeX:T.eyeX[kk],eyeY:T.eyeY[kk],face});
   if(kk>=196&&kk<284){ o.anim2={shot:GEST,frame:kk-196+4}; o.mix=Math.min(cl((kk-196)/12),cl((284-kk)/12)); } }
 const u=await p.evaluate(([v])=>window.frame(v.t,v),[o]);
 fs.writeFileSync(`${OUT}/f${String(i).padStart(4,'0')}.png`,Buffer.from(u.split(',')[1],'base64'));
 if(i%50===0) console.log(i);
}
await b.close(); console.log('done',N);})();
