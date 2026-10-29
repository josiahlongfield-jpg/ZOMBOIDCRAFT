const {chromium}=require('playwright'); const fs=require('fs'); const http=require('http'); const path=require('path');
const dir=__dirname; const srv=http.createServer((q,r)=>{const f=path.join(dir,decodeURIComponent(q.url.split('?')[0])); if(!fs.existsSync(f)||fs.statSync(f).isDirectory()){r.writeHead(404);r.end();return;} r.writeHead(200,{'Content-Type':f.endsWith('.js')?'text/javascript':f.endsWith('.html')?'text/html':'application/octet-stream'}); fs.createReadStream(f).pipe(r);}).listen(8771, async()=>{
 const b=await chromium.launch({args:['--js-flags=--max-old-space-size=6000']}); const p=await b.newPage(); p.on('console',m=>console.log('console:',m.text()));
 await p.goto('http://localhost:8771/'+(process.argv[2]||'export.html')); await p.waitForFunction('window.DONE',null,{timeout:600000});
 console.log((await p.evaluate('window.LOG')).join('\n')); const o=await p.evaluate('window.OUT||""'); if(o) fs.writeFileSync(path.join(dir,process.argv[3]||'woman.glb'),process.argv[4]==='text'?o:Buffer.from(o,'base64'));
 await b.close(); srv.close();});
