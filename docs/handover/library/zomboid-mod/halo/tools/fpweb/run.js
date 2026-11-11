const {chromium}=require('playwright');const fs=require('fs');const {spawn}=require('child_process');
const NAMES={assault_bomb:'Assault bomb',assault_rifle:'MA37 Assault Rifle',ball:'Oddball skull',concussion_rifle:'Concussion Rifle',dmr:'M392 DMR',energy_sword:'Energy Sword',flag:'Flag',flak_cannon:'Fuel Rod Gun',focus_rifle:'Focus Rifle',gravity_hammer:'Gravity Hammer',grenade_launcher:'M319 Grenade Launcher',magnum:'M6G Magnum',needle_rifle:'Needle Rifle',needler:'Needler',plasma_launcher:'Plasma Launcher',plasma_pistol:'Plasma Pistol',plasma_repeater:'Plasma Repeater',plasma_rifle:'Plasma Rifle',rocket_launcher:'M41 Rocket Launcher',shotgun:'M45 Shotgun',sniper_rifle:'SRS99 Sniper Rifle',spartan_laser:'Spartan Laser',spike_rifle:'Spiker',target_laser:'Target Locator'};
function seq(w,C){const s=[];const has=n=>n in C;const add=(n,lab,dur)=>{if(has(n))s.push([n,lab,dur??C[n]]);};
 add(has('ready_initial')?'ready_initial':'ready','Draw');
 add(has('idle')?'idle':'idle:var1','Idle',1.6);
 const melee=['energy_sword','gravity_hammer'].includes(w), obj=['ball','flag','assault_bomb'].includes(w);
 if(melee){add('melee_lunge','Lunge');add('melee_strike_1','Swing');add('melee_strike_2','Swing 2');}
 else if(!obj){
  if(w==='spartan_laser'){add('fire_2','Charge and fire');}
  else{const f=['fire_1:var1','fire_1:var0','fire_1'].find(has);if(f){const n=Math.max(1,Math.min(6,Math.ceil(1.2/C[f])));for(let i=0;i<n;i++)add(f,'Fire');}
   if(w==='plasma_pistol')add('fire_2','Overcharged shot');}
  if(w==='shotgun'){add('reload_enter','Reload');add('reload_continue_empty','Reload');add('reload_continue_empty','Reload');add('chamber_1','Pump');}
  else if(has('reload_full'))add('reload_full','Reload');else if(has('reload_full:var1'))add('reload_full:var1','Reload');
  else if(w==='plasma_repeater'){add('vent_enter','Vent');add('vent_loop','Vent');add('vent_exit','Vent');}
  else if(has('overheated')){add('overheated','Overheat');add('o_h_exit','Overheat');}
  add('melee_strike_1','Melee');
 } else {add('melee_strike_1','Melee');}
 add(has('posing:var1')?'posing:var1':'posing:var0','Inspect');
 if(!obj&&!melee)add('throw_grenade','Grenade');
 return s;}
(async()=>{const list=process.argv[2].split(',');const tag=process.argv[3]||'six';const extra=process.argv[4]||'';
const b=await chromium.launch({args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader','--ignore-gpu-blocklist']});
for(const w of list){const p=await b.newPage({viewport:{width:960,height:540}});p.on('pageerror',e=>console.log('err',e.message));
 await p.goto(`http://127.0.0.1:8765/index.html?f=fp/spartan_fp_${w}.glb${extra}`);await p.waitForFunction('window.READY===true',null,{timeout:180000});
 const C=await p.evaluate('window.CLIPS');const S=seq(w,C);const FPS=30;
 const out=`/tmp/claude-0/fpweb/out/${tag}_${w}.mp4`;
 const ff=spawn('ffmpeg',['-y','-loglevel','error','-f','image2pipe','-framerate',String(FPS),'-c:v','mjpeg','-i','-','-c:v','libx264','-pix_fmt','yuv420p','-crf','27','-preset','medium',out]);
 let nf=0;for(const [n,lab,dur] of S){const k=Math.max(1,Math.round(dur*FPS));for(let i=0;i<k;i++){const t=Math.min(i/FPS,C[n]);
  const u=await p.evaluate(([n,t,c])=>window.frame(n,t,c),[n,t,`${NAMES[w]}  ·  ${lab}`]);if(!ff.stdin.write(Buffer.from(u.split(',')[1],'base64')))await new Promise(r=>ff.stdin.once('drain',r));nf++;}}
 ff.stdin.end();await new Promise(r=>ff.on('close',r));console.log(w,nf,'frames');await p.close();}
await b.close();})();
