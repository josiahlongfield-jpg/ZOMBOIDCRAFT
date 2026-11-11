const {chromium}=require('playwright'); const fs=require('fs');
const ease=x=>x<0?0:x>1?1:x*x*(3-2*x), lerp=(a,b,t)=>a+(b-a)*t;
(async()=>{const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
const p=await b.newPage({viewport:{width:1280,height:720}}); p.on('pageerror',e=>console.log('err',e.message));
await p.goto('http://localhost:8765/index.html?w=1280&h=720'); await p.waitForFunction('window.READY===true'); await p.waitForTimeout(2000);
const FPS=30, N=14*FPS; const caps=[];
for(let i=0;i<N;i++){ const t=i/FPS; let o={t};
 const wide={dist:4.0,ty:0.92,cy:1.15};
 if(t<3){ const k=ease(t/2.6); o={...o,...wide,yaw:lerp(-0.35,0,ease(t/3)),rez:lerp(-0.08,1.12,k),glow:0.7+0.3*k,cap:'Materialise (rez in)'}; }
 else if(t<8){ const k=ease((t-3)/5); o={...o,...wide,yaw:lerp(0,1.25,k),rez:1.12,cap:'Idle on the projector'}; }
 else if(t<11){ const k=ease((t-8)/3); o={...o,dist:lerp(1.6,1.25,k),ty:1.5,cy:1.55,yaw:lerp(0.55,0.15,k),rez:1.12,look:0.15,cap:'Close-up: code flow + scanlines from her own textures'}; }
 else { const k=ease((t-11)/2.3); o={...o,...wide,yaw:lerp(0.2,-0.1,k),rez:lerp(1.12,-0.08,k),glow:1-0.3*k*(0.5+0.5*Math.sin(t*40)),cap:'Dematerialise (rez out)'}; }
 const u=await p.evaluate(([v])=>window.frame(v.t,v),[o]);
 fs.writeFileSync(`/tmp/claude-0/web/frames/f${String(i).padStart(4,'0')}.png`,Buffer.from(u.split(',')[1],'base64')); caps.push(o.cap);
 if(i%60===0) console.log(i);
}
fs.writeFileSync('/tmp/claude-0/web/frames/caps.json',JSON.stringify(caps)); await b.close(); console.log('done');})();
