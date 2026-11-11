// ===== FULL HALO 4 RECIPE (?full=1) : ported from the decompiled storm_cortana shaders =====
const FULL=P.has('full');
const T2={ bodyDiffS:tx('storm_cortana_default_body_diff'), bodyAmb:tx('storm_cortana_default_body_mask_diff',false),
  headW:tx('storm_cortana_default_head_wrinkle_normal_fixed',false), hairCtl:tx('storm_cortana_default_hair_control',false),
  eyeCtl:tx('storm_cortana_default_eye_control',false), irisN:tx('storm_cortana_default_eye_iris_normal_fixed',false),
  corneaN:tx('storm_cortana_default_eye_cornea_normal_fixed',false), hairS:tx('storm_cortana_default_hair_diff'),
  longhorz:tx('longhorz_sparse_noise512',false) };
const LP=(P.get('lights')||'0.05,0.55,1;-0.9,0.2,0.3;0.7,0.4,-0.6').split(';').map(t=>new THREE.Vector3(...t.split(',').map(Number)).normalize());
const LU={L1:{value:LP[0]},L2:{value:LP[1]},L3:{value:LP[2]},Lk:{value:new THREE.Vector3(...(P.get('lk')||'1.5,0.65,0.85').split(',').map(Number))},
  wrinkleK:{value:0}, flowSpeed:{value:+(P.get('fs')||0.35)}, expo:{value:+(P.get('ex')||1.0)}, neckK:{value:P.has('noneck')?0:1}};
const vsFull=vs.replace('varying vec2 vUv;','attribute vec2 uv2x; varying vec2 vUv,vUv2;').replace('void main(){ vUv=uv;','void main(){ vUv=uv; vUv2=uv2x;');
const FCOM=`
precision highp float;
uniform float time,rez,minY,maxY,glow; varying vec2 vUv,vUv2; varying vec3 vN,vV,vW;
uniform sampler2D ramp,noise; uniform vec3 L1,L2,L3,Lk; uniform float wrinkleK,flowSpeed,expo,neckK;
float sat(float x){return clamp(x,0.0,1.0);}
float rezMask(out float edge){ float h=(vW.y-minY)/(maxY-minY); float n=texture2D(noise,vW.xz*3.0+vec2(0.0,time*0.2)).r*0.08;
  float d=rez-(h+n); edge=smoothstep(0.06,0.0,abs(d))*step(-0.06,d); return step(0.0,d); }
// tangent frame from derivatives: Tu (v4 in the game = follows U), Tv (v3 = follows V)
mat3 tbn(vec3 N){ vec3 dp1=dFdx(vW), dp2=dFdy(vW); vec2 du1=dFdx(vUv), du2=dFdy(vUv);
  vec3 a=cross(dp2,N), b=cross(N,dp1); vec3 Tu=a*du1.x+b*du2.x; vec3 Tv=a*du1.y+b*du2.y;
  float iv=inversesqrt(max(max(dot(Tu,Tu),dot(Tv,Tv)),1e-12)); return mat3(Tu*iv,Tv*iv,N); }
vec3 geoN(){ vec3 n=normalize(vN); return gl_FrontFacing?n:-n; }
// game: n = x*Tu + y*Tv + sqrt(1-x^2-y^2)*N
vec3 nmap(mat3 F, vec2 xy){ return normalize(F[0]*xy.x+F[1]*xy.y+F[2]*sqrt(sat(1.0-dot(xy,xy)))); }
vec2 nxy(sampler2D s, vec2 uv){ return texture2D(s,uv).xy*2.0-1.0; }
vec3 rampAt(sampler2D r, float x, float row){ return texture2D(r,vec2(sat(x),row)).rgb; }
float blinn(vec3 N, vec3 L, vec3 V, float pw){ return exp2(log2(max(sat(dot(N,normalize(L+V))),1e-6))*pw-1.6514962); }
vec3 outc(vec3 c){ return pow(max(c,0.0),vec3(1.0/2.2))*expo; }
// neck: head and body meshes overlap from y~1.43 to 1.49; fade the normal-map detail of BOTH across the overlap so both meshes
// shade from the same smooth geometric normal there (the game hides this under the collar line; we cannot)
float neckF(){ return 1.0-neckK*smoothstep(1.36,1.43,vW.y)*(1.0-smoothstep(1.50,1.57,vW.y)); }
`;
// skin: body + head (+hands) share this exact recipe; body adds ambient-wrap and edge fresnel, head adds wrinkle normals
function skinFS(isHead){ return FCOM+`
uniform sampler2D diff,ctl,nrm,flow,amb,wrk;
void main(){ float e; if(rezMask(e)<0.5) discard;
  vec3 Ng=geoN(); mat3 F=tbn(Ng); vec3 V=normalize(vV);
  vec2 xy=nxy(nrm,vUv) ${isHead?'+ wrinkleK*nxy(wrk,vUv)':''}; xy*=neckF();
  vec3 N=nmap(F,xy);
  vec4 cm=texture2D(ctl,vUv);
  ${isHead?'':'float ambM=texture2D(amb,vUv).x*0.4;'}
  // three lights through the lighting ramp (row 0), squared and screen-blended
  vec3 D=vec3(1.0);
  for(int i=0;i<3;i++){ vec3 L=i==0?L1:(i==1?L2:L3); float k=i==0?Lk.x:(i==1?Lk.y:Lk.z);
    float nl=sat(dot(L,N)); float x=nl*k ${isHead?'':'+ sat(1.0-nl*k)*ambM'};
    vec3 r=rampAt(ramp,x,0.0); D*=1.0-r*r; }
  D=1.0-D;
  // specular, masked by the circuit channel (control A), coloured through the ramp's mid row
  float sp=(blinn(N,L1,V,20.0)*Lk.x+blinn(N,L2,V,20.0)*Lk.y+blinn(N,L3,V,20.0)*Lk.z)*cm.a;
  vec3 S=(1.0-(1.0-D)*(1.0-rampAt(ramp,sp,0.5)))*sp;
  // opacity: control map + spec ${isHead?'':'+ edge fresnel on the G areas'}
  float al=sat(dot(cm.zywx,vec4(1.1,0.25,1.1,1.0)));
  ${isHead?'':'float fr=1.0-exp2(log2(max(sat(dot(V,N)),1e-6))*0.5)-0.025; al+=sat(sat(fr/(1.0-0.025))*2.0*cm.y);'}
  al=sat(al+S.x*(1.0-cm.y));
  vec3 c=D+D*cm.a*(vec3(1.0)-D);                 // circuit lines lift toward white
  c*=mix(vec3(1.0),vec3(0.2388,0.3112,0.4591),cm.y); // G mask: deep navy bands
  // dataflow pulses running along the circuit paths (second UV set)
  vec4 fl=texture2D(flow,vUv2); float fm=sat((fl.x-0.98)*255.0);
  float ph=fract(fl.y+fl.w-time*flowSpeed); float pulse=min(exp2(-1500.0*ph*ph),1.0);
  vec3 em=vec3(0.2271,0.4479,0.8513)*(5.0-2.5*cm.y)*fm*pulse;
  vec3 col=c*texture2D(diff,vUv).rgb+em+S*(1.0-cm.y);
  col*=al; if(al<=0.996) col/=3.0;
  gl_FragColor=vec4(outc(col)+vec3(0.6,0.9,1.0)*e*3.0, glow); }`; }
const hairFS=FCOM+`
uniform sampler2D diff,nrm,hctl,rampH;
void main(){ float e; if(rezMask(e)<0.5) discard;
  vec4 cmap=texture2D(diff,vUv); if(cmap.a<0.3) discard;
  vec3 Ng=geoN(); mat3 F=tbn(Ng); vec3 V=normalize(vV);
  vec3 N=nmap(F,nxy(nrm,vUv));
  // strand direction = V-tangent made orthogonal to N, shifted along N by the hair control map (Kajiya-Kay)
  vec3 Tv=F[1]; vec3 t=normalize(cross(cross(N,Tv),N)); t*=sign(dot(t,Tv)+1e-6);
  vec2 hc=texture2D(hctl,vUv).xy; vec3 Ts=normalize(t+N*(hc.y+0.75));
  float s1=pow(sqrt(sat(1.0-pow(dot(normalize(L1+V),Ts),2.0))),170.0), s2=pow(sqrt(sat(1.0-pow(dot(normalize(L2+V),Ts),2.0))),170.0), s3=pow(sqrt(sat(1.0-pow(dot(normalize(L3+V),Ts),2.0))),170.0);
  float sp=hc.x*2.0*(s1*Lk.x+s2*Lk.y+s3*Lk.z);
  vec3 D=vec3(1.0); for(int i=0;i<3;i++){ vec3 L=i==0?L1:(i==1?L2:L3); float k=i==0?Lk.x:(i==1?Lk.y:Lk.z); vec3 r=rampAt(rampH,sat(dot(L,N))*k,0.0); D*=1.0-r*r; } D=1.0-D;
  vec3 c=cmap.rgb*D; c+=sp*c;
  gl_FragColor=vec4(outc(c)+vec3(0.6,0.9,1.0)*e*3.0, glow); }`;
const eyeFS=FCOM+`
uniform sampler2D diff,ectl,irisN,corneaN;
void main(){ float e; if(rezMask(e)<0.5) discard;
  vec3 Ng=geoN(); mat3 F=tbn(Ng); vec3 V=normalize(vV);
  vec3 Nc=nmap(F,nxy(corneaN,vUv)), Ni=nmap(F,nxy(irisN,vUv));
  vec4 cmap=texture2D(diff,vUv); vec3 ec=texture2D(ectl,vUv).xyz;
  float cs=blinn(Nc,L1,V,10.0)*Lk.x+blinn(Nc,L2,V,10.0)*Lk.y+blinn(Nc,L3,V,10.0)*Lk.z;
  float ipw=1.0/max(mix(0.025,0.05,sat(1.0-ec.z)),1e-5);
  float is=blinn(Ni,L1,V,ipw)*Lk.x+blinn(Ni,L2,V,ipw)*Lk.y+blinn(Ni,L3,V,ipw)*Lk.z;
  vec3 D=vec3(1.0); for(int i=0;i<3;i++){ vec3 L=i==0?L1:(i==1?L2:L3); float k=i==0?Lk.x:(i==1?Lk.y:Lk.z); vec3 r=rampAt(ramp,sat(dot(L,Ng))*k,0.0); D*=1.0-r*r; } D=1.0-D;
  vec3 spc=mix(vec3(1.0),cmap.rgb,0.0)*10.0*ec.x*cs + mix(vec3(1.0),cmap.rgb,0.9)*20.0*ec.y*is;
  float fres=pow(1.0-sat(dot(Nc,V)),5.0)*ec.y;
  vec3 col=D*cmap.rgb+spc*0.1+D*fres*0.35;
  gl_FragColor=vec4(outc(col), glow); }`;
function fullMat(kind){
  const u={...U,...LU,faceK:{value:0},ramp:{value:T.ramp},noise:{value:T.noise}};
  let fs;
  if(kind==='body'||kind==='head'){ const h=kind==='head'; fs=skinFS(h);
    Object.assign(u,{diff:{value:h?T.headDiff:T2.bodyDiffS},ctl:{value:h?T.headCtl:T.bodyCtl},nrm:{value:h?T.headN:T.bodyN},flow:{value:T.flow},amb:{value:T2.bodyAmb},wrk:{value:T2.headW}}); }
  else if(kind==='hair'){ fs=hairFS; Object.assign(u,{diff:{value:T2.hairS},nrm:{value:T.hairN},hctl:{value:T2.hairCtl},rampH:{value:T.rampHair}}); }
  else { fs=eyeFS; Object.assign(u,{diff:{value:T.eye},ectl:{value:T2.eyeCtl},irisN:{value:T2.irisN},corneaN:{value:T2.corneaN}}); }
  return new THREE.ShaderMaterial({uniforms:u,vertexShader:vsFull,fragmentShader:fs,transparent:true,
    blending:THREE.NormalBlending,depthWrite:true,side:kind==='hair'?THREE.DoubleSide:THREE.FrontSide,extensions:{derivatives:true}});
}
// ---- final_combine_hologram: screen pass (glitch tears, 400-line scanlines, coarse scan brightness, colour noise) ----
let RT=null, postScene=null, postCam=null, postMat=null;
function setupPost(){
  RT=new THREE.WebGLRenderTarget(W,H,{samples:4}); RT.texture.colorSpace=THREE.SRGBColorSpace;
  postCam=new THREE.OrthographicCamera(-1,1,1,-1,0,1); postScene=new THREE.Scene();
  postMat=new THREE.ShaderMaterial({uniforms:{src:{value:RT.texture},scan:{value:T.scan},nz:{value:T.noise},lh:{value:T2.longhorz},time:U.time,res:{value:new THREE.Vector2(W,H)},bg:{value:new THREE.Color(scene.background)},on:{value:P.has('nopost')?0:1}},
    vertexShader:'varying vec2 v; void main(){ v=uv; gl_Position=vec4(position.xy,0.0,1.0); }',
    fragmentShader:`uniform sampler2D src,scan,nz,lh; uniform float time,on; uniform vec2 res; uniform vec3 bg; varying vec2 v;
    void main(){ vec2 uv=v;
      // glitch tears: sparse long-horizontal noise picks rows to shift sideways (consts 0.0073/0.0145)
      float t1=texture2D(lh,vec2(uv.x*1.225,uv.y*0.9+time*0.11)).r, t2=texture2D(lh,vec2(uv.x,uv.y*2.902+time*0.07)).r;
      vec2 off=vec2(0.0073*t1+0.0145*t2*(t1),0.0)*on;
      vec4 c=texture2D(src,uv+off);
      float m=smoothstep(0.0,4.0/255.0,c.a); // hologram pixels only
      float fine=1.0+0.2*(texture2D(scan,vec2(0.5,uv.y*400.0)).r-1.0); // 400 fine lines
      float coarse=0.5+0.15*(texture2D(scan,vec2(0.5,uv.y-time*0.05)).r-0.5);
      float n=texture2D(nz,uv*vec2(220.0,200.0)/64.0+vec2(0.8627,-2.104)*time).r; n=n*(n-0.5)+0.5;
      vec3 tint=1.0+0.065*(vec3(0.8751,0.8592,0.9406)*n*2.0-1.0);
      vec3 h=c.rgb*mix(1.0,fine,on)*mix(vec3(1.0),tint,on)*mix(1.0,coarse*2.0,on*0.6);
      gl_FragColor=vec4(mix(bg,h,1.0)*m+bg*(1.0-m)*(1.0-c.a)+c.rgb*(1.0-m)*c.a,1.0); }`});
  postScene.add(new THREE.Mesh(new THREE.PlaneGeometry(2,2),postMat));
}
// ===== END FULL =====
