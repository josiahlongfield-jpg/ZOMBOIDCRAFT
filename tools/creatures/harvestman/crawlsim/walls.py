import math, numpy as np
from simcore import *
from route import Route
V=np.array
class Follower:
    """HarvestmanRoute's following: the farthest point of the route it can see, a few cells on."""
    def __init__(s,w): s.r=Route(w); s.pts=None; s.idx=0; s.target=None; s.t=0; s.made=-999
    def aim(s,h,goal):
        s.t+=1
        foot=h.c-h.surf*(getattr(h,'want',STAND)-0.5)
        if s.target is None or np.linalg.norm(goal-s.target)>2 or s.t-s.made>100 and h.stuck>10:
            path=s.r.plan(tuple(np.floor(foot).astype(int)),tuple(np.floor(goal).astype(int)))
            s.pts,s.gates=(None,{}) if path is None else s.r.points(path); s.idx=0; s.target=goal.copy(); s.made=s.t
            s.plen=None if path is None else len(path)
        h.stay=False
        if not s.pts: return goal
        while s.idx<len(s.pts)-1 and s.passed(h,foot): s.idx+=1
        # slow coming up to a gap, so it can line up and get through
        h.slow=any(k in s.gates and np.linalg.norm(s.pts[k]-foot)<4 for k in range(max(0,s.idx-1),min(len(s.pts),s.idx+4)))
        if s.idx in s.r.narrows:
            # through the gap: a point on the line through its middle, a little ahead, so it closes on the line
            p=s.pts[s.idx]; ax=s.gates[s.idx]
            return s.pick(h,foot,p+ax*(np.dot(foot-p,ax)+0.8))
        last=min(len(s.pts)-1,s.idx+6)
        for k in range(s.idx+1,last+1):
            if k in s.gates: last=k; break
        for j in range(last,s.idx,-1):
            p=s.pts[j]
            if np.linalg.norm(p-foot)<7 and s.clear(h,foot,p):
                return s.pick(h,foot,goal if j==len(s.pts)-1 else p)
        return s.pick(h,foot,s.pts[s.idx])
    def passed(s,h,foot):
        p=s.pts[s.idx]
        if s.idx in s.r.narrows:
            return np.dot(foot-p,s.gates[s.idx])>2.1
        if s.idx in s.gates:
            return np.linalg.norm(foot-p)<0.7
        # on to the next once close, or right there when the way on isn't clear for its body (a corner)
        v=foot-p; d=np.linalg.norm(v); flat=np.linalg.norm(v-h.surf*np.dot(v,h.surf))
        return flat<0.6 or d<1.6 and s.clear(h,foot,s.pts[s.idx+1])
    def pick(s,h,foot,p):
        # the route goes on along the face it holds: it doesn't climb what it meets (it ducks through or goes round)
        h.stay=abs(np.dot(p-foot,h.surf))<1.2
        return p
    def clear(s,h,a,b):
        """In sight with room for its body: down the middle and either side."""
        d=b-a; lat=np.cross(d,h.surf); L=np.linalg.norm(lat)
        lat=lat/L*0.7 if L>1e-6 else lat*0
        return all(h.cast(a+o,b+o) is None and tuple(np.floor(a+o).astype(int)) not in h.w.solid for o in (lat*0,lat,-lat))
def run(name,w,start,goal,ticks=2400,route=True):
    h=H(w,start); f=Follower(w); g=V(goal,float); res=None; bad=0
    for t in range(ticks):
        h.goal=f.aim(h,g) if route else g
        r=h.tick(); e=h.check()
        if e: bad+=1
        if r=='gaveup': res='gave up'; break
        to=g-h.c; off=np.dot(to,h.up)
        if -2.8<off<0.8 and np.linalg.norm(to-h.up*off)<1.2: res='reached'; break
    print(f"{name:28s} route={route!s:5} plan={getattr(f,'plen',None)} -> {res} at {np.round(h.c,1)} surf {h.surf} ticks {t} float/inside {bad}")
def ground(): w=World(); w.box(-40,-1,-40,40,0,40); return w
# a long wall 6 high across x, with a door gap at x=8..10 (3 high)
w=ground(); w.box(-30,0,5,8,6,6); w.box(10,0,5,30,6,6); w.box(8,3,5,10,6,6)
for rt in (False,True): run('wall with a door to one side',w,(0,2.5,0),(0,1,12),route=rt)
# a closed house (roofed) with one doorway on the far side; prey inside
w=ground(); w.box(-6,0,4,6,5,5); w.box(-6,0,14,6,5,15); w.box(-6,0,4,-5,5,15); w.box(5,0,4,6,5,15); w.box(-6,5,4,6,6,15)
for c in range(-1,1): w.solid.discard((c,0,14)); w.solid.discard((c,1,14)); w.solid.discard((c,2,14))
for rt in (False,True): run('house, door round the back',w,(0,2.5,-4),(0,1,9),route=rt)
# a wall 4 high and nothing else: over the top is the way
w=ground(); w.box(-30,0,5,8,6,6); w.box(9,0,5,30,6,6); w.box(8,2,5,9,6,6)
w.box(-30,6,5,30,30,6)
run('tall wall, one-wide doorway (2 high)',w,(0,2.5,0),(0,1,12))
w=ground(); w.box(-30,0,5,30,4,6)
run('low wall, over the top',w,(0,2.5,0),(0,1,12))
# a sealed box: no way in
w=ground(); w.box(-4,0,4,4,5,5); w.box(-4,0,12,4,5,13); w.box(-4,0,4,-3,5,13); w.box(3,0,4,4,5,13); w.box(-4,5,4,4,6,13)
run('sealed box (no way in)',w,(0,2.5,-4),(0,1,8),ticks=600)
