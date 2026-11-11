const {chromium}=require('playwright'); const http=require('http'),fs=require('fs'),path=require('path');
const root=__dirname; const PORT=+(process.env.PORT||8952);
const srv=http.createServer((q,s)=>{const f=path.join(root,decodeURIComponent(q.url.split('?')[0]));fs.readFile(f,(e,d)=>{if(e){s.writeHead(404);s.end();return;} s.writeHead(200,{'Content-Type':f.endsWith('.html')?'text/html':f.endsWith('.js')?'text/javascript':'application/octet-stream'});s.end(d);});}).listen(PORT);
(async()=>{const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});const p=await b.newPage({viewport:{width:1200,height:600}});
p.on('pageerror',e=>console.log('ERR',e.message));
await p.goto(`http://localhost:${PORT}/zskfit.html`); await p.waitForFunction('window.ready');
const jobs=JSON.parse(fs.readFileSync(process.argv[2]));
for(const j of jobs){ await p.evaluate(a=>show(...a),[j.dir,j.az,j.el]); await p.waitForTimeout(100); await p.locator('canvas').screenshot({path:j.out}); console.log(j.out);}
await b.close(); srv.close();})();
