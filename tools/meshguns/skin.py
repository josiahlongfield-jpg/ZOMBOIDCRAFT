"""Puts a bought gun's mesh (pack.py) on one of our guns: rewrites the gun's item definitions so every block body,
handle, slide and magazine becomes the "zomboidcraft:mesh_gun" special model (MeshGunRenderer) under the same
base model, so its display transforms, the reload pieces and the attachments all stay where they were.

skins.json maps our gun to the packed mesh: {"shotgun": "pbr_shotgun"}. kit.py applies it every time it writes a
gun's item definitions, so a weapons.py regen keeps it; run this file to apply it without regenerating.
"""
import json, os, re

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ITEMS = os.path.join(ROOT, 'src/main/resources/assets/zomboidcraft/items')
SKINS = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'skins.json')
# the block model's last path segment -> the mesh's part (the mesh has its own iron sights)
PARTS = {'body': 'body', 'charging_handle': 'handle', 'charging_handle_open': 'handle', 'slide': 'slide', 'mag': 'mag'}
DROP = {'irons_up', 'irons_folded'}


def skins():
    return json.load(open(SKINS)) if os.path.exists(SKINS) else {}


def swap(node, gun, mesh):
    if isinstance(node, list):
        return [swap(n, gun, mesh) for n in node]
    if not isinstance(node, dict):
        return node
    if node.get('type') == 'minecraft:model':
        m = re.fullmatch(rf'zomboidcraft:item/weapon/{gun}/(?:.+/)?([a-z_]+)', node['model'])
        if m and m.group(1) in PARTS:
            return {'type': 'minecraft:special', 'base': node['model'],
                    'model': {'type': 'zomboidcraft:mesh_gun', 'id': mesh, 'part': PARTS[m.group(1)]}}
        if m and m.group(1) in DROP:
            return {'type': 'minecraft:empty'}
        return node
    return {k: swap(v, gun, mesh) for k, v in node.items()}


def apply(gun, items=ITEMS):
    mesh = skins().get(gun)
    if not mesh:
        return
    for name in [gun] + [f[:-5] for f in os.listdir(items) if f.startswith(gun + '_loose_') and f.endswith('.json')]:
        path = os.path.join(items, name + '.json')
        data = json.load(open(path))
        out = swap(data, gun, mesh)
        if out != data:
            with open(path, 'w') as f:
                f.write(json.dumps(out, indent=1) + '\n')


if __name__ == '__main__':
    for g in skins():
        apply(g)
        print('skinned', g, '->', skins()[g])
