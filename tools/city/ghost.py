#!/usr/bin/env python3
"""The ghost station's kit: EXCHANGE HALL, the Grayhaven Subway's stop that closed in 1961 and was
sealed from the street, and the transit authority's control bunker dug under it (world/county/
GhostSite.java; the bunker is Bunker kind SUBWAY).

After the photos in refs/subway: the abandoned City Hall station (station-map-boards/03: a vault of
Guastavino tile laid herringbone, cream with bands of dark green, brass lamps round a leaded
skylight), the mosaic name tablets (platform/04, 05), the tunnels (tunnel-track/01, 02, 05: soot,
cable runs, the dark), and the Cincinnati subway's portal (tunnel-track/03: an arch in the wall
filled in, a steel double door painted grey-blue, a grille in the arch's head over it). Fictional
names only.

- gh_tile, gh_tile_green, gh_tile_band: the old station's cream tile, its green dado, the band of
  green and cream at eye height;
- gh_vault: the herringbone tile vault (its underside), gh_vault_rib: the green bands that cross it,
  gh_haunch: the vault springing from the wall, gh_skylight: the leaded skylight, tarred over from
  the street above;
- gh_fixture: the brass lamp hung from the vault (dead), gh_name_0..3: EXCHANGE HALL in mosaic;
- gh_brick, gh_infill: the infill that closed the platform off from the tunnel (gh_infill keeps the tunnel concrete on its track side);
- gh_portal_<col>_<row>: the arch in the tunnel wall, four by four (row 0 at the bottom, the door's
  two middle columns of rows 0 and 1 are gh_door), the concrete round it the tunnel's own;
- gh_arch_1_3, gh_arch_2_3: the head of an arch bricked up whole (its middle below is gh_brick);
- gh_door: the steel door, grey-blue (a door block, so two side by side are a double door);
- gh_closed: STATION CLOSED notice, gh_poster_0..2: what was left on the walls in 1961;
- gh_line_map_0..1: the line's map with this stop crossed out (the paper maps' clue);
- gh_staff: TRANSIT AUTHORITY / AUTHORIZED PERSONNEL ONLY over the door down;
- gh_diagram_0..7: the control room's track diagram, four by two, its lamps dead;
- gh_console: the control desk under it.

    python3 tools/city/ghost.py        rewrites the gh_ entries in blocks.json

photo_textures.py draws the textures (ghost.register()). The base model faces north: anything on
a wall has its back at z = 16 against the wall; tile 0 is the viewer's left.
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import railway  # noqa: E402
from furniture import area, disc  # noqa: E402
from school import Pic  # noqa: E402
from diner import stext, rivets, peel  # noqa: E402
from gasstation import weather  # noqa: E402

N = P.N
HERE = Path(__file__).parent
NS = None
SERIF = railway.SERIF
SANS = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
CREAM = (222, 210, 176)
GREEN = (46, 86, 62)
STEEL = (104, 128, 142)   # the portal door's grey-blue
NAME = "EXCHANGE|HALL"
STOPS = ["UNION STATION", "STADIUM", "BROADCAST TOWER", "EXCHANGE HALL", "POWER STATION", "WATERFRONT", "MERCY GENERAL"]


# ---------------------------------------------------------------- the old station

def tile(seed, rgb, gone=0.12):
    return railway.tile_broken(seed, rgb, dirt=0.55, gone=gone)


def band(seed):
    """Green and cream tiles in a checker, two rows, between green lines."""
    t = F.plain(seed, CREAM, 0.03, 0.75)
    t.f0[:] = 40
    s = 8
    for y in range(16, 48, s):
        for x in range(0, N, s):
            col = GREEN if ((x // s) + (y // s)) % 2 == 0 else CREAM
            area(t, x, y, x + s, y + s, col)
            area(t, x, y, x + s, y + 1, mul=0.7, dh=-0.2)
            area(t, x, y, x + 1, y + s, mul=0.7, dh=-0.2)
    area(t, 0, 10, N, 14, GREEN)
    area(t, 0, 50, N, 54, GREEN)
    t.mix(np.clip(P.fbm(t.rng) * 1.5 - 0.2, 0, 1), (86, 76, 58), 0.35)
    t.streaks(0.8, dark=0.8)
    return t


def herringbone(seed, rib=False):
    """The vault's underside: thin tiles laid herringbone in cream, a darker one here and there,
    soot along the joints; the rib is the same in green with a cream border."""
    base = GREEN if rib else CREAM
    t = F.plain(seed, base, 0.04, 0.7)
    t.f0[:] = 40
    rng = t.rng
    yy, xx = np.mgrid[0:N, 0:N]
    # bricks 8 x 4 at 45 degrees, alternating
    u = (xx + yy) % 16
    v = (xx - yy) % 16
    joint = ((u % 8 == 0) & (v < 8)) | ((v % 8 == 0) & (u >= 8)) | (u % 16 == 0) | (v % 16 == 0)
    cell = ((xx + yy) // 8 * 31 + (xx - yy) // 8 * 17) % 7
    shade = 0.92 + 0.03 * cell
    t.c *= shade[..., None]
    if not rib:
        dark = (cell == 3) & (rng.random((N, N)) < 1)
        t.c[dark] = t.c[dark] * 0.6 + np.array(GREEN, np.float32) * 0.4
    t.c[joint] = t.c[joint] * 0.55 + np.array((70, 64, 54), np.float32) * 0.45
    t.h[joint] -= 0.2
    if rib:
        for a, b in ((0, 4), (N - 4, N)):
            area(t, 0, a, N, b, CREAM)
            area(t, a, 0, b, N, CREAM)
    t.grime(0.45, (40, 36, 30))
    t.mix(np.clip(P.fbm(rng) * 2 - 0.8, 0, 1), (210, 206, 190), 0.25)   # lime weeping through
    return t


def skylight(seed):
    """Leaded glass in a brass ring, dark: the street was laid over it, tar seeping between the panes."""
    t = herringbone(seed)
    yy, xx = np.mgrid[0:N, 0:N] + 0.5
    r = np.hypot(xx - N / 2, yy - N / 2)
    glass = r < N * 0.42
    t.c[glass] = np.array((34, 52, 50), np.float32)
    t.c[glass] *= (1 + 0.25 * P.fbm(t.rng))[glass][..., None]
    lead = glass & ((np.abs((xx - N / 2) - (yy - N / 2)) < 1) | (np.abs((xx - N / 2) + (yy - N / 2)) < 1)
                    | (np.abs(r - N * 0.27) < 0.8) | (np.abs(r - N * 0.14) < 0.8))
    t.c[lead] = (30, 28, 26)
    t.h[lead] += 0.2
    ring = (r >= N * 0.42) & (r < N * 0.47)
    t.c[ring] = (150, 118, 60)
    t.f0[ring] = 230
    t.smooth[glass] = 0.85
    t.f0[glass] = 40
    t.mix(np.clip(P.fbm(t.rng) * 2 - 0.6, 0, 1) * glass, (14, 12, 10), 0.6)
    return t


def brass(seed):
    t = F.plain(seed, (150, 116, 58), 0.06, 0.6)
    t.f0[:] = 230
    t.mix(np.clip(P.fbm(t.rng) * 1.6 - 0.2, 0, 1), (60, 70, 50), 0.45)   # verdigris and soot
    return t


def name(i):
    t = Pic(4, 1, GREEN, 9701)
    W = t.W
    t.rect(4, 4, W - 4, N - 4, fill=(36, 50, 70))
    t.rect(7, 7, W - 7, N - 7, outline=(160, 120, 60), width=1.5)
    for k in range(0, W, 6):
        t.rect(k, 0, k + 3, 3, fill=(150, 64, 44) if (k // 6) % 2 else (190, 160, 90))
        t.rect(k, N - 3, k + 3, N, fill=(150, 64, 44) if (k // 6) % 2 else (190, 160, 90))
    stext(t, W / 2, 14, NAME, SERIF, 18, (226, 218, 196))
    tt = t.tile(i, 9710 + i, smooth=0.6, grime=0.3)
    for k in range(0, N, 2):
        area(tt, k, 0, k + 1, N, mul=0.88, dh=-0.1)
        area(tt, 0, k, N, k + 1, mul=0.88, dh=-0.1)
    tt.f0[:] = 40
    return tt


def brick(seed):
    """The infill: common brick laid in 1961, soot from the trains on it since, lime bleeding out."""
    t = P.bricks(seed, (112, 62, 48), (120, 114, 104), soot=0.6)
    t.mix(np.clip(P.fbm(t.rng) * 2 - 0.9, 0, 1), (196, 192, 178), 0.35)
    return t


# ---------------------------------------------------------------- the portal

def portal(col, row, grille=True):
    """One block of the arch in the tunnel wall, drawn over the tunnel's concrete: the stone ring,
    the brick inside it, the grille over the door in the arch's head."""
    t = NS.build("rw_tunnel")
    t.rng = np.random.default_rng(9720 + col * 7 + row)
    b = brick(9730 + col + row * 4)
    S = 4 * N
    yy, xx = np.mgrid[0:N, 0:N] + 0.5
    u = xx + col * N
    v = yy + (3 - row) * N   # from the top of the whole portal
    cx, spring = S / 2, 2.0 * N
    ri, ro = 1.62 * N, 1.95 * N
    dx = u - cx
    r = np.where(v < spring, np.hypot(dx, v - spring), np.abs(dx))
    inside = r < ri
    ring = (r >= ri) & (r < ro)
    # the brick infill
    t.c[inside] = b.c[inside]
    t.h[inside] = b.h[inside] - 0.15
    t.smooth[inside] = b.smooth[inside]
    # the voussoirs: lighter cast stone, joints radiating from the arch's centre
    stone = np.array((150, 146, 134), np.float32)
    t.c[ring] = stone * (0.9 + 0.12 * P.fbm(t.rng))[ring][..., None]
    ang = np.degrees(np.arctan2(v - spring, dx))
    joint = ring & (((v < spring) & (np.abs((ang + 180) % 12 - 6) < 0.5)) | ((v >= spring) & ((v.astype(int) % 16) == 0)))
    t.c[joint] *= 0.6
    t.h[ring] += 0.15
    t.h[joint] -= 0.2
    # the grille over the door: dark behind square bars, in the arch's head between the door's columns
    head = grille & inside & (v < spring) & (np.abs(dx) < N - 2) & (v > spring - ri + 6)
    if head.any():
        t.c[head] = (16, 15, 14)
        t.h[head] = 0.2
        bars = head & (((u.astype(int) % 8) < 2) | ((v.astype(int) % 16) < 2))
        t.c[bars] = np.array(STEEL, np.float32) * 0.55
        t.h[bars] = 0.8
        frame = grille & inside & (v < spring) & (np.abs(dx) >= N - 2) & (np.abs(dx) < N + 1) & (v > spring - ri + 6)
        t.c[frame] = np.array(STEEL, np.float32) * 0.7
    t.grime(0.35, (24, 22, 20))
    t.streaks(0.7, dark=0.8)
    return t


def door(seed, top):
    """The portal's steel door: plate on an angle frame, grey-blue paint chalking and flaking to the
    red lead, rust bleeding from the bolts; a hasp and a stencil on the top half."""
    t = F.plain(seed, STEEL, 0.05, 0.4)
    area(t, 0, 0, 3, N, mul=0.75, dh=0.2)
    area(t, N - 3, 0, N, N, mul=0.75, dh=0.2)
    area(t, 0, 0 if top else N - 4, N, 4 if top else N, mul=0.75, dh=0.2)
    area(t, 3, N // 2 - 1, N - 3, N // 2 + 1, mul=0.82, dh=0.15)
    rivets(t, (6, N - 7), step=8)
    peel(t, 3.0, (150, 70, 46))
    weather(t, rust=1.2, streak=0.8, grime=0.35)
    if top:
        m = F.text_mask("KEEP|OUT", SANS, 9, N - 12, 26, seed=seed)
        h, w = m.shape
        t.mix(np.pad(m, ((10, N - 10 - h), (6, N - 6 - w))), (230, 226, 210), 0.8)
    else:
        area(t, N - 14, 6, N - 6, 16, (70, 70, 72), dh=0.3)   # the hasp
        disc(t, N - 10, 20, 3.0, (60, 58, 56), dh=0.4)        # its padlock
    return t


# ---------------------------------------------------------------- notices, posters, the map

def notice(seed, text, bg=(232, 226, 204), ink=(30, 30, 30), size=10, card=False):
    """card: the notice blocks show only the middle 12/16 of the texture, so the text keeps inside that."""
    t = F.plain(seed, bg, 0.05, 0.2)
    w, h = ((N * 5 // 8), (N * 5 // 8)) if card else (N - 10, N - 14)
    if card:
        from PIL import ImageFont
        lines = text.split("|")
        while size > 4 and (max(ImageFont.truetype(SANS, size).getlength(x) for x in lines) > w - 1
                            or len(lines) * size * 1.05 > h):
            size -= 1
    m = F.text_mask(text, SANS, size, w, h, seed=seed)
    h, w = m.shape
    t.mix(np.pad(m, (((N - h) // 2, N - h - (N - h) // 2), ((N - w) // 2, N - w - (N - w) // 2))), ink, 0.95)
    t.mix(np.clip(P.fbm(t.rng) * 1.6 - 0.2, 0, 1), (140, 120, 84), 0.4)
    peel(t, 1.0, (90, 86, 80))
    return t


POSTERS = [("RIDE THE|HARBOR|LINE", (176, 60, 40), (238, 226, 196)),
           ("IN CASE OF|ATTACK|GO BELOW", (40, 40, 40), (226, 200, 60)),
           ("GRAYHAVEN|FAIR|1960", (40, 74, 120), (236, 224, 198))]


def poster(k):
    text, ink, bg = POSTERS[k]
    t = notice(9740 + k, text, bg, ink, 9)
    area(t, 4, N - 12, N - 4, N - 8, ink)
    return t


def line_map(i):
    """The line on a card two blocks wide: a loop through the stops, this one crossed out in red pencil."""
    p = Pic(2, 1, (232, 226, 206), 9750)
    W = p.W
    p.rect(3, 3, W - 3, N - 3, outline=(40, 40, 40), width=1)
    p.text(W / 2, 5, "GRAYHAVEN SUBWAY", SANS, 5, (40, 40, 40), anchor="ma")
    cx, cy, rx, ry = W / 2, N / 2 + 4, W / 2 - 16, N / 2 - 16
    p.ellipse(cx - rx, cy - ry, cx + rx, cy + ry, outline=(150, 40, 40), width=2)
    for k, s in enumerate(STOPS):
        a = 2 * np.pi * k / len(STOPS) - np.pi / 2
        x, y = cx + rx * np.cos(a), cy + ry * np.sin(a)
        p.ellipse(x - 2.5, y - 2.5, x + 2.5, y + 2.5, fill=(250, 250, 250), outline=(30, 30, 30), width=1)
        p.text(x, y + (4 if np.sin(a) >= 0 else -9), s, SANS, 3.2, (30, 30, 30), anchor="ma")
        if s == "EXCHANGE HALL":
            p.line([(x - 6, y - 6), (x + 6, y + 6)], (200, 30, 30), width=1.6)
            p.line([(x - 6, y + 6), (x + 6, y - 6)], (200, 30, 30), width=1.6)
    t = p.tile(i, 9752 + i, smooth=0.2, grime=0.25)
    return t


def diagram(i):
    """The model board: the loop drawn out flat in white on black, the stops as boxes with their
    names, the signal lamps along it in rows, every one of them dead."""
    p = Pic(4, 2, (22, 24, 24), 9760)
    W, H = p.W, p.H
    p.rect(2, 2, W - 2, H - 2, outline=(90, 92, 90), width=1.5)
    p.text(W / 2, 6, "GRAYHAVEN SUBWAY  -  LINE CONTROL", SANS, 7, (210, 206, 190), anchor="ma")
    for y in (40, 88):
        p.line([(12, y), (W - 12, y)], (220, 220, 214), width=2)
        for x in range(16, W - 12, 8):
            p.ellipse(x - 1.6, y - 6.6, x + 1.6, y - 3.4, fill=(80, 40, 36) if (x // 8) % 3 else (40, 70, 40))
    p.line([(12, 40), (6, 64), (12, 88)], (220, 220, 214), width=2)
    p.line([(W - 12, 40), (W - 6, 64), (W - 12, 88)], (220, 220, 214), width=2)
    for k, s in enumerate(STOPS):
        x = 24 + k * (W - 48) / (len(STOPS) - 1)
        y = 40 if k % 2 == 0 else 88
        p.rect(x - 14, y - 3, x + 14, y + 3, fill=(200, 196, 180) if s != "EXCHANGE HALL" else (90, 90, 86))
        p.text(x, y + (8 if y == 40 else -16), s, SANS, 4.2, (200, 196, 180), anchor="ma")
    t = p.tile(i, 9770 + i, smooth=0.5, grime=0.2)
    t.f0[:] = 60
    return t


def textures():
    T = {}
    T["gh_tile"] = lambda: tile(9700, CREAM)
    T["gh_tile_green"] = lambda: tile(9702, GREEN, gone=0.18)
    T["gh_tile_band"] = lambda: band(9703)
    T["gh_vault"] = lambda: herringbone(9704)
    T["gh_vault_rib"] = lambda: herringbone(9705, True)
    T["gh_skylight"] = lambda: skylight(9706)
    T["gh_brass"] = lambda: brass(9707)
    T["gh_brick"] = lambda: brick(9708)
    for i in range(4):
        T[f"gh_name_{i}"] = lambda i=i: name(i)
    for col in range(4):
        for row in range(4):
            if col in (1, 2) and row < 2:
                continue
            T[f"gh_portal_{col}_{row}"] = lambda col=col, row=row: portal(col, row)
    for col in (1, 2):
        T[f"gh_arch_{col}_3"] = lambda col=col: portal(col, 3, False)
    T["gh_door_top"] = lambda: door(9780, True)
    T["gh_door_bottom"] = lambda: door(9781, False)
    T["gh_closed"] = lambda: notice(9782, "STATION|CLOSED|TRAINS DO|NOT STOP", (226, 220, 196), (30, 30, 30), 9, True)
    T["gh_staff"] = lambda: notice(9783, "TRANSIT|AUTHORITY|AUTHORIZED|PERSONNEL|ONLY", (200, 40, 34), (240, 236, 220), 7, True)
    for k in range(len(POSTERS)):
        T[f"gh_poster_{k}"] = lambda k=k: poster(k)
    for i in range(2):
        T[f"gh_line_map_{i}"] = lambda i=i: line_map(i)
    for i in range(8):
        T[f"gh_diagram_{i}"] = lambda i=i: diagram(i)
    return T


def register(ns):
    global NS
    NS = ns
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", decor=False, tex=None, light=None, **extra):
        spec = {"model": "boxes", "boxes": boxes} if boxes else {"tex": tex}
        spec.update(title=title, sound=sound)
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        if light:
            spec["light"] = light
        spec.update(extra)
        out[name] = spec

    add("gh_tile", None, "Old Subway Tile", "STONE", rotate=None, tex={"all": "gh_tile"})
    add("gh_tile_green", None, "Old Subway Tile", "STONE", rotate=None, tex={"all": "gh_tile_green"})
    add("gh_tile_band", None, "Old Subway Tile", "STONE", rotate=None, tex={"all": "gh_tile_band"})
    add("gh_vault", None, "Tile Vault", "STONE", rotate=None, tex={"down": "gh_vault", "up": "rw_tunnel", "side": "gh_vault"})
    add("gh_vault_rib", None, "Tile Vault", "STONE", rotate=None, tex={"down": "gh_vault_rib", "up": "rw_tunnel",
                                                                        "side": "gh_vault_rib"})
    add("gh_skylight", None, "Skylight", "GLASS", rotate=None, tex={"down": "gh_skylight", "up": "rw_tunnel", "side": "gh_vault"})
    # the vault springing off the wall: tile stepped out to meet the crown (the wall's side at z = 16)
    haunch = [[0, y0, z0, 16, y1, 16, "gh_vault"] for y0, y1, z0 in
              ((4, 8, 14.5), (8, 11, 12.5), (11, 13, 10.5), (13, 14.5, 8), (14.5, 15.5, 5.5), (15.5, 16, 2.5))]
    add("gh_haunch", haunch, "Tile Vault", "STONE")
    # a brass lamp hung from the vault: rod, a ring of arms, five dead bulbs
    fix = [[7.5, 8, 7.5, 8.5, 16, 8.5, "gh_brass"], [4, 6, 4, 12, 7, 12, "gh_brass"], [6, 4, 6, 10, 6, 10, "gh_brass"]]
    for bx, bz in ((3, 3), (12, 3), (3, 12), (12, 12), (7.5, 7.5)):
        fix.append([bx - 1, 3, bz - 1, bx + 1, 5, bz + 1, "bunk_glass_off"])
    add("gh_fixture", fix, "Lamp", "METAL", rotate=None, decor=True)
    for i in range(4):
        add(f"gh_name_{i}", [[0, 0, 0, 16, 16, 16, {"north": f"gh_name_{i}", "side": "gh_tile", "up": "gh_tile", "down": "gh_tile"}]],
            "Name Mosaic", "STONE")
    add("gh_brick", None, "Brick Infill", "STONE", rotate=None, tex={"all": "gh_brick"})
    # the wall between the arches: the tunnel's concrete toward the track, the infill brick behind
    add("gh_infill", [[0, 0, 0, 16, 16, 16, {"north": "rw_tunnel", "south": "gh_brick", "side": "rw_tunnel", "up": "rw_tunnel",
                                            "down": "rw_tunnel"}]], "Brick Infill", "STONE")
    for col in range(4):
        for row in range(4):
            if col in (1, 2) and row < 2:
                continue
            n = f"gh_portal_{col}_{row}"
            add(n, [[0, 0, 0, 16, 16, 16, {"north": n, "south": "gh_brick", "side": "rw_tunnel", "up": "rw_tunnel", "down": "rw_tunnel"}]],
                "Bricked Arch", "STONE")
    # the other bricked-up arches along the old platform: no door, no grille (the rest of each is
    # gh_portal's own edge tiles and plain gh_brick)
    for col in (1, 2):
        n = f"gh_arch_{col}_3"
        add(n, [[0, 0, 0, 16, 16, 16, {"north": n, "south": "gh_brick", "side": "rw_tunnel", "up": "rw_tunnel", "down": "rw_tunnel"}]], "Bricked Arch", "STONE")
    out["gh_door"] = {"door": "steel", "tex": {"top": "gh_door_top", "bottom": "gh_door_bottom"}, "title": "Steel Door"}
    for k in ("closed", "staff"):
        add(f"gh_{k}", [[2, 2, 15.6, 14, 14, 16, {"north": f"gh_{k}", "side": "paper"}]], "Notice", "WOOL", decor=True)
    for k in range(len(POSTERS)):
        add(f"gh_poster_{k}", [[1, 0, 15.6, 15, 16, 16, {"north": f"gh_poster_{k}", "side": "paper"}]], "Poster", "WOOL",
            decor=True)
    for i in range(2):
        add(f"gh_line_map_{i}", [[0, 3, 15.4, 16, 13, 16, {"north": f"gh_line_map_{i}", "side": "cv_oak"}]], "Line Map", "WOOD",
            decor=True)
    for i in range(8):
        add(f"gh_diagram_{i}", [[0, 0, 15, 16, 16, 16, {"north": f"gh_diagram_{i}", "side": "bunk_plate"}]], "Track Diagram",
            "METAL", decor=True)
    # the control desk: a steel cabinet, its top raked toward the operator, switches and dead lamps on it
    add("gh_console", [[0, 0, 4, 16, 10, 16, {"north": "bunk_plate", "side": "bunk_plate", "up": "gh_diagram_5", "down": "bunk_plate"}],
                       [0, 10, 8, 16, 13, 16, {"north": "gh_diagram_6", "side": "bunk_plate", "up": "bunk_plate", "down": "bunk_plate"}]],
        "Control Desk", "METAL", loot="office")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    made = blocks()
    kept = {k: made.get(k, v) for k, v in data["blocks"].items() if not k.startswith("gh_") or k in made}
    kept.update({k: v for k, v in made.items() if k not in kept})
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} ghost station blocks in {path}")


if __name__ == "__main__":
    main()
