# A Python port of HarvestmanEntity.crawl (+ leg support) in a voxel world, to check it holds surfaces.
import math, sys
import numpy as np
V=np.array
def norm(v):
    l=np.linalg.norm(v); return v if l<1e-9 else v/l
STAND,CLING,CROUCH,MID=2.5,2.05,0.9,0.4
LEGS=[(0.46,-0.08,0.06,1.28,3.6086,0.74,1.75),(0.55,0.12,-0.45,1.28,3.7048,2.36,1.85)]
class World:
    def __init__(s): s.solid=set()
    def box(s,x0,y0,z0,x1,y1,z1):
        for x in range(x0,x1):
            for y in range(y0,y1):
                for z in range(z0,z1): s.solid.add((x,y,z))
    def clip(s,a,b):
        d=b-a; L=np.linalg.norm(d)
        if L<1e-9: return None
        d=d/L; t=0.0; step=0.02
        prev=tuple(np.floor(a).astype(int))
        if prev in s.solid: return None
        while t<=L:
            p=a+d*t; c=tuple(np.floor(p).astype(int))
            if c!=prev and c in s.solid:
                # face normal: which coordinate changed
                n=np.zeros(3)
                for i in range(3):
                    if c[i]!=prev[i]: n[i]=-np.sign(d[i]) if abs(d[i])>0 else 0
                if np.count_nonzero(n)>1:
                    i=int(np.argmax(np.abs(d)*np.abs(n))); m=np.zeros(3); m[i]=n[i]; n=m
                # back off to the face
                return (p-d*step*0.5, n)
            prev=c; t+=step
        return None
    def free(s,lo,hi):
        for x in range(math.floor(lo[0]),math.floor(hi[0])+1):
            for y in range(math.floor(lo[1]),math.floor(hi[1])+1):
                for z in range(math.floor(lo[2]),math.floor(hi[2])+1):
                    if (x,y,z) in s.solid: return False
        return True
def floorness(up):
    x=min(1,max(0,(up[1]-0.55)/0.4)); return x*x*(3-2*x)
def slerp(a,b,t):
    t=min(1,max(0,t)); v=a*(1-t)+b*t
    return b if np.dot(v,v)<1e-8 else norm(v)
class H:
    def __init__(s,w,pos):
        s.w=w; s.c=V(pos,float); s.surf=V([0,1.,0]); s.up=s.surf.copy(); s.fwd=V([0,0,1.])
        s.goal=None; s.goalSpeed=1.9; s.speed=0; s.pivotT=-1; s.dropping=False; s.attached=False; s.fall=0
        s.wantFloor=False; s.lost=0; s.stuck=0; s.log=[]
    def cast(s,a,b): return s.w.clip(a,b)
    def fits(s,c,f):
        for z in (0.5,-0.3,-1.1):
            p=c+f*z
            if not s.w.free(p-0.36,p+0.36): return False
        return True
    def support(s,c,up,f,h):
        x=np.cross(up,f); n=0
        for li in range(4):
            if s.lost>>li&1: continue
            hx,hy,hz,a,lc,ra,rr=LEGS[li&1]; side=-1 if li<2 else 1
            r0=rr*(CLING*0+ (1.85+(1-1.85)*floorness(up)))
            dmin=lc-a+0.45; r=max(r0,math.sqrt(max(0,dmin*dmin-h*h))+0.65)
            Hp=c+x*side*hx+up*hy+f*hz
            off=x*(side*math.sin(ra)*r)-up*h+f*(math.cos(ra)*r)
            P0=c+off; d=P0-Hp
            hit=s.cast(Hp,Hp+d*1.4)
            if hit is None: hit=s.cast(P0+up*1.5,P0-up*2.5)
            if hit is None: hit=s.cast(P0-up*0.6,c-up*(h+0.6))
            if hit is None: hit=s.cast(P0+up*1.0,c-up*h)
            if hit is not None and np.linalg.norm(hit[0]-Hp)<=a+lc-0.05: n+=1
        return n
    def wallward(s,c,lean):
        if getattr(s,'wallDir',None) is not None: return s.wallDir
        a=V([0,1.,0]) if abs(s.surf[1])<0.9 else V([1.,0,0])
        e1=norm(a-s.surf*np.dot(a,s.surf)); e2=np.cross(s.surf,e1); best=None; bs=1e9
        for k in range(8):
            ang=k*math.pi/4; d=e1*math.cos(ang)+e2*math.sin(ang)
            hit=s.cast(c,c+d*16)
            if hit is not None and np.dot(hit[1],d)<-0.5:
                sc=np.linalg.norm(hit[0]-c)-3*np.dot(d,lean)
                if sc<bs: bs=sc; best=d
        s.wallDir=best; s.wallFace=s.surf.copy(); return best
    def height(s,c,d):
        want=STAND+(CLING-STAND)*(1-floorness(s.surf))
        for p in (c,c+d*1.6):
            b=s.cast(p,p-s.surf*6); a=s.cast(p,p+s.surf*6)
            if a is not None and b is not None:
                gap=np.linalg.norm(b[0]-p)+np.linalg.norm(a[0]-p); want=min(want,max(CROUCH,gap-0.8))
        # a low gap coming up (a doorway's lintel): felt for from down by its feet, so it ducks before it gets there
        b=s.cast(c,c-s.surf*6)
        if b is not None:
            base=b[0]+s.surf*0.3
            for k in (0.9,1.8,2.7,-0.7,-1.5):
                p=base+(d*k if k>0 else s.fwd*k)      # and over its tail, still under it
                if s.cast(base,p) is not None: continue
                a=s.cast(p,p+s.surf*6)
                if a is not None: want=min(want,max(CROUCH,np.linalg.norm(a[0]-p)+0.3-0.8))
        return want
    def attach(s,c):
        best=STAND+1.6; face=None
        if s.attached:
            hd=s.cast(c,c-s.surf*(STAND+3.5))
            if hd is not None and np.allclose(hd[1],s.surf): s.pivotT=-1; return
        for n in [V([0,1.,0]),V([0,-1.,0]),V([1.,0,0]),V([-1.,0,0]),V([0,0,1.]),V([0,0,-1.])]:
            hit=s.cast(c,c-n*(STAND+1.6))
            if hit is not None and np.allclose(hit[1],n):
                dd=np.linalg.norm(hit[0]-c)-(0.6 if n[1]>0.5 else 0)
                if dd<best: best=dd; face=n
        s.attached=True; s.pivotT=-1
        if face is None: s.dropping=True
        else: s.surf=face; s.up=face.copy()
    def floorBelow(s,p,depth):
        h=s.cast(p,p-V([0,depth,0])); return h is not None and h[1][1]>0.5
    def overEdge(s,c,nxt,d,want):
        onFloor=s.surf[1]>0.5
        if onFloor:
            dn=s.cast(nxt,nxt-V([0,want+3.2,0]))
            if dn is not None and dn[1][1]>0.5:
                h=nxt[1]-dn[0][1]; return nxt+V([0,min(0.3,max(-0.3,want-h)),0])
        low=nxt-s.surf*(want+0.3)
        wrap=s.cast(low,low-d*2.2); held=s.cast(c,c-s.surf*(want+1.6))
        if wrap is not None and held is not None and np.dot(wrap[1],d)>0.5 and not (s.wantFloor and onFloor and s.goal is not None and s.goal[1]<c[1]-6 and s.floorBelow(nxt,14)):
            n1=wrap[1]; pf=held[0]; pw=wrap[0]
            s.pivotAt=c-s.surf*np.dot(s.surf,c-pf)-n1*np.dot(n1,c-pw)-s.surf*0.3
            s.pivotFrom=s.surf; s.pivotTo=n1; s.pivotT=0
            return s.pivotAt+s.surf*want
        out=nxt+d*1.4
        if onFloor and s.goal is not None and s.goal[1]<c[1]-1.5 and s.floorBelow(out,16) and s.fits(out,d):
            s.dropping=True; s.fwd=d; return out
        return None
    def doFall(s,c):
        s.fall=min(s.fall+0.08,3.5); step=V([0,-s.fall,0])
        fl=s.cast(c,c+V([0,-s.fall-STAND,0]))
        if fl is not None:
            room=c[1]-fl[0][1]-STAND
            if room<=s.fall:
                step=V([0,-max(0,room),0]); s.dropping=False; s.fall=0; s.surf=V([0,1.,0]); s.up=s.surf.copy(); s.pivotT=-1
        s.c=c+step
    def tick(s):
        dt=0.05; c=s.c.copy(); s.t=getattr(s,'t',0)+1
        if not s.attached or (not s.dropping and s.pivotT<0 and s.cast(c,c-s.surf*(STAND+3.5)) is None): s.attach(c)
        if s.dropping: s.doFall(c); return 'fall'
        d=s.fwd.copy(); dist=0
        if s.goal is not None:
            to=s.goal-c; t=to-s.surf*np.dot(to,s.surf); dist=np.linalg.norm(t)
            if dist>1e-3: d=t/dist
        if getattr(s,'wallFace',None) is None or not np.allclose(s.wallFace,s.surf): s.wallDir=None
        off=0 if s.goal is None else np.dot(s.goal-c,s.surf)
        if s.goal is not None and off>0.8 and (dist<3 or s.wallDir is not None):
            wd=s.wallward(c, d if dist>1e-3 else s.fwd)
            if wd is not None: d=wd; dist=max(dist,4)
        else: s.wallDir=None
        d=d-s.surf*np.dot(d,s.surf); d=s.fwd if np.dot(d,d)<1e-6 else norm(d)
        want=s.height(c,d); s.want=want
        top=0 if s.goal is None else min(0.7 if getattr(s,'slow',False) else s.goalSpeed, math.sqrt(2*3*max(0,dist-0.3)))
        s.speed=min(top,s.speed+1.8*dt) if s.speed<top else max(top,s.speed-2.6*dt)
        blocked=False
        if s.pivotT>=0:
            s.pivotT=min(1,s.pivotT+max(0.6,s.speed)*dt/(want*math.pi/2))
            n=slerp(s.pivotFrom,s.pivotTo,s.pivotT); nxt=s.pivotAt+n*want
            if s.pivotT>=1: s.surf=s.pivotTo; s.pivotT=-1
        else:
            if s.speed>0.01:
                ah=s.cast(c,c+d*(CLING+0.25+s.speed*dt))
                if ah is not None:
                    n=ah[1]
                    if -0.5<np.dot(n,d)<0 and np.dot(n,s.surf)<0.5:
                        al=d-n*np.dot(d,n)
                        if np.dot(al,al)>1e-4: d=norm(al)
                    elif np.dot(n,d)<=-0.5 and np.dot(n,s.surf)<0.5:
                        if getattr(s,'stay',False): pass       # its route goes on along this face: it carries on (ducking under)
                        elif not (s.wantFloor and s.surf[1]>0.5) and s.fits(c,s.fwd):
                            s.surf=n; s.speed*=0.6; s.log.append('climb onto %s'%n)
                        else: blocked=True
            nxt=c if blocked else c+d*s.speed*dt
            un=s.cast(nxt+s.surf*0.4,nxt-s.surf*(want+1.6))
            if un is None:
                r=s.overEdge(c,nxt,d,want)
                if r is None: nxt=c; blocked=True
                else:
                    nxt=r
                    if s.pivotT==0: s.log.append('pivot %s->%s'%(s.pivotFrom,s.pivotTo))
                    if s.dropping: s.c=nxt; return 'drop'
            else:
                h=np.dot(un[0]-nxt,-s.surf); nxt=nxt+s.surf*min(0.3,max(-0.3,(want-h)*min(1,6*dt)))
        if s.pivotT<0 and nxt is not c:
            f2=norm(s.fwd-s.surf*np.dot(s.fwd,s.surf)) if np.linalg.norm(s.fwd-s.surf*np.dot(s.fwd,s.surf))>1e-3 else norm(np.cross(s.surf,V([1.,0,0])) if abs(s.surf[0])<0.9 else np.cross(s.surf,V([0,0,1.])))
            sup=s.support(nxt,s.surf,f2,want)
            if not s.fits(nxt,f2) or sup<3:
                nxt=c; blocked=True; s.speed=0
                # creeping up to an outer edge its legs can't plant past: it swings round on to the face beyond now
                p=c+d*0.6
                if sup<3 and s.cast(p+s.surf*0.4,p-s.surf*(want+1.6)) is None:
                    r=s.overEdge(c,p,d,want)
                    if r is not None and s.pivotT==0: nxt=r; blocked=False
                    elif r is not None and s.dropping: s.c=r; return 'drop'
        s.c=nxt
        s.up=slerp(s.up, slerp(s.pivotFrom,s.pivotTo,s.pivotT) if s.pivotT>=0 else s.surf, 8*dt)
        # face toward d within the plane of up
        f=s.fwd-s.up*np.dot(s.fwd,s.up)
        if np.dot(f,f)<1e-6: f=V([0,1.,0])-s.up*s.up[1] if abs(s.up[1])<0.9 else V([0,0,1.])-s.up*s.up[2]
        f=norm(f); wnt=d-s.up*np.dot(d,s.up)
        if s.speed>0.05 and np.dot(wnt,wnt)>1e-6:
            wnt=norm(wnt); ang=math.acos(max(-1,min(1,np.dot(f,wnt)))); mx=(3.8 if getattr(s,'slow',False) else 1.9)*dt
            f=wnt if ang<=mx else slerp(f,wnt,mx/ang)
        nf=norm(f-s.up*np.dot(f,s.up))
        # it only turns where its body has the room to (its tail doesn't swing into a wall)
        if s.pivotT>=0 or s.fits(s.c,nf) or not s.fits(s.c,s.fwd): s.fwd=nf
        if s.goal is not None and blocked:
            s.stuck+=1
            if s.stuck>40: s.goal=None; s.stuck=0; return 'gaveup'
        else: s.stuck=0
        return 'blocked' if blocked else 'ok'
    def check(s):
        # holds: something solid within want+0.7 straight under it (or swinging round an edge); body clear
        if s.dropping or s.pivotT>=0: return None
        hit=s.cast(s.c,s.c-s.surf*(s.want+0.8))
        if hit is None: return 'FLOATING'
        if not s.fits(s.c, s.fwd): return 'INSIDE'
        return None
