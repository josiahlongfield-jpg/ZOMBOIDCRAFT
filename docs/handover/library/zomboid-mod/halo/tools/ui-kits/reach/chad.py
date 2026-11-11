import sys,os,struct
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from chdt import C
def chad(c,name):
    k=c.byname.get(('chad',name))
    if k is None: return None
    o=c.tago(k); out={}
    for key,off,sz,fmt in (('pos',4,0x10,'f3f'),('rot',0x10,0x10,'f3f'),('size',0x1c,0xc,'f2f'),('alpha',0x34,8,'ff')):
        for blk in c.items(o,off,0x20)[:1]:
            out[key]=[c.f(p,fmt.replace('3f','fff').replace('2f','ff')) for p in c.items(blk,0,sz)]
    out['frames']=c.f(o+0x70,'f')[0]
    return out
if __name__=='__main__':
    c=C('/mnt/user-data/uploads/haloreach/maps/20_sword_slayer.map')
    for n in sys.argv[1:]: print(n,chad(c,n))
