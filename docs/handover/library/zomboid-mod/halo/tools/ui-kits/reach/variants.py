import sys,struct;sys.path.insert(0,'/tmp/claude-0/-home-user-zomboidcraft/87bca096-c08a-5c14-8dff-4bef71334809/scratchpad/reach')
from reach import X
def blk(r,o,k):
    c,a=struct.unpack_from('<II',r.d,o+k); return c,(r.off(X(a)) if c else 0)
def variants(r,hlmt_off):
    """{variant: {region: perm}} from hlmt +0x84 (variant 56 B, regions @+0x14: 24 B? name, ..., perms block)"""
    out={}
    c,a=blk(r,hlmt_off,0x84)
    for i in range(c):
        e=a+i*56; vn=r.sid(struct.unpack_from('<I',r.d,e)[0])
        rc,ra=blk(r,e,0x14); regs={}
        for j in range(rc):
            re_=ra+j*24; rn=r.sid(struct.unpack_from('<I',r.d,re_)[0])
            pc,pa=blk(r,re_,0x8)
            perms=[r.sid(struct.unpack_from('<I',r.d,pa+q*36)[0]) for q in range(pc)]
            regs[rn]=perms
        out[vn]=regs
    return out
def change_colors(r,obj_off,off=0x134):
    """{variant: [rgb x4]} from object change colors (24 B channels; initial perms 32 B: weight, lower rgb, upper rgb, variant sid)"""
    c,a=blk(r,obj_off,off); res={}
    for ch in range(c):
        n,pa=blk(r,a+ch*24,0)
        for j in range(n):
            w=struct.unpack_from('<7f',r.d,pa+j*32); v=r.sid(struct.unpack_from('<I',r.d,pa+j*32+28)[0])
            res.setdefault(v,[None]*c)[ch]=[round((w[1]+w[4])/2,4),round((w[2]+w[5])/2,4),round((w[3]+w[6])/2,4)]
    return res
