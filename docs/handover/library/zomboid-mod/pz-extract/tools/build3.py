import sys,base64,json,os
S=sys.argv[1]; O=f'{S}/pzout'
c=json.load(open(f'{O}/chars.json'))
c['female']['parts']['tomb']=json.load(open(f'{O}/tomb_f.json'))
c['male']['parts']['tomb']=json.load(open(f'{O}/tomb_m.json'))
for g in ('female','male'): c[g]['parts'].pop('trousers',None)
# FBX winding is opposite to the .x bodies: swap the 2nd and 3rd corner of every triangle
def flip(part):
    for key,n in (('pos',3),('nrm',3),('uv',2),('si',4),('sw',4)):
        a=part[key]; out=[]
        for i in range(0,len(a),3*n):
            t=a[i:i+3*n]; out+=t[:n]+t[2*n:3*n]+t[n:2*n]
        part[key]=out
for g in ('female','male'): flip(c[g]['parts']['tomb'])
b64=lambda p:'data:image/png;base64,'+base64.b64encode(open(p,'rb').read()).decode()
ks=['bk_f_van','bk_f_tomb','bk_f_goth','bk_f_detail','bk_f_ink','m_uw','tomb_m','goth_m','f_hair','m_hair']
tex={k:b64(f'{O}/{k}.png') for k in ks}
faces={f[5:-4]:b64(f'{O}/{f}') for f in os.listdir(O) if f.startswith('face_')}
t=open(f'{S}/pztools/tomb.html').read().replace('__DATA__',json.dumps(c,separators=(',',':'))).replace('__TEX__',json.dumps(tex)).replace('__FACES__',json.dumps(faces))
open(f'{O}/zomboid-tomb-body.html','w').write(t);print(len(t))
