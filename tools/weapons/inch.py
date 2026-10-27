"""Modelling in real measurements: guns and attachments are drawn in inches from their real dimensions.

One build unit (a sixteenth of a block) is 31 mm, the scale the first rifle was drawn at, so a carbine is
about 27 units long. The frame: x to the gun's right (the ejection port side), y up, z back toward the
stock, so the muzzle points to -z.
"""
import math

import numpy as np

IN = 25.4 / 31.0        # build units per inch


class Inch:
    """Draws into a Model in inches measured from `origin` (a point in build units)."""

    def __init__(self, m, origin=(0.0, 0.0, 0.0)):
        self.m = m
        self.o = np.array(origin, float)

    def p(self, x, y, z):
        return self.o + IN * np.array([x, y, z], float)

    def box(self, lo, hi, mat, **kw):
        return self.m.box(self.p(*lo), self.p(*hi), mat, **kw)

    def rod(self, a0, a1, r, mat, axis=2, at=(0.0, 0.0), **kw):
        """A round bar along `axis` from a0 to a1, centred at `at` on the other two axes (in x, y, z order)."""
        others = [i for i in range(3) if i != axis]
        o = self.o
        return self.m.rod(o[axis] + IN * a0, o[axis] + IN * a1, IN * r, mat,
                          cx=o[others[0]] + IN * at[0], cz=o[others[1]] + IN * at[1], axis=axis, **kw)

    def round_rod(self, z0, z1, r, mat, at=(0.0, 0.0), **kw):
        """A small round bar along z that still reads as round: four boxes in a stepped circle, where rod draws one
        square box for anything under a third of a pixel (a cartridge, seen up close in first person)."""
        x, y = at
        for ca, cb in ((0.98, 0.195), (0.83, 0.556), (0.556, 0.83), (0.195, 0.98)):
            self.box((x - r * ca, y - r * cb, z0), (x + r * ca, y + r * cb, z1), mat, axis=2, round=IN * r, **kw)

    def ogive(self, z_base, z_tip, r, mat, steps=4, **kw):
        """A bullet's rounded nose from radius r at z_base to a blunt point at z_tip, in round steps."""
        for i in range(steps):
            t0, t1 = i / steps, (i + 1) / steps
            rr = r * (1 - t0 ** 2) ** 0.5 if t0 > 0 else r
            self.round_rod(z_base + (z_tip - z_base) * t0, z_base + (z_tip - z_base) * t1, max(rr, r * 0.3), mat, **kw)

    def seg(self, p0, p1, w, t, mat, x=0.0, **kw):
        """A straight bar in the side (z-y) plane from p0 to p1, each (z, y); w across it, t wide in x."""
        o = self.o
        return self.m.seg((o[2] + IN * p0[0], o[1] + IN * p0[1]), (o[2] + IN * p1[0], o[1] + IN * p1[1]),
                          IN * w, IN * t, mat, z=o[0] + IN * x, plane='zy', **kw)

    def tube(self, z0, z1, r_in, r_out, mat, at=(0.0, 0.0), **kw):
        """A hollow round tube along z (sixteen staves), so you can see through it."""
        o = self.o
        rm, w = (r_in + r_out) / 2, r_out - r_in
        return self.m.arc((o[0] + IN * at[0], o[1] + IN * at[1]), IN * rm, -11.25, 348.75, IN * w,
                          IN * abs(z1 - z0), mat, steps=16, z=o[2] + IN * (z0 + z1) / 2, **kw)

    def ring(self, z0, z1, r, mat, at=(0.0, 0.0), steps=8, w=0.08, **kw):
        """A thin ring of bars round the z axis: a reticle, a lens rim."""
        o = self.o
        step = 360.0 / steps
        return self.m.arc((o[0] + IN * at[0], o[1] + IN * at[1]), IN * r, -step / 2, 360 - step / 2, IN * w,
                          IN * abs(z1 - z0), mat, steps=steps, z=o[2] + IN * (z0 + z1) / 2, **kw)


def picatinny(b, z0, z1, side='top', base=0.83, mat='anod', across=0.0):
    """A MIL-STD-1913 rail from z0 to z1 on the face `base` inches out from the centre line: a spine,
    the dovetail flats and a cross-slot every 0.394"."""
    pitch, tooth = 0.394, 0.188

    def put(a0, a1, o0, o1, m=mat):
        pts = []
        for a in (a0, a1):
            for o in (o0, o1):
                if side == 'top':
                    pts.append((across + a, o))
                elif side == 'bottom':
                    pts.append((across + a, -o))
                elif side == 'left':
                    pts.append((-o, across + a))
                else:
                    pts.append((o, across + a))
        xs, ys = [p[0] for p in pts], [p[1] for p in pts]
        return (min(xs), min(ys)), (max(xs), max(ys)), m

    out = []
    for (x0, y0), (x1, y1), m in (put(-0.31, 0.31, base, base + 0.23), put(-0.4175, 0.4175, base + 0.1, base + 0.23)):
        out.append(b.box((x0, y0, z0), (x1, y1, z1), m))
    n = int(math.floor((z1 - z0 - 0.12 - tooth) / pitch)) + 1
    start = z0 + ((z1 - z0) - ((n - 1) * pitch + tooth)) / 2
    for k in range(n):
        zt = start + k * pitch
        (x0, y0), (x1, y1), m = put(-0.31, 0.31, base + 0.23, base + 0.37)
        out.append(b.box((x0, y0, zt), (x1, y1, zt + tooth), m))
    return out
