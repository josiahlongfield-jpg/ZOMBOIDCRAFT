// FP stills of a batch-1 gun: GUN=b1_pm.json ANIM=anims_pm.json BASE=pm_idle_armed node fp2.js '[[clip,frame,outpath],...]'
const {chromium}=require('playwright'); const http=require('http'),fs=require('fs'),path=require('path');
const root=__dirname; const PORT=8800+Math.floor(Math.random()*150);
const srv=http.createServer((q,s)=>{const f=path.join(root,decodeURIComponent(q.url.split('?')[0]));fs.readFile(f,(e,d)=>{if(e){s.writeHead(404);s.end();return;} s.writeHead(200,{'Content-Type':f.endsWith('.html')?'text/html':f.endsWith('.js')?'text/javascript':'application/octet-stream'});s.end(d);});}).listen(PORT);
(async()=>{const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});const p=await b.newPage({viewport:{width:1280,height:720}});
p.on('pageerror',e=>console.log('ERR',e.message));
await p.goto(`http://localhost:${PORT}/index.html`); await p.waitForFunction('window.ready');
await p.evaluate(([a,bc])=>{window.ANIMFILE=a; window.BASECLIP=bc;},[process.env.ANIM,process.env.BASE]);
console.log(JSON.stringify(await p.evaluate(v=>setup(v),process.env.GUN)));
await p.evaluate(()=>{window.IK=false; window.ALLLAYERS=true; window.TRUECAM=true; window.TPITCH=-0.18; window.FOV=62;}); await p.waitForTimeout(1500);
for(const [clip,frame,name] of JSON.parse(process.argv[2])){ await p.evaluate(([c,f])=>{pose(c,f);view('fp')},[clip,frame]); await p.waitForTimeout(80); await p.evaluate(([c,f])=>{pose(c,f);view('fp')},[clip,frame]); await p.locator('canvas').screenshot({path:name}); }
await b.close(); srv.close();})();
