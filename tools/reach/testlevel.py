"""A made-up level for CI (no Halo files there): a 40 m floor at 0.5 m, a 4 m wall along x = 30.5 and a ramp.
Saved like the layout copies of a real level's collision (V metres x east, y up, z south; T triangles; M materials)."""
import sys

import numpy as np

V, T = [], []


def quad(a, b, c, d):
    i = len(V)
    V.extend([a, b, c, d])
    T.extend([(i, i + 1, i + 2), (i, i + 2, i + 3)])


quad((0, 0.5, 0), (40, 0.5, 0), (40, 0.5, 40), (0, 0.5, 40))           # floor
quad((30.5, 0.5, 0), (30.5, 4.5, 0), (30.5, 4.5, 40), (30.5, 0.5, 40))  # wall
quad((2, 0.5, 30), (12, 3.5, 30), (12, 3.5, 34), (2, 0.5, 34))          # ramp up to 3.5 m
np.savez(sys.argv[1], V=np.array(V, np.float32), T=np.array(T, np.int32), M=np.zeros(len(T), np.int16))
