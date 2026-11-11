const {chromium}=require('playwright'); const http=require('http'),fs=require('fs'),path=require('path');
const root=__dirname; const srv=http.createServer((q,s)=>{const f=path.join(root,decodeURIComponent(q.url.split('?')[0]));fs.readFile(f,(e,d)=>{if(e){s.writeHead(404);s.end();return;} s.writeHead(200,{'Content-Type':f.endsWith('.html')?'text/html':f.endsWith('.js')?'text/javascript':'application/octet-stream'});s.end(d);});}).listen(8766);
(async()=>{const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});const p=await b.newPage({viewport:{width:1280,height:720}});
p.on('pageerror',e=>console.log('ERR',e.message)); p.on('console',m=>console.log('PAGE',m.text()));
await p.goto('http://localhost:8766/index.html'); await p.waitForFunction('window.ready');
const v=process.argv[2]; await p.evaluate(v=>setup(v),v); await p.waitForTimeout(1500); if(process.env.NOIK) await p.evaluate(()=>{window.IK=false}); if(process.env.ALL) await p.evaluate(()=>{window.ALLLAYERS=true}); if(process.env.ZARMS) await p.evaluate(async()=>{await zinit(); window.ZARMS=true}); if(process.env.TRUECAM) await p.evaluate(([z,f])=>{window.TRUECAM=true; window.TPITCH=+z; window.FOV=+f},[process.env.TRUECAM,process.env.FOV||62]);
const shots=JSON.parse(process.argv[3]);
for(const [clip,frame,name] of shots){ await p.evaluate(([c,f])=>{pose(c,f);view('fp')},[clip,frame]); await p.waitForTimeout(100); await p.evaluate(([c,f])=>{pose(c,f);view('fp')},[clip,frame]); await p.locator('canvas').screenshot({path:`/tmp/claude-0/tk/${name}.png`}); }
await b.close(); srv.close();})();
