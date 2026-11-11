# Exact outline template of the new body (style A) for an image AI to paint over: front, back, side.
from PIL import Image, ImageDraw, ImageFont
W, H = 3072, 2048
S = 1800 / 34.4            # px per model unit; figure spans y 0.4..34.8
G = H - 110                # image y of model y = 0.4 (soles)
F = '/usr/share/fonts/truetype/dejavu/'
big = ImageFont.truetype(F + 'DejaVuSans-Bold.ttf', 64); sm = ImageFont.truetype(F + 'DejaVuSans.ttf', 26)
for bold in ['DejaVuSans-Bold.ttf']: pass
def Y(y): return G - (y - 0.4) * S
def make(fill_mode):
    im = Image.new('RGB', (W, H), 'white'); d = ImageDraw.Draw(im)
    def rect(cx, x0, x1, y0, y1, label=None, col=(205, 205, 205)):
        box = [cx + x0 * S, Y(y1), cx + x1 * S, Y(y0)]
        d.rectangle(box, fill=col if fill_mode else None, outline=(40, 40, 40), width=4)
        if label: d.text(((box[0] + box[2]) / 2, (box[1] + box[3]) / 2), label, fill=(90, 90, 90), font=sm, anchor='mm')
    # front and back share the same silhouette; the side view shows depth
    for cx, name, mirror in [(560, 'FRONT', 1), (1536, 'BACK', -1)]:
        R, L = ('R', 'L') if mirror > 0 else ('L', 'R')        # the character's right is on the image left from the front
        rect(cx, -3.2, 3.2, 27.6, 34.8, 'head')
        rect(cx, -3.8, 3.8, 21.96, 27.6, 'chest')
        rect(cx, -3.3, 3.3, 18.16, 21.96, 'waist')
        rect(cx, -3.5, 3.5, 15.2, 18.2, 'hips')
        for side, tag in [(-1, R), (1, L)]:
            ax = side * 5.05
            rect(cx, ax - 1.6, ax + 1.6, 25.3, 27.3, None)
            rect(cx, ax - 1.35, ax + 1.35, 21.6, 25.3, 'upper\narm ' + tag)
            rect(cx, ax - 1.25, ax + 1.25, 16.7, 21.6, 'fore\narm')
            rect(cx, ax - 1.35, ax + 1.35, 13.7, 16.7, 'hand')
            lx = side * 1.82
            rect(cx, lx - 1.7, lx + 1.7, 8.4, 15.2, 'thigh ' + tag)
            rect(cx, lx - 1.5, lx + 1.5, 1.8, 8.4, 'shin')
            rect(cx, lx - 1.6, lx + 1.6, 0.4, 1.8, 'foot')
        d.text((cx, 40), name, fill=(20, 20, 20), font=big, anchor='mt')
    cx = 2560                                                       # side view, facing left
    f = -1
    def srect(z0, z1, y0, y1, label=None): rect(cx, f * z1, f * z0, y0, y1, label)
    srect(-3.3, 3.3, 27.6, 34.8, 'head'); srect(-2.1, 2.1, 21.96, 27.6, 'chest'); srect(-1.9, 1.9, 18.16, 21.96, 'waist'); srect(-1.9, 1.9, 15.2, 18.2, 'hips')
    srect(-1.7, 1.7, 8.4, 15.2, 'thigh'); srect(-1.5, 1.5, 1.8, 8.4, 'shin'); srect(-1.6, 3.6, 0.4, 1.8, 'foot')
    srect(-1.35, 1.35, 21.6, 27.3, 'upper\narm'); srect(-1.25, 1.25, 16.7, 21.6, 'fore\narm'); srect(-0.75, 0.95, 13.7, 16.7, 'hand')
    d.text((cx, 40), 'SIDE (faces left)', fill=(20, 20, 20), font=big, anchor='mt')
    d.line([0, G, W, G], fill=(150, 150, 150), width=3)
    return im
make(True).save('/mnt/project-files/zomboid-mod/skin-template/zombie-turnaround-template.png')
