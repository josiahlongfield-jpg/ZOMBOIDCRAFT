import struct,numpy as np,sys
from rres import RR
from reach import X
from rmodel import strips
import rsbsp
r=RR('maps/20_sword_slayer.map'); nm=r.names(); d=r.d
nmb='levels\\multi\\20_sword_slayer\\20_sword_slayer'
ks=[k for k,t in enumerate(r.tags) if t[0]=='sbsp' and nm[k]==nmb][0]
os_=r.off(X(r.tags[ks][2]))
DG=rsbsp.geometry(r,os_,0x340)
c1,a1=struct.unpack_from('<II',d,os_+0x328); a1=r.off(X(a1))
c2,a2=struct.unpack_from('<II',d,os_+0x334); a2=r.off(X(a2))
print(c1,c2)
for i in range(min(c1,c2,40)):
  e=a1+i*28; dat=struct.unpack_from('<I',d,e)[0]; var=d[e+4]; mi=d[e+5]
  raw=d[a2+i*28:a2+i*28+28]
  ist,icn,vst,vcn=struct.unpack_from('<4H',raw)
  me=DG['meshes'][mi]; it,ix=DG['ibs'][me['ibi'][0]]; vb=DG['vbs'][me['vbi'][0]]
  seg=ix[ist:ist+icn]
  print(i,nm[dat&0xffff].split('\\')[-1][:26],'mi',mi,'it',it,ist,icn,vst,vcn,'seg',seg.min(),seg.max(),'nv',vb[0], raw.hex()[16:], d[e:e+28].hex())
