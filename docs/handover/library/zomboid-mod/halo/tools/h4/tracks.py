import json,wave,numpy as np
FPS=30; w=wave.open('talk.wav'); sr=w.getframerate(); x=np.frombuffer(w.readframes(w.getnframes()),np.int16).astype(float)/32768
N=int(len(x)/sr*FPS); hop=sr//FPS; t=np.arange(N)/FPS
env=np.array([np.sqrt(np.mean(x[i*hop:(i+1)*hop]**2)) for i in range(N)]); env=np.clip(env/np.percentile(env[env>0.005],95),0,1)**0.6
L1=1.5; L2=1.5+4.246+0.9
W1=[('a',0.0),('topographical',0.14),('scan',0.8),('of',1.26),('the',1.44),('area',1.54),('shows',1.96),('a',2.32),('break',2.52),('in',2.66),('the',2.84),('foliage',2.96),('north',3.32),('of',3.62),('here',3.86)]
W2=[('a',0.0),('lot',0.1),('can',0.26),('happen',0.44),('in',0.76),('four',0.94),('years',1.16),('either',1.74),('way',1.9),("he's",2.2),('probably',2.44),('not',2.66),('alone',2.96),('we',3.56),('should',3.62),('be',3.76),('careful',3.9)]
def ends(W,dur):
    out=[]
    for i,(w_,s) in enumerate(W):
        e=W[i+1][1] if i+1<len(W) else dur
        e=min(e,s+0.6) if i+1<len(W) and W[i+1][1]-s>0.6 else e
        out.append((w_,s,e))
    return out
V={'a':(0.95,0.15,0,0),'e':(0.55,0.75,0,0),'i':(0.5,0.85,0,0),'y':(0.45,0.8,0,0),'o':(0.75,0,0.7,0),'u':(0.4,0,1,0),'w':(0.3,0,1,0),
   'm':(0,0,0,0),'b':(0,0,0,0),'p':(0,0,0,0),'f':(0.12,0.2,0,1),'v':(0.12,0.2,0,1),'r':(0.35,0,0.45,0),'l':(0.45,0.3,0,0),'th':(0.3,0.3,0,0.4),'sh':(0.3,0,0.6,0),'ch':(0.3,0,0.5,0)}
def phon(word):
    w_=word.lower().replace("'",''); out=[]; i=0
    while i<len(w_):
        two=w_[i:i+2]
        if two in ('th','sh','ch','ph','oo','ee','ea','ou','ow'):
            out.append({'ph':'f','oo':'u','ee':'i','ea':'e','ou':'o','ow':'o'}.get(two,two)); i+=2; continue
        c=w_[i]; out.append(c if c in V else 'k'); i+=1
    # collapse silent trailing e
    if len(out)>2 and out[-1]=='e': out=out[:-1]
    return out
jaw=np.zeros(N); wide=np.zeros(N); rnd=np.zeros(N); lipIn=np.zeros(N); cnt=np.zeros(N)
for off,W,dur in ((L1,W1,4.246),(L2,W2,4.313)):
    for w_,s,e in ends(W,dur):
        ph=phon(w_); n=len(ph)
        for k,p in enumerate(ph):
            a,b=off+s+(e-s)*k/n, off+s+(e-s)*(k+1)/n
            j,wd,r,li=V.get(p,(0.3,0.1,0,0))
            i0,i1=int(a*FPS),max(int(a*FPS)+1,int(b*FPS))
            jaw[i0:i1]+=j; wide[i0:i1]+=wd; rnd[i0:i1]+=r; lipIn[i0:i1]+=li; cnt[i0:i1]+=1
cnt[cnt==0]=1; jaw/=cnt; wide/=cnt; rnd/=cnt; lipIn/=cnt
def sm(a,s=1.2):
    k=np.exp(-0.5*(np.arange(-4,5)/s)**2); k/=k.sum(); return np.convolve(a,k,'same')
jaw=sm(jaw*(0.35+0.65*env)); wide=sm(wide)*0.8; rnd=sm(rnd)*0.9; lipIn=sm(lipIn)
# expression keys: (time, value) per channel, linear interp + smoothing
def key(pts):
    ts=[p[0] for p in pts]; vs=[p[1] for p in pts]; return sm(np.interp(t,ts,vs),3)
T1=lambda s:L1+s; T2=lambda s:L2+s
E={
 'browUL':key([(0,0),(T1(0),0.2),(T1(0.2),0.7),(T1(0.9),0.55),(T1(1.6),0.15),(T1(2.5),0.45),(T1(2.9),0.1),(T1(3.9),0.3),(T1(4.3),0.1),(T2(0),0),(12.2,0)]),
 'browUR':key([(0,0),(T1(0),0.2),(T1(0.2),0.6),(T1(0.9),0.5),(T1(1.6),0.1),(T1(2.5),0.4),(T1(2.9),0.1),(T1(3.9),0.25),(T1(4.3),0.1),(T2(0),0),(12.2,0)]),
 'browIn':key([(0,0),(T2(-0.3),0),(T2(0.2),0.55),(T2(1.3),0.7),(T2(1.8),0.3),(T2(3.4),0.2),(T2(3.7),0.75),(T2(4.4),0.6),(12.2,0.3)]),
 'browDn':key([(0,0),(T1(0.7),0),(T1(0.9),0.35),(T1(1.3),0.0),(T2(2.1),0),(T2(2.5),0.55),(T2(3.2),0.5),(T2(3.6),0.15),(12.2,0)]),
 'smile':key([(0,0.15),(T1(0),0.2),(T1(0.5),0.3),(T1(2.4),0.35),(T1(3.3),0.25),(T1(4.3),0.55),(T2(-0.2),0.4),(T2(0.2),0.05),(12.2,0)]),
 'frown':key([(0,0),(T2(0.4),0),(T2(1.1),0.4),(T2(1.6),0.15),(T2(2.5),0.35),(T2(3.6),0.3),(T2(4.0),0.5),(12.2,0.35)]),
 'squint':key([(0,0),(T1(0.7),0),(T1(0.95),0.4),(T1(1.4),0.1),(T2(2.4),0.1),(T2(2.8),0.45),(T2(3.4),0.2),(T2(4.0),0.35),(12.2,0.2)]),
}
# blinks: natural + phrase boundaries
bl=np.zeros(N)
for b0 in [0.9,T1(1.3),T1(2.3),T1(4.4),T2(1.45),T2(3.3),T2(4.6)]:
    bl+=np.exp(-0.5*((t-b0-0.07)/0.045)**2)
bl=np.clip(bl,0,1)
# head: nods on stressed words, tilt for concern, turn gently
nod=np.zeros(N)
for s,a in [(T1(0.85),0.03),(T1(2.55),0.035),(T1(3.35),0.03),(T2(0.95),0.025),(T2(2.7),0.04),(T2(3.95),0.045)]:
    nod+=a*np.exp(-0.5*((t-s)/0.12)**2)
tilt=key([(0,0),(T1(0),0.02),(T1(4),-0.01),(T2(0),0.02),(T2(1.8),0.06),(T2(3.5),0.03),(12.2,0.04)])
look=key([(0,-0.12),(T1(0.5),-0.05),(T1(3.2),-0.18),(T2(0),-0.1),(T2(2.0),0.02),(T2(3.6),-0.08),(12.2,-0.1)])
# eyes: glance up-left while "accessing" the scan, down when wistful, to camera when warning
eyeX=key([(0,0),(T1(0.6),0),(T1(0.8),0.12),(T1(1.5),0.1),(T1(1.8),-0.02),(T2(0.9),0.06),(T2(1.6),0.04),(T2(1.8),-0.02),(12.2,0)])
eyeY=key([(0,0),(T1(0.6),0),(T1(0.8),-0.08),(T1(1.5),-0.06),(T1(1.8),0),(T2(0.6),0),(T2(0.9),0.06),(T2(1.6),0.05),(T2(1.8),0),(12.2,0)])
out=dict(n=N,jaw=(jaw*0.11).round(4).tolist(),nod=nod.round(4).tolist(),tilt=tilt.round(4).tolist(),look=look.round(4).tolist(),eyeX=eyeX.round(4).tolist(),eyeY=eyeY.round(4).tolist(),
 face={k:np.clip(v,0,1).round(3).tolist() for k,v in dict(E,wide=wide,round=rnd,lipIn=lipIn,blinkL=bl,blinkR=bl).items()})
json.dump(out,open('talk2.json','w')); print(N, round(max(out['jaw']),3))
