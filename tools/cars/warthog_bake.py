"""
Cuts the Warthog down and bakes its detail into one texture (run inside Blender's Python, bpy 5.0):

    python warthog_bake.py WARTHOG.blend LABELS.npy OUT_DIR [SIZE] [SAMPLES]

The source is a ripped game model (never commit it: only tools/cars/warthog.py's scrambled .zcar
ships). LABELS holds a part per source triangle, in object order (Plane left out):
    0 body, 1 turret post (turns), 2 gun (tilts), 3 barrels (spin), 4-7 wheels (front left, front right,
    back left, back right).
Each part is decimated on its own, the whole set is unwrapped into one atlas, and the full-detail model is
baked onto it under a white sky (colour, its normal maps and the shade of its own nooks: a diffuse bake),
so the cut-down copy keeps the look. Only the front left wheel is baked: warthog.py copies it to the others.

Writes OUT_DIR/atlas.png and OUT_DIR/low.npz: per triangle its corners (Blender world space), uvs, part
and source material name.
"""
import sys

import bpy
import bmesh
import numpy as np

args = sys.argv[1:]


def eyes():
    """Where the vehicle is looked at from: all round it (down to the ground, to look under it), and the seats."""
    import math
    from mathutils import Vector
    f, l, up = Vector((1, -1, 0)).normalized(), Vector((1, 1, 0)).normalized(), Vector((0, 0, 1))
    out = []
    n = 160
    for i in range(n):
        z = 1 - (i + 0.5) / n * 2
        r = math.sqrt(1 - z * z)
        a = i * math.pi * (3 - math.sqrt(5))
        p = Vector((math.cos(a) * r * 7, math.sin(a) * r * 7, 0.9 + z * 7))
        p.z = max(p.z, 0.12)
        out.append(p)
    for gx, gy, gz in ((0.43, 1.75, -0.2), (-0.43, 1.75, -0.2), (0, 2.55, -2.1), (0.43, 1.3, -0.2), (-0.43, 1.3, -0.2)):
        out.append(l * gx + f * gz + up * (gy - 0.157))
    return out


def facing(path):
    """Which side of each low triangle anyone sees (the game culls back faces): 1 front, 2 back, 3 both,
    0 neither. Added to low.npz."""
    from mathutils import Vector
    from mathutils.bvhtree import BVHTree
    z = dict(np.load(path))
    P = z['P'].reshape(-1, 3)
    tris = np.arange(len(P)).reshape(-1, 3)
    tree = BVHTree.FromPolygons([Vector(p) for p in P], tris.tolist(), epsilon=0.0)
    E = eyes()
    out = np.zeros(len(tris), np.int8)
    for t, (a, b, c) in enumerate(tris):
        va, vb, vc = Vector(P[a]), Vector(P[b]), Vector(P[c])
        n = (vb - va).cross(vc - va)
        if n.length < 1e-12:
            continue
        n.normalize()
        ctr = (va + vb + vc) / 3
        got = 0
        for e in E:
            d = e - ctr
            dist = d.length
            d /= dist
            side = 1 if d.dot(n) > 0 else 2
            if got & side:
                continue
            if tree.ray_cast(ctr + d * 0.002, d, dist)[0] is None:
                got |= side
                if got == 3:
                    break
        out[t] = got
    z['facing'] = out
    np.savez_compressed(path, **z)
    print('facing: front', (out == 1).sum(), 'back', (out == 2).sum(), 'both', (out == 3).sum(), 'none', (out == 0).sum())


if args[0] == '--facing':
    facing(args[1])
    sys.exit(0)
blend, labels, out = args[0], args[1], args[2]
SIZE = int(args[3]) if len(args) > 3 else 3072
SAMPLES = int(args[4]) if len(args) > 4 else 32

# triangles each part is cut down to
KEEP = {0: 50000, 1: 2400, 2: 8000, 3: 1600, 4: 4200, 'glass': 400}

bpy.ops.wm.open_mainfile(filepath=blend)
lab = np.load(labels)


def diffuse_only(m):
    """A plain diffuse copy of a Forza material: metal, gloss, glass and glow all become their colour,
    keeping colour maps and normal maps, so a diffuse bake sees the paint rather than black reflections."""
    if not m.use_nodes:
        return
    nt = m.node_tree
    out_node = next((n for n in nt.nodes if n.type == 'OUTPUT_MATERIAL' and n.is_active_output), None) or \
        next((n for n in nt.nodes if n.type == 'OUTPUT_MATERIAL'), None)
    colour_src, colour, normal_src = None, None, None
    p = next((n for n in nt.nodes if n.type == 'BSDF_PRINCIPLED'), None)
    d = next((n for n in nt.nodes if n.type == 'BSDF_DIFFUSE'), None)
    e = next((n for n in nt.nodes if n.type == 'EMISSION'), None)
    g = next((n for n in nt.nodes if n.type in ('BSDF_GLOSSY', 'BSDF_GLASS')), None)
    name = m.name.lower()
    if p is not None:
        bc = p.inputs['Base Color']
        colour_src = bc.links[0].from_socket if bc.is_linked else None
        colour = tuple(bc.default_value)
        if p.inputs['Normal'].is_linked:
            normal_src = p.inputs['Normal'].links[0].from_socket
    elif d is not None:
        c = d.inputs['Color']
        colour_src = c.links[0].from_socket if c.is_linked else None
        colour = tuple(c.default_value)
    elif e is not None:
        c = e.inputs['Color']
        colour_src = c.links[0].from_socket if c.is_linked else None
        colour = tuple(c.default_value)
    elif g is not None:
        c = g.inputs['Color']
        colour_src = c.links[0].from_socket if c.is_linked else None
        colour = tuple(c.default_value)
    if 'chrome' in name:
        colour_src, colour = None, (0.42, 0.42, 0.43, 1)
    if name == 'glass':
        colour_src, colour = None, (0.17, 0.21, 0.23, 1)
    if colour is None:
        colour = (0.5, 0.5, 0.5, 1)
    # nothing in the world is pure black: tyres and rubber at a dark grey so their tread still shows
    colour = tuple(max(c, 0.035) for c in colour[:3]) + (1,)
    diff = nt.nodes.new('ShaderNodeBsdfDiffuse')
    diff.inputs['Color'].default_value = colour
    if colour_src is not None:
        if colour_src.node.type == 'TEX_IMAGE':
            colour_src = colour_src.node.outputs['Color']
        nt.links.new(colour_src, diff.inputs['Color'])
    if normal_src is not None:
        nt.links.new(normal_src, diff.inputs['Normal'])
    if out_node is None:
        out_node = nt.nodes.new('ShaderNodeOutputMaterial')
    for l in list(out_node.inputs['Surface'].links):
        nt.links.remove(l)
    nt.links.new(diff.outputs['BSDF'], out_node.inputs['Surface'])
    out_node.is_active_output = True


for m in bpy.data.materials:
    diffuse_only(m)

# label every source polygon, then gather each part (glass on its own) into one high-detail object
src = [o for o in bpy.data.objects if o.type == 'MESH' and o.name != 'Plane']
for o in list(bpy.data.objects):
    if o.type != 'MESH' or o.name == 'Plane':
        bpy.data.objects.remove(o, do_unlink=True)
k = 0
for o in src:
    n = len(o.data.polygons)
    a = o.data.attributes.new('zc_part', 'INT', 'FACE')
    part = lab[k:k + n].astype(int)
    glass = np.array([o.material_slots[p.material_index].material is not None and
                      o.material_slots[p.material_index].material.name == 'glass' for p in o.data.polygons], bool) \
        if o.material_slots else np.zeros(n, bool)
    part = np.where(glass & (part == 0), 9, part)
    a.data.foreach_set('value', part)
    k += n
assert k == len(lab), (k, len(lab))

bpy.ops.object.select_all(action='DESELECT')
for o in src:
    o.data = o.data.copy()
    o.select_set(True)
bpy.context.view_layer.objects.active = src[0]
bpy.ops.object.convert(target='MESH')
bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)
bpy.ops.object.join()
whole = bpy.context.view_layer.objects.active
whole.name = 'whole'
names = [s.material.name if s.material else '' for s in whole.material_slots]


def take(obj, keep_parts, name):
    """A copy of obj holding only the faces whose part is in keep_parts."""
    c = obj.copy()
    c.data = obj.data.copy()
    c.name = name
    bpy.context.collection.objects.link(c)
    bm = bmesh.new()
    bm.from_mesh(c.data)
    layer = bm.faces.layers.int['zc_part']
    bmesh.ops.delete(bm, geom=[f for f in bm.faces if f[layer] not in keep_parts], context='FACES')
    bm.to_mesh(c.data)
    bm.free()
    return c


PARTS = [(0, (0,)), (1, (1,)), (2, (2,)), (3, (3,)), (4, (4,)), ('glass', (9,))]
high, low = {}, {}
for key, ids in PARTS:
    h = take(whole, ids, f'high_{key}')
    high[key] = h
    l = h.copy()
    l.data = h.data.copy()
    l.name = f'low_{key}'
    bpy.context.collection.objects.link(l)
    n = len(l.data.polygons)
    ratio = min(1.0, KEEP[key] / max(n, 1))
    if ratio < 1:
        mod = l.modifiers.new('cut', 'DECIMATE')
        mod.decimate_type = 'COLLAPSE'
        mod.ratio = ratio
        mod.use_collapse_triangulate = True
        bpy.context.view_layer.objects.active = l
        bpy.ops.object.modifier_apply(modifier='cut')
    bm = bmesh.new()
    bm.from_mesh(l.data)
    bmesh.ops.triangulate(bm, faces=bm.faces[:])
    bm.to_mesh(l.data)
    bm.free()
    # the low copy is only a target: it neither shades nor hides anything during the bake
    for attr in ('visible_camera', 'visible_diffuse', 'visible_glossy', 'visible_transmission',
                 'visible_volume_scatter', 'visible_shadow'):
        setattr(l, attr, False)
    low[key] = l
    print(key, n, '->', len(l.data.polygons), flush=True)
# the wheels other than the baked one still shade the body
take(whole, (5, 6, 7), 'other_wheels')
bpy.data.objects.remove(whole, do_unlink=True)



def cull_hidden(obj):
    """Drops the faces nothing can see: not from around the vehicle (down to the ground, to look under it),
    nor from a seat. They'd only take room in the atlas."""
    from mathutils.bvhtree import BVHTree
    bm = bmesh.new()
    bm.from_mesh(obj.data)
    bm.faces.ensure_lookup_table()
    tree = BVHTree.FromBMesh(bm)
    eyes_ = eyes()
    drop = []
    for face in bm.faces:
        c = face.calc_center_median()
        seen = False
        for e in eyes_:
            d = e - c
            dist = d.length
            d /= dist
            hit = tree.ray_cast(c + d * 0.002, d, dist)
            if hit[0] is None:
                seen = True
                break
        if not seen:
            drop.append(face)
    print('  hidden faces dropped:', len(drop), 'of', len(bm.faces), flush=True)
    bmesh.ops.delete(bm, geom=drop, context='FACES')
    bm.to_mesh(obj.data)
    bm.free()


cull_hidden(low[0])

# one atlas for every low part
bpy.ops.object.select_all(action='DESELECT')
for l in low.values():
    l.select_set(True)
    l.data.uv_layers.new(name='atlas')
    l.data.uv_layers.active = l.data.uv_layers['atlas']
bpy.context.view_layer.objects.active = low[0]
bpy.ops.object.mode_set(mode='EDIT')
bpy.ops.mesh.select_all(action='SELECT')
bpy.ops.uv.smart_project(angle_limit=1.15, island_margin=0.0, area_weight=0.0, correct_aspect=True, scale_to_bounds=False)
bpy.ops.uv.pack_islands(margin=2.5 / SIZE, rotate=True, shape_method='CONCAVE', margin_method='FRACTION')
bpy.ops.object.mode_set(mode='OBJECT')

img = bpy.data.images.new('atlas', SIZE, SIZE, alpha=False, float_buffer=False)
img.pixels.foreach_set(np.tile(np.float32([0.2, 0.2, 0.2, 1]), SIZE * SIZE))
bake = bpy.data.materials.new('bake')
bake.use_nodes = True
tex = bake.node_tree.nodes.new('ShaderNodeTexImage')
tex.image = img
bake.node_tree.nodes.active = tex
for key, l in low.items():
    # remember each low face's source material before swapping in the bake target
    mats = np.zeros(len(l.data.polygons), int)
    l.data.polygons.foreach_get('material_index', mats)
    l['src_mats'] = [l.material_slots[i].material.name if l.material_slots[i].material else '' for i in mats] \
        if l.material_slots else [''] * len(mats)
    l.data.materials.clear()
    l.data.materials.append(bake)

sc = bpy.context.scene
sc.render.engine = 'CYCLES'
sc.cycles.device = 'CPU'
sc.cycles.samples = SAMPLES
sc.cycles.max_bounces = 3
sc.cycles.diffuse_bounces = 2
sc.render.bake.use_selected_to_active = True
sc.render.bake.cage_extrusion = 0.012
sc.render.bake.max_ray_distance = 0.06
sc.render.bake.margin = 6
sc.render.bake.use_clear = False
world = sc.world or bpy.data.worlds.new('w')
sc.world = world
world.use_nodes = True
bg = next(n for n in world.node_tree.nodes if n.type == 'BACKGROUND')
for l_ in list(bg.inputs['Color'].links):
    world.node_tree.links.remove(l_)
bg.inputs['Color'].default_value = (1, 1, 1, 1)
bg.inputs['Strength'].default_value = 1.0
for o in list(bpy.data.objects):
    if o.type in ('LIGHT', 'CAMERA'):
        bpy.data.objects.remove(o, do_unlink=True)

for key, l in low.items():
    bpy.ops.object.select_all(action='DESELECT')
    high[key].select_set(True)
    l.select_set(True)
    bpy.context.view_layer.objects.active = l
    print('baking', key, flush=True)
    bpy.ops.object.bake(type='DIFFUSE', pass_filter={'DIRECT', 'INDIRECT', 'COLOR'})
img.filepath_raw = out + '/atlas.png'
img.file_format = 'PNG'
img.save()

P, UV, PART, MAT = [], [], [], []
keys = {k: (9 if k == 'glass' else k) for k in low}
for key, l in low.items():
    me = l.data
    uv = me.uv_layers['atlas'].data
    for f, mname in zip(me.polygons, l['src_mats']):
        P.append([tuple(me.vertices[v].co) for v in f.vertices])
        UV.append([tuple(uv[li].uv) for li in f.loop_indices])
        PART.append(keys[key])
        MAT.append(mname)
np.savez_compressed(out + '/low.npz', P=np.array(P, np.float32), UV=np.array(UV, np.float32),
                    part=np.array(PART, np.int32), mat=np.array(MAT), mats=np.array(names))
print('done', len(P), 'triangles', flush=True)
facing(out + '/low.npz')
