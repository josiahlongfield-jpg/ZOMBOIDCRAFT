import sys,os,struct,json
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from rres import RR
from reach import X
ANCH={}
class C:
    def __init__(s,mp):
        s.r=RR(mp); s.nm=s.r.names(); s.d=s.r.d
        s.byname={}
        for k,t in enumerate(s.r.tags):
            if t[0]: s.byname[(t[0],s.nm[k])]=k
    def tago(s,k): return s.r.off(X(s.r.tags[k][2]))
    def blk(s,o,k):
        c,a=struct.unpack_from('<II',s.d,o+k); return [ (s.r.off(X(a)) if c else 0) + 0 for _ in [0]][0],c
    def items(s,o,k,sz):
        b,c=s.blk(o,k); return [b+i*sz for i in range(c)] if c and c<5000 else []
    def ref(s,o):
        dt=struct.unpack_from('<I',s.d,o+12)[0]
        if dt==0xffffffff: return None
        k=dt&0xffff
        if k>=len(s.nm): return '?%x'%dt
        return (s.r.tags[k][0],s.nm[k],k)
    def f(s,o,fmt): return struct.unpack_from('<'+fmt,s.d,o)
    def placement(s,o):
        out=[]
        for p in s.items(o,0x38,0x1c):
            fl,an,u=s.f(p,'BBh'); mo=s.f(p+4,'2f'); of=s.f(p+0xc,'2f'); sc=s.f(p+0x14,'2f')
            out.append(dict(anchor=an,flags=fl,mirror=mo,offset=of,scale=sc))
        return out
    def render(s,o):
        out=[]
        for p in s.items(o,0x70,0x78):
            sh=s.d[p]
            cols=[]
            for q in range(4):
                a=s.f(p+4+q*16,'f')[0]; rgb=s.f(p+8+q*16,'3f'); cols.append((round(a,3),)+tuple(round(x,3) for x in rgb))
            sc=s.f(p+0x44,'4f'); oc=s.f(p+0x54,'6H'); os_=s.f(p+0x60,'6H')
            out.append(dict(shader=sh,local=cols,scal=sc,outc=oc,outs=os_))
        return out
    def anims(s,o):
        out=[]
        for p in s.items(o,0x54,0x64):
            for q in range(5):
                fl,fn=s.d[p+q*0x14],s.d[p+q*0x14+1]; rf=s.ref(p+q*0x14+4)
                if rf: out.append(dict(i=q,flags=fl,fn=fn,anim=rf[1]))
        return out
    def common(s,o):
        e=dict(name=s.r.sid(s.f(o,'I')[0]),special=s.d[o+4],inp=s.f(o+8,'H')[0],rng=s.f(o+0xa,'H')[0])
        for key,tro,fn in (('state',0xc,None),('place',0x28,s.placement),('anim',0x44,s.anims),('render',0x60,s.render)):
            rf=s.ref(o+tro)
            if rf: e[key+'_tpl']=rf[1]
            if fn:
                v=fn(o)
                if not v and rf and isinstance(rf,tuple):
                    to=s.tago(rf[2])
                    # templates: same field set at the same relative offset? try block at template start
                    v=fn(to-{'place':0x38,'anim':0x54,'render':0x70}[key]) if False else s.tpl(key,to)
                    if v: e[key+'_from_tpl']=True
                e[key]=v
        return e
    def tpl(s,key,to):
        fn={'place':s.placement,'anim':s.anims,'render':s.render}[key]
        k={'place':0x38,'anim':0x54,'render':0x70}[key]
        # template tag: block at +0
        return fn(to-k)
    def parse(s,name):
        o=s.tago(s.byname[('chdt',name)]); out=[]
        for w in s.items(o,0,0xdc):
            e=s.common(w); e['bitmaps']=[]; e['texts']=[]
            for b in s.items(w,0x98,0x9c):
                be=s.common(b); rf=s.ref(b+0x80)
                be['bitmap']=rf[1] if rf else None; be['sprite']=s.d[b+0x90]; be['bflags']=s.f(b+0x7c,'I')[0]
                be['texcoord']=s.f(b+0x94,'4h')
                e['bitmaps'].append(be)
            for t in s.items(w,0xa4,0x84):
                te=s.common(t); te['font']=s.f(t+0x7e,'H')[0]; te['string']=s.r.sid(s.f(t+0x80,'I')[0]); te['tflags']=s.f(t+0x7c,'H')[0]
                e['texts'].append(te)
            out.append(e)
        return out
if __name__=='__main__':
    c=C(sys.argv[1]); res=c.parse(sys.argv[2])
    print(json.dumps(res,indent=1)[:int(sys.argv[3]) if len(sys.argv)>3 else 20000])
