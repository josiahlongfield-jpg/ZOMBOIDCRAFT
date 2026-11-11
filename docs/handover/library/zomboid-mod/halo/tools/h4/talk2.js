const {chromium}=require('playwright'); const fs=require('fs');
const T=JSON.parse(fs.readFileSync('/tmp/claude-0/web/talk2.json'));
const ease=x=>x<0?0:x>1?1:x*x*(3-2*x), lerp=(a,b,t)=>a+(b-a)*t;
const OUT=process.argv[2]||'/tmp/claude-0/web/tframes'; const only=process.argv[3]?JSON.parse(process.argv[3]):null;
(async()=>{const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
const p=await b.newPage({viewport:{width:1280,height:720}}); p.on('pageerror',e=>console.log('err',e.message));
await p.goto('http://localhost:8765/index.html?w=1280&h=720&bg=0b1220'); await p.waitForFunction('window.READY===true'); await p.waitForTimeout(2000);
fs.mkdirSync(OUT,{recursive:true});
const FPS=30, N=T.n;
for(const i of (only||[...Array(N).keys()])){ const t=i/FPS; const k=ease(t/1.4);
 const face={}; for(const c in T.face) face[c]=T.face[c][i];
 const o={t, rez:lerp(-0.08,1.12,k), glow:0.7+0.3*k,
   yaw:lerp(0.32,0.1,ease(t/12)), dist:lerp(0.9,0.62,ease(t/12)), ty:1.6, cy:1.63,
   jaw:T.jaw[i], nod:T.nod[i], tilt:T.tilt[i], look:T.look[i], eyeX:T.eyeX[i], eyeY:T.eyeY[i], face};
 const u=await p.evaluate(([v])=>window.frame(v.t,v),[o]);
 fs.writeFileSync(`${OUT}/f${String(i).padStart(4,'0')}.png`,Buffer.from(u.split(',')[1],'base64'));
 if(i%60===0) console.log(i);
}
await b.close(); console.log('done');})();
