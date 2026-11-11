# Compose an MCC/Reach emblem: background shape in the background colour, foreground primary layer in the primary
# colour, secondary layer in the secondary colour (toggleable), like Reach's emblem editor.
import json, numpy as np
from PIL import Image
D = '/tmp/claude-0/mcc/emb/'
E = json.load(open(D + 'emblems.json'))
COLS = [(0.392,0.392,0.392),(0.612,0.612,0.612),(0.847,0.847,0.847),(0.451,0.357,0.282),(0.624,0.494,0.416),(0.8,0.663,0.58),
        (0.459,0.486,0.349),(0.592,0.651,0.431),(0.718,0.792,0.549),(0.22,0.478,0.278),(0.353,0.678,0.439),(0.533,0.804,0.569),
        (0.2,0.533,0.518),(0.294,0.698,0.69),(0.498,0.863,0.847),(0.247,0.376,0.514),(0.396,0.506,0.647),(0.553,0.69,0.851),
        (0.373,0.365,0.553),(0.494,0.478,0.718),(0.651,0.639,0.867),(0.635,0.263,0.263),(0.827,0.275,0.275),(0.918,0.58,0.58),
        (0.706,0.345,0.169),(0.925,0.49,0.259),(0.922,0.678,0.537),(0.698,0.518,0.188),(0.871,0.769,0.31),(0.945,0.922,0.482)]
NAMES = ['STEEL','SILVER','WHITE','BROWN','TAN','KHAKI','SAGE','OLIVE','DRAB','FOREST','GREEN','SEA FOAM','TEAL','AQUA','CYAN',
         'BLUE','COBALT','ICE','VIOLET','ORCHID','LAVENDER','MAROON','BRICK','ROSE','RUST','CORAL','PEACH','GOLD','YELLOW','PALE']
def layer(img, col):
    a = np.array(Image.open(D + img + '.png').convert('RGBA')).astype(float) / 255
    out = np.zeros_like(a); out[..., :3] = a[..., :3] * np.array(col); out[..., 3] = a[..., 3]
    return Image.fromarray((out * 255).astype('uint8'), 'RGBA')
def compose(fg, bg, prim, sec, back, secondary=True, size=256):
    f = E['foreground'][fg]; b = E['background'][bg]
    im = Image.new('RGBA', (256, 256), (0, 0, 0, 0))
    im.alpha_composite(layer(b['img'], COLS[back]))
    if secondary: im.alpha_composite(layer(f['s'], COLS[sec]))
    im.alpha_composite(layer(f['p'], COLS[prim]))
    return im.resize((size, size), Image.LANCZOS)
