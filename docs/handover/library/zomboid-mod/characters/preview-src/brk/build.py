import sys
s = open(sys.argv[1]).read()
a = s.index('<script>', s.index('three.min.js')) + len('<script>')
body = s[a:s.index("const canvas = document.getElementById('main');")].replace("pieces: [] };\n(() => {", "pieces: [] };\nawait (async () => {", 1)
shim, raster, render = open('shim.js').read(), open('raster.js').read(), open(sys.argv[2]).read()
html = f"""<!doctype html><html><head><meta charset=utf-8></head><body style="margin:0;background:#fff">
<canvas id=out></canvas>
<script>{shim}</script><script>{raster}</script>
<script>(async()=>{{
{body}
{render}
}})();
}})().catch(e=>{{console.log('ERR '+e.stack);document.title='done';}});</script></body></html>"""
open(sys.argv[3], 'w').write(html)
