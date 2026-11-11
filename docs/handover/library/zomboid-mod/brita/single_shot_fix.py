# Turn a shot with baked-in echo slaps into one clean shot: keep the first hit, smooth the tail.
import sys,subprocess,numpy as np,wave
SR=44100
def load(f):
    raw=subprocess.run(['ffmpeg','-v','error','-i',f,'-ac','1','-ar',str(SR),'-f','s16le','-'],capture_output=True).stdout
    return np.frombuffer(raw,np.int16).astype(float)/32768
def env(a,ms):
    w=int(SR*ms/1000); return np.sqrt(np.convolve(a*a,np.ones(w)/w,'same'))+1e-6
def lowpass(a,fc):
    # simple 2-pass one-pole
    k=np.exp(-2*np.pi*fc/SR); y=np.empty_like(a); s=0.
    for _ in range(2):
        for i,x in enumerate(a): s=(1-k)*x+k*s; y[i]=s
        a=y.copy()
    return a
def clean(src,dst,cut_ms,decay_s):
    a=load(src); n=len(a); t=np.arange(n)/SR
    e5=env(a,20); on=max(0,np.argmax(e5>0.75*e5.max())-int(SR*0.015))  # the main hit, not any pre-click
    c=on+int(SR*cut_ms/1000)
    fast=env(a,8)
    lvl=0.4*fast[on:c].max()                                  # tail starts well under the first hit
    target=lvl*np.exp(-(t-t[c])*6.9/decay_s)                 # -60 dB over decay_s
    g=np.clip(target/fast,0,1.0)                              # only ever turn slaps down
    g=np.convolve(g,np.ones(220)/220,'same')                  # 5 ms smoothing, no clicks
    tail=a*g
    tail=0.55*tail+0.45*lowpass(tail,1800)                    # softer rumble, fewer cracks
    x=np.clip((np.arange(n)-c)/(SR*0.012),0,1)                # 12 ms crossfade head->tail
    out=a*(1-x)+tail*x
    out=out[:min(n,c+int(SR*decay_s*1.1))]
    f=int(SR*0.05); out[-f:]*=np.linspace(1,0,f)
    out=out/np.abs(out).max()*0.97
    w=wave.open(dst,'wb'); w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
    w.writeframes((out*32767).astype(np.int16).tobytes()); w.close()
if __name__=='__main__':
    clean(sys.argv[1],sys.argv[2],float(sys.argv[3]),float(sys.argv[4]))
