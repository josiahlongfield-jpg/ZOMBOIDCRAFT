const DEG=Math.PI/180;
let A=null;
function seeded(n){let s=n*7919+13;return()=>((s=(s*9301+49297)%233280)/233280)}
const texCache={};
function loadTex(uri){if(texCache[uri])return texCache[uri];return texCache[uri]=new Promise(res=>{new THREE.TextureLoader().load(uri,t=>{t.magFilter=THREE.NearestFilter;t.minFilter=THREE.NearestFilter;res(t)})})}
async function mcModel(model){
  const root=new THREE.Group(),m=new THREE.MeshLambertMaterial({map:await loadTex(model.tex),alphaTest:.1});
  model.elements.forEach(e=>{
    const [fx,fy,fz]=e.from,[tx,ty,tz]=e.to,pos=[],uv=[],ind=[];
    const F={north:[[tx,ty,fz],[fx,ty,fz],[tx,fy,fz],[fx,fy,fz]],south:[[fx,ty,tz],[tx,ty,tz],[fx,fy,tz],[tx,fy,tz]],east:[[tx,ty,tz],[tx,ty,fz],[tx,fy,tz],[tx,fy,fz]],west:[[fx,ty,fz],[fx,ty,tz],[fx,fy,fz],[fx,fy,tz]],up:[[fx,ty,fz],[tx,ty,fz],[fx,ty,tz],[tx,ty,tz]],down:[[fx,fy,tz],[tx,fy,tz],[fx,fy,fz],[tx,fy,fz]]};
    Object.entries(e.faces).forEach(([dir,f])=>{const v=F[dir];if(!v)return;const b=pos.length/3;const [u1,v1,u2,v2]=f.uv.map(x=>x/16);
      let c=[[u1,1-v1],[u2,1-v1],[u2,1-v2],[u1,1-v2]];// tl tr br bl
      for(let r=0;r<((f.rotation||0)/90);r++)c=[c[3],c[0],c[1],c[2]];
      const uvs=[c[0],c[1],c[3],c[2]];v.forEach((p,i)=>{pos.push(...p);uv.push(...uvs[i])});ind.push(b,b+2,b+1,b+1,b+2,b+3)});
    const g=new THREE.BufferGeometry();g.setAttribute("position",new THREE.Float32BufferAttribute(pos,3));g.setAttribute("uv",new THREE.Float32BufferAttribute(uv,2));g.setIndex(ind);g.computeVertexNormals();
    const mesh=new THREE.Mesh(g,m);let node=mesh;
    if(e.rotation){const r=e.rotation,o=r.origin,piv=new THREE.Group();piv.position.set(...o);mesh.position.set(-o[0],-o[1],-o[2]);piv.add(mesh);piv.rotation[r.axis]=r.angle*DEG;node=piv}
    root.add(node)});
  return root;
}
function canvasTex(w,h,draw){const c=document.createElement("canvas");c.width=w;c.height=h;draw(c.getContext("2d"),w,h);const t=new THREE.CanvasTexture(c);t.magFilter=THREE.NearestFilter;t.minFilter=THREE.NearestFilter;return t}
function rgb(c,d){return `rgb(${Math.max(0,Math.min(255,c[0]+d))|0},${Math.max(0,Math.min(255,c[1]+d))|0},${Math.max(0,Math.min(255,c[2]+d))|0})`}
function hex2(h){const n=parseInt(h.slice(1),16);return[n>>16&255,n>>8&255,n&255]}
// a cloth/metal material: base colour with per-pixel noise, plus an optional overlay painter
function mat(hex,amt,seed,paint,size=16){const col=hex2(hex),r=seeded(seed);return new THREE.MeshLambertMaterial({map:canvasTex(size,size,(x,w,h)=>{for(let i=0;i<w;i++)for(let j=0;j<h;j++){x.fillStyle=rgb(col,(r()-.5)*amt);x.fillRect(i,j,1,1)}if(paint)paint(x,w,h,r)})})}
function box(from,to,m){const g=new THREE.Mesh(new THREE.BoxGeometry(to[0]-from[0],to[1]-from[1],to[2]-from[2]),m);g.position.set((from[0]+to[0])/2,(from[1]+to[1])/2,(from[2]+to[2])/2);return g}
function cyl(r,h,m,seg=12,y=0){const g=new THREE.Mesh(new THREE.CylinderGeometry(r,r,h,seg),m);g.position.y=y+h/2;return g}
function grp(...k){const g=new THREE.Group();k.forEach(c=>g.add(c));return g}
const wear=(x,w,h,r,n=30,a=.25)=>{for(let i=0;i<n;i++){x.fillStyle=`rgba(0,0,0,${r()*a})`;x.fillRect(r()*w|0,r()*h|0,1,1)}for(let i=0;i<n/3;i++){x.fillStyle=`rgba(255,240,220,${r()*a*.6})`;x.fillRect(r()*w|0,r()*h|0,1,1)}};

/* ---------- procedural gear (no items in the mod yet) ---------- */
const P={
helmet(){const o=mat("#4a5132",22,11,(x,w,h,r)=>wear(x,w,h,r,40,.3)),d=mat("#2a2d22",10,12),s=mat("#3b3a2e",14,13),g=mat("#1b1d1a",8,14);
  return grp(box([-4.7,0,-4.7],[4.7,2.4,4.7],o),box([-4.5,2.4,-4.5],[4.5,3.8,4.5],o),box([-4.1,3.8,-4.1],[4.1,4.9,4.1],o),box([-3.5,4.9,-3.5],[3.5,5.7,3.5],o),box([-2.6,5.7,-2.6],[2.6,6.2,2.6],o),box([-4.9,-.3,-4.9],[4.9,.4,4.9],d),
   box([-1.5,1.2,4.7],[1.5,3.4,5.5],g),box([-.7,3.4,4.5],[.7,4.2,5.3],g),box([-4.95,.8,-1.6],[-4.65,3,1.6],s),box([4.65,.8,-1.6],[4.95,3,1.6],s),
   box([-4.8,-2.4,-.4],[-4.5,0,.4],s),box([4.5,-2.4,-.4],[4.8,0,.4],s))},
jacket(){const o=mat("#59583a",22,21,(x,w,h,r)=>{wear(x,w,h,r,30,.25);x.fillStyle="rgba(0,0,0,.25)";x.fillRect(7,0,1,16)}),p=mat("#4e4d33",20,22,(x)=>{x.fillStyle="rgba(0,0,0,.35)";x.fillRect(0,0,16,2)}),c=mat("#3e3d29",14,23),z=mat("#26261c",6,24);
  const L=grp(box([-2,-11,-2],[2,0,2],o),box([-2.15,-11.5,-2.15],[2.15,-10,2.15],c));L.position.set(-6,12,0);L.rotation.z=-.1;L.rotation.x=.08;
  const Rr=grp(box([-2,-11,-2],[2,0,2],o),box([-2.15,-11.5,-2.15],[2.15,-10,2.15],c));Rr.position.set(6,12,0);Rr.rotation.z=.1;Rr.rotation.x=-.06;
  return grp(box([-4,0,-2],[4,12,2],o),box([-4.3,10.5,-2.3],[4.3,13,2.3],c),box([-.25,0,2],[.25,10.5,2.15],z),box([-3.6,2,2],[-.8,5.4,2.6],p),box([.8,2,2],[3.6,5.4,2.6],p),box([-3.4,7,2],[-1,9.4,2.5],p),box([1,7,2],[3.4,9.4,2.5],p),L,Rr)},
gloves(){const l=mat("#6b4a2c",26,31,(x,w,h,r)=>wear(x,w,h,r,40,.3)),c=mat("#3a2a1b",12,32),k=mat("#8a6a44",20,33);
  const one=s=>{const g=grp(box([-2,0,-1.2],[2,4.5,1.2],l),box([-2.15,-1.6,-1.35],[2.15,0,1.35],c));[-1.6,-.6,.4,1.4].forEach((fx,i)=>g.add(box([fx-.45,4.5,-1],[fx+.45,4.5+(i==1||i==2?2.6:2.1),.9],l)));g.add(box([s*2,1.4,-.9],[s*3.1,3.6,.9],l),box([-1.8,2.6,1.2],[1.8,3.4,1.4],k));return g};
  const a=one(1),b=one(-1);a.position.set(-3,0,0);a.rotation.z=.18;b.position.set(3.4,-.4,0);b.rotation.z=-.12;return grp(a,b)},
trousers(){const o=mat("#4a4b44",22,41,(x,w,h,r)=>{wear(x,w,h,r,30,.25);x.fillStyle="rgba(0,0,0,.25)";x.fillRect(0,9,16,1)}),p=mat("#42433c",18,42,(x)=>{x.fillStyle="rgba(0,0,0,.35)";x.fillRect(0,0,16,2)}),b=mat("#2a241c",8,43),k=mat("#8f8466",10,44);
  return grp(box([-4,0,-2],[-.1,12,2],o),box([.1,0,-2],[4,12,2],o),box([-4.2,11,-2.2],[4.2,13,2.2],b),box([-.7,11.4,2.15],[.7,12.6,2.4],k),box([-4.6,4,-1.6],[-4,7.6,1.6],p),box([4,4,-1.6],[4.6,7.6,1.6],p),box([-3.6,0,-2.1],[-.4,1,2.1],b),box([.4,0,-2.1],[3.6,1,2.1],b))},
boots(){const k=mat("#221d18",16,51,(x,w,h,r)=>wear(x,w,h,r,40,.35)),s=mat("#141210",6,52),l=mat("#6f6650",10,53);
  const one=()=>{const g=grp(box([-2,0,-2],[2,6,2],k),box([-2.1,-1,-2.2],[2.1,0,4.6],s),box([-2,0,1.6],[2,2.4,4.4],k),box([-2.1,5.4,-2.1],[2.1,6.2,2.1],s));[1,2.2,3.4,4.6].forEach(y=>g.add(box([-1.2,y,2],[1.2,y+.3,2.15],l)));return g};
  const a=one(),b=one();a.position.set(-2.6,0,0);b.position.set(2.8,0,-1.2);b.rotation.y=-.25;return grp(a,b)},
/* supplies */
beans(){const t=mat("#a8a8a2",12,61),lab=new THREE.MeshLambertMaterial({map:canvasTex(32,16,(x,w,h)=>{x.fillStyle="#8b2a1c";x.fillRect(0,0,w,h);x.fillStyle="#e7c97c";x.fillRect(0,5,w,6);x.fillStyle="#6a3a1a";for(let i=2;i<w;i+=5)x.fillRect(i,7,3,2);const r=seeded(4);wear(x,w,h,r,50,.3)})});
  const c=new THREE.Mesh(new THREE.CylinderGeometry(3,3,6.4,16),[lab,t,t]);c.position.y=4;return grp(c,cyl(3.1,.6,t,16,0.4),cyl(3.1,.6,t,16,7.2),cyl(2.7,.15,mat("#cfcfc9",8,62),16,7.8))},
water(){const b=new THREE.MeshLambertMaterial({color:0x9cc4d8,transparent:true,opacity:.62}),w=new THREE.MeshLambertMaterial({color:0x5c8fb0,transparent:true,opacity:.55}),cap=mat("#2d63a8",14,71),lab=mat("#e8eef0",8,72,(x)=>{x.fillStyle="#2d63a8";x.fillRect(0,6,16,4)});
  return grp(cyl(2.4,9,b,14,0),cyl(2.2,6.5,w,14,0.2),cyl(2.45,3,lab,14,3),new THREE.Mesh(new THREE.CylinderGeometry(1.1,2.4,2,14),b).translateY(10),cyl(1.1,1.3,cap,14,11))},
pills(){const b=new THREE.MeshLambertMaterial({color:0xd8771c,transparent:true,opacity:.85}),cap=mat("#f0ede4",8,81),lab=mat("#f4f1e8",6,82,(x)=>{x.fillStyle="#333";for(let i=4;i<12;i+=2)x.fillRect(3,i,10,1)});
  return grp(cyl(2,5.4,b,14,0),cyl(2.03,3,lab,14,1),cyl(2.2,1.6,cap,14,5.4))},
tape(){const s=mat("#9a9c9e",14,91,(x,w,h,r)=>wear(x,w,h,r,30,.2)),c=mat("#a07f55",14,92);
  const ring=new THREE.Mesh(new THREE.CylinderGeometry(4,4,2.6,20,1,true),s);ring.material.side=THREE.DoubleSide;
  const top=new THREE.Mesh(new THREE.RingGeometry(2.6,4,20),mat("#7c7e80",10,93));top.rotation.x=-Math.PI/2;top.position.y=1.3;
  const core=new THREE.Mesh(new THREE.CylinderGeometry(2.6,2.6,2.6,20,1,true),c);core.material.side=THREE.DoubleSide;
  const g=grp(ring,top,core);g.rotation.x=.5;return g},
scrap(){const r1=mat("#7a4a2c",40,101,(x,w,h,r)=>wear(x,w,h,r,60,.4)),r2=mat("#6c6e6a",30,102,(x,w,h,r)=>wear(x,w,h,r,60,.4)),r3=mat("#8a5a34",40,103);
  const a=box([-5,0,-3],[4,.6,3],r1);a.rotation.z=.12;const b=box([-3,1,-2],[5,1.5,2.6],r2);b.rotation.y=.6;b.rotation.z=-.2;const c=box([-1,2,-1],[1,7,1],r3);c.rotation.z=.9;return grp(a,b,c)},
powder(){const t=mat("#3e4a3a",14,111),lab=new THREE.MeshLambertMaterial({map:canvasTex(32,16,(x,w,h)=>{x.fillStyle="#d8cfb2";x.fillRect(0,0,w,h);x.fillStyle="#7a1d14";x.fillRect(0,2,w,3);x.fillRect(0,11,w,3);x.fillStyle="#222";x.fillRect(4,7,24,2);wear(x,w,h,seeded(5),40,.3)})});
  const c=new THREE.Mesh(new THREE.CylinderGeometry(3.2,3.2,5,16),[lab,t,t]);c.position.y=3;return grp(c,cyl(3.3,.6,t,16,0),cyl(3.4,1,mat("#2b3329",10,112),16,5.4))},
batteries(){const k=mat("#1d1d1f",6,121),c=mat("#c88a1c",14,122),t=mat("#b8b8b8",6,123);
  const one=()=>grp(cyl(1,5.6,k,12,0),cyl(1.02,2,c,12,3.6),cyl(.4,.5,t,8,5.6));const a=one(),b=one();a.position.x=-1.2;b.position.x=1.2;b.rotation.z=-.15;const g=grp(a,b);g.rotation.z=1.1;return g},
matches(){const b=mat("#c9b178",18,131,(x)=>{x.fillStyle="#7a2416";x.fillRect(0,0,16,5);x.fillStyle="#1a1a1a";x.fillRect(2,9,12,2)}),s=mat("#3a2c22",10,132),w=mat("#d8c39a",10,133),h=mat("#8a1a10",10,134);
  const g=grp(box([-4,0,-2.5],[4,2.2,2.5],b),box([-4.05,.3,-2.55],[-3.7,1.9,2.55],s));const tray=grp(box([-3.8,.2,-2.4],[3.8,1.6,2.4],w));tray.position.x=3;g.add(tray);[-1.6,-.6,.4,1.4].forEach(z=>{g.add(box([5,1.4,z-.25],[8.5,1.8,z+.15],w),box([8.5,1.3,z-.35],[9.2,1.9,z+.25],h))});return g},
rags(){const a=mat("#8a7a68",30,141,(x,w,h,r)=>{wear(x,w,h,r,60,.35);x.fillStyle="rgba(110,30,20,.35)";x.fillRect(3,4,5,4)}),b=mat("#6e6658",26,142);
  const p=box([-4,0,-3],[4,1.2,3],a);const q=box([-3,1,-2.5],[3.5,2.4,2],b);q.rotation.y=.4;q.rotation.z=.1;const r=box([-2,2.2,-1.5],[2.5,3.4,1.5],a);r.rotation.y=-.3;return grp(p,q,r)},
spool(){const w=mat("#b89a6a",14,151),t=mat("#e8e2d2",10,152,(x)=>{x.fillStyle="rgba(0,0,0,.12)";for(let i=0;i<16;i+=2)x.fillRect(0,i,16,1)}),n=mat("#cfd2d6",6,153);
  const g=grp(cyl(2.8,.7,w,14,0),cyl(2.1,4.2,t,14,.7),cyl(2.8,.7,w,14,4.9));const nd=box([-.15,0,-.15],[.15,7,.15],n);nd.position.set(3.5,0,1);nd.rotation.z=.3;g.add(nd);return g},
jerky(){const p=mat("#7b5a3a",20,161,(x)=>{x.fillStyle="#c9a24a";x.fillRect(2,3,12,4);x.fillStyle="#3a2414";x.fillRect(4,10,8,3)});return grp(box([-3.5,0,-.8],[3.5,9,.8],p),box([-3.6,8.6,-.9],[3.6,9.2,.9],mat("#5c4128",10,162)))},
};

/* ---------- the survivor, dressed in the kit ---------- */
async function figure(){
  const skin=mat("#b88a6c",12,1),hair=mat("#2e2218",18,2);
  const faceTex=canvasTex(8,8,(x)=>{x.fillStyle="#b88a6c";x.fillRect(0,0,8,8);x.fillStyle="#2e2218";x.fillRect(0,0,8,2);x.fillRect(0,2,1,2);x.fillRect(7,2,1,2);x.fillStyle="#efe9de";x.fillRect(1,4,2,1);x.fillRect(5,4,2,1);x.fillStyle="#2a3a4a";x.fillRect(2,4,1,1);x.fillRect(5,4,1,1);x.fillStyle="#8f604a";x.fillRect(3,5,2,1);x.fillStyle="#5e3426";x.fillRect(3,6,2,1);x.fillStyle="#3d2f24";x.fillRect(1,7,6,1);x.fillStyle="rgba(60,40,30,.35)";x.fillRect(1,6,1,2);x.fillRect(6,6,1,2)});
  const jk=mat("#59583a",22,21,(x,w,h,r)=>{wear(x,w,h,r,30,.25)}),jkd=mat("#4e4d33",20,22),tr=mat("#4a4b44",22,41,(x,w,h,r)=>wear(x,w,h,r,30,.25)),bt=mat("#221d18",16,51,(x,w,h,r)=>wear(x,w,h,r,40,.35)),gl=mat("#4a3826",20,31),
    hm=mat("#4a5132",22,11,(x,w,h,r)=>wear(x,w,h,r,40,.3)),hmd=mat("#2a2d22",10,12),olive=mat("#4b5230",26,5),dark=mat("#2c311e",10,6),pouch=mat("#5c6439",24,7),strap=mat("#3a3d2a",12,8),pack=mat("#5a5a3c",24,9,(x,w,h,r)=>wear(x,w,h,r,40,.3)),bandage=mat("#e9e2d0",10,10,(x)=>{x.fillStyle="rgba(150,30,20,.55)";x.fillRect(5,6,5,3)});
  const hero=new THREE.Group();
  // legs
  const sole=mat("#141210",6,52);
  const leg=()=>grp(box([-2,-12,-2],[2,0,2],tr),box([-2.12,-12,-2.12],[2.12,-6.4,2.12],bt),box([-2.15,-12.7,-2.3],[2.15,-11.8,2.9],sole),box([-2.15,-7,-2.15],[2.15,-6.4,2.15],sole));
  const ll=leg(),rl=leg();ll.position.set(-2,12,0);rl.position.set(2,12,0);ll.rotation.x=.06;rl.rotation.x=-.08;
  hero.add(ll,rl);
  const body=new THREE.Group();hero.add(body);
  body.add(box([-4,12,-2],[4,24,2],jk),box([-4.2,11.6,-2.2],[4.2,13,2.2],mat("#2a241c",8,43)));
  // plate carrier with pouches
  body.add(box([-4.5,14,-2.7],[4.5,22.6,2.7],olive),box([-4.6,22.4,-2.6],[-2.2,24.3,2.6],dark),box([2.2,22.4,-2.6],[4.6,24.3,2.6],dark));
  [-3.2,-1.05,1.1].forEach(x=>body.add(box([x,14.4,2.7],[x+2,18.4,3.9],pouch)));body.add(box([-4,19,2.7],[4,21.6,3.3],pouch),box([-1.2,19.4,3.3],[1.2,21.2,3.5],mat("#8f8466",10,44)));
  // rucksack on the back
  body.add(box([-4,13,-7.4],[4,24.5,-2.7],pack),box([-3.4,13.6,-8.6],[3.4,19,-7.4],pack),box([-4.2,23,-7.6],[4.2,25,-2.6],mat("#4a4a30",18,15)),box([-3.6,14,-2.8],[-2.6,24,-2.6],strap),box([2.6,14,-2.8],[3.6,24,-2.6],strap));
  // arms
  const la=grp(box([-2,-12,-2],[2,0,2],jk),box([-2.1,-12,-2.1],[2.1,-8.2,2.1],gl),box([-2.08,-7.2,-2.08],[2.08,-4.6,2.08],bandage)),ra=grp(box([-2,-12,-2],[2,0,2],jk),box([-2.1,-12,-2.1],[2.1,-8.2,2.1],gl));
  la.position.set(-6,24,0);ra.position.set(6,24,0);la.rotation.z=-.1;la.rotation.x=.08;ra.rotation.z=.12;ra.rotation.x=-.18;body.add(la,ra);
  // head and helmet
  const head=new THREE.Group();head.position.set(0,24,0);body.add(head);
  const hmesh=new THREE.Mesh(new THREE.BoxGeometry(8,8,8),[skin,skin,hair,skin,new THREE.MeshLambertMaterial({map:faceTex}),hair]);hmesh.position.set(0,4,0);head.add(hmesh);
  head.add(box([-4.6,5.6,-4.6],[4.6,8.4,4.6],hm),box([-4.2,8.4,-4.2],[4.2,9.6,4.2],hm),box([-3.4,9.6,-3.4],[3.4,10.4,3.4],hm),box([-4.8,5.3,-4.8],[4.8,5.9,4.8],hmd),box([-1.3,6.4,4.6],[1.3,8.4,5.3],mat("#1b1d1a",8,14)),box([-4.7,1.4,-.5],[-4.45,5.6,.5],strap),box([4.45,1.4,-.5],[4.7,5.6,.5],strap));
  head.rotation.y=.18;head.rotation.x=.04;
  // hunting rifle slung across the back
  const rifle=grp(await mcModel(A.models.hunting_rifle),await mcModel(A.models.hunting_rifle_mag),await mcModel(A.models.hunting_rifle_scope));
  rifle.children.forEach(c=>c.position.set(-8,-8,-8));
  const sling=new THREE.Group();sling.add(rifle);rifle.scale.setScalar(1.25);rifle.rotation.y=Math.PI/2;
  sling.position.set(0,19,-9.4);sling.rotation.z=.85;body.add(sling);
  // pistol holster on the right thigh
  const hol=grp(box([-.6,-5,-1.4],[.6,0,1.4],mat("#1d1b17",10,16)));hol.position.set(4.5,11,0.3);hero.add(hol);
  // a faint red glow round the bandaged forearm
  const glow=new THREE.MeshBasicMaterial({color:0xff2a12,transparent:true,opacity:.16,depthWrite:false,blending:THREE.AdditiveBlending});
  [0.4,0.9,1.5].forEach((g,i)=>{const m=box([-2-g,-12-g,-2-g],[2+g,-3.5+g,2+g],glow.clone());m.material.opacity=[.09,.05,.025][i];la.add(m)});
  hero.rotation.y=-.55;
  return hero;
}

/* ---------- rendering ---------- */
const renderer=new THREE.WebGLRenderer({antialias:true,alpha:true,preserveDrawingBuffer:true});renderer.setPixelRatio(1);renderer.setClearColor(0,0);document.body.appendChild(renderer.domElement);
function lights(scene,mood,g=1){
  scene.add(new THREE.HemisphereLight(0xd8dcd0,0x2a2620,(mood?.6:1.15)*g));
  const k=new THREE.DirectionalLight(0xffe2bc,(mood?1.2:1.35)*g);k.position.set(-6,10,9);scene.add(k);
  const r=new THREE.DirectionalLight(0x9fc0ff,(mood?.95:.5)*g*1.4);r.position.set(9,4,-8);scene.add(r);
}
// fit: ortho camera framing the object's projected bounds with padding
async function render(job){
  const [W,H]=job.size,scene=new THREE.Scene();lights(scene,job.mood,job.gain||1);
  let obj;
  if(job.figure)obj=await figure();
  else if(job.proc)obj=P[job.proc]();
  else{obj=new THREE.Group();for(const k of job.models){const m=await mcModel(A.models[k]);m.position.set(-8,-8,-8);obj.add(m)}}
  const outer=new THREE.Group(),mid=new THREE.Group();mid.add(obj);outer.add(mid);
  const gui=!job.rot&&job.models&&A.models[job.models[0]].gui;
  const rot=job.rot||(gui?gui.rotation:[20,-35,0]);
  mid.rotation.set(rot[0]*DEG,rot[1]*DEG,rot[2]*DEG,"XYZ");outer.rotation.z=(job.tilt||0)*DEG;scene.add(outer);
  outer.updateMatrixWorld(true);
  let cam;
  if(job.persp){
    const bb=new THREE.Box3().setFromObject(outer),c=bb.getCenter(new THREE.Vector3()),sz=bb.getSize(new THREE.Vector3());
    cam=new THREE.PerspectiveCamera(job.persp,W/H,.1,2000);const d=Math.max(sz.y/(2*Math.tan(job.persp*DEG/2))/(1-job.pad),sz.x/(2*Math.tan(job.persp*DEG/2)*W/H)/(1-job.pad));
    cam.position.set(c.x,c.y+(job.camY||0),c.z+d+sz.z/2);cam.lookAt(c.x,c.y,c.z);
  }else{
    const bb=new THREE.Box3().setFromObject(outer),c=bb.getCenter(new THREE.Vector3()),sz=bb.getSize(new THREE.Vector3());
    const pad=job.pad??.1,s=Math.max(sz.x/(W*(1-pad*2)),sz.y/(H*(1-pad*2)));
    cam=new THREE.OrthographicCamera(-W/2*s,W/2*s,H/2*s,-H/2*s,-500,500);cam.position.set(c.x,c.y,c.z+100);cam.lookAt(c.x,c.y,c.z);
  }
  renderer.setSize(W,H,false);renderer.render(scene,cam);return renderer.domElement.toDataURL("image/png");
}
window.setup=a=>{A=a};window.render=render;
