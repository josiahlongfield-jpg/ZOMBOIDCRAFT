import struct
def load(fn):
    b=open(fn,'rb').read()
    tb=struct.unpack_from('<I',b,0x78)[0]; n=tb//4
    hs=struct.unpack_from('<%dI'%n,b,0x80)
    s=b[0x80+tb:].split(b'\x00')
    return dict(zip(hs,[x.decode('utf-8','replace') for x in s[:n]]))

def mpeg2(s):
    c=0xffffffff
    for b in s:
        c^=b<<24
        for _ in range(8): c=((c<<1)^0x04C11DB7)&0xffffffff if c&0x80000000 else (c<<1)&0xffffffff
    return c
import glob as _g, os as _o
ALL={}
for _f in sorted(_g.glob(_o.path.join(_o.path.dirname(__file__),'../loose/localization/EN_*.bin'))):
    for _k,_v in load(_f).items(): ALL.setdefault(_k,_v)
def name(key):
    return ALL.get(mpeg2(key.lstrip('$').encode()))
