"""Send Cortana's look from Blender into the game (open your Cortana .blend, Scripting tab, open this file, Run Script).

Adds a "Cortana game" tab to the 3D view's side panel (press N) with one job: Send to game. It
  - backs up the game's cortana.zsk first (Put back last backup undoes a send),
  - takes her meshes as they are in this file (shape, smoothing and custom normals, bone weights, in her rest pose),
  - takes your baked textures from the Bakes folder: cortana_body_baked.1002.png / .1012.png and
    cortana_head_baked.1001.png / .1011.png (UDIM tiles, any you have; the rest keep the game's current colours),
  - and writes them into cortana.zsk. Her bones, moves and mouth are kept exactly as they are.
Every UDIM tile gets its own space in her texture, so the two halves of her body (and head) can differ.
Nothing is uploaded; everything stays on this PC. Close Minecraft before you send, then start it again to see her.
"""
import io
import os
import shutil
import struct
import time
import zlib

import numpy as np

MODEL = 'cortana'
VERT = np.dtype([('p', '>f4', 3), ('n', 'i1', 3), ('uv', '>u2', 2), ('j', 'i1', 4), ('w', 'u1', 4)])  # as the game reads it

# Her texture before the first send (one square per part, repeated over every UV tile) ...
OLD = dict(size=(4096, 3072), regions={'body': {None: (0, 0, 2048)}, 'head': {None: (2048, 0, 2048)},
                                       'hair': {None: (0, 2048, 1024)}, 'eye': {None: (1024, 2048, 512)}})
# ... and after: the body and head get a square per UDIM tile. A tile not listed falls back to the part's first one.
NEW = dict(size=(4096, 5120), regions={'body': {1002: (0, 0, 2048), 1012: (2048, 0, 2048)},
                                       'head': {1001: (0, 2048, 2048), 1011: (2048, 2048, 2048)},
                                       'hair': {None: (0, 4096, 1024)}, 'eye': {None: (1024, 4096, 512)}})
BAKED = ('body', 'head')  # parts that take bakes; hair and eyes keep the game's colours


# ---------------------------------------------------------------- the game file (no Blender needed for this part)

def _jhash(s):
    h = 0
    for ch in s:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    return h


def scramble(data, model=MODEL):
    """The game's xor stream (NpcAssets.unscramble): the same call scrambles and unscrambles."""
    M = 0xFFFFFFFF
    x = (0x3C6EF372 ^ _jhash(model)) & M or 1
    n = (len(data) + 3) // 4
    ks = np.empty(n, '>u4')
    for i in range(n):
        x ^= (x << 13) & M
        x ^= x >> 17
        x ^= (x << 5) & M
        ks[i] = x
    k = np.frombuffer(ks.tobytes(), np.uint8)[:len(data)]
    return (np.frombuffer(bytes(data), np.uint8) ^ k).tobytes()


class _R:
    def __init__(self, b):
        self.b, self.p = b, 0

    def take(self, n):
        v = self.b[self.p:self.p + n]
        self.p += n
        return v

    def u(self, fmt):
        return struct.unpack('>' + fmt, self.take(struct.calcsize('>' + fmt)))

    def utf(self):
        return self.take(self.u('H')[0]).decode('utf-8')


def read_zsk(path):
    raw = zlib.decompress(scramble(open(path, 'rb').read()))
    r = _R(raw)
    if r.take(4) != b'ZSKN' or r.u('i')[0] != 1:
        raise ValueError('not a Cortana game file')
    groups = []
    for _ in range(r.u('i')[0]):
        mesh, piece, tex = r.utf(), r.utf(), r.utf()
        flags, level = r.u('bb')
        n = r.u('i')[0]
        v = np.frombuffer(r.take(n * VERT.itemsize), VERT)
        t = np.frombuffer(r.take(r.u('i')[0] * 12), '>i4').reshape(-1, 3)
        groups.append(dict(mesh=mesh, piece=piece, tex=tex, flags=flags, level=level, v=v, tris=t))
    textures = {}
    for _ in range(r.u('i')[0]):
        k = r.utf()
        textures[k] = r.take(r.u('i')[0])
    tail = raw[r.p:]  # bones, mouth, moves: written back untouched
    t = _R(tail)
    names, origins = [], []
    for _ in range(t.u('i')[0]):
        names.append(t.utf())
        t.u('b')
        m = np.array(t.u('12f')).reshape(3, 4)
        origins.append(-np.linalg.solve(m[:, :3], m[:, 3]))  # where the bone sits in her model space
    return dict(groups=groups, textures=textures, tail=tail, bones=names, origins=np.array(origins))


def write_zsk(path, z):
    o = io.BytesIO()
    w = lambda fmt, *v: o.write(struct.pack('>' + fmt, *v))

    def utf(s):
        b = s.encode('utf-8')
        w('H', len(b))
        o.write(b)

    o.write(b'ZSKN')
    w('i', 1)
    w('i', len(z['groups']))
    for g in z['groups']:
        utf(g['mesh']); utf(g['piece']); utf(g['tex'])
        w('bb', g['flags'], g['level'])
        w('i', len(g['v']))
        o.write(np.ascontiguousarray(g['v'], VERT).tobytes())
        w('i', len(g['tris']))
        o.write(np.ascontiguousarray(g['tris'], '>i4').tobytes())
    w('i', len(z['textures']))
    for k, b in z['textures'].items():
        utf(k); w('i', len(b)); o.write(b)
    o.write(z['tail'])
    data = scramble(zlib.compress(o.getvalue(), 9))
    tmp = path + '.writing'
    with open(tmp, 'wb') as f:
        f.write(data)
    os.replace(tmp, path)


def fit(src, dst):
    """Scale, turn and move taking points src onto dst (least squares). Returns (s, R, t, worst miss)."""
    ms, md = src.mean(0), dst.mean(0)
    a, b = src - ms, dst - md
    U, S, Vt = np.linalg.svd(b.T @ a)
    D = np.diag([1, 1, np.sign(np.linalg.det(U @ Vt))])
    R = U @ D @ Vt
    s = (S * np.diag(D)).sum() / (a * a).sum()
    t = md - s * R @ ms
    miss = np.abs((s * (R @ src.T)).T + t - dst).max()
    return s, R, t, miss


def layout_of(size):
    for L in (NEW, OLD):
        if tuple(size) == L['size']:
            return L
    raise ValueError(f'her texture is {size[0]}x{size[1]}, a layout this script does not know')


def udim(tu, tv):
    return 1001 + int(tu) + 10 * int(tv)


def atlas_uv(slot, UV, polys, layout=NEW):
    """Blender uvs per corner -> her texture's uvs (0..1, image-down v). Each face is placed by the UDIM tile its middle
    sits in; the texture repeats, so a part with one square takes every tile on it."""
    W, H = layout['size']
    regs = layout['regions'][slot]
    first = next(iter(regs.values()))
    out = np.zeros_like(UV)
    for ls in polys:
        tu, tv = np.floor(UV[ls].mean(0))
        x0, y0, s = regs.get(udim(tu, tv) if tu >= 0 and tv >= 0 else -1, regs.get(None, first))
        u = np.clip(UV[ls, 0] - tu, 0.5 / s, 1 - 0.5 / s)  # half a texel in from the edge: no bleeding next door
        v = np.clip(1 - (UV[ls, 1] - tv), 0.5 / s, 1 - 0.5 / s)
        out[ls, 0] = (x0 + u * s) / W
        out[ls, 1] = (y0 + v * s) / H
    return out


def build_group(P, N, AUV, corners, JW):
    """One game group. P (V,3) positions and N (C,3) corner normals in her model space, AUV (C,2) atlas uvs,
    corners (T,3,2) -> (vertex index, corner index), JW (V,4,2) bone index and weight per vertex."""
    vi, ci = corners[..., 0], corners[..., 1]
    uvq = np.round(np.clip(AUV[ci], 0, 1) * 65535).astype(int)
    n = N[ci]
    n = n / np.maximum(np.linalg.norm(n, axis=-1, keepdims=True), 1e-9)
    nq = np.clip(np.round(n * 127), -127, 127).astype(int)
    key = np.concatenate([vi[..., None], nq, uvq], -1).reshape(-1, 6)
    uniq, inv = np.unique(key, axis=0, return_inverse=True)
    V = len(uniq)
    out = np.zeros(V, VERT)
    src = uniq[:, 0]
    out['p'] = P[src]
    out['n'] = uniq[:, 1:4]
    out['uv'] = uniq[:, 4:6]
    wt = JW[src, :, 1]
    wt = wt / np.maximum(wt.sum(1, keepdims=True), 1e-9)
    wb = np.floor(wt * 255 + 0.5).astype(int)
    wb[np.arange(V), wt.argmax(1)] += 255 - wb.sum(1)
    out['j'] = JW[src, :, 0].astype(int)
    out['w'] = wb
    return out, inv.reshape(-1, 3).astype('>i4')


def relayout(old_px, bakes):
    """old_px: her current texture, rows top-down RGBA uint8, in the OLD or NEW layout. bakes: {(slot, tile): rows
    top-down RGB(A) uint8 square}. Returns the texture in the NEW layout: bakes where given, her current colours elsewhere."""
    src = layout_of((old_px.shape[1], old_px.shape[0]))
    W, H = NEW['size']
    a = np.zeros((H, W, 4), np.uint8)
    for slot, regs in NEW['regions'].items():
        for tile, (x0, y0, s) in regs.items():
            sr = src['regions'][slot]
            ox, oy, os_ = sr.get(tile, sr.get(None, next(iter(sr.values()))))
            a[y0:y0 + s, x0:x0 + s] = _resize(old_px[oy:oy + os_, ox:ox + os_], s)
            b = bakes.get((slot, tile))
            if b is not None:
                b = _resize(b, s)
                a[y0:y0 + s, x0:x0 + s, :3] = b[..., :3]
            if slot != 'hair':
                a[y0:y0 + s, x0:x0 + s, 3] = 255  # only hair strands are cut out
    return a


def _resize(px, s):
    if px.shape[0] == s:
        return px
    i = (np.arange(s) * px.shape[0] / s).astype(int)  # nearest; only for a bake made at another size
    return px[i][:, i]


# ---------------------------------------------------------------- Blender side

try:
    import bpy
except ImportError:  # the file part above runs outside Blender too
    bpy = None


def _slot_of(mat):
    n = (mat.name if mat else '').lower()
    for word, slot in (('eye', 'eye'), ('hair', 'hair'), ('lash', 'hair'), ('head', 'head'), ('body', 'body')):
        if word in n:
            return slot
    return None


def _parts():
    return [o for o in bpy.data.objects if o.type == 'MESH'
            and any(m.type == 'ARMATURE' and m.object for m in o.modifiers)
            and any(_slot_of(s.material) for s in o.material_slots)]


def _png_read(data_or_path, tmpdir):
    """PNG -> rows top-down RGBA uint8, through Blender (Pillow isn't in Blender's Python)."""
    f = data_or_path
    if isinstance(data_or_path, (bytes, bytearray)):
        f = os.path.join(tmpdir, 'cortana_in.png')
        with open(f, 'wb') as h:
            h.write(data_or_path)
    im = bpy.data.images.load(f, check_existing=False)
    try:
        im.colorspace_settings.name = 'Non-Color'  # bytes as they are in the file, no colour management
        w, h = im.size
        px = np.empty(w * h * 4, np.float32)
        im.pixels.foreach_get(px)
    finally:
        bpy.data.images.remove(im)
    return np.clip(np.round(px.reshape(h, w, 4)[::-1] * 255), 0, 255).astype(np.uint8)


def _png_write(px, tmpdir):
    h, w = px.shape[:2]
    im = bpy.data.images.new('cortana_out', w, h, alpha=True)
    try:
        im.colorspace_settings.name = 'Non-Color'
        im.pixels.foreach_set((px[::-1].astype(np.float32) / 255).reshape(-1))
        f = os.path.join(tmpdir, 'cortana_out.png')
        im.filepath_raw = f
        im.file_format = 'PNG'
        im.save()
    finally:
        bpy.data.images.remove(im)
    with open(f, 'rb') as h:
        return h.read()


def _model_space(z, arm):
    idx = {n: i for i, n in enumerate(z['bones'])}
    a, b = [], []
    for bone in arm.data.bones:
        if bone.name in idx:
            a.append(list(arm.matrix_world @ bone.head_local))
            b.append(z['origins'][idx[bone.name]])
    if len(a) < 10:
        raise RuntimeError(f'only {len(a)} of her bones match the game skeleton by name')
    s, R, t, miss = fit(np.array(a), np.array(b))
    if miss > 0.01:
        raise RuntimeError(f'her skeleton here is {miss * 100:.1f} cm off the game one (bones moved in Edit Mode?)')
    return s, R, t, idx


def _mesh_groups(ctx, z, subdiv, report):
    parts = _parts()
    if not parts:
        raise RuntimeError('no Cortana meshes here (meshes with an Armature modifier and her materials)')
    arm = next(m.object for m in parts[0].modifiers if m.type == 'ARMATURE' and m.object)
    s, R, t, idx = _model_space(z, arm)
    was = arm.data.pose_position
    off = []
    for o in parts:
        for m in o.modifiers:
            if m.type == 'SUBSURF' and m.show_viewport and not subdiv:
                m.show_viewport = False
                off.append(m)
    arm.data.pose_position = 'REST'
    ctx.view_layer.update()
    old = {g['mesh']: g for g in z['groups']}
    new = []
    try:
        dg = ctx.evaluated_depsgraph_get()
        for o in parts:
            ev = o.evaluated_get(dg)
            me = ev.to_mesh()
            try:
                W = np.array(o.matrix_world)
                co = np.empty(len(me.vertices) * 3)
                me.vertices.foreach_get('co', co)
                P = co.reshape(-1, 3) @ W[:3, :3].T + W[:3, 3]
                P = s * P @ R.T + t
                cn = np.empty(len(me.loops) * 3)
                if hasattr(me, 'corner_normals'):
                    me.corner_normals.foreach_get('vector', cn)
                else:
                    me.calc_normals_split()
                    me.loops.foreach_get('normal', cn)
                Nn = cn.reshape(-1, 3) @ np.linalg.inv(W[:3, :3]) @ R.T
                lay = next((l for l in me.uv_layers if l.active_render), me.uv_layers[0])
                UV = np.empty(len(me.loops) * 2)
                lay.data.foreach_get('uv', UV)
                UV = UV.reshape(-1, 2)
                polys = [list(p.loop_indices) for p in me.polygons]
                gnames = {g.index: g.name for g in o.vertex_groups}
                JW = np.zeros((len(me.vertices), 4, 2))
                for v in me.vertices:
                    ws = sorted(((g.weight, idx[gnames[g.group]]) for g in v.groups
                                 if g.weight > 0 and gnames.get(g.group) in idx), reverse=True)[:4]
                    for k, (wt, bi) in enumerate(ws):
                        JW[v.index, k] = (bi, wt)
                me.calc_loop_triangles()
                lv = np.empty(len(me.loops), int)
                me.loops.foreach_get('vertex_index', lv)
                by_mat = {}
                for lt in me.loop_triangles:
                    by_mat.setdefault(lt.material_index, []).append([(lv[l], l) for l in lt.loops])
                base = o.name.split('.')[0].replace('__default', '')
                for mi, tris in sorted(by_mat.items()):
                    mat = o.material_slots[mi].material if mi < len(o.material_slots) else None
                    slot = _slot_of(mat)
                    if slot is None:
                        report.append(f'{base}: skipped faces with material {mat.name if mat else "none"}')
                        continue
                    AUV = atlas_uv(slot, UV, polys)
                    v, tr = build_group(P, Nn, AUV, np.array(tris), JW)
                    name = base + '_eyes' if slot == 'eye' and len(by_mat) > 1 else base
                    g0 = old.get(name)
                    new.append(dict(mesh=name, piece=g0['piece'] if g0 else 'body', tex=g0['tex'] if g0 else 'atlas',
                                    flags=3 if slot == 'hair' else 0, level=g0['level'] if g0 else 0, v=v, tris=tr))
                    report.append(f'{name}: {len(v)} points, {len(tr)} triangles' + ('' if g0 else ' (new part)'))
            finally:
                ev.to_mesh_clear()
    finally:
        arm.data.pose_position = was
        for m in off:
            m.show_viewport = True
        ctx.view_layer.update()
    order = [g['mesh'] for g in z['groups']]
    missing = [n for n in order if n not in {g['mesh'] for g in new}]
    if missing:
        raise RuntimeError('these parts of her are not in this file: ' + ', '.join(missing))
    new.sort(key=lambda g: order.index(g['mesh']) if g['mesh'] in order else len(order))
    return new


def send(ctx, path, bakes_dir, backup_dir, subdiv):
    if not os.path.isfile(path):
        return None, f'Game file not found: {path}'
    tmp = bpy.app.tempdir or os.path.dirname(path)
    z = read_zsk(path)
    report = []
    groups = _mesh_groups(ctx, z, subdiv, report)
    bakes = {}
    for slot in BAKED:
        for tile in NEW['regions'][slot]:
            f = os.path.join(bakes_dir, f'cortana_{slot}_baked.{tile}.png')
            if os.path.isfile(f):
                bakes[(slot, tile)] = _png_read(f, tmp)
    report.append('Bakes used: ' + (', '.join(f'{s} {t}' for s, t in bakes) or 'none (her colours stay as they are)'))
    old_px = _png_read(z['textures']['atlas'], tmp)
    z['textures']['atlas'] = _png_write(relayout(old_px, bakes), tmp)
    z['groups'] = groups
    os.makedirs(backup_dir, exist_ok=True)
    bak = os.path.join(backup_dir, time.strftime('cortana-%Y%m%d-%H%M%S.zsk'))
    shutil.copy2(path, bak)
    report.insert(0, f'Backup: {bak}')
    write_zsk(path, z)
    read_zsk(path)  # reads back cleanly, or this throws now instead of in the game
    return report, None


if bpy is not None:
    class CORTANA_OT_game_send(bpy.types.Operator):
        bl_idname = 'cortana.game_send'
        bl_label = 'Send to game'
        bl_description = 'Back up cortana.zsk, then write her meshes and your bakes into it'

        def execute(self, ctx):
            sc = ctx.scene
            try:
                rep, err = send(ctx, bpy.path.abspath(sc.cortana_zsk), bpy.path.abspath(sc.cortana_bakes),
                                bpy.path.abspath(sc.cortana_backups), sc.cortana_subdiv)
            except Exception as e:  # say what went wrong in the status bar, not just the console
                self.report({'ERROR'}, f'Not sent: {e}')
                return {'CANCELLED'}
            if err:
                self.report({'WARNING'}, err)
                return {'CANCELLED'}
            for line in rep:
                print('Cortana:', line)
            self.report({'INFO'}, 'Sent. Start Minecraft to see her. ' + rep[0])
            return {'FINISHED'}

    class CORTANA_OT_game_restore(bpy.types.Operator):
        bl_idname = 'cortana.game_restore'
        bl_label = 'Put back last backup'
        bl_description = 'Copy the newest backup over cortana.zsk'

        def execute(self, ctx):
            sc = ctx.scene
            d = bpy.path.abspath(sc.cortana_backups)
            baks = sorted(f for f in os.listdir(d) if f.startswith('cortana-') and f.endswith('.zsk')) if os.path.isdir(d) else []
            if not baks:
                self.report({'WARNING'}, 'No backups yet.')
                return {'CANCELLED'}
            shutil.copy2(os.path.join(d, baks[-1]), bpy.path.abspath(sc.cortana_zsk))
            self.report({'INFO'}, 'Put back ' + baks[-1])
            return {'FINISHED'}

    class CORTANA_PT_game(bpy.types.Panel):
        bl_label = 'Cortana game'
        bl_idname = 'CORTANA_PT_game'
        bl_space_type = 'VIEW_3D'
        bl_region_type = 'UI'
        bl_category = 'Cortana game'

        def draw(self, ctx):
            sc = ctx.scene
            col = self.layout.column(align=True)
            col.prop(sc, 'cortana_zsk', text='Game file')
            col.prop(sc, 'cortana_bakes', text='Bakes')
            col.prop(sc, 'cortana_backups', text='Backups')
            col.prop(sc, 'cortana_subdiv')
            col.separator()
            col.label(text='Minecraft closed, then:')
            col.operator('cortana.game_send', icon='EXPORT')
            col.separator()
            col.operator('cortana.game_restore', icon='LOOP_BACK')

    CLASSES = (CORTANA_OT_game_send, CORTANA_OT_game_restore, CORTANA_PT_game)
    ADD = r'C:\Dev\Zomboidcraft-Desktop\additions'

    def register():
        S = bpy.types.Scene
        S.cortana_zsk = bpy.props.StringProperty(
            name='Game file', subtype='FILE_PATH',
            default=os.path.expandvars(r'%APPDATA%\ModrinthApp\profiles\Sword-Base\config\swordbase\npc\cortana.zsk'))
        S.cortana_bakes = bpy.props.StringProperty(name='Bakes', subtype='DIR_PATH',
                                                   default=ADD + r'\halo-4\cortana\model\shading-v2' + '\\')
        S.cortana_backups = bpy.props.StringProperty(name='Backups', subtype='DIR_PATH',
                                                     default=ADD + r'\cortana-mixamo\zsk-backups' + '\\')
        S.cortana_subdiv = bpy.props.BoolProperty(
            name='Include Subdivision', default=True,
            description='Send her Subdivision smoothing too (about 4x the triangles on those parts, so heavier in game)')
        for c in CLASSES:
            bpy.utils.register_class(c)

    def unregister():
        for c in reversed(CLASSES):
            bpy.utils.unregister_class(c)
        for p in ('cortana_zsk', 'cortana_bakes', 'cortana_backups', 'cortana_subdiv'):
            delattr(bpy.types.Scene, p)

    if __name__ == '__main__':
        try:
            unregister()
        except Exception:
            pass
        register()
