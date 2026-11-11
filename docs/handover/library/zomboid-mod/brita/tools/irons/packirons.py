"""Josiah's attachment-pack flip-up irons (att_irons_front_up / att_irons_rear_up, deployed; OUR jar meshes) fitted on
a local gun in place of the MBUS-style kit sights (Josiah 2026-11-03 03:48: "switch to the iron sights from the pack
I bought, the old ones, but put them in the same position").

The pack sights are scaled so the rear aperture centre sits on the same sight line the kit sights had (same height
over the rail, same front/rear z), so the gun's aim-down-sights line is unchanged.
"""
import os
import numpy as np
from PIL import Image
import sightkit as K

HERE = os.path.dirname(os.path.abspath(__file__))
# measured on the pack meshes (item px, base of the clamp at y 0)
RAIL = 0.137          # underside of the clamp over the rail = rail top
REAR_C = (-0.0062, 0.803, 0.13)  # middle of the rear aperture hole (x, y, z of the leaf); 2026-11-03 re-measured by
                                 # filling the hole seen down the sight line (was 0.0015, 0.792: ~0.6 mm off centre)
FRONT_T = (-0.0065, 0.812, 0.133)  # top of the front post (middle of the post across, re-measured 2026-11-03)


def load(name):
    return K.read(os.path.join(HERE, 'pack', name + '.zmesh'), name)


def add_mesh(m, src, scale, offset, part='body'):
    """Appends src's triangles (scaled, then moved by offset) to m's part, its texture stacked under m's texture."""
    tex = m['tex']; W, H = tex.size
    st = src['tex'].resize((W, int(round(src['tex'].size[1] * W / src['tex'].size[0]))), Image.LANCZOS)
    h2 = st.size[1]
    new = Image.new('RGB', (W, H + h2)); new.paste(tex, (0, 0)); new.paste(st, (0, H))
    for p in m['parts']:
        p[1]['v'] = np.round(p[1]['v'].astype(float) * H / (H + h2)).astype(np.uint16)
    for name, rec in src['parts']:
        r = rec.copy()
        for k, ax in enumerate('xyz'):
            r[ax] = np.clip(np.round((rec[ax].astype(float) / 1000 * scale + offset[k]) * 1000), -32768, 32767)
        r['v'] = np.round((H + rec['v'].astype(float) / 65535 * h2) / (H + h2) * 65535).astype(np.uint16)
        for p in m['parts']:
            if p[0] == part:
                p[1] = np.concatenate([p[1], r.astype(m['dt'])]); break
    m['tex'] = new


def fit(b, X, rail, line, zf, zr, colour='dark'):
    """Puts the pack's front and rear flip-up sights on b's gun: rail = top of the rail, line = sight line height,
    zf / zr = where the old kit front / rear sight stood. Returns (front tip, rear aperture middle)."""
    suffix = '' if colour == 'base' else '_' + colour
    s = (line - rail) / (REAR_C[1] - RAIL)
    out = []
    for nm, ref, z in (('front', FRONT_T, zf), ('rear', REAR_C, zr)):
        # both the post top and the aperture middle on the line (the post sits ~1 mm lower into the rail for it)
        off = (X - s * ref[0], line - s * ref[1], z - s * ref[2])
        b.meshes.append((load(f'att_irons_{nm}_up{suffix}'), s, off))
        out.append((X, line, z))
    return tuple(out)
