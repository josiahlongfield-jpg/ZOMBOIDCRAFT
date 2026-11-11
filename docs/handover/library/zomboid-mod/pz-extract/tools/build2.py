import sys,base64,json
S=sys.argv[1]
d=open(f'{S}/pzout/chars.json').read()
ks=['m_uw','f_uw_std','f_uw_detail','f_uw_ink','m_body','m_jeans','m_hair','f_body_std','f_body_detail','f_body_ink','f_jeans','f_hair']
tex={k:'data:image/png;base64,'+base64.b64encode(open(f'{S}/pzout/{k}.png','rb').read()).decode() for k in ks}
t=open(f'{S}/pztools/chars.html').read().replace('__DATA__',d).replace('__TEX__',json.dumps(tex))
open(f'{S}/pzout/zomboid-survivors.html','w').write(t);print(len(t))
