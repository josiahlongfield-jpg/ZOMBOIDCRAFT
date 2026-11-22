"""Cortana desk mist pad v1: parametric printed parts (mm). x = width, y = depth (front y=0), z = up (desk z=0)."""
import json, math, sys
import numpy as np
import manifold3d as mf
from manifold3d import Manifold as M, CrossSection as CS
import trimesh

SEG = 72

def box(x0, y0, z0, x1, y1, z1):
    x0, x1 = min(x0, x1), max(x0, x1); y0, y1 = min(y0, y1), max(y0, y1); z0, z1 = min(z0, z1), max(z0, z1)
    return M.cube([x1 - x0, y1 - y0, z1 - z0]).translate([x0, y0, z0])

def cyl(r, h, x=0, y=0, z=0, seg=SEG, r2=None):
    return M.cylinder(h, r, r if r2 is None else r2, seg).translate([x, y, z])

def cylx(r, h, x, y, z, seg=SEG):  # along +x
    return M.cylinder(h, r, r, seg).rotate([0, 90, 0]).translate([x, y, z])

def cyly(r, h, x, y, z, seg=SEG):  # along +y
    return M.cylinder(h, r, r, seg).rotate([-90, 0, 0]).translate([x, y, z])

def prism(pts, z0, h):
    return M.extrude(CS([pts]), h).translate([0, 0, z0])

def chamfer_rect(x0, y0, x1, y1, c):
    return [(x0 + c, y0), (x1 - c, y0), (x1, y0 + c), (x1, y1 - c), (x1 - c, y1), (x0 + c, y1), (x0, y1 - c), (x0, y0 + c)]

def chamfer_front(x0, y0, x1, y1, c):  # chamfer only the two front corners
    return [(x0 + c, y0), (x1 - c, y0), (x1, y0 + c), (x1, y1), (x0, y1), (x0, y0 + c)]

def union(parts):
    return M.batch_boolean(parts, mf.OpType.Add) if len(parts) > 1 else parts[0]

def hexgrid(x0, y0, x1, y1, cell, wall, z0, h):
    """hex cell cutters filling a rectangle (flat-to-flat = cell)"""
    r = cell / math.sqrt(3)  # circumradius for flat-to-flat cell
    pitch_x = cell + wall
    pitch_y = (cell + wall) * math.sqrt(3) / 2
    cutters = []
    row = 0
    y = y0
    while y < y1 + cell:
        x = x0 + (pitch_x / 2 if row % 2 else 0)
        while x < x1 + cell:
            cutters.append(cyl(r, h, x, y, z0, seg=6).rotate([0, 0, 0]))
            x += pitch_x
        y += pitch_y
        row += 1
    return union(cutters) ^ box(x0, y0, z0 - 1, x1, y1, z0 + h + 1)

