# Python port of HarvestmanRoute: A* over the cells its body can be in, holding a floor, wall or ceiling.
import heapq, math
import numpy as np
DIRS=[(0,1,0),(0,-1,0),(1,0,0),(-1,0,0),(0,0,1),(0,0,-1)]
MOVES=[d for d in DIRS]
for a in range(3):
    for b in range(a+1,3):
        for sa in (1,-1):
            for sb in (1,-1):
                m=[0,0,0]; m[a]=sa; m[b]=sb; MOVES.append(tuple(m))
MAX=12000; RANGE=48; DROP=16
class Route:
    def __init__(s,w): s.w=w; s.cache={}
    def solid(s,p): return p in s.w.solid
    def hold(s,p):
        """0 not a place it can be, else its cost factor: 1 on a floor, 1.4 on a wall, 2 on a ceiling."""
        if p in s.cache: return s.cache[p]
        r=0
        if not s.solid(p):
            for d,f in (((0,1,0),1.0),((1,0,0),1.4),((-1,0,0),1.4),((0,0,1),1.4),((0,0,-1),1.4),((0,-1,0),2.0)):
                below=(p[0]-d[0],p[1]-d[1],p[2]-d[2]); off=(p[0]+d[0],p[1]+d[1],p[2]+d[2])
                if s.solid(below) and not s.solid(off): r=f; break
        s.cache[p]=r; return r
    def near(s,p,r=3):
        best=None; bd=1e9
        for dx in range(-r,r+1):
            for dy in range(-r,r+1):
                for dz in range(-r,r+1):
                    q=(p[0]+dx,p[1]+dy,p[2]+dz)
                    if s.hold(q):
                        d=dx*dx+dy*dy*1.2+dz*dz
                        if d<bd: bd=d; best=q
        return best
    def plan(s,start,goal):
        a=s.near(start,2); b=s.near(goal,3)
        if a is None or b is None: return None
        g={a:0.0}; came={}; openq=[(0,a)]; n=0
        def h(p): return math.dist(p,b)
        while openq:
            f,p=heapq.heappop(openq)
            if p==b:
                out=[p]
                while p in came: p=came[p]; out.append(p)
                return out[::-1]
            n+=1
            if n>MAX: return None
            gp=g[p]; hp=s.hold(p)
            for m in MOVES:
                q=(p[0]+m[0],p[1]+m[1],p[2]+m[2])
                if max(abs(q[i]-a[i]) for i in range(3))>RANGE: continue
                hq=s.hold(q)
                if not hq: continue
                if sum(map(abs,m))==2:
                    # round an edge or across a corner: not squeezed between two solid blocks
                    i,j=[k for k in range(3) if m[k]]
                    c1=list(p); c1[i]+=m[i]; c2=list(p); c2[j]+=m[j]
                    # (flat across a floor, wall or ceiling it can't cut a corner; over an edge one side is the edge)
                    if s.solid(tuple(c1)) and s.solid(tuple(c2)) or m[1]==0 and (s.solid(tuple(c1)) or s.solid(tuple(c2))): continue
                cost=math.sqrt(sum(map(abs,m)))*(hp+hq)/2
                s.relax(g,came,openq,p,q,gp+cost,h)
            # off a floor's edge, down to the floor below
            if hp==1.0:
                for d in DIRS[2:]:
                    q=(p[0]+d[0],p[1],p[2]+d[2])
                    if s.solid(q) or s.solid((q[0],q[1]+1,q[2])) or not (s.solid((q[0],q[1]-1,q[2]))==False): continue
                    for k in range(2,DROP+1):
                        l=(q[0],q[1]-k,q[2])
                        if s.solid(l): break
                        if s.solid((l[0],l[1]-1,l[2])):
                            s.relax(g,came,openq,p,l,gp+2+0.25*k,h); break
        return None
    def centre(s,p):
        """A route point: the cell's middle, moved off a solid side toward open room (the middle of a door)."""
        c=np.array(p,float)+0.5
        for i in range(3):
            lo=list(p); lo[i]-=1; hi=list(p); hi[i]+=1
            a,b=s.solid(tuple(lo)),s.solid(tuple(hi))
            if a and not b: c[i]+=0.3
            elif b and not a: c[i]-=0.3
        # an outside corner (solid only across the diagonal): out round it, so its long body clears the corner
        for i,j in ((0,2),(0,1),(1,2)):
            for si in (1,-1):
                for sj in (1,-1):
                    q=list(p); q[i]+=si; q[j]+=sj
                    qi=list(p); qi[i]+=si; qj=list(p); qj[j]+=sj
                    if s.solid(tuple(q)) and not s.solid(tuple(qi)) and not s.solid(tuple(qj)):
                        c[i]-=si*0.35; c[j]-=sj*0.35
        return c
    def narrow(s,p):
        """A doorway, a gap or a corridor: solid both sides across one way, open both ways along another. The axis it
        runs along, or None."""
        for i in (0,2,1):
            for j in (0,2,1):
                if i==j: continue
                a=list(p); a[j]-=1; b=list(p); b[j]+=1
                c=list(p); c[i]-=1; d=list(p); d[i]+=1
                if s.solid(tuple(a)) and s.solid(tuple(b)) and not s.solid(tuple(c)) and not s.solid(tuple(d)):
                    return i
        return None
    def points(s,path):
        """The route as points to make for. A doorway gets a point lined up two cells before it, its gate, so it comes at
        the gap square on rather than across the jamb."""
        out=[]; gates={}; s.narrows=set()
        for k,p in enumerate(path):
            c=s.centre(p)
            ax=s.narrow(p) if k>0 else None
            if ax is not None and (not out or s.narrow(path[k-1]) is None):
                sign=np.sign(c[ax]-out[-1][ax]) if out else 0
                if sign!=0:
                    g=c.copy(); g[ax]-=sign*2.6
                    if tuple(np.floor(g).astype(int)) not in s.w.solid:
                        # the points already closer in than the gate go: it lines up first
                        while out and (c[ax]-out[-1][ax])*sign<2.6 and len(out)-1 not in gates: out.pop()
                        gates[len(out)]=np.eye(3)[ax]*sign; out.append(g)
                        gates[len(out)]=-np.eye(3)[ax]*sign*0      # placeholder, set below
                        out.append(c); gates[len(out)-1]=np.eye(3)[ax]*sign; s.narrows.add(len(out)-1); continue
            out.append(c)
        return out,gates
    def relax(s,g,came,openq,p,q,ng,h):
        if ng<g.get(q,1e18):
            g[q]=ng; came[q]=p; heapq.heappush(openq,(ng+h(q),q))
