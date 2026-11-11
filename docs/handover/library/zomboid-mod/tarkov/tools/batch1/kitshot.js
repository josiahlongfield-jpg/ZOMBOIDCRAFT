// Side / 3-4 previews of one exported batch-1 kit: node kitshot.js b1_pm out_prefix
const {chromium}=require('playwright'); const http=require('http'),fs=require('fs'),path=require('path');
const root=__dirname; const PORT=8770+Math.floor(Math.random()*200);
const srv=http.createServer((q,s)=>{const f=path.join(root,decodeURIComponent(q.url.split('?')[0]));fs.readFile(f,(e,d)=>{if(e){s.writeHead(404);s.end();return;} s.writeHead(200,{'Content-Type':f.endsWith('.html')?'text/html':f.endsWith('.js')?'text/javascript':'application/octet-stream'});s.end(d);});}).listen(PORT);
(async()=>{const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});const p=await b.newPage({viewport:{width:1280,height:720}});
p.on('pageerror',e=>console.log('ERR',e.message));
await p.goto(`http://localhost:${PORT}/index.html`); await p.waitForFunction('window.ready');
const f=process.argv[2], out=process.argv[3];
const sz=await p.evaluate(f=>setupStatic(f,1.0),f); console.log('size',JSON.stringify(sz));
await p.waitForTimeout(2500);
const views=JSON.parse(process.argv[4]||'[["side",[1,0,0]],["other",[-1,0,0]],["q",[0.8,0.35,0.6]]]');
for(const [nm,d] of views){
  await p.evaluate(d=>{ const T=THREE, m=window.STATIC; const bb=new T.Box3().setFromObject(m); const c=bb.getCenter(new T.Vector3());
    const cam=window.CAM||null; const s=window.SCENECAM; }, d);
  await p.evaluate(d=>window.lookStatic(d),d); await p.waitForTimeout(400);
  await p.locator('canvas').screenshot({path:`${out}_${nm}.png`});
}
await b.close(); srv.close();})();
