const {chromium}=require('playwright'); const fs=require('fs');
const ease=x=>x<0?0:x>1?1:x*x*(3-2*x), lerp=(a,b,t)=>a+(b-a)*t;
(async()=>{const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
const p=await b.newPage({viewport:{width:1280,height:720}}); p.on('pageerror',e=>console.log('err',e.message));
await p.goto('http://localhost:8765/index.html?w=1280&h=720&full=1&lk=1.2,0.5,0.6&ex=1.15&grad=0.7'); await p.waitForFunction('window.READY===true'); await p.waitForTimeout(2000);
const FPS=30, N=13*FPS; const caps=[];
for(let i=0;i<N;i++){ const t=i/FPS; let o={t};
 const wide={dist:3.6,ty:0.92,cy:1.1};
 if(t<2.5){ const k=ease(t/2.2); o={...o,...wide,yaw:-0.3,rez:lerp(-0.08,1.12,k),glow:0.7+0.3*k,cap:'Materialise'}; }
 else if(t<8.5){ const k=(t-2.5)/6; o={...o,...wide,yaw:-0.3+k*Math.PI*2,rez:1.12,cap:'Full turn: Halo 4 shader recipe on every part'}; }
 else { const k=ease((t-8.5)/4.5); o={...o,dist:lerp(1.1,0.6,k),ty:lerp(1.45,1.55,k),cy:lerp(1.47,1.58,k),yaw:lerp(0.5,0.15,k),rez:1.12,nod:-0.08*k,face:{blinkL:0.15,blinkR:0.15},cap:'Neck and face close-up'}; }
 const u=await p.evaluate(([v])=>window.frame(v.t,v),[o]);
 fs.writeFileSync(`/tmp/claude-0/web/ffull3/f${String(i).padStart(4,'0')}.png`,Buffer.from(u.split(',')[1],'base64')); caps.push(o.cap);
 if(i%60===0) console.log(i);
}
fs.writeFileSync('/tmp/claude-0/web/ffull3/caps.json',JSON.stringify(caps)); await b.close(); console.log('done');})();
