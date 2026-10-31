// Draws the field-kit HUD sprites in a headless browser (canvas and SVG, the same code as the approved mockup):
// enamel moodle plates with sprayed stencil icons, the bare belt squares, the digital wristwatch and its LCD
// glyphs, the magazine, and the soft overlays (blood vignette, glow, shadow).
//
//     node tools/ui/hud/gen_hud.js
//
// Needs Playwright with Chromium. The Barlow Condensed font is the HUD's own (assets font/barlow_condensed.ttf).
const {chromium} = require('playwright');
const fs = require('fs');
const path = require('path');

const OUT = path.join(__dirname, '../../../src/main/resources/assets/zomboidcraft/textures/gui/sprites/hud');
const FONT = path.join(__dirname, '../../../src/main/resources/assets/zomboidcraft/font/barlow_condensed.ttf');

const page = `<!doctype html><meta charset="utf-8"><style>
@font-face{font-family:Barlow;src:url("data:font/ttf;base64,${fs.readFileSync(FONT).toString('base64')}")}
body{margin:0;background:transparent}</style><body><script>
const PATHS={
 drop:"M12 2.5C12 2.5 5.5 10 5.5 14.5a6.5 6.5 0 0 0 13 0C18.5 10 12 2.5 12 2.5z",
 fork:"M6.5 2v7a2 2 0 0 0 1.5 1.94V22h2V10.94A2 2 0 0 0 11.5 9V2h-1.2v6h-.9V2H8.2v6h-.9V2zM16.5 2c-1.7 1.6-2.5 4.3-2.5 7.5V13h2v9h2V2z",
 eye:"M2.6 9.6c2.3 3.3 5.7 5.2 9.4 5.2s7.1-1.9 9.4-5.2l-1.5-1C17.9 11.4 15.1 13 12 13S6.1 11.4 4.1 8.6zM4.6 12.6l-1.8 2.4 1.4 1 1.8-2.5zM8.9 14.5l-.9 2.9 1.6.5.9-2.9zM15.1 14.5l.9 2.9 1.6-.5-.9-2.9zM19.4 12.6l1.8 2.5 1.4-1-1.8-2.4z",
 blood:"M9.5 3S4.5 9.5 4.5 13a5 5 0 0 0 10 0C14.5 9.5 9.5 3 9.5 3zM17.5 12s-2.2 2.6-2.2 4a2.2 2.2 0 0 0 4.4 0c0-1.4-2.2-4-2.2-4zM18 3.8s-1.5 1.8-1.5 2.8a1.5 1.5 0 0 0 3 0c0-1-1.5-2.8-1.5-2.8z",
 lungs:"M11 2.5h2v7.4c.5 1 1.5 1.7 2.4 1.7V7.4c0-1.1.9-1.9 1.9-1.6C20 6.6 21.6 11 21.6 16c0 2.6-1.3 4.1-3.4 4.1-1.9 0-3.3-1-3.8-2.6L13.3 14H13v-1.7c-.4-.2-.7-.5-1-.8-.3.3-.6.6-1 .8V14h-.3l-1.1 3.5c-.5 1.6-1.9 2.6-3.8 2.6-2.1 0-3.4-1.5-3.4-4.1 0-5 1.6-9.4 4.3-10.2 1-.3 1.9.5 1.9 1.6v4.2c.9 0 1.9-.7 2.4-1.7z",
 pack:"M9 2h6v2.2c2.8.7 4.5 3 4.5 6V20a2 2 0 0 1-2 2h-11a2 2 0 0 1-2-2v-9.8c0-3 1.7-5.3 4.5-6zm1.5 1.5v.5h3v-.5zM7.5 12.5v5.5h9v-5.5zM8.8 13.8h6.4v1H8.8z",
 thermo:"M10 4a2 2 0 0 1 4 0v9.3a4.5 4.5 0 1 1-4 0zm1.2 2.8v7.3a3 3 0 1 0 1.6 0V6.8zM12 15.2a1.9 1.9 0 1 1 0 3.8 1.9 1.9 0 0 1 0-3.8z",
 skull:"M12 2C7.6 2 4 5.3 4 9.6c0 2.6 1.3 4.6 3 5.8V18a1 1 0 0 0 1 1h1v2h2v-2h2v2h2v-2h1a1 1 0 0 0 1-1v-2.6c1.7-1.2 3-3.2 3-5.8C20 5.3 16.4 2 12 2zM8.5 9.3a2.1 2.1 0 1 0 0 4.2 2.1 2.1 0 0 0 0-4.2zM15.5 9.3a2.1 2.1 0 1 0 0 4.2 2.1 2.1 0 0 0 0-4.2zM12 13.6l-1.3 2.4h2.6z",
 rain:"M7 4S4 8 4 10a3 3 0 0 0 6 0C10 8 7 4 7 4zM17 4s-3 4-3 6a3 3 0 0 0 6 0c0-2-3-6-3-6zM12 12s-3 4-3 6a3 3 0 0 0 6 0c0-2-3-6-3-6z",
 heart:"M12 21s-7.5-4.6-9.6-9.3C.9 8.3 3 4.5 6.7 4.5c2.2 0 3.9 1.2 5.3 3 1.4-1.8 3.1-3 5.3-3 3.7 0 5.8 3.8 4.3 7.2C19.5 16.4 12 21 12 21z",
 bolt:"M13.5 2L4.5 13.5h6l-1.5 8.5 9-11.5h-6z",
 weight:"M8 3h8a3 3 0 0 1 3 3v1.2a3 3 0 0 1-.9 2.1A7.5 7.5 0 1 1 5.9 9.3 3 3 0 0 1 5 7.2V6a3 3 0 0 1 3-3zm.2 2.2c-.5 0-1 .4-1 1v1c0 .5.4 1 1 1h7.6c.6 0 1-.5 1-1v-1c0-.6-.5-1-1-1z",
 vial:"M9 2h6v2h-1v5.2l5.3 9.2A2.4 2.4 0 0 1 17.2 22H6.8a2.4 2.4 0 0 1-2.1-3.6L10 9.2V4H9zm2.4 2v5.6l-2.9 5h7l-2.9-5V4z",
 bone:"M6.2 3.2a2.6 2.6 0 0 1 4.2 2.4l7.9 7.9a2.6 2.6 0 1 1 2.4 4.2 2.6 2.6 0 1 1-4.2 2.4l-7.9-7.9a2.6 2.6 0 1 1-2.4-4.2 2.6 2.6 0 0 1 0-4.8z",
 flame:"M12 2c1 4 6 6 6 12a6 6 0 0 1-12 0c0-3 1.6-5 3-6.4.3 2.4 1.4 3.6 2.6 4C11 8.6 11 5 12 2z",
 cut:"M4 18.6L17.6 3.4l2.2 1.6L6.4 20.4zM9.2 21.4l11.6-12.8 1.6 1.4L10.8 22.8zM2.2 14.6L12.8 2.6l1.4 1.2L3.6 15.8z",
 pain:"M13.20 2.07L13.61 8.12L19.07 4.93L15.88 10.39L21.93 13.20L15.88 13.61L19.07 19.07L13.61 15.88L10.80 21.93L10.39 15.88L4.93 19.07L8.12 13.61L2.07 10.80L8.12 10.39L4.93 4.93L10.39 8.12z",
 germ:"M7.00 12.00a5 5 0 1 1 10 0 5 5 0 1 1 -10 0zM15.41 14.22L19.47 15.91L20.05 14.52L15.98 12.84zM18.45 15.29a1.5 1.5 0 1 1 3.0 0 1.5 1.5 0 1 1 -3.0 0zM12.84 15.98L14.52 20.05L15.91 19.47L14.22 15.41zM13.79 19.95a1.5 1.5 0 1 1 3.0 0 1.5 1.5 0 1 1 -3.0 0zM9.78 15.41L8.09 19.47L9.48 20.05L11.16 15.98zM7.21 19.95a1.5 1.5 0 1 1 3.0 0 1.5 1.5 0 1 1 -3.0 0zM8.02 12.84L3.95 14.52L4.53 15.91L8.59 14.22zM2.55 15.29a1.5 1.5 0 1 1 3.0 0 1.5 1.5 0 1 1 -3.0 0zM8.59 9.78L4.53 8.09L3.95 9.48L8.02 11.16zM2.55 8.71a1.5 1.5 0 1 1 3.0 0 1.5 1.5 0 1 1 -3.0 0zM11.16 8.02L9.48 3.95L8.09 4.53L9.78 8.59zM7.21 4.05a1.5 1.5 0 1 1 3.0 0 1.5 1.5 0 1 1 -3.0 0zM14.22 8.59L15.91 4.53L14.52 3.95L12.84 8.02zM13.79 4.05a1.5 1.5 0 1 1 3.0 0 1.5 1.5 0 1 1 -3.0 0zM15.98 11.16L20.05 9.48L19.47 8.09L15.41 9.78zM18.45 8.71a1.5 1.5 0 1 1 3.0 0 1.5 1.5 0 1 1 -3.0 0z",
 sad:"M2.50 12.00a9.5 9.5 0 1 1 19.0 0 9.5 9.5 0 1 1 -19.0 0zM7.30 9.60a1.4 1.4 0 1 1 2.8 0 1.4 1.4 0 1 1 -2.8 0zM13.90 9.60a1.4 1.4 0 1 1 2.8 0 1.4 1.4 0 1 1 -2.8 0zM7.4 17.6Q12 12.6 16.6 17.6L15.4 18.6Q12 15 8.6 18.6z",
 stress:"M2.50 12.00a9.5 9.5 0 1 1 19.0 0 9.5 9.5 0 1 1 -19.0 0zM7.60 11.00a1.2 1.2 0 1 1 2.4 0 1.2 1.2 0 1 1 -2.4 0zM14.00 11.00a1.2 1.2 0 1 1 2.4 0 1.2 1.2 0 1 1 -2.4 0zM6.6 7.3l4.2 1.9-.5 1.1-4.2-1.9zM17.4 7.3l-4.2 1.9.5 1.1 4.2-1.9zM7.6 14.6h8.8v3.6H7.6zM9.6 14.6h.8v3.6h-.8zM11.6 14.6h.8v3.6h-.8zM13.6 14.6h.8v3.6h-.8zM7.6 16.2h8.8v.5H7.6z",
 hourglass:"M6 2h12v2h-1v3.2c0 1.8-1.2 3.4-3 4.8 1.8 1.4 3 3 3 4.8V20h1v2H6v-2h1v-3.2c0-1.8 1.2-3.4 3-4.8-1.8-1.4-3-3-3-4.8V4H6zM8.8 4v3.2c0 1.2.9 2.4 2.6 3.7h1.2c1.7-1.3 2.6-2.5 2.6-3.7V4zM11.4 13.1c-1.7 1.3-2.6 2.5-2.6 3.7v.4h6.4v-.4c0-1.2-.9-2.4-2.6-3.7z",
 spiral:"M12 12a1.6 1.6 0 0 1 3.2 0 3.2 3.2 0 0 1-6.4 0 4.8 4.8 0 0 1 9.6 0 6.4 6.4 0 0 1-12.8 0 8 8 0 0 1 16 0"
};
const STROKED={spiral:2.1};
// drawn whole rather than with holes where their pieces overlap
const NONZERO={germ:true};
// mild, bad, severe, and a muted green for effects that help
const LEVEL_RGB={1:[168,143,60],2:[166,90,38],3:[142,39,27],good:[78,104,62]};
function seeded(n){let s=n*7919+13;return()=>((s=(s*9301+49297)%233280)/233280)}
function canvas(w,h){const c=document.createElement("canvas");c.width=w;c.height=h;return c}
function plate(icon,lvl){
  const N=128,c=canvas(N,N),x=c.getContext("2d"),key=String(lvl),r=seeded(icon.length*31+(key.length+key.charCodeAt(0))*7+icon.charCodeAt(0));
  const [R,G,B]=LEVEL_RGB[lvl],chips=lvl==="good"?12:14+lvl*5;
  x.fillStyle="#2a2b28";x.beginPath();x.roundRect(4,4,N-8,N-8,10);x.fill();
  const paint=canvas(N,N),p=paint.getContext("2d");
  const g=p.createLinearGradient(0,0,0,N);g.addColorStop(0,"rgb("+(R+28)+","+(G+24)+","+(B+18)+")");g.addColorStop(.55,"rgb("+R+","+G+","+B+")");g.addColorStop(1,"rgb("+(R*.72|0)+","+(G*.72|0)+","+(B*.72|0)+")");
  p.fillStyle=g;p.beginPath();p.roundRect(4,4,N-8,N-8,10);p.fill();
  for(let i=0;i<900;i++){p.fillStyle="rgba("+(r()<.5?"0,0,0":"255,245,225")+","+r()*.07+")";p.fillRect(4+r()*(N-8),4+r()*(N-8),1+r()*2,1+r()*2)}
  p.globalCompositeOperation="destination-out";
  for(let i=0;i<chips;i++){const side=r()*4|0,t=8+r()*(N-16),d=4+r()*5;const [cx,cy]=side===0?[t,4+r()*3]:side===1?[N-4-r()*3,t]:side===2?[t,N-4-r()*3]:[4+r()*3,t];p.beginPath();for(let k=0;k<7;k++){const a=k/7*Math.PI*2,rr=d*(.5+r()*.7);p.lineTo(cx+Math.cos(a)*rr,cy+Math.sin(a)*rr)}p.fill()}
  p.globalCompositeOperation="source-over";
  x.drawImage(paint,0,0);
  x.strokeStyle="rgba(255,248,230,.22)";x.lineWidth=2;x.beginPath();x.moveTo(14,6);x.lineTo(N-14,6);x.stroke();
  x.strokeStyle="rgba(0,0,0,.35)";x.beginPath();x.moveTo(14,N-6);x.lineTo(N-14,N-6);x.stroke();
  const ink=canvas(N,N),k=ink.getContext("2d");
  k.translate(N*.2,N*.2);k.scale(N*.6/24,N*.6/24);const path=new Path2D(PATHS[icon]);
  k.fillStyle=k.strokeStyle="rgba(20,16,12,.93)";k.shadowColor="rgba(20,16,12,.55)";k.shadowBlur=3;
  if(STROKED[icon]){k.lineWidth=STROKED[icon];k.lineCap="round";k.stroke(path)}else k.fill(path,NONZERO[icon]?"nonzero":"evenodd");
  k.setTransform(1,0,0,1,0,0);k.globalCompositeOperation="destination-out";
  for(let i=0;i<260;i++){k.fillStyle="rgba(0,0,0,"+(.35+r()*.6)+")";k.fillRect(r()*N,r()*N,1+r()*1.5,1+r()*1.5)}
  k.globalCompositeOperation="source-over";
  for(let i=0;i<160;i++){const a=r()*Math.PI*2,d=N*.3+r()*N*.12;k.fillStyle="rgba(20,16,12,"+r()*.18+")";k.fillRect(N/2+Math.cos(a)*d,N/2+Math.sin(a)*d,1,1)}
  x.drawImage(ink,0,0);
  for(const [a,b] of [[16,16],[N-16,N-16]]){const rg=x.createRadialGradient(a-1,b-1,0,a,b,4);rg.addColorStop(0,"#9a9a92");rg.addColorStop(1,"#3a3a36");x.fillStyle=rg;x.beginPath();x.arc(a,b,3.4,0,7);x.fill()}
  return down(c,64);
}
function down(c,n){const o=canvas(n,n*c.height/c.width|0),x=o.getContext("2d");x.imageSmoothingEnabled=true;x.imageSmoothingQuality="high";x.drawImage(c,0,0,o.width,o.height);return o.toDataURL()}
function scratches(x,size,seed,strength){const r=seeded(seed);
  for(let i=0;i<34;i++){x.strokeStyle="rgba(255,250,235,"+(.04+r()*.1)*strength+")";x.lineWidth=.6*size/128;x.beginPath();const a=r()*size,b=r()*size,l=(6+r()*30)*size/128,t=r()*Math.PI;x.moveTo(a,b);x.lineTo(a+Math.cos(t)*l,b+Math.sin(t)*l);x.stroke()}
  for(let i=0;i<140;i++){x.fillStyle="rgba(255,250,235,"+r()*.08*strength+")";x.fillRect(r()*size,r()*size,1,1)}
  for(let i=0;i<40;i++){const e=r()<.5?r()*6:size-6+r()*6,f=r()*size;x.fillStyle="rgba(255,250,235,"+r()*.12*strength+")";r()<.5?x.fillRect(e,f,1.5,1.5):x.fillRect(f,e,1.5,1.5)}}
// a belt square: no backdrop beyond a smoked-glass tint, a hairline bone edge and scuffs (24 GUI px drawn at 3x)
function square(selected){
  const S=3,box=24*S,pad=selected?4*S:0,W=box+pad*2,c=canvas(W,W),x=c.getContext("2d");
  if(selected){
    for(let i=pad;i>0;i--){x.strokeStyle="rgba(224,160,80,"+(.10*(1-i/pad))+")";x.lineWidth=2;x.strokeRect(pad-i,pad-i,box+i*2,box+i*2)}
  }
  x.save();x.translate(pad,pad);
  const g=x.createLinearGradient(0,0,0,box);g.addColorStop(0,"rgba(20,20,18,.40)");g.addColorStop(1,"rgba(6,6,5,.55)");x.fillStyle=g;x.fillRect(0,0,box,box);
  scratches(x,box,selected?11:7,1);
  if(selected){const ig=x.createRadialGradient(box/2,box/2,box*.2,box/2,box/2,box*.75);ig.addColorStop(0,"rgba(224,160,80,0)");ig.addColorStop(1,"rgba(224,160,80,.20)");x.fillStyle=ig;x.fillRect(0,0,box,box)}
  x.strokeStyle=selected?"rgba(224,168,90,.95)":"rgba(233,225,203,.32)";x.lineWidth=selected?3:2;x.strokeRect(x.lineWidth/2,x.lineWidth/2,box-x.lineWidth,box-x.lineWidth);
  x.strokeStyle="rgba(255,255,255,.06)";x.lineWidth=2;x.beginPath();x.moveTo(4,5);x.lineTo(box-4,5);x.stroke();
  x.restore();return c.toDataURL();
}
// an SVG drawn as an image can't see the page's fonts, so each one carries the font itself
const FONT_CSS='<style>@font-face{font-family:Barlow;src:url(data:font/ttf;base64,${fs.readFileSync(FONT).toString('base64')})}</style>';
function svgPng(svg,w,h){svg=svg.replace(/(<svg[^>]*>)/,"$1"+FONT_CSS);return new Promise(res=>{const img=new Image();img.onload=()=>{const c=canvas(w,h);c.getContext("2d").drawImage(img,0,0,w,h);res(c.toDataURL())};img.src="data:image/svg+xml;charset=utf-8,"+encodeURIComponent(svg)})}
const U=4; // watch pixels per watch unit
function watchBase(lit){
  return svgPng('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 74" width="'+100*U+'" height="'+74*U+'">'+
  '<defs><linearGradient id="strap" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="#181816"/><stop offset=".5" stop-color="#262623"/><stop offset="1" stop-color="#121210"/></linearGradient>'+
  '<linearGradient id="case" x1="0" x2="1" y1="0" y2="1"><stop offset="0" stop-color="#5b5d58"/><stop offset=".35" stop-color="#2e302d"/><stop offset=".7" stop-color="#1e1f1d"/><stop offset="1" stop-color="#3d3f3b"/></linearGradient>'+
  '<linearGradient id="lcd" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="'+(lit?"#7fd6c8":"#a8b292")+'"/><stop offset="1" stop-color="'+(lit?"#4fb3a6":"#8a9677")+'"/></linearGradient>'+
  '<radialGradient id="glow"><stop offset="0" stop-color="rgba(120,230,210,.55)"/><stop offset="1" stop-color="rgba(120,230,210,0)"/></radialGradient></defs>'+
  '<rect x="30" y="0" width="40" height="16" fill="url(#strap)"/><rect x="30" y="58" width="40" height="16" fill="url(#strap)"/>'+
  [4,9,63,68].map(y=>'<path d="M31 '+y+'h38" stroke="rgba(255,255,255,.05)"/>').join("")+
  '<ellipse cx="50" cy="69" rx="2.2" ry="1.3" fill="#070706"/><ellipse cx="50" cy="73" rx="2.2" ry="1.3" fill="#070706"/>'+
  '<rect x="5" y="12" width="5" height="7" rx="1" fill="#2a2b28"/><rect x="5" y="44" width="5" height="7" rx="1" fill="#2a2b28"/><rect x="90" y="12" width="5" height="7" rx="1" fill="#2a2b28"/><rect x="90" y="44" width="5" height="7" rx="1" fill="#2a2b28"/>'+
  '<rect x="8" y="10" width="84" height="54" rx="11" fill="url(#case)" stroke="#0b0b0a" stroke-width="1"/>'+
  '<rect x="8.8" y="10.8" width="82.4" height="52.4" rx="10.4" fill="none" stroke="rgba(255,255,255,.12)" stroke-width=".8"/>'+
  [[13,15],[87,15],[13,59],[87,59]].map(([a,b])=>'<circle cx="'+a+'" cy="'+b+'" r="1.5" fill="#6a6c66"/><path d="M'+(a-1)+' '+b+'h2" stroke="#1a1a18" stroke-width=".5" transform="rotate('+(a*b%90)+' '+a+' '+b+')"/>').join("")+
  '<text x="50" y="18" text-anchor="middle" font-family="Barlow" font-size="4.4" letter-spacing="1.2" fill="rgba(233,225,203,.55)">FIELD &#183; WR50M</text>'+
  '<text x="13" y="11" font-family="Barlow" font-size="3.2" fill="rgba(233,225,203,.35)">LIGHT</text><text x="78" y="11" font-family="Barlow" font-size="3.2" fill="rgba(233,225,203,.35)">MODE</text>'+
  '<rect x="16" y="21" width="68" height="36" rx="3" fill="#0c0d0b"/><rect x="18" y="23" width="64" height="32" rx="2" fill="url(#lcd)"/>'+
  (lit?'<rect x="18" y="23" width="64" height="32" rx="2" fill="url(#glow)"/>':'')+
  '</svg>',100*U,74*U);
}
// the glass goes on top of the digits: a scratch and a sheen
function watchGlass(){
  return svgPng('<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 23 64 32" width="'+64*U+'" height="'+32*U+'">'+
  '<path d="M19 24l22 30M52 24l10 13" stroke="rgba(255,255,255,.16)" stroke-width=".35"/><path d="M18 23h64v9c-20-3-44-2-64 3z" fill="rgba(255,255,255,.07)"/></svg>',64*U,32*U);
}
const SEG={0:"abcdef",1:"bc",2:"abdeg",3:"abcdg",4:"bcfg",5:"acdfg",6:"acdefg",7:"abc",8:"abcdefg",9:"abcdfg"};
// one 7-segment digit in white (tinted in game), 10 x 17 units with a half-unit margin
function segDigit(n){
  const x=.5,y=.5,w=9,h=16,t=1.7,P={a:[[x+1,y],[x+w-1,y],[x+w-1-t,y+t],[x+1+t,y+t]],b:[[x+w,y+1],[x+w,y+h/2-.6],[x+w-t,y+h/2-.6-t*.6],[x+w-t,y+1+t]],c:[[x+w,y+h/2+.6],[x+w,y+h-1],[x+w-t,y+h-1-t],[x+w-t,y+h/2+.6+t*.6]],d:[[x+1,y+h],[x+w-1,y+h],[x+w-1-t,y+h-t],[x+1+t,y+h-t]],e:[[x,y+h/2+.6],[x,y+h-1],[x+t,y+h-1-t],[x+t,y+h/2+.6+t*.6]],f:[[x,y+1],[x,y+h/2-.6],[x+t,y+h/2-.6-t*.6],[x+t,y+1+t]],g:[[x+1,y+h/2],[x+t,y+h/2-t/2],[x+w-t,y+h/2-t/2],[x+w-1,y+h/2],[x+w-t,y+h/2+t/2],[x+t,y+h/2+t/2]]};
  let s="";for(const k of SEG[n])s+='<polygon points="'+P[k].map(p=>p.join(",")).join(" ")+'" fill="#fff"/>';
  return svgPng('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 17" width="'+10*U*1.1+'" height="'+17*U*1.1+'">'+s+'</svg>',Math.round(10*U*1.1),Math.round(17*U*1.1));
}
// LCD text in white: a box 3 + 4 units, text on a baseline 3.6 down
function lcdText(t,w){return svgPng('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 '+w+' 5" width="'+w*U*2+'" height="'+5*U*2+'"><text x="0" y="4.1" font-family="Barlow" font-size="5" fill="#fff">'+t+'</text></svg>',w*U*2,5*U*2)}
function magazine(){
  return svgPng('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 60" width="80" height="240"><defs><linearGradient id="mg" x1="0" x2="1"><stop offset="0" stop-color="#2c2e2b"/><stop offset=".45" stop-color="#4a4d48"/><stop offset="1" stop-color="#232421"/></linearGradient></defs>'+
  '<path d="M4 4h12l1 52c-3 2-11 2-14 0z" fill="url(#mg)" stroke="rgba(0,0,0,.6)" stroke-width=".8"/><path d="M5 9h10M5 14h10" stroke="rgba(255,255,255,.08)"/><rect x="3" y="2" width="14" height="3" rx="1" fill="#3a3c38"/></svg>',80,240);
}
function radial(w,h,stops){const c=canvas(w,h),x=c.getContext("2d");x.translate(w/2,h/2);x.scale(w/2,h/2);const g=x.createRadialGradient(0,0,0,0,0,1);stops.forEach(([o,s])=>g.addColorStop(o,s));x.fillStyle=g;x.fillRect(-1,-1,2,2);return c.toDataURL()}
function arrow(up){const c=canvas(24,24),x=c.getContext("2d");x.fillStyle="#fff";x.beginPath();if(up){x.moveTo(12,4);x.lineTo(22,18);x.lineTo(2,18)}else{x.moveTo(2,6);x.lineTo(22,6);x.lineTo(12,20)}x.fill();return c.toDataURL()}
async function all(){
  await document.fonts.load("10px Barlow");
  const out={};
  for(const icon of Object.keys(PATHS))for(const lvl of [1,2,3,"good"])out["moodle/"+icon+"_"+lvl]=plate(icon,lvl);
  out.square=square(false);out.square_selected=square(true);
  out["watch/base"]=await watchBase(false);out["watch/base_lit"]=await watchBase(true);out["watch/glass"]=await watchGlass();
  for(let n=0;n<10;n++){out["watch/seg_"+n]=await segDigit(n);out["watch/num_"+n]=await lcdText(n,2.6)}
  for(const d of ["su","mo","tu","we","th","fr","sa"])out["watch/"+d]=await lcdText(d.toUpperCase(),7);
  out["watch/day"]=await lcdText("DAY",8);
  out.magazine=await magazine();
  out.blood=radial(320,180,[[0,"rgba(90,4,0,0)"],[.55,"rgba(90,4,0,0)"],[.8,"rgba(110,6,0,.45)"],[1,"rgba(70,0,0,.9)"]]);
  out.glow=radial(64,64,[[0,"rgba(255,255,255,.9)"],[.5,"rgba(255,255,255,.35)"],[1,"rgba(255,255,255,0)"]]);
  out.shadow=radial(64,16,[[0,"rgba(0,0,0,.6)"],[.7,"rgba(0,0,0,.25)"],[1,"rgba(0,0,0,0)"]]);
  out.arrow_up=arrow(true);out.arrow_down=arrow(false);
  return out;
}
</script>`;

(async () => {
  const browser = await chromium.launch();
  const tab = await browser.newPage();
  await tab.setContent(page);
  const sprites = await tab.evaluate(() => all());
  for (const [name, url] of Object.entries(sprites)) {
    const file = path.join(OUT, name + '.png');
    fs.mkdirSync(path.dirname(file), {recursive: true});
    fs.writeFileSync(file, Buffer.from(url.split(',')[1], 'base64'));
  }
  console.log(Object.keys(sprites).length + ' sprites in ' + OUT);
  await browser.close();
})();
