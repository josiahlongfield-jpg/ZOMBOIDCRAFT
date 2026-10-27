"""The Glass Line: the gl_ kit for the see-through tube under the South Arm (Josiah 2026-10-27,
"an underwater subway tube that's see through ... and have a reason for doing so").

The story: in 1986 the harbour board sank a pair of glass tubes across the South Arm between the
island and the Eastside quays, instead of a fourth bridge the ships would have to pass under. It
was sold as the sight of the county (the brochures had the harbour seals); in practice it is a
shuttle under eight to fourteen blocks of grey harbour water, the glass is fogged with algae and
the lamps have been dying since before the end. It is still the end of the world: panes cracked,
one weeping where the seal let go, plywood bolted over the worst and a blue tarp taped over a leak
nobody came back to fix.

Each bore is ten blocks across, built of these pieces (o = blocks out from the bore's centre, the
centre falls between o -1 and 0; h = blocks over the track bed):

    h -1  gl_keel along the whole width (the concrete raft the pair sits on)
    h  0  gl_keel at o -5 and 4 (and under the walkways), the track's ballast between
    h  1  gl_sill at o -5 / 4, gl_walk at o -4 / 3: the walkways, at the cars' floor height
    h  2  gl_wall_lo at o -5 / 4 (the glass's foot in its channel, the handrail inside)
    h  3  gl_wall
    h  4  gl_chamfer at o -5 / 4  (the glass turns 45 degrees in toward the crown, through the
    h  5  gl_chamfer at o -4 / 3   blocks' corners, so three blocks make one straight pane)
    h  6  gl_chamfer at o -3 / 2, gl_crown at o -2..1 (the roof glass, the strip lamps under it
          at o -2 and 1: gl_crown_lamp)

Every fourth block along the tube the steel ring frame: the _rib pieces. Facing models, outward
is north (the glass at z = 0) and the tube runs along x; the right side of a bore is the same
blocks turned to face south.

Drawn from what such tubes and immersed tunnels are known to be (no photos were in the library:
subway/underwater-tube was queued with the references; redo what the photos show is off).

    python3 tools/city/glassline.py        rewrites the gl_ entries in blocks.json

photo_textures.py draws the textures (glassline.register()).
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
from furniture import area, disc  # noqa: E402
from diner import rivets  # noqa: E402
from gasstation import glow  # noqa: E402
import transit as T  # noqa: E402
import exterior  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BOLD = T.BOLD
NS = None

AQUA = (64, 168, 160)       # the Glass Line's colour
WHITE = T.WHITE             # the ring frames' white steel
SEA = (138, 178, 170)       # the glass's tint: thick laminated panes, green-blue at the edges
# the chamfer pane: a box laid through the block's outer-bottom and inner-top corners
DIAG = 11.3137              # half of 16 * sqrt 2
TURN = {"axis": "x", "angle": -45, "origin": [8, 8, 8]}


# ---------------------------------------------------------------- the textures

def star(t, cx, cy, legs=(5, 9), reach=(14, 40), col=(190, 200, 198)):
    """A star crack: legs wandering out from the strike, the strike itself crazed white."""
    for _ in range(int(t.rng.integers(*legs))):
        ang = t.rng.uniform(0, np.pi * 2)
        ln = t.rng.uniform(*reach)
        for i in range(int(ln)):
            ang += t.rng.normal(0, 0.07)
            x, y = int(cx + np.cos(ang) * i), int(cy + np.sin(ang) * i)
            if 0 <= x < N and 0 <= y < N:
                t.c[y, x] = col
                t.alpha[y, x] = 175
    disc(t, cx, cy, 2.0, col=(196, 204, 202), alpha=205)


def pane(seed, cracked=0, leak=False, silt=False):
    """Underwater glass: thick and clear but filmed with algae in soft patches (no edges: the
    panes run four blocks between the frames), a spat of tiny barnacles, silt settled on the
    crown's; a leak runs wet streaks down from a crack."""
    t = F.plain(seed, SEA, 0.0, 0.9)
    t.alpha[:] = 74
    t.f0[:] = 60
    film = np.clip(P.fbm(t.rng, ((2, 1.0), (4, 0.6), (8, 0.3), (16, 0.12))) * 1.6 + 0.05, 0, 1)
    t.mix(film * 0.75, (58, 92, 66), 0.85)
    t.alpha += film * 70
    spat = t.rng.random((N, N)) < 0.0012
    t.c[spat] = (120, 124, 108)
    t.alpha[spat] = 200
    if silt:
        # silt settles on the roof glass in a soft even skin, thicker in drifts
        mud = np.clip(P.fbm(t.rng, ((2, 1.0), (4, 0.6))) * 1.2 + 0.45, 0, 1)
        t.mix(mud * 0.7, (78, 76, 60), 0.9)
        t.alpha += mud * 95
    yy, xx = np.mgrid[0:N, 0:N]
    t.c += (np.clip(1 - np.abs(xx - yy - 10) / 9, 0, 1) * 14)[..., None]   # the sheen across it
    strikes = []
    for _ in range(cracked):
        cx, cy = t.rng.uniform(12, N - 12, 2)
        star(t, cx, cy)
        strikes.append((cx, cy))
    if leak and strikes:
        cx, cy = strikes[0]
        for _ in range(7):
            x = cx + t.rng.normal(0, 3)
            for y in range(int(cy), N):
                x += t.rng.normal(0, 0.25)
                xi = int(x) % N
                t.c[y, xi] = t.c[y, xi] * 0.55 + np.array((150, 176, 172)) * 0.45
                t.alpha[y, xi] = min(255, t.alpha[y, xi] + 60)
                t.smooth[y, xi] = 1.0
        disc(t, cx, cy, 4, col=(70, 96, 74), alpha=190)     # the slime round the weep
    t.alpha = np.clip(t.alpha, 0, 255)
    return t


def barnacles(t, amount, low=0.0):
    """Barnacles and mussels on anything that's been under for years: white volcano cones, blue
    black mussels in clumps, thickest toward the foot (low = how much toward it)."""
    rng = t.rng
    for _ in range(int(60 * amount)):
        x = rng.uniform(0, N)
        y = N * (1 - rng.random() ** (1 + low * 2))
        r = rng.uniform(0.9, 2.2)
        disc(t, x % N, min(y, N - 1), r, col=(184, 178, 160), h=0.95)
        disc(t, x % N, min(y, N - 1), r * 0.4, col=(40, 38, 32), dh=-0.3)
    for _ in range(int(10 * amount)):
        cx, cy = rng.uniform(0, N), N * (1 - rng.random() ** (1 + low * 3))
        for _ in range(int(rng.integers(3, 8))):
            x, y = cx + rng.normal(0, 2.5), cy + rng.normal(0, 2)
            m = F.blob_mask(rng, x % N, min(max(y, 0), N - 1), rng.uniform(1.0, 1.8), rough=0.3)
            t.mix(m, (28, 32, 44), 0.95)
            t.h += m * 0.25
            t.smooth += m * 0.4


def weed(t, amount):
    """The green-brown fur of weed and slime over everything below the tide."""
    m = np.clip(P.fbm(t.rng, ((8, 1.0), (16, 0.6), (32, 0.3))) * 2.2 - 0.6, 0, 1) * amount
    t.mix(m, (58, 70, 40), 0.8)
    t.h += m * 0.1


def keel(seed, top=False):
    """The raft: cast concrete, the shutter joints and tie holes, weed and barnacles on the
    sides; the top is the slab the walkway and the track sit in."""
    t = F.plain(seed, (118, 118, 110), 0.08, 0.2)
    t.vary(0.1)
    if top:
        t.grime(0.25)
        return t
    area(t, 0, 31, N, 32, mul=0.75, dh=-0.1)
    for x, y in ((12, 15), (44, 15), (12, 47), (44, 47)):
        disc(t, x, y, 1.6, col=(60, 60, 56), dh=-0.3)
    weed(t, 0.9)
    barnacles(t, 1.0, low=0.6)
    t.grime(0.2)
    return t


def rib(seed):
    """The ring frames: white steel plate, bolted, rust running from every bolt."""
    t = T.steel(seed, WHITE, rust=0.5, chips=1.0)
    rivets(t, (6, 26, 38, 58), step=8)
    t.streaks(0.6, 0.6)
    return t


def sill_out(seed):
    """The steel band the glass stands on, from outside: the line's teal, a row of bolts, rust
    weeping from them and barnacles crusting the foot."""
    t = T.steel(seed, AQUA, rust=0.35, chips=1.2)
    area(t, 0, 0, N, 6, col=(150, 154, 150), h=0.9)
    rivets(t, range(4, N, 8), step=64)
    t.streaks(0.7, 0.55)
    weed(t, 0.5)
    barnacles(t, 0.6, low=1.0)
    return t


def grating(seed, edge=False):
    """The walkway: galvanised grating, a bar every four pixels each way, mud and rust in the
    holes; the walkway's own has a yellow nosing along the side toward the track (z = 16)."""
    t = F.plain(seed, (14, 14, 12), 0.02, 0.1)
    t.h[:] = 0.1
    for k in range(0, N, 4):
        area(t, k, 0, k + 1, N, col=(132, 134, 130), h=0.9, f0=120)
        area(t, 0, k, N, k + 1, col=(122, 124, 120), h=0.85, f0=120)
    mud = np.clip(P.fbm(t.rng, ((4, 1.0), (8, 0.5))) * 2 - 0.2, 0, 1)
    t.mix(mud * 0.6, (70, 60, 44), 0.8)
    t.speckle(0.03, (120, 70, 40), 0.6)
    if edge:
        area(t, 0, 54, N, N, col=(196, 156, 30), h=0.9, smooth=0.3)
        for x in range(0, N, 8):
            area(t, x, 54, x + 4, N, mul=0.82)
        t.grime(0.18)
    return t


def walk_face(seed):
    """The walkway's face toward the track: concrete, the teal line, three black cables on
    hooks and the distance stencils every so often."""
    t = keel(seed, top=True)
    area(t, 0, 6, N, 10, col=AQUA, h=0.8)
    for y in (24, 34, 44):
        area(t, 0, y, N, y + 4, col=(22, 22, 22), h=0.95, smooth=0.5)
        area(t, 0, y, N, y + 1, mul=1.6)
    for x in (16, 48):
        area(t, x, 20, x + 2, 50, col=(92, 92, 88), h=1.0)
    t.streaks(0.5, 0.6)
    return t


def lamp(seed, on):
    """A sealed strip light seen from below: yellowed polycarbonate over a single tube, the
    aluminium housing, flies and silt inside the cover."""
    t = F.plain(seed, (150, 150, 140), 0.03, 0.5)
    area(t, 0, 0, N, 10, col=(96, 98, 96), h=0.9, f0=180)
    area(t, 0, 54, N, N, col=(96, 98, 96), h=0.9, f0=180)
    area(t, 0, 10, N, 54, col=(214, 206, 172) if on else (128, 124, 106), h=0.8, smooth=0.7)
    if on:
        tube = np.zeros((N, N), np.float32)
        tube[26:38, :] = 1
        glow(t, tube, (246, 244, 226), 1.0, 0.95)
        t.emit[10:54, :] = np.maximum(t.emit[10:54, :], 0.55)
    t.speckle(0.02, (30, 28, 24), 0.7)
    t.grime(0.15)
    return t


def plywood(seed):
    """A sheet of plywood bolted over a broken pane: the plies along its edge, coach bolts, the
    sealant squeezed out round it, LEAK sprayed on it and the date it was done."""
    t = F.wood(seed, "pine")
    area(t, 0, 0, N, 2, mul=0.7)
    area(t, 0, N - 2, N, N, mul=0.7)
    for x, y in ((6, 6), (58, 6), (6, 58), (58, 58), (32, 6), (32, 58)):
        disc(t, x, y, 1.8, col=(70, 70, 68), h=1.0, f0=150)
    F.paste(t, F.text_mask("LEAK", BOLD, 15, 50, 18, angle=-6, jitter=0.6, seed=seed), 7, 20, (180, 30, 24), 0.9)
    F.paste(t, F.text_mask("7/2", BOLD, 9, 24, 11, seed=seed + 1), 34, 42, (40, 40, 40), 0.85)
    stain = np.clip(np.linspace(-0.4, 1, N), 0, 1)[:, None] * np.clip(P.fbm(t.rng) * 1.5 + 0.6, 0, 1)
    t.mix(stain * 0.7, (52, 40, 28), 0.85)
    return t


def tarp(seed):
    """A blue tarp taped over a weeping pane: creased, sagging with what's caught behind it, the
    silver tape round its edges peeling."""
    t = exterior.tarp("panel", seed)
    for y0, y1 in ((0, 6), (N - 6, N)):
        area(t, 0, y0, N, y1, col=(150, 152, 150), h=0.85, smooth=0.6)
    for x0, x1 in ((0, 6), (N - 6, N)):
        area(t, x0, 0, x1, N, col=(150, 152, 150), h=0.85, smooth=0.6)
    for _ in range(3):
        x, y = t.rng.uniform(0, N), t.rng.choice([3, N - 3])
        disc(t, x, y, 3, col=(70, 100, 150))
    sag = np.clip(np.linspace(-0.6, 1, N), 0, 1)[:, None] * np.ones((1, N))
    t.mix(sag * 0.35, (20, 40, 70), 0.8)
    return t


def exit_sign(seed, right):
    """The tube's escape signs on the frames, every hundred blocks or so: EXIT and an arrow to the
    nearer end in white on the green, lit from inside where the cells still hold."""
    t = F.plain(seed, (24, 128, 70), 0.03, 0.5)
    area(t, 0, 0, N, 3, col=(190, 192, 188))
    area(t, 0, N - 3, N, N, col=(190, 192, 188))
    m = F.text_mask("EXIT", BOLD, 26, 52, 30, seed=seed)
    F.paste(t, m, 6, 8, (244, 246, 240))
    t.emit[3:N - 3, :] = 0.75
    arrow = np.zeros((N, N), np.float32)
    arrow[46:50, 14:44] = 1
    for i in range(8):
        arrow[48 - i:48 + i + 1, 44 + 8 - i] = 1
    if not right:
        arrow = arrow[:, ::-1]
    t.mix(arrow, (244, 246, 240), 1.0)
    t.grime(0.15)
    return t


def cable(seed):
    """Armoured cable's black sheath, dusty on top, a printed rating every so often."""
    t = F.plain(seed, (24, 24, 24), 0.04, 0.55)
    area(t, 0, 0, N, 10, mul=1.6)
    for x in range(8, N, 32):
        area(t, x, 28, x + 10, 32, col=(170, 170, 160))
    t.grime(0.2, col=(90, 84, 70))
    return t


def coal(seed, top=False):
    """Coal that went down with the barge: lumps heaped any old way, black with a dull glint on
    their faces, and over the top a grey bloom of the harbour's silt and a fur of weed."""
    t = F.plain(seed, (30, 30, 32), 0.12, 0.35)
    rng = t.rng
    for _ in range(90):
        x, y, r = rng.uniform(0, N), rng.uniform(0, N), rng.uniform(1.8, 5.5)
        c = rng.uniform(16, 46)
        disc(t, x, y, r, col=(c, c, c + 3), dh=0.5 * r / 5.5)
        disc(t, x - r * 0.35, y - r * 0.35, r * 0.3, col=(c + 48, c + 48, c + 54), smooth=0.85)
        disc(t, x + r * 0.5, y + r * 0.5, r * 0.25, col=(8, 8, 9), dh=-0.2)
    silt = np.clip(P.fbm(rng, ((8, 1.0), (16, 0.6), (32, 0.3))) * 2.0 - (0.2 if top else 0.6), 0, 1)
    t.mix(silt, (92, 90, 80), 0.6 if top else 0.35)
    weed(t, 0.5 if top else 0.25)
    return t


def textures():
    X = {}
    X["gl_coal"] = lambda: coal(7820)
    X["gl_coal_top"] = lambda: coal(7821, top=True)
    X["gl_glass"] = lambda: pane(7800)
    X["gl_glass_cracked"] = lambda: pane(7801, cracked=2)
    X["gl_glass_leak"] = lambda: pane(7802, cracked=1, leak=True)
    X["gl_glass_crown"] = lambda: pane(7803, silt=True)
    X["gl_glass_crown_cracked"] = lambda: pane(7804, cracked=2, silt=True)
    X["gl_rib"] = lambda: rib(7805)
    X["gl_teal"] = lambda: T.steel(7806, AQUA, rust=0.35, chips=1.0)
    X["gl_keel"] = lambda: keel(7807)
    X["gl_keel_top"] = lambda: keel(7808, top=True)
    X["gl_sill_out"] = lambda: sill_out(7809)
    X["gl_grating"] = lambda: grating(7810)
    X["gl_walk_top"] = lambda: grating(7811, edge=True)
    X["gl_walk_face"] = lambda: walk_face(7812)
    X["gl_lamp"] = lambda: lamp(7813, False)
    X["gl_lamp_on"] = lambda: lamp(7814, True)
    X["gl_plywood"] = lambda: plywood(7815)
    X["gl_tarp"] = lambda: tarp(7816)
    X["gl_cable"] = lambda: cable(7819)
    X["gl_exit_r"] = lambda: exit_sign(7817, True)
    X["gl_exit_l"] = lambda: exit_sign(7818, False)
    return X


def register(ns):
    global NS
    NS = ns
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def glass(tex):
    return {f: tex for f in ("east", "west", "north", "south", "up", "down")}


def sheet(box, tex, faces):
    """A pane as a box with only its two broad faces: its thin edges would draw a dark line at
    every block joint (a see-through block hides none of its neighbours' faces). faces maps each
    face kept to its UVs."""
    return list(box) + [glass(tex), faces]


def wall_pane(tex, y0=0):
    uv = [0, 0, 16, 16 - y0]
    return sheet([0, y0, 0.5, 16, 16, 1.5], tex, {"north": uv, "south": uv})


def blocks():
    out = {}

    def add(name, boxes, title, sound, rotate="facing", tex=None, **extra):
        spec = {"model": "boxes", "boxes": boxes} if boxes else {"tex": tex}
        spec.update(title=title, sound=sound)
        if rotate:
            spec["rotate"] = rotate
        spec.update(extra)
        out[name] = spec

    # the raft and the walkways
    add("gl_keel", None, "Tube Footing", "STONE", rotate=None, tex={"side": "gl_keel", "up": "gl_keel_top", "down": "gl_keel_top"})
    add("gl_sill", None, "Tube Sill", "METAL", tex={"north": "gl_sill_out", "up": "gl_grating", "down": "gl_keel_top",
                                                     "side": "tx_steel_d"})
    add("gl_walk", None, "Tube Walkway", "METAL", tex={"up": "gl_walk_top", "south": "gl_walk_face", "down": "gl_keel_top",
                                                        "side": "gl_keel_top"})

    # the glass: the pane half a pixel in from the block's outer face (the water draws its face on
    # the block's edge: a pane flush with it would flicker against it)
    rib = [6, 0, 0, 10, 16, 3.5, "gl_rib"]
    rail = [0, 13, 4.5, 16, 14.2, 5.7, "tx_steel_d"]
    bracket = [7.4, 12.6, 3.5, 8.6, 13.4, 4.5, "tx_steel_d"]
    channel = [0, 0, 0, 16, 1.5, 2.5, "gl_teal"]
    wall_shape = [[0, 0, 0, 16, 16, 1.5]]
    lo_shape = wall_shape + [[0, 13, 4.5, 16, 14.2, 5.7]]
    for v, tex in (("", "gl_glass"), ("_cracked", "gl_glass_cracked"), ("_leak", "gl_glass_leak")):
        p = wall_pane(tex)
        plo = wall_pane(tex, 1.5)
        add(f"gl_wall{v}", [p], "Tube Glass", "GLASS", render="translucent", shape=wall_shape)
        add(f"gl_wall_lo{v}", [plo, channel, rail], "Tube Glass", "GLASS", render="translucent", shape=lo_shape)
    add("gl_wall_rib", [wall_pane("gl_glass"), rib], "Tube Glass", "GLASS", render="translucent",
        shape=[[0, 0, 0, 16, 16, 1.5], [6, 0, 0, 10, 16, 3.5]])
    add("gl_wall_lo_rib", [wall_pane("gl_glass", 1.5), channel, rib, bracket, rail], "Tube Glass", "GLASS",
        render="translucent", shape=lo_shape + [[6, 0, 0, 10, 16, 3.5]])
    # patched: the broken pane behind a sheet of plywood bolted to the frame, or a tarp taped on
    ply = [1, 1, 1.5, 15, 15, 2.5, {"south": "gl_plywood", "north": "gl_plywood", "side": "tx_steel_d", "up": "tx_steel_d",
                                     "down": "tx_steel_d"}]
    add("gl_wall_patch", [wall_pane("gl_glass_cracked"), ply], "Patched Tube Glass", "GLASS",
        render="translucent", shape=[[0, 0, 0, 16, 16, 2.5]])
    add("gl_wall_tarp", [wall_pane("gl_glass_leak"), sheet([0, 0, 1.6, 16, 16, 2.0], "gl_tarp",
                                                            {"north": [0, 0, 16, 16], "south": [0, 0, 16, 16]})],
        "Patched Tube Glass", "GLASS", render="translucent", shape=[[0, 0, 0, 16, 16, 2.0]])

    # the chamfer: one pane through the block's outer-bottom and inner-top corners, the next block
    # in and up carries it on; the outside of it (the water side) is what you bump
    def chamfer(tex, *more):
        return [sheet([0, 7.5, 8 - DIAG, 16, 8.5, 8 + DIAG], tex, {"up": [0, 0, 16, 16], "down": [0, 0, 16, 16]}) + [TURN]] \
            + list(more)
    steps = [[0, 4, 0, 16, 16, 4], [0, 8, 4, 16, 16, 8], [0, 12, 8, 16, 16, 12]]
    crib = [6, 4.5, 8 - DIAG, 10, 9, 8 + DIAG, "gl_rib", None, TURN]
    add("gl_chamfer", chamfer("gl_glass"), "Tube Glass", "GLASS", render="translucent", shape=steps)
    add("gl_chamfer_cracked", chamfer("gl_glass_cracked"), "Tube Glass", "GLASS", render="translucent", shape=steps)
    add("gl_chamfer_rib", chamfer("gl_glass", crib), "Tube Glass", "GLASS", render="translucent", shape=steps)
    add("gl_chamfer_patch", chamfer("gl_glass_cracked", [1, 5.5, 8 - DIAG + 2, 15, 6.5, 8 + DIAG - 2,
                                                          {"down": "gl_plywood", "up": "gl_plywood", "side": "tx_steel_d"},
                                                          None, TURN]),
        "Patched Tube Glass", "GLASS", render="translucent", shape=steps)

    # the crown: the roof glass, silted over on top, and its frames
    roof = {"up": [0, 0, 16, 16], "down": [0, 0, 16, 16]}
    top = sheet([0, 14.5, 0, 16, 15.5, 16], "gl_glass_crown", roof)
    crown_shape = [[0, 14.5, 0, 16, 16, 16]]
    rrib = [6, 12, 0, 10, 16, 16, "gl_rib"]
    add("gl_crown", [top], "Tube Glass", "GLASS", render="translucent", shape=crown_shape)
    add("gl_crown_cracked", [sheet([0, 14.5, 0, 16, 15.5, 16], "gl_glass_crown_cracked", roof)], "Tube Glass", "GLASS",
        render="translucent", shape=crown_shape)
    add("gl_crown_rib", [top, rrib], "Tube Glass", "GLASS", render="translucent", shape=crown_shape)

    # the cable trays along both upper sides (o -3 / 2, h 5: outside the cars' four blocks, tucked
    # under the glass where the chamfer meets the block's top outer edge): power and signal cables
    # in a steel tray, and every few blocks a sealed strip light hung under it (a lamp in a block
    # of its own, so only it draws full bright when it's lit)
    tray = [[0, 12.5, 0.5, 16, 13, 6, "tx_steel_d"], [0, 13, 0.5, 16, 15, 1, "tx_steel_d"], [0, 13, 5.5, 16, 15, 6, "tx_steel_d"],
            [0, 13, 1.3, 16, 14.3, 2.6, "gl_cable"], [0, 13, 2.8, 16, 14.0, 4.0, "gl_cable"], [0, 14.0, 2.0, 16, 15.0, 3.2, "gl_cable"]]
    add("gl_tray", tray, "Cable Tray", "METAL", decor=True, shape=[[0, 12.5, 0.5, 16, 15, 6]])
    housing = [0, 9, 1.5, 16, 11.5, 8, {"down": "gl_lamp", "up": "tx_steel_d", "side": "tx_steel_d"}]
    rods = [[x, 11.5, 3.5, x + 1, 12.5, 4.5, "tx_steel_d"] for x in (3, 12)]
    add("gl_lamp", tray + [housing] + rods, "Tube Light", "METAL", decor=True, shape=[[0, 9, 0.5, 16, 15, 8]],
        flicker={"light": 12, "on": {"gl_lamp": "gl_lamp_on"}})
    add("gl_lamp_off", tray + [housing] + rods, "Tube Light", "METAL", decor=True, shape=[[0, 9, 0.5, 16, 15, 8]])

    # the coal the sunk barge spilled across the bed beside the line (Wrecks.java)
    add("gl_coal", None, "Coal", "GRAVEL", rotate=None, tex={"side": "gl_coal", "up": "gl_coal_top", "down": "gl_coal"})

    # the escape signs, on a frame over the walkway, the arrow to the viewer's right (_r) or left
    for side in ("r", "l"):
        sign = [2, 4, 3.5, 14, 10, 4.5, {"south": f"gl_exit_{side}", "north": "tx_steel_d", "side": "tx_steel_d",
                                         "up": "tx_steel_d", "down": "tx_steel_d"}]
        add(f"gl_wall_exit_{side}", [wall_pane("gl_glass"), rib, sign], "Tube Glass", "GLASS",
            render="translucent", shape=[[0, 0, 0, 16, 16, 1.5], [6, 0, 0, 10, 16, 3.5]])
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith("gl_")}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    path.write_text(json.dumps(data, indent=1) + "\n")
    print(f"{len(made)} glass line blocks in {path}")


if __name__ == "__main__":
    main()
