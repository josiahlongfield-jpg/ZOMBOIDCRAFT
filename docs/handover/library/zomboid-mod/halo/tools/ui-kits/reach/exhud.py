import sys,os,struct,traceback
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
from rres import RR
from reach import X
from rbitmap import bitmap
mp,out=sys.argv[1],sys.argv[2]
r=RR(mp); nm=r.names()
ok=bad=0
for k,t in enumerate(r.tags):
    if t[0]!='bitm' or not nm[k].startswith('ui\\'): continue
    o=r.off(X(t[2])); n=struct.unpack_from('<I',r.d,o+0x7c)[0]
    base=os.path.join(out,nm[k].replace('\\','/'))
    os.makedirs(os.path.dirname(base),exist_ok=True)
    for i in range(n):
        p=base+('' if n==1 else '__%d'%i)+'.png'
        if os.path.exists(p): continue
        try:
            im,info=bitmap(r,o,i); im.save(p); ok+=1
        except Exception as e:
            bad+=1; print('FAIL',nm[k],i,repr(e)[:120])
print('ok',ok,'bad',bad)
