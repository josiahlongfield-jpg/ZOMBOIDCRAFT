# Add Josiah's 15 per-type boxer skins (ai-skin-15) to kits.html as the default body skin.
import base64, json
D = '/mnt/project-files/zomboid-mod/skin-template/ai-skin-15/'
O = 'civilian construction doctor farmer firefighter hoodie jogger nurse office patient police prisoner security soldier survivor'.split()
from PIL import Image
import io
def jpg(o):
    im = Image.open(D + f'{o}_1024.png').convert('RGBA'); bg = Image.new('RGB', im.size, (128, 128, 128)); bg.paste(im, mask=im.split()[3])
    b = io.BytesIO(); bg.save(b, 'JPEG', quality=88); return 'data:image/jpeg;base64,' + base64.b64encode(b.getvalue()).decode()
ai15 = {o: jpg(o) for o in O}  # opaque JPEG: the body only samples the base layer, and the page must stay under 16 MB
s = open('kits.html').read()
def rep(a, b):
    global s
    assert s.count(a) == 1, a[:70]
    s = s.replace(a, b)
rep("const AISKIN = AIFACES.idle;", "const AISKIN = AIFACES.idle;\nconst AI15 = " + json.dumps(ai15) + ";")
rep("let skinKey = 'olive_1', fig = null, baseSkin = 'ai';", "let skinKey = 'olive_1', fig = null, baseSkin = 'ai15';")
rep("const src = baseSkin === 'ai' ? AISKIN : SKINS[KIT.outfit][skinKey];", "const src = baseSkin === 'ai15' ? AI15[KIT.outfit] : baseSkin === 'ai' ? AISKIN : SKINS[KIT.outfit][skinKey];")
rep('''        <button data-v="ai" aria-pressed="true">Your AI skin</button>''', '''        <button data-v="ai15" aria-pressed="true">Your 15 type skins</button>
        <button data-v="ai" aria-pressed="false">Mood-face skin</button>''')
rep("(its face changes with the movement:", "or one of your fifteen per-type boxer skins (the default), or the mood-face skin (its face changes with the movement:")
open('kits.html', 'w').write(s)
import re; open('chk_all.cjs', 'w').write(re.findall(r'<script>(.*?)</script>', s, re.S)[0])
print(len(s) // 1024, 'KB')
