// FP video frames of a batch-1 gun: GUN= ANIM= BASE= SEQ=file node vid2.js OUTDIR
const {chromium}=require('playwright'); const http=require('http'),fs=require('fs'),path=require('path');
const root=__dirname; const PORT=8700+Math.floor(Math.random()*60);
const srv=http.createServer((q,s)=>{const f=path.join(root,decodeURIComponent(q.url.split('?')[0]));fs.readFile(f,(e,d)=>{if(e){s.writeHead(404);s.end();return;} s.writeHead(200,{'Content-Type':f.endsWith('.html')?'text/html':f.endsWith('.js')?'text/javascript':'application/octet-stream'});s.end(d);});}).listen(PORT);
const out=process.argv[2]; fs.mkdirSync(out,{recursive:true});
(async()=>{const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});const p=await b.newPage({viewport:{width:1280,height:720}});
p.on('pageerror',e=>console.log('ERR',e.message));
await p.goto(`http://localhost:${PORT}/index.html`); await p.waitForFunction('window.ready');
await p.evaluate(([a,bc])=>{window.ANIMFILE=a; window.BASECLIP=bc;},[process.env.ANIM,process.env.BASE]);
await p.evaluate(g=>setup(g),process.env.GUN);
await p.evaluate(()=>{window.IK=false; window.ALLLAYERS=true; window.TRUECAM=true; window.TPITCH=-0.18; window.FOV=62; pose(null,0); view('fp');}); await p.waitForTimeout(1500);
let n=0; for(const [clip,f0,f1] of JSON.parse(fs.readFileSync(process.env.SEQ))){ for(let f=f0; f<=f1; f++){ await p.evaluate(([c,f])=>{pose(c,f); view('fp')},[clip,f]); await p.locator('canvas').screenshot({path:`${out}/f${String(n++).padStart(4,'0')}.png`}); } }
console.log('frames',n); await b.close(); srv.close();})();
