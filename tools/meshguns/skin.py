"""Draws the bought meshes (pack.py) in the item definitions weapons.py writes, so the game shows nothing of the old
block guns (Josiah 2026-10-29: the bought guns go in as if the block guns never existed, only where they sat on the
screen and how they were held kept; every attachment is a bought one too).

guns.json, one entry per bought gun: its mesh, which of the block gun's pieces each mesh part stands in for (null:
nothing drawn there), its sights ({"part": a mesh part shown while no optic is fitted}, or {"flip": front and rear
flip-up sights from the attachment pack}, or null), its muzzle's flash point and sight line, and every slot's mount
on the mesh (item pixels), what fits it and what it comes with ("keys" moves one part elsewhere in the slot, e.g. a
long launcher back from the grips; "roll" turns a slot's parts, e.g. a pistol's light hung under its frame).
kit.py lays the gun out from the same file. Every block model the item definitions name for a bought gun becomes its
mesh, an attachment's mesh or nothing: only the muzzle flash stays.

attachments.json, one entry per attachment model key: its mesh (packed with its clamp at the origin, standing up off
a top rail; null: none bought yet, nothing drawn), and per-gun stand-ins ("guns": {gun: {"mesh", "scale"}}). On the
block guns that are left (the double barrel, revolver, lever action, rimfire, sawn-off) attachments are meshes too, at
the mount points their gun_slots files give.

skins.json, the other bought meshes: {"machete": "pbr_machete"} for a melee weapon's one model, or {"models":
{"part/crossbow_bolt": "pbr_bolt"}} to swap any block model (under item/weapon/, or by its full id elsewhere, e.g.
"zomboidcraft:item/throwable/frag_grenade") an item file names.

kit.py applies it every time it writes a gun's item definitions, weapons.py once more for the attachments' and
magazines' own items; run this file to apply all of it without regenerating.
"""
import json, os, re
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/zomboidcraft')
ITEMS = os.path.join(ASSETS, 'items')
WEAPONS = os.path.join(ASSETS, 'models/item/weapon')
EMPTY = {'type': 'minecraft:empty'}
# how a slot's parts are turned off a top rail (degrees about the bore, MeshGunRenderer's roll)
SLOT_ROLL = {'optic': 0, 'magnifier': 0, 'muzzle': 0, 'stock': 0, 'magazine': 0, 'underbarrel': 180, 'bipod': 180,
             'side_rail': 90, 'right_rail': -90}
# a block gun's mount is the top of its rail; a bought attachment's clamp reaches this far past it
JAWS = 0.4


def _load(name):
    p = os.path.join(HERE, name)
    return json.load(open(p)) if os.path.exists(p) else {}


def guns():
    return _load('guns.json')


def atts():
    return _load('attachments.json')


def skins():
    return _load('skins.json')


def bounds():
    return _load('bounds.json')


def attachment_mesh(key, gun):
    """(mesh, scale) drawn for an attachment key on a gun, or (None, 1) for one with no mesh yet."""
    a = atts().get(key)
    if not a:
        return None, 1.0
    own = a.get('guns', {}).get(gun, {})
    return own.get('mesh', a['mesh']), own.get('scale', 1.0)


def special(base, mesh, part='body', offset=None, roll=0, pitch=0, scale=1.0):
    m = {'type': 'zomboidcraft:mesh', 'id': mesh, 'part': part}
    if offset is not None:
        m['offset'] = [round(float(x), 3) for x in offset]
    if roll:
        m['roll'] = roll
    if pitch:
        m['pitch'] = pitch
    if scale != 1:
        m['scale'] = scale
    return {'type': 'minecraft:special', 'base': base, 'model': m}


def composite(models):
    return {'type': 'minecraft:composite', 'models': models}


def slot_mounts(gun):
    """{slot: (mount, roll)} for a gun: a bought gun's from guns.json, a block gun's from its gun_slots file."""
    cfg = guns().get(gun)
    if cfg:
        return {s: (c['at'], c.get('roll', SLOT_ROLL[s]), c.get('keys', {})) for s, c in cfg['slots'].items()}
    path = os.path.join(ASSETS, 'gun_slots', gun + '.json')
    if not os.path.exists(path):
        return {}
    out = {}
    for s in json.load(open(path))['slots']:
        roll = SLOT_ROLL[s['slot']]
        a = np.radians(roll)
        up = np.array([-np.sin(a), np.cos(a), 0.0])      # which way the rail faces
        at = np.asarray(s['mount'], float) - (JAWS * up if s['slot'] not in ('muzzle', 'stock', 'magazine') else 0)
        out[s['slot']] = (list(at), roll, {})
    return out


# a magnifier swung aside turns on its mount's hinge, off the sight line to the right
ASIDE_ROLL = -90


def attachment(base, key, gun, mounts):
    """An attachment key's model on a gun: its mesh at the slot's mount, or nothing."""
    if key.endswith('_aside'):
        m = attachment(base, key.removesuffix('_aside'), gun, mounts)
        if m and m.get('type') == 'minecraft:special':
            m['model']['roll'] = m['model'].get('roll', 0) + ASIDE_ROLL
        return m
    a = atts().get(key)
    mesh, scale = attachment_mesh(key, gun)
    if not a or a['slot'] not in mounts:
        return None
    if mesh is None:
        return EMPTY
    at, roll, keys = mounts[a['slot']]
    return special(base, mesh, offset=keys.get(key, at), roll=roll, scale=scale)


def irons(base, cfg, up):
    sights = cfg.get('irons')
    if not sights:
        return EMPTY
    if 'part' in sights:
        return special(base, cfg['mesh'], sights['part']) if up else EMPTY
    f = sights['flip']
    state = 'up' if up else 'down'
    return composite([special(base, f'att_irons_front_{state}', offset=f['front']),
                      special(base, f'att_irons_rear_{state}', offset=f['rear'])])


def swap(node, gun, cfg, mounts, models, left):
    """cfg: the bought gun's guns.json entry (None for a block gun: only its attachments change). left collects the
    names of block models a bought gun still had, which become nothing."""
    if isinstance(node, list):
        return [swap(n, gun, cfg, mounts, models, left) for n in node]
    if not isinstance(node, dict):
        return node
    if node.get('type') == 'minecraft:special' and node.get('model', {}).get('type') == 'zomboidcraft:mesh':
        node = {'type': 'minecraft:model', 'model': node['base']}       # skinned before: start again from the block
    if node.get('type') != 'minecraft:model':
        return {k: swap(v, gun, cfg, mounts, models, left) for k, v in node.items()}
    base = node['model']
    path = base.removeprefix('zomboidcraft:item/weapon/')
    if path in models or base in models:
        return special(base, models.get(path) or models[base])
    m = re.fullmatch(rf'zomboidcraft:item/weapon/{gun}/(?:.+/)?([a-z_0-9]+)', base)
    if not m:
        return node
    name = m.group(1)
    if name.startswith(('muzzle_flash', 'loose_')):
        return node     # the flash, and the rounds a reload hand carries in, aren't the gun's
    if cfg and name in cfg['parts']:
        p = cfg['parts'][name]
        if p is None:
            return EMPTY
        if isinstance(p, dict):
            return special(base, p['mesh'], p.get('part', 'body'))
        return special(base, cfg['mesh'], p)
    if cfg and name in ('irons_up', 'irons_folded'):
        return irons(base, cfg, name == 'irons_up')
    a = attachment(base, name, gun, mounts)
    if a is not None:
        return a
    if cfg:
        left.add(name)
        return EMPTY
    return node


def apply(gun, items=ITEMS):
    """Rewrites a gun's item definitions (and its loose reload pieces), or a melee weapon's or a throwable's."""
    cfg = guns().get(gun)
    sk = skins().get(gun)
    mounts = slot_mounts(gun)
    if not cfg and not sk and not mounts:
        return
    models = sk.get('models', {}) if isinstance(sk, dict) else {}
    melee = sk if isinstance(sk, str) else None
    names = [gun] + [f[:-5] for f in os.listdir(items) if f.startswith(gun + '_loose_') and f.endswith('.json')]
    left = set()
    for name in names:
        path = os.path.join(items, name + '.json')
        if not os.path.exists(path):
            continue
        data = json.load(open(path))
        if melee:
            out = melee_swap(data, gun, melee)
        else:
            out = swap(data, gun, cfg, mounts, models, left)
        if out != data:
            with open(path, 'w') as f:
                f.write(json.dumps(out, indent=1) + '\n')
    if left:
        print(f'  {gun}: block pieces left out: {sorted(left)}')


def melee_swap(node, gun, mesh):
    if isinstance(node, list):
        return [melee_swap(n, gun, mesh) for n in node]
    if not isinstance(node, dict):
        return node
    if node.get('type') == 'minecraft:model' and re.fullmatch(rf'zomboidcraft:item/weapon/{gun}(?:_worn)?', node['model']):
        return {'type': 'minecraft:special', 'base': node['model'], 'model': {'type': 'zomboidcraft:mesh', 'id': mesh}}
    return {k: melee_swap(v, gun, mesh) for k, v in node.items()}


def centred(base, mesh, part='body', scale=1.0):
    """A mesh (or one of its parts) moved to sit where the block model it stands in for sat: its own item, dropped or
    in the inventory, keeps the block model's display transforms."""
    m = json.load(open(os.path.join(WEAPONS, base.split('item/weapon/')[1] + '.json')))
    while 'elements' not in m:
        m = json.load(open(os.path.join(WEAPONS, m['parent'].split('item/weapon/')[1] + '.json')))
    a = np.array([e['from'] for e in m['elements']], float); b = np.array([e['to'] for e in m['elements']], float)
    want = (np.minimum(a, b).min(0) + np.maximum(a, b).max(0)) / 2
    lo, hi = part_bounds(mesh, part)
    return special(base, mesh, part, offset=want - scale * (lo + hi) / 2, scale=scale)


def part_bounds(mesh, part):
    """A packed mesh part's reach (item pixels): the whole mesh's from bounds.json, or read off the part."""
    if part == 'body' and mesh in bounds():
        lo, hi = bounds()[mesh][:2]
        return np.array(lo), np.array(hi)
    import importlib.util, io, struct, zlib
    sp = importlib.util.spec_from_file_location('cars_pack', os.path.join(ROOT, 'tools', 'cars', 'pack.py'))
    cp = importlib.util.module_from_spec(sp); sp.loader.exec_module(cp)
    raw = zlib.decompress(cp.scramble(open(os.path.join(ASSETS, 'meshes', mesh + '.zmesh'), 'rb').read(), mesh))
    b = io.BytesIO(raw); b.read(12); b.read(struct.unpack('>i', b.read(4))[0])
    for _ in range(struct.unpack('>i', b.read(4))[0]):
        name = b.read(struct.unpack('>H', b.read(2))[0]).decode(); t = struct.unpack('>i', b.read(4))[0]
        rec = np.frombuffer(b.read(t * 39), dtype=[('x', '>i2'), ('y', '>i2'), ('z', '>i2'), ('r', 'V7')])
        if name == part:
            v = np.stack([rec['x'], rec['y'], rec['z']], -1) / 1000.0
            return v.min(0), v.max(0)
    raise KeyError(f'{mesh} has no part {part}')


def apply_items(items=ITEMS):
    """Every attachment's, magazine's and stock's own item as its mesh."""
    for key, a in atts().items():
        path = os.path.join(items, a['item'] + '.json')
        if a['mesh'] and os.path.exists(path):
            write(path, centred(f'zomboidcraft:item/weapon/part/{key}', a['mesh']))
    for key, a in atts().items():           # the pistol's can is the compact one, smaller
        if key == 'suppressor' and os.path.exists(os.path.join(items, 'pistol_suppressor.json')):
            mesh, scale = attachment_mesh(key, 'pistol')
            write(os.path.join(items, 'pistol_suppressor.json'), centred('zomboidcraft:item/weapon/part/pistol_suppressor', mesh, scale=scale))
    done = set()
    for gun, cfg in guns().items():          # a magazine's own item: the first bought gun it fits
        for key, part in (('mag', 'mag'), ('stock', 'stock')):
            slot = 'magazine' if key == 'mag' else 'stock'
            sp = os.path.join(ASSETS, 'gun_slots', gun + '.json')
            if slot not in cfg['slots'] or not os.path.exists(sp) or cfg['parts'].get(key) is None:
                continue
            item = next((s['fits'][0].split(':')[1] for s in json.load(open(sp))['slots'] if s['slot'] == slot), None)
            path = os.path.join(items, f'{item}.json') if item else None
            if not path or item in done or not os.path.exists(path):
                continue
            base = json.load(open(path))['model']
            base = base.get('base') or base.get('model')
            write(path, centred(base, cfg['mesh'], part))
            done.add(item)


def write(path, model):
    out = {'model': model}
    if json.load(open(path)) != out:
        with open(path, 'w') as f:
            f.write(json.dumps(out, indent=1) + '\n')


if __name__ == '__main__':
    names = set(guns()) | set(skins())
    names |= {f[:-5] for f in os.listdir(os.path.join(ASSETS, 'gun_slots'))}
    for g in sorted(names):
        apply(g)
    apply_items()
    print('skinned', len(names))
