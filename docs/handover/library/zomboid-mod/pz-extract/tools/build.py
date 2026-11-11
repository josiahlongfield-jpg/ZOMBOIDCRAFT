import sys,base64,json
S=sys.argv[1]
d=open(f'{S}/pzout/z.json').read()
tex={k:'data:image/png;base64,'+base64.b64encode(open(f'{S}/pzout/{k}.png','rb').read()).decode() for k in ['body1','body2','body3','trousers','jacket','hair']}
t=open(f'{S}/pztools/page.html').read().replace('__DATA__',d).replace('__TEX__',json.dumps(tex))
open(f'{S}/pzout/zomboid-zombie.html','w').write(t)
print(len(t))
