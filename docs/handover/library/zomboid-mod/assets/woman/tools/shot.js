const {chromium}=require('playwright'); const fs=require('fs'); const http=require('http'); const path=require('path');
const dir=__dirname; const types={js:'text/javascript',html:'text/html'};
const jobs=JSON.parse(process.argv[2]); const show=process.argv[3]||'';
const srv=http.createServer((q,r)=>{let f=path.join(dir,decodeURIComponent(q.url.split('?')[0])); try{f=fs.realpathSync(f)}catch(e){} if(!fs.existsSync(f)||fs.statSync(f).isDirectory()){r.writeHead(404);r.end();return;} r.writeHead(200,{'Content-Type':types[f.split('.').pop()]||'application/octet-stream'}); fs.createReadStream(f).pipe(r);}).listen(8779, async()=>{
 const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});
 const p=await b.newPage({viewport:{width:900,height:1000}}); p.on('pageerror',e=>console.log('pageerror',e.message)); p.on('console',m=>console.log('console',m.text().slice(0,200)));
 await p.goto('http://localhost:8779/view.html'+(show?'?show='+show:'')); await p.waitForFunction('window.DONE',null,{timeout:300000});
 console.log(await p.evaluate('window.INFO'));
 for(const j of jobs){ await p.evaluate(a=>window.shot(...a), j.a); await p.screenshot({path:path.join(dir,j.out+'.png')}); }
 await b.close(); srv.close();});
