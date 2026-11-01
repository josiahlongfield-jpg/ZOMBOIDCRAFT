#!/usr/bin/env python3
"""Draws a packed vehicle (.zcar) from a few sides, textured, for a quick look without the game.

    python3 tools/cars/look.py FILE.zcar ID OUT.png [--state usable] [--size 480]
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from zcar import read  # noqa: E402


def view(T, UV, tex, yaw, pitch, size, glass):
    a, b = np.radians(yaw), np.radians(pitch)
    Ry = np.array([[np.cos(a), 0, np.sin(a)], [0, 1, 0], [-np.sin(a), 0, np.cos(a)]])
    Rx = np.array([[1, 0, 0], [0, np.cos(b), -np.sin(b)], [0, np.sin(b), np.cos(b)]])
    c = T.reshape(-1, 3).mean(0)
    Q = (T - c) @ (Rx @ Ry).T
    span = np.abs(Q[..., :2]).max() * 1.1
    S = size / (2 * span)
    X = Q[..., 0] * S + size / 2
    Y = size / 2 - Q[..., 1] * S
    Z = Q[..., 2]
    img = np.full((size, size, 3), 200.0)
    zb = np.full((size, size), -1e9)
    th, tw = tex.shape[:2]
    light = np.array([0.4, 0.8, 0.45]) / np.linalg.norm([0.4, 0.8, 0.45])
    nrm = np.cross(T[:, 1] - T[:, 0], T[:, 2] - T[:, 0])
    nrm /= np.maximum(np.linalg.norm(nrm, axis=1, keepdims=True), 1e-9)
    for t in range(len(T)):
        xs, ys, zs = X[t], Y[t], Z[t]
        d = (xs[1] - xs[0]) * (ys[2] - ys[0]) - (xs[2] - xs[0]) * (ys[1] - ys[0])
        if abs(d) < 1e-9:
            continue
        x0, x1 = int(max(xs.min(), 0)), int(min(xs.max() + 1, size))
        y0, y1 = int(max(ys.min(), 0)), int(min(ys.max() + 1, size))
        if x1 <= x0 or y1 <= y0:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
        l1 = ((gx - xs[0]) * (ys[2] - ys[0]) - (xs[2] - xs[0]) * (gy - ys[0])) / d
        l2 = ((xs[1] - xs[0]) * (gy - ys[0]) - (gx - xs[0]) * (ys[1] - ys[0])) / d
        l0 = 1 - l1 - l2
        ins = (l0 >= 0) & (l1 >= 0) & (l2 >= 0)
        if not ins.any():
            continue
        z = l0 * zs[0] + l1 * zs[1] + l2 * zs[2]
        yy, xx = np.nonzero(ins)
        zz = z[ins]
        sel = zz > zb[yy + y0, xx + x0]
        yy, xx, zz = yy[sel], xx[sel], zz[sel]
        if not len(yy):
            continue
        L0, L1, L2 = l0[ins][sel], l1[ins][sel], l2[ins][sel]
        u = L0 * UV[t, 0, 0] + L1 * UV[t, 1, 0] + L2 * UV[t, 2, 0]
        v = L0 * UV[t, 0, 1] + L1 * UV[t, 1, 1] + L2 * UV[t, 2, 1]
        col = tex[np.clip((v * th).astype(int), 0, th - 1), np.clip((u * tw).astype(int), 0, tw - 1)]
        shade = 0.55 + 0.45 * abs(float(nrm[t] @ light))
        if glass[t]:
            col = col * 0.5 + np.array([120, 140, 150]) * 0.5
        zb[yy + y0, xx + x0] = zz
        img[yy + y0, xx + x0] = col * shade
    return Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('file')
    ap.add_argument('id')
    ap.add_argument('out')
    ap.add_argument('--state', default='usable')
    ap.add_argument('--size', type=int, default=420)
    a = ap.parse_args()
    st = read(a.id, a.file)[a.state]
    tex = np.asarray(st['image'], float)
    views = [(-35, 18), (145, 18), (90, 0), (0, 0)]
    ims = [view(st['T'], st['UV'], tex, y, p, a.size, st['glass']) for y, p in views]
    out = Image.new('RGB', (a.size * 2, a.size * 2))
    for i, im in enumerate(ims):
        out.paste(im, ((i % 2) * a.size, (i // 2) * a.size))
    out.save(a.out)


if __name__ == '__main__':
    main()
