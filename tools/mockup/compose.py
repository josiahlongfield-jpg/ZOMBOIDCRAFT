import json, sys
from PIL import Image, ImageDraw, ImageFont
d = sys.argv[1]
parts = json.load(open(f"{d}/skins/walker_parts.json"))
names = list(parts)
F = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
title, lab = ImageFont.truetype(F, 22), ImageFont.truetype(F, 12)
k = 0.7
rw, rh = int(1060 * k), int(260 * k)
cw, ch = rw + 12, rh + 54
sheet = Image.new("RGB", (cw * 3 + 12, ch * 5 + 12), (10, 11, 13))
dr = ImageDraw.Draw(sheet)
views = [(150, "FRONT"), (150, "LEFT"), (150, "BACK"), (150, "RIGHT"), (200, "HEAD"), (260, "POSE")]
skins = ["pale", "olive", "dark"]
for i, n in enumerate(names):
    x, y = 12 + (i % 3) * cw, 12 + (i // 3) * ch
    row = Image.open(f"{d}/rows/{n}.png").convert("RGB").resize((rw, rh), Image.LANCZOS)
    sheet.paste(row, (x, y + 30))
    dr.text((x + 4, y + 2), n.upper(), fill=(235, 235, 235), font=title)
    dr.text((x + rw - 4, y + 8), f"{skins[i % 3]} skin", fill=(140, 140, 140), font=lab, anchor="ra")
    vx = x
    for w, t in views:
        dr.text((vx + w * k / 2, y + 30 + rh + 4), t, fill=(200, 200, 200), font=lab, anchor="ma")
        vx += w * k
sheet.save(sys.argv[2])
print(sheet.size)
