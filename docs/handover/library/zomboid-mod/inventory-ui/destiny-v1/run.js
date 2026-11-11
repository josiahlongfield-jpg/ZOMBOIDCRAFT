const {chromium}=require('playwright');const fs=require('fs');const path=require('path');
(async()=>{
  const jobs=JSON.parse(fs.readFileSync(process.argv[2]));const out=process.argv[3];fs.mkdirSync(out,{recursive:true});
  const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']});const p=await b.newPage();
  p.on('console',m=>console.log('page:',m.text()));p.on('pageerror',e=>console.log('ERR',e.message));
  await p.goto('file://'+path.resolve(__dirname,'page.html'));
  await p.evaluate(a=>window.setup(a),JSON.parse(fs.readFileSync(path.resolve(__dirname,'models.json'))));
  for(const j of jobs){const d=await p.evaluate(j=>window.render(j),j);fs.writeFileSync(path.join(out,j.name+'.png'),Buffer.from(d.split(',')[1],'base64'));console.log('ok',j.name)}
  await b.close()})();
