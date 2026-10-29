"""Puts a bought gun's mesh (pack.py) on one of our guns: rewrites the gun's item definitions so every block body,
handle, slide and magazine becomes the "zomboidcraft:mesh" special model (MeshGunRenderer) under the same
base model, so its display transforms, the reload pieces and the attachments all stay where they were.

skins.json maps our gun to the packed mesh: {"shotgun": "pbr_shotgun"}, or {"assault_rifle": {"mesh": ...,
"keep": ["irons_up"]}} to keep some block models (a mesh with no iron sights of its own keeps ours). kit.py applies it every time it writes a
gun's item definitions, so a weapons.py regen keeps it; run this file to apply it without regenerating.
"""
import json, os, re
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ITEMS = os.path.join(ROOT, 'src/main/resources/assets/zomboidcraft/items')
WEAPONS = os.path.join(ROOT, 'src/main/resources/assets/zomboidcraft/models/item/weapon')
SKINS = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'skins.json')
# the block model's last path segment -> the mesh's part (the mesh has its own iron sights)
PARTS = {'body': 'body', 'charging_handle': 'handle', 'charging_handle_open': 'handle', 'slide': 'slide', 'mag': 'mag',
         'stock': 'stock'}
DROP = {'irons_up', 'irons_folded'}


def centre(model):
    m = json.load(open(os.path.join(WEAPONS, model + '.json')))
    while 'elements' not in m:
        m = json.load(open(os.path.join(WEAPONS, m['parent'].split('item/weapon/')[1] + '.json')))
    a = np.array([e['from'] for e in m['elements']], float); b = np.array([e['to'] for e in m['elements']], float)
    return (np.minimum(a, b).min(0) + np.maximum(a, b).max(0)) / 2


def offset(gun, name):
    """A block piece drawn somewhere else in its own model (the revolver's cylinder swung out: charging_handle_open)
    gets the mesh's piece moved by the same amount, since the mesh only has it in the closed place."""
    if not name.endswith('_open'):
        return None
    d = centre(f'{gun}/{name}') - centre(f'{gun}/{name[:-5]}')
    return [round(float(x), 3) for x in d] if np.abs(d).max() > 1e-3 else None


def skins():
    return json.load(open(SKINS)) if os.path.exists(SKINS) else {}


def swap(node, gun, mesh, keep=()):
    if isinstance(node, list):
        return [swap(n, gun, mesh, keep) for n in node]
    if not isinstance(node, dict):
        return node
    if node.get('type') == 'minecraft:model':
        m = re.fullmatch(rf'zomboidcraft:item/weapon/{gun}/(?:.+/)?([a-z_]+)', node['model'])
        if m and m.group(1) in keep:
            return node
        if m and m.group(1) in PARTS:
            model = {'type': 'zomboidcraft:mesh', 'id': mesh, 'part': PARTS[m.group(1)]}
            off = offset(gun, m.group(1))
            if off:
                model['offset'] = off
            return {'type': 'minecraft:special', 'base': node['model'], 'model': model}
        if m and m.group(1) in DROP:
            return {'type': 'minecraft:empty'}
        return node
    return {k: swap(v, gun, mesh, keep) for k, v in node.items()}


def apply(gun, items=ITEMS):
    mesh = skins().get(gun)
    if not mesh:
        return
    keep = ()
    if isinstance(mesh, dict):
        mesh, keep = mesh['mesh'], tuple(mesh.get('keep', ()))
    for name in [gun] + [f[:-5] for f in os.listdir(items) if f.startswith(gun + '_loose_') and f.endswith('.json')]:
        path = os.path.join(items, name + '.json')
        data = json.load(open(path))
        out = swap(data, gun, mesh, keep)
        if out != data:
            with open(path, 'w') as f:
                f.write(json.dumps(out, indent=1) + '\n')


if __name__ == '__main__':
    for g in skins():
        apply(g)
        print('skinned', g)
