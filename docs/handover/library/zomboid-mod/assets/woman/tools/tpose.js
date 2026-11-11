const {chromium}=require('playwright'); const fs=require('fs'); const http=require('http'); const path=require('path');
const dir=__dirname; const types={js:'text/javascript',html:'text/html',jpg:'image/jpeg',webp:'image/webp',txt:'text/plain'};
const show=process.argv[2], out=process.argv[3];
const srv=http.createServer((q,r)=>{let f=path.join(dir,decodeURIComponent(q.url.split('?')[0])); try{f=fs.realpathSync(f)}catch(e){} if(!fs.existsSync(f)||fs.statSync(f).isDirectory()){r.writeHead(404);r.end();return;} r.writeHead(200,{'Content-Type':types[f.split('.').pop()]||'application/octet-stream'}); fs.createReadStream(f).pipe(r);}).listen(8778, async()=>{
 const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--js-flags=--max-old-space-size=4000']});
 const p=await b.newPage({viewport:{width:900,height:1000}}); p.on('pageerror',e=>console.log('pageerror',e.message));
 await p.goto('http://localhost:8778/tpose.html?show='+show+'&foot='+(process.argv[4]||32)+(process.argv[5]==='raw'?'&raw=1':process.argv[5]==='nofeet'?'&feet=0':'')); await p.waitForFunction('window.DONE',null,{timeout:300000}); await p.waitForTimeout(1500);
 await p.screenshot({path:out+'_front.png'});
 await p.evaluate(()=>{A.cam.position.set(4.6,0.95,0);}); await p.waitForTimeout(800); await p.screenshot({path:out+'_side.png'});
 fs.writeFileSync(out+'.obj',await p.evaluate('window.OUT')); fs.writeFileSync(out+'_face.json',await p.evaluate('window.FACE')); console.log(await p.evaluate('JSON.stringify([window.MATS,window.ANG_l,window.ANG_r])'));
 await b.close(); srv.close();});
