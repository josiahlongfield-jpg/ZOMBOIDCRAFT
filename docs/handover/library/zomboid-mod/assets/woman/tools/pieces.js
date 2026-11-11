const {chromium}=require('playwright'); const fs=require('fs'); const http=require('http'); const path=require('path');
const dir=__dirname; const types={js:'text/javascript',html:'text/html',jpg:'image/jpeg',webp:'image/webp',txt:'text/plain'};
const srv=http.createServer((q,r)=>{let f=path.join(dir,decodeURIComponent(q.url.split('?')[0])); try{f=fs.realpathSync(f)}catch(e){} if(!fs.existsSync(f)||fs.statSync(f).isDirectory()){r.writeHead(404);r.end();return;} r.writeHead(200,{'Content-Type':types[f.split('.').pop()]||'application/octet-stream'}); fs.createReadStream(f).pipe(r);}).listen(8779, async()=>{
 const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--js-flags=--max-old-space-size=4000']});
 const p=await b.newPage({viewport:{width:600,height:700}}); p.on('pageerror',e=>console.log('pageerror',e.message)); p.on('console',m=>console.log('console',m.text()));
 await p.goto('http://localhost:8779/pieces.html'); await p.waitForFunction('window.DONE',null,{timeout:300000});
 fs.writeFileSync(process.argv[2],await p.evaluate('window.OUT')); await b.close(); srv.close();});
