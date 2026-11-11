const {chromium}=require('playwright'); const http=require('http'),fs=require('fs'),path=require('path');
const root=__dirname; const port=8770+Math.floor(Math.random()*100);
const srv=http.createServer((q,s)=>{const f=path.join(root,decodeURIComponent(q.url.split('?')[0]));fs.readFile(f,(e,d)=>{if(e){s.writeHead(404);s.end();return;} s.writeHead(200,{'Content-Type':f.endsWith('.html')?'text/html':f.endsWith('.js')?'text/javascript':'application/octet-stream'});s.end(d);});}).listen(port);
const mode=process.argv[2], out=process.argv[3]; fs.mkdirSync(out,{recursive:true});
(async()=>{const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
const p=await b.newPage({viewport:{width:1280,height:720}}); p.on('pageerror',e=>console.log('ERR',e.message));
await p.goto(`http://localhost:${port}/index.html`); await p.waitForFunction('window.ready');
let n=0; const snap=async()=>{ await p.locator('canvas').screenshot({path:`${out}/f${String(n++).padStart(4,'0')}.png`}); };
if(mode.startsWith('turn')){ // turn-plain / turn-kit / turn-brita
  const v=mode.split('-')[1];
  if(v==='brita'){ await p.evaluate(()=>setup('plain')); await p.evaluate(()=>{pose(null,0)}); const dir=await p.evaluate(()=>gunDir());
     await p.evaluate(()=>{ W.root.visible=false; }); await p.evaluate(()=>setupStatic('brita',0.84));
     await p.evaluate((d)=>{ const V=W.root.position.constructor; STATIC.quaternion.setFromAxisAngle(new V(0,1,0), Math.atan2(-d[0],-d[2])); },dir);
     await p.waitForTimeout(1500);
     for(let i=0;i<180;i++){ await p.evaluate(a=>turnObj(STATIC,a,1.8),Math.PI*2*i/180+0.25); if(i==0){await p.waitForTimeout(500); await p.evaluate(a=>turnObj(STATIC,a,1.8),0.25);} await snap(); }
  } else { await p.evaluate(v=>setup(v),v); await p.evaluate(()=>{pose(null,0)}); await p.waitForTimeout(1500);
     for(let i=0;i<180;i++){ await p.evaluate(a=>{ const g=W.root; turnObj(g,a,1.8); },Math.PI*2*i/180+0.25); if(i==0){await p.waitForTimeout(500); await p.evaluate(a=>turnObj(W.root,a,1.8),0.25);} await snap(); } }
} else { // fp-plain / fp-kit
  const v=mode.split('-')[1]; await p.evaluate(v=>setup(v),v); if(process.env.ZARMS) await p.evaluate(async()=>{await zinit()}); await p.evaluate((Z)=>{window.IK=false; window.ALLLAYERS=true; window.ZARMS=Z; window.TRUECAM=true; window.TPITCH=-0.18; window.FOV=62; pose(null,0); view('fp')},!!process.env.ZARMS); await p.waitForTimeout(1500);
  const seq=JSON.parse(fs.readFileSync(path.join(root,process.env.SEQ||'seq.json')));
  for(const [clip,f0,f1] of seq){ for(let f=f0; f<=f1; f++){ await p.evaluate(([c,f])=>{pose(c,f); view('fp')},[clip,f]); await snap(); } }
}
await b.close(); srv.close();})();
