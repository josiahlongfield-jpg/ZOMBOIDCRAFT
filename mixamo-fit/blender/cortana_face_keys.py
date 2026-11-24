"""Her face dials as shape keys you can animate (Josiah's choice, 24 Nov 2026).

Her smile, frown, brows, blinks and the rest aren't stored in the model: her desktop body (Face.cs) and the game work
them out live, by moving the points of her head and eyelashes with a few formulas. This script runs those exact
formulas once and saves the result as shape keys on head__default and eyelashes__default, one per dial, named
face_smile, face_frown, face_browUL ... Each key at 1.0 is that dial at 1.0, the same face she makes herself. Her jaw is
the b_jaw bone, so it isn't a key.

You run it yourself (her mesh and shape keys are yours; Cortana never adds or changes them): open your Cortana .blend,
Scripting tab, open this file (it needs cortana_game.py beside it), Run Script. A "Cortana face" tab appears in the 3D
view's side panel (N) with:
  Make face shape keys   adds the keys (or redoes them, if they're there), all at 0, so nothing changes until you
                         move a slider. One Ctrl+Z takes them off again.
  Remove face shape keys takes off only the face_ keys (and Basis, if nothing else uses it).
It reads the game's cortana.zsk once to line her up with her game self; nothing is written to the game.
Blinks and squints turn her eyelids, which a shape key can only slide, so in between 0 and 1 they're very close to,
not exactly, her own; at 0 and 1 they match.
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cortana_game as cg  # noqa: E402

# the dials in Face.cs's order (jaw, index 12 there, is the b_jaw bone instead)
DIALS = ("smile", "frown", "browUL", "browUR", "browIn", "browDn", "wide", "round", "lipIn", "squint", "blinkL", "blinkR",
         "upUp", "tuck", "press")
PREFIX = "face_"
FACE_PARTS = ("head", "eyelashes")
LIFT = 0.002456


# ---------------------------------------------------------------- Face.cs Shape(), unchanged (numpy, many points at once)

def _sm(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def _g(px, py, pz, cx, cy, cz, r):
    dx, dy, dz = px - cx, py - cy, pz - cz
    return np.exp(-(dx * dx + dy * dy + dz * dz) / (r * r))


def _lid(px, py, pz, ex, ey, ez, bl, sq):
    qx, qy, qz = px - ex, py - ey, pz - ez
    dist = np.sqrt(qx * qx + qy * qy + qz * qz)
    w = _sm(0.024, 0.013, dist) * _sm(0, 0.03, qz + 0.01)
    up, dn = _sm(-0.001, 0.005, qy), _sm(0.001, -0.005, qy)
    a = (bl * 0.95 + sq * 0.22) * w * up - sq * 0.12 * w * dn
    c, s = np.cos(a), np.sin(a)
    return ex + qx, ey + qy * c - qz * s, ez + qy * s + qz * c


def shape(P, F):
    """points P (n x 3, her space) with the dials F (dict name -> value) applied, exactly as Face.Shape"""
    f = {k: float(F.get(k, 0.0)) for k in DIALS}
    px, py, pz = P[:, 0].astype(np.float64), P[:, 1] - LIFT, P[:, 2].astype(np.float64)
    fr = _sm(0.03, 0.075, pz)
    on = fr > 0
    sx = np.sign(px)
    dx = np.zeros_like(px)
    dy = np.zeros_like(px)
    dz = np.zeros_like(px)
    bu = np.where(px > 0, f["browUL"], f["browUR"])
    dy += (0.008 * bu * _g(px, py, pz, sx * 0.034, 1.662, 0.104, 0.026)
           + 0.007 * f["browIn"] * _g(px, py, pz, sx * 0.015, 1.657, 0.112, 0.016)
           - 0.005 * f["browDn"] * _g(px, py, pz, sx * 0.015, 1.657, 0.112, 0.018))
    dx -= sx * 0.003 * f["browDn"] * _g(px, py, pz, sx * 0.015, 1.657, 0.112, 0.017)
    gc = _g(px, py, pz, sx * 0.025, 1.5655, 0.108, 0.012) * _sm(0.007, 0.017, np.abs(px))
    sm = f["smile"]
    dx += sx * 0.004 * sm * gc
    dy += 0.0058 * sm * gc
    dz -= 0.003 * sm * gc
    dy += 0.003 * sm * _g(px, py, pz, sx * 0.034, 1.59, 0.10, 0.02)
    dy -= 0.0045 * f["frown"] * gc
    dx += sx * 0.0035 * f["wide"] * gc
    dx -= sx * 0.012 * f["round"] * _g(px, py, pz, sx * 0.022, 1.5655, 0.11, 0.016) * _sm(0.004, 0.014, np.abs(px))
    dz += 0.0075 * f["round"] * _g(px, py, pz, 0, 1.564, 0.122, 0.019)
    dz -= 0.0022 * f["lipIn"] * _g(px, py, pz, 0, 1.560, 0.122, 0.008)
    upUp, tuck, press = f["upUp"], f["tuck"], f["press"]
    if upUp or tuck or press:
        up = _sm(1.561, 1.565, py)
        lo = 1 - up
        gu = _g(px, py, pz, 0, 1.569, 0.122, 0.011)
        gl = _g(px, py, pz, 0, 1.556, 0.121, 0.010)
        dy += 0.0036 * upUp * gu * up
        dz -= 0.0006 * upUp * gu * up
        dy += 0.0052 * tuck * gl * lo
        dz -= 0.0062 * tuck * gl * lo
        dy += press * (-0.0010 * gu * up + 0.0012 * gl * lo)
        dz -= 0.0012 * press * (gu * up + gl * lo)
    nx, ny, nz = px + dx * fr, py + dy * fr, pz + dz * fr
    eye = on & (ny > 1.60) & (ny < 1.67)
    lx, ly, lz = _lid(nx, ny, nz, 0.0316, 1.6335, 0.0993, f["blinkL"], f["squint"])
    rx, ry, rz = _lid(nx, ny, nz, -0.0317, 1.6335, 0.0993, f["blinkR"], f["squint"])
    left = nx >= 0
    nx2 = np.where(eye, np.where(left, lx, rx), nx)
    ny2 = np.where(eye, np.where(left, ly, ry), ny)
    nz2 = np.where(eye, np.where(left, lz, rz), nz)
    out = np.stack([np.where(on, nx2, P[:, 0]), np.where(on, ny2 + LIFT, P[:, 1]), np.where(on, nz2, P[:, 2])], 1)
    return out


# ---------------------------------------------------------------- Blender

def default_zsk():
    return os.path.join(os.environ.get("APPDATA", ""), "ModrinthApp", "profiles", "Sword-Base", "config", "swordbase", "npc",
                        "cortana.zsk")


def _face_objects():
    out = []
    for o in cg._parts():
        base = o.name.split(".")[0].replace("__default", "")
        if base in FACE_PARTS:
            out.append((base, o))
    return out


def make(ctx, zsk_path):
    """adds (or redoes) her face shape keys on her head and eyelashes; returns lines saying what it did"""
    z = cg.read_zsk(zsk_path)
    parts = _face_objects()
    if not parts:
        raise RuntimeError("no head__default or eyelashes__default with her armature in this file")
    arm = next(m.object for m in parts[0][1].modifiers if m.type == "ARMATURE" and m.object)
    s, R, t, _ = cg._model_space(z, arm)
    rep = []
    for base, o in parts:
        me = o.data
        n = len(me.vertices)
        if me.shape_keys is None:
            o.shape_key_add(name="Basis", from_mix=False)
        ref = me.shape_keys.reference_key
        co = np.empty(n * 3)
        ref.data.foreach_get("co", co)
        co = co.reshape(-1, 3)
        W = np.array(o.matrix_world)
        A, b = W[:3, :3], W[:3, 3]
        P = s * (co @ A.T + b) @ R.T + t           # her game space
        moved = 0
        for d in DIALS:
            Q = shape(P, {d: 1.0})
            back = (((Q - t) / s) @ R - b) @ np.linalg.inv(A).T
            name = PREFIX + d
            kb = me.shape_keys.key_blocks.get(name)
            if kb is None:
                kb = o.shape_key_add(name=name, from_mix=False)
            kb.data.foreach_set("co", back.astype(np.float32).ravel())
            kb.relative_key = ref
            kb.slider_min, kb.slider_max = 0.0, 1.0
            kb.value = 0.0
            moved = max(moved, int((np.abs(Q - P).max(1) > 1e-6).sum()))
        rep.append("%s: %d face keys, up to %d of its %d points move" % (o.name, len(DIALS), moved, n))
    return rep


def remove(ctx):
    rep = []
    for _, o in _face_objects():
        sk = o.data.shape_keys
        if sk is None:
            continue
        gone = 0
        for kb in [k for k in sk.key_blocks if k.name.startswith(PREFIX)]:
            o.shape_key_remove(kb)
            gone += 1
        sk = o.data.shape_keys
        if sk is not None and len(sk.key_blocks) == 1 and sk.key_blocks[0].name == "Basis":
            o.shape_key_remove(sk.key_blocks[0])
        rep.append("%s: %d face keys removed" % (o.name, gone))
    return rep


try:
    import bpy

    class CORTANA_FACE_OT_make(bpy.types.Operator):
        """Add her face dials (smile, frown, brows, blinks ...) as shape keys on her head and eyelashes, all at 0"""
        bl_idname = "cortana_face.make"
        bl_label = "Make face shape keys"
        bl_options = {"REGISTER", "UNDO"}

        def execute(self, ctx):
            try:
                for line in make(ctx, bpy.path.abspath(ctx.scene.cortana_face_zsk)):
                    self.report({"INFO"}, line)
            except Exception as e:
                self.report({"ERROR"}, str(e))
                return {"CANCELLED"}
            return {"FINISHED"}

    class CORTANA_FACE_OT_remove(bpy.types.Operator):
        """Take off only her face_ shape keys"""
        bl_idname = "cortana_face.remove"
        bl_label = "Remove face shape keys"
        bl_options = {"REGISTER", "UNDO"}

        def execute(self, ctx):
            for line in remove(ctx):
                self.report({"INFO"}, line)
            return {"FINISHED"}

    class CORTANA_FACE_PT_panel(bpy.types.Panel):
        bl_idname = "CORTANA_FACE_PT_panel"
        bl_label = "Cortana face"
        bl_space_type = "VIEW_3D"
        bl_region_type = "UI"
        bl_category = "Cortana face"

        def draw(self, ctx):
            col = self.layout.column(align=True)
            col.prop(ctx.scene, "cortana_face_zsk", text="Game file")
            col.operator("cortana_face.make", icon="SHAPEKEY_DATA")
            col.operator("cortana_face.remove", icon="X")
            col.label(text="Keys are on head__default (Object Data tab)")

    _CLASSES = (CORTANA_FACE_OT_make, CORTANA_FACE_OT_remove, CORTANA_FACE_PT_panel)

    def register():
        bpy.types.Scene.cortana_face_zsk = bpy.props.StringProperty(name="Game file", subtype="FILE_PATH",
                                                                    default=default_zsk())
        for c in _CLASSES:
            try:
                bpy.utils.unregister_class(c)
            except Exception:
                pass
            bpy.utils.register_class(c)

    if __name__ == "__main__":
        register()
except ImportError:  # outside Blender: shape() alone, for checking against Face.cs
    pass
