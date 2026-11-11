import json,numpy as np,sys
sys.path.insert(0,'/tmp/claude-0/web')
A=json.load(open('site/anim_v7.json')); names=json.load(open('site/bone_names.json'))
FPS=30
V={'a':(0.95,0.15,0,0),'e':(0.55,0.75,0,0),'i':(0.5,0.85,0,0),'y':(0.45,0.8,0,0),'o':(0.75,0,0.7,0),'u':(0.4,0,1,0),'w':(0.3,0,1,0),
   'm':(0,0,0,0),'b':(0,0,0,0),'p':(0,0,0,0),'f':(0.12,0.2,0,1),'v':(0.12,0.2,0,1),'r':(0.35,0,0.45,0),'l':(0.45,0.3,0,0),'th':(0.3,0.3,0,0.4),'sh':(0.3,0,0.6,0),'ch':(0.3,0,0.5,0)}
def phon(word):
    w_=word.lower().replace("'",''); out=[]; i=0
    while i<len(w_):
        two=w_[i:i+2]
        if two in ('th','sh','ch','ph','oo','ee','ea','ou','ow'):
            out.append({'ph':'f','oo':'u','ee':'i','ea':'e','ou':'o','ow':'o'}.get(two,two)); i+=2; continue
        c=w_[i]; out.append(c if c in V else 'k'); i+=1
    if len(out)>2 and out[-1]=='e': out=out[:-1]
    return out
def sm(a,s=1.2):
    k=np.exp(-0.5*(np.arange(-int(3*s)-1,int(3*s)+2)/s)**2); k/=k.sum(); return np.convolve(np.pad(a,len(k)//2,mode='edge'),k,'valid')
arm=[i for i,n in enumerate(names) if any(s in n for s in ('upperarm','forearm','hand','wrist'))]
# per gesture: implied line, expression keys (fraction of phrase span -> values), head
WAVD='/mnt/user-data/uploads/Zomboidcraft-Desktop/additions/halo-4/cortana/voice/mission/m90/'
WORDS=json.load(open('/tmp/claude-0/web/m90words.json'))
LINE={'over_here':'m90_didactship_portals_defend_nudge_00101.wav','up_here':'m90_didactship_portals_defend_nudge_00102.wav','wait_chief':'m90_didactship_portals_defend_nudge_00100.wav',
 'you_think':'m90_didactship_portals_defend_nudge_00104.wav','you_cant':'m90_didactship_portals_defend_nudge_00106.wav','just_wanna':'m90_didactship_portals_defend_nudge_00103.wav',
 'pick_up':'m90_didactship_portals_defend_00124.wav','reuse':'m90_didactship_portals_defend_00106.wav'}
import subprocess
def env_of(f,F):
    a=np.frombuffer(subprocess.run(['ffmpeg','-loglevel','error','-i',WAVD+f,'-ac','1','-ar','48000','-f','s16le','-'],capture_output=True).stdout,np.int16).astype(float)/32768
    hop=1600; n=len(a)//hop+1; e=np.array([np.sqrt(np.mean(a[i*hop:(i+1)*hop]**2)) if len(a[i*hop:(i+1)*hop]) else 0 for i in range(n)])
    e=np.clip(e/max(1e-6,np.percentile(e[e>0.005],95) if (e>0.005).any() else 1),0,1)**0.6; out=np.zeros(F); out[:min(F,n)]=e[:F]; return out
G={
 'over_here':("",dict(browIn=.9,browUL=.35,browUR=.35,wide=.3,frown=.15),dict(look=.2,tilt=.05)),
 'up_here':("",dict(browUL=.9,browUR=.9,wide=.6,smile=.15),dict(nod=-.12)),
 'wait_chief':("",dict(browIn=.85,browUL=.4,browUR=.4,wide=.45,frown=.2),dict(tilt=.05)),
 'you_think':("",dict(browUL=.85,browUR=.2,squint=.4,smile=.4),dict(tilt=.09,nod=.03)),
 'you_cant':("",dict(browIn=.6,browDn=.35,frown=.35,squint=.25),dict(nod=.05)),
 'just_wanna':("",dict(browIn=.85,browUL=.4,browUR=.4,smile=.15),dict(tilt=-.07)),
 'pick_up':("",dict(browDn=.35,wide=.35,browUL=.2,browUR=.2),dict(nod=.05)),
 'reuse':("",dict(browDn=.55,squint=.35),dict(nod=.04,look=-.1)),
}
out={}
for full,a in A.items():
    k=full.replace('vin_m90_solo_defend_','')
    if k not in G: continue
    F=a['frames']; Q={int(n):np.array(v) for n,v in a['rot'].items()}
    e=np.zeros(F)
    for n in arm:
        if n in Q and len(Q[n])==F:
            d=np.abs(np.sum(Q[n][1:]*Q[n][:-1],1)); e[1:]+=np.degrees(2*np.arccos(np.clip(d,0,1)))
    e=sm(e,3); on=np.flatnonzero(e>0.35*e.max()); s0,s1=on[0],on[-1]
    line,ex,hd=G[k]; t=np.arange(F)
    # expression envelope: rises at gesture onset, holds through, eases out at end
    env=np.clip(np.minimum((t-(s0-8))/10,( (s1+18)-t)/14),0,1); env=env*env*(3-2*env)
    ch={c:0*t.astype(float) for c in ['browUL','browUR','browIn','browDn','smile','frown','squint','wide','round','lipIn','blinkL','blinkR']}
    for c,v in ex.items(): ch[c]=env*v
    W=WORDS[LINE[k]]; line=' '.join(w[0] for w in W); E=env_of(LINE[k],F)
    jaw=np.zeros(F); wd=np.zeros(F); rn=np.zeros(F); li=np.zeros(F); cnt=np.zeros(F)
    for w,ws,we in W:
        ph=phon(''.join(ch for ch in w if ch.isalpha() or ch=="'")) or ['a']
        for j,p in enumerate(ph):
            i0=int((ws+(we-ws)*j/len(ph))*FPS); i1=max(i0+1,int((ws+(we-ws)*(j+1)/len(ph))*FPS))
            jv,wv,rv,lv=V.get(p,(0.3,0.1,0,0)); jaw[i0:i1]+=jv; wd[i0:i1]+=wv; rn[i0:i1]+=rv; li[i0:i1]+=lv; cnt[i0:i1]+=1
    cnt[cnt==0]=1; jaw=sm((jaw/cnt)*(0.35+0.65*E))*0.11; ch['wide']=np.maximum(ch['wide'],sm(wd/cnt)*0.8); ch['round']=sm(rn/cnt)*0.9; ch['lipIn']=np.maximum(ch['lipIn'],sm(li/cnt))
    # blinks: one before onset, one after stroke
    for b in [max(2,s0-12),min(F-4,s1+8)]: ch['blinkL']+=np.exp(-0.5*((t-b)/1.4)**2); ch['blinkR']+=np.exp(-0.5*((t-b)/1.4)**2)
    head={h:(env*v).tolist() for h,v in hd.items()}
    out[full]=dict(wav=LINE[k],F=F,s0=int(s0),s1=int(s1),line=line,jaw=jaw.round(4).tolist(),face={c:np.clip(v,0,1).round(3).tolist() for c,v in ch.items()},head=head)
    print(k,F,s0,s1,line)
json.dump(out,open('gface.json','w'))
