#!/usr/bin/env python3
"""The ship fit-out kit: what fills the inside of the carrier and the cargo ships, so a compartment
reads as a ship's and not a steel box. The overheads (pipe runs with their bands, the firemain,
cable trays, vent trunks, sprinkler lines and the deep frames across them), what hangs on the
bulkheads (stiffeners, valves, junction boxes, speakers, sound-powered phones, fire stations,
damage control lockers, escape breathing sets) and the lighting a warship carries: caged
fluorescent fittings, red night lights, battery battle lanterns, caged globes, hangar dome lamps,
floodlights and the green escape signs. Lights come lit, dead, or on a dying circuit (Flicker).

    python3 tools/city/shipkit.py        rewrites the fit_ entries in blocks.json

photo_textures.py draws the textures (shipkit.register()). Pieces follow furniture.py's rule:
the base model faces north with its back (z = 16) against what it hangs on; overhead pieces sit in
the top of their block (y 11 to 16) and run along z when they take an axis.
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).parent))
import paint_textures as P  # noqa: E402
import furniture as F  # noqa: E402
import bunker as B  # noqa: E402
import vessel as V  # noqa: E402

N = P.N
HERE = Path(__file__).parent
BOLD = V.BOLD
PREFIXES = ("fit_",)

GREY = (126, 130, 132)
YELLOW = (204, 166, 40)
RED = (164, 36, 30)
GREEN = (34, 150, 74)


# ---------------------------------------------------------------- painters

def steel(seed, rgb, rough=0.35):
    return V.steel_paint(seed, rgb, rough)


def lens(seed, rgb, on):
    """A lamp's glass: bright in the middle when lit, grey and fly-specked when not."""
    return B.glass(seed, rgb, on)


def dome(seed):
    """A hangar lamp's shade: grey enamel outside, chipped at the rim."""
    t = F.enamel(seed, (92, 98, 102))
    F.area(t, 0, 56, N, N, (150, 150, 146), f0=180)
    B.chips(t, 0.08, (120, 116, 108))
    return F.wear(t, 0.3, 3)


def exit_sign(seed):
    """An escape sign: white letters and an arrow on green, faintly lit from inside."""
    t = F.plain(seed, GREEN, 0.03, 0.8)
    F.area(t, 0, 0, N, 2, (230, 230, 226))
    F.area(t, 0, 62, N, N, (230, 230, 226))
    m = B.fit_text("ESCAPE", BOLD, 14, N, 30, seed)
    full = np.zeros((N, N), np.float32)
    full[6:36, :] = m
    t.c[full > 0.45] = np.array((238, 240, 232), np.float32)
    yy, xx = np.mgrid[0:N, 0:N]
    arrow = ((np.abs(yy - 47) <= 3) & (xx >= 14) & (xx <= 44)) | ((xx >= 40) & (xx <= 52) & (np.abs(yy - 47) <= 52 - xx))
    t.c[arrow] = np.array((238, 240, 232), np.float32)
    t.emit[:] = 0.55
    return t


def hose(seed):
    """Canvas fire hose, faded red, laid in flat coils."""
    t = F.plain(seed, (150, 40, 32), 0.08, 0.2)
    yy = np.arange(N)[:, None]
    t.c *= (0.8 + 0.3 * np.abs(np.sin(yy / N * np.pi * 6)))[..., None]
    t.speckle(0.04, (110, 100, 90), 0.4)
    return F.wear(t, 0.3, 0)


def locker(seed, text):
    """A damage control locker's door: grey with louvres at the top, red stencil, a padlock hasp."""
    t = steel(seed, (128, 132, 132), 0.4)
    for y in range(4, 16, 3):
        F.area(t, 10, y, 54, y + 1, mul=0.55, dh=-0.1)
    B.stencil(t, text, (176, 34, 28), 11, 4, 20, 56, 26, chip=0.15, font=BOLD)
    F.area(t, 0, 0, 2, N, mul=0.7)
    F.area(t, 62, 0, N, N, mul=0.7)
    F.area(t, 50, 46, 56, 54, (60, 60, 58), f0=150)
    B.rust_runs(t, 3, 0.35, 56)
    return F.wear(t, 0.3, 4)


def canister(seed):
    """An escape breathing set's yellow canister with its black band and label."""
    t = steel(seed, YELLOW, 0.3)
    F.area(t, 0, 10, N, 16, (30, 30, 28))
    B.stencil(t, "EEBD", (24, 24, 22), 12, 4, 24, 56, 22, chip=0.05, font=BOLD)
    return F.wear(t, 0.25, 3)


def grille(seed):
    """A speaker's face: grey pressed steel with rows of slots."""
    t = steel(seed, (150, 152, 150), 0.4)
    for y in range(10, 54, 6):
        F.area(t, 10, y, 54, y + 3, (36, 36, 34), dh=-0.2)
    return F.wear(t, 0.25, 2)


def phone(seed):
    """A sound-powered phone box: the handset in its cradle, the circuit stencilled on the lid."""
    t = steel(seed, (140, 144, 144), 0.4)
    F.area(t, 14, 18, 50, 28, (30, 30, 28), dh=0.2)
    F.area(t, 12, 16, 18, 30, (30, 30, 28), dh=0.2)
    F.area(t, 46, 16, 52, 30, (30, 30, 28), dh=0.2)
    F.area(t, 22, 38, 42, 54, (44, 44, 42), dh=-0.1)
    B.stencil(t, "JZ", (24, 24, 22), 10, 4, 2, 56, 14, chip=0.1, font=BOLD)
    return F.wear(t, 0.3, 3)


def jbox(seed):
    """A junction box: grey, four bolts, a white label plate."""
    t = steel(seed, (136, 140, 140), 0.4)
    for x, y in ((6, 6), (54, 6), (6, 54), (54, 54)):
        F.area(t, x, y, x + 4, y + 4, (90, 90, 88), dh=0.15)
    F.area(t, 16, 24, 48, 36, (226, 222, 210))
    B.stencil(t, "1-110-2", (30, 30, 28), 7, 16, 24, 32, 12, chip=0.02, font=BOLD)
    return F.wear(t, 0.3, 3)


def flood_body(seed):
    t = steel(seed, (70, 74, 76), 0.45)
    for x in range(4, N, 8):
        F.area(t, x, 0, x + 2, N, mul=0.7, dh=0.15)
    return F.wear(t, 0.3, 3)


def yellow(seed):
    """Support gear's yellow enamel: smooth, chipped to grey at the edges, grimy low down."""
    t = F.enamel(seed, YELLOW)
    B.chips(t, 0.05, (96, 96, 92))
    return F.wear(t, 0.3, 4)


def gse(seed):
    """Flight deck support gear's yellow: louvres down one side, NO STEP, oil and scuffs."""
    t = F.enamel(seed, YELLOW)
    B.chips(t, 0.05, (96, 96, 92))
    for y in range(8, 30, 4):
        F.area(t, 8, y, 30, y + 2, (60, 52, 20), dh=-0.15)
    B.stencil(t, "NO STEP", (24, 24, 22), 10, 6, 38, 52, 14, chip=0.15, font=BOLD)
    F.area(t, 0, 58, N, N, (70, 62, 40))
    B.rust_runs(t, 4, 0.3, 50)
    return F.wear(t, 0.4, 5)


def bottle(seed):
    """A wheeled fire bottle: red with a white band and its stencil."""
    t = steel(seed, RED, 0.3)
    F.area(t, 0, 8, N, 16, (226, 222, 210))
    B.stencil(t, "HALON", (226, 222, 210), 10, 4, 28, 56, 16, chip=0.1, font=BOLD)
    return F.wear(t, 0.3, 3)


def enamel_steel(seed, rgb):
    """Frames and beams in the bulkheads' glossy off-white enamel, a weld line along them, a little rust."""
    t = F.enamel(seed, rgb)
    t.smooth[:] = 0.7
    F.area(t, 0, 31, N, 33, mul=0.9, dh=0.05)
    B.rust_runs(t, 2, 0.25)
    return F.wear(t, 0.15, 3, specks=0.002)


def deck_line(seed):
    """A painted yellow line on the hangar deck, scuffed through to the non-skid by tyres and boots."""
    t = B.clear(seed)
    F.area(t, 26, 0, 38, N, (214, 172, 44), alpha=255)
    t.alpha[:, 26:38] = 255
    rng = t.rng
    for _ in range(40):
        x, y = rng.integers(26, 38), rng.integers(0, N)
        t.alpha[y:y + rng.integers(1, 4), x:x + rng.integers(1, 3)] = 0
    return t


def lagging(seed):
    """Lagged pipe: off-white canvas over the insulation, banded with colour, the seams stitched, grimy."""
    t = F.plain(seed, (214, 210, 196), 0.05, 0.2)
    for y in range(0, N, 8):
        F.area(t, 0, y, N, y + 1, mul=0.86, dh=-0.1)
    F.area(t, 0, 24, N, 30, (36, 92, 160))
    F.area(t, 0, 30, N, 32, (230, 226, 210))
    return F.wear(t, 0.35, 2)


def label(seed, text, col=(226, 222, 210), ink=(24, 24, 22)):
    """An engraved plate screwed to the bulkhead."""
    t = B.clear(seed)
    F.area(t, 4, 20, 60, 44, col, alpha=255)
    t.alpha[20:44, 4:60] = 255
    B.stencil(t, text, ink, 10, 6, 22, 52, 20, chip=0.03, font=BOLD)
    return t


SIGNS = {
    "fire": ("FIRE STATION", (176, 34, 28)), "repair": ("REPAIR|LOCKER", (176, 34, 28)),
    "escape": ("ESCAPE|TRUNK", (226, 222, 210)), "hearing": ("HEARING|PROTECTION|REQUIRED", (218, 178, 38)),
    "nosmoking": ("NO|SMOKING", (176, 34, 28)), "keepclosed": ("WATERTIGHT|KEEP CLOSED", (226, 222, 210)),
    "ammo": ("AMMUNITION|HANDLING", (176, 34, 28)), "oxygen": ("OXYGEN|NO OIL", (52, 128, 70)),
    "head": ("HEAD", (226, 222, 210)), "laundry": ("LAUNDRY", (226, 222, 210)),
    "chapel": ("CHAPEL", (226, 222, 210)), "store": ("SHIP'S|STORE", (226, 222, 210)),
    "wardroom": ("WARDROOM", (226, 222, 210)), "chiefs": ("CHIEFS|MESS", (226, 222, 210)),
    "aimd": ("AVIONICS|SHOP", (226, 222, 210)), "post": ("POST|OFFICE", (226, 222, 210)),
}
LABELS = ("2-96-0-L", "2-118-2-L", "2-150-1-Q", "3-44-0-L", "3-132-2-M", "3-176-1-L", "4-60-0-M", "4-212-2-Q",
          "1-140-0-A", "1-188-1-A", "01-72-0-L", "02-120-1-C", "03-150-0-C", "07-160-0-C", "2-250-0-E", "3-290-1-A")


def textures():
    T = {
        "fit_yellow": lambda: yellow(8000),
        "fit_lens_on": lambda: lens(8001, (246, 236, 206), True), "fit_lens_off": lambda: lens(8002, (214, 208, 190), False),
        "fit_dome": lambda: dome(8003), "fit_exit": lambda: exit_sign(8004), "fit_hose": lambda: hose(8005),
        "fit_dc_locker": lambda: locker(8006, "REPAIR 2|DC LOCKER"), "fit_eebd": lambda: canister(8007),
        "fit_grille": lambda: grille(8008), "fit_phone": lambda: phone(8009), "fit_jbox": lambda: jbox(8010),
        "fit_flood": lambda: flood_body(8011), "fit_grey": lambda: steel(8012, GREY, 0.4),
        "fit_red": lambda: steel(8013, RED, 0.4), "fit_frame": lambda: enamel_steel(8014, (210, 206, 192)),
        "fit_beam": lambda: enamel_steel(8015, (220, 216, 204)),
        "fit_dc_locker_b": lambda: locker(8016, "REPAIR 5|DC LOCKER"),
        "fit_gse": lambda: gse(8017), "fit_bottle": lambda: bottle(8018),
        "fit_tank": lambda: F.wear(F.enamel(8019, (176, 180, 178)), 0.25, 3),
        "fit_lagging": lambda: lagging(8020), "fit_line": lambda: deck_line(8022), "fit_sodium_on": lambda: lens(8021, (255, 170, 72), True),
    }
    for i, (k, (text, col)) in enumerate(SIGNS.items()):
        T[f"fit_st_{k}"] = (lambda a, b, c: lambda: B.stencil_sign(a, b, c, 12))(8040 + i, text, col)
    for i, text in enumerate(LABELS):
        T[f"fit_pl_{i}"] = (lambda a, b: lambda: label(a, b))(8070 + i, text)
    return T


def register(ns):
    ns.EXTRA.update(textures())


# ---------------------------------------------------------------- the blocks

def blocks():
    out = {}

    def add(name, boxes, title, sound="METAL", rotate="facing", decor=True, light=0, loot=None):
        spec = {"model": "boxes", "boxes": boxes, "title": title, "sound": sound}
        if rotate:
            spec["rotate"] = rotate
        if decor:
            spec["decor"] = True
        if light:
            spec["light"] = light
        if loot:
            spec["loot"] = loot
        out[name] = spec

    def flicker(name, swap, light):
        out[name]["flicker"] = {"light": light, "on": swap}
        out[name].pop("light", None)

    def lamp(name, boxes, title, rotate, light, glass, lit, dead, flick=None):
        """name_on (lit), name (dead) and, with flick, name_flicker on a dying circuit."""
        on = [b[:6] + [lit if b[6] == glass else b[6]] for b in boxes]
        off = [b[:6] + [dead if b[6] == glass else b[6]] for b in boxes]
        add(name + "_on", on, title, rotate=rotate, light=light)
        add(name, off, title, rotate=rotate)
        if flick:
            add(name + "_flicker", off, title, rotate=rotate)
            flicker(name + "_flicker", {dead: lit}, flick)

    # ---- the lights
    G = "GLASS"
    # a two-tube fitting a metre long under the deckhead, end caps and three guard straps across the tubes
    lamp("fit_tube", [[4.5, 14.5, 0.5, 11.5, 16, 15.5, "bunk_fitting"], [5.3, 13, 1.5, 7.5, 14.5, 14.5, G],
                      [8.5, 13, 1.5, 10.7, 14.5, 14.5, G], [4.5, 12.8, 0.5, 11.5, 14.5, 1.5, "bunk_fitting"],
                      [4.5, 12.8, 14.5, 11.5, 14.5, 15.5, "bunk_fitting"]]
         + [[4.8, 12.5, z, 11.2, 12.9, z + 0.5, "bunk_grey"] for z in (4.5, 7.75, 11)],
         "Fluorescent Light", "axis", 14, G, "bunk_tube_on", "bunk_tube_off", 12)
    lamp("fit_red", [[6, 14.5, 5, 10, 16, 11, "bunk_grey"], [6.5, 13.2, 5.5, 9.5, 14.5, 10.5, G],
                     [6.2, 12.8, 5.2, 9.8, 14.6, 10.8, "bunk_cage_wire"]],
         "Night Light", None, 6, G, "bunk_red_on", "bunk_red_off")
    lamp("fit_lantern", [[4, 7, 10, 12, 13, 16, "fit_yellow"], [5, 8, 9.5, 11, 12, 10, G],
                         [6, 13, 12, 10, 14.5, 14, "bunk_grey"], [5, 6, 14, 11, 7, 16, "bunk_grey"]],
         "Battle Lantern", "facing", 11, G, "fit_lens_on", "fit_lens_off")
    lamp("fit_globe", [[6, 7, 15, 10, 13, 16, "bunk_grey"], [6.5, 8, 11, 9.5, 12, 15, G],
                       [6, 7.5, 10.5, 10, 12.5, 15.5, "bunk_cage_wire"]],
         "Caged Light", "facing", 12, G, "bunk_glass_on", "bunk_glass_off", 10)
    lamp("fit_globe_down", [[6, 15, 6, 10, 16, 10, "bunk_grey"], [6.5, 11, 6.5, 9.5, 15, 9.5, G],
                            [6, 10.5, 6, 10, 15, 10, "bunk_cage_wire"]],
         "Caged Light", None, 12, G, "bunk_glass_on", "bunk_glass_off", 10)
    # a dome lamp on a stem: the shade stepped out in three rings, the lamp glass under it
    lamp("fit_highbay", [[7.5, 10.5, 7.5, 8.5, 16, 8.5, "bunk_grey"], [6, 9.5, 6, 10, 10.5, 10, "fit_dome"],
                         [4.5, 8, 4.5, 11.5, 9.5, 11.5, "fit_dome"], [3, 6.5, 3, 13, 8, 13, "fit_dome"],
                         [4, 6, 4, 12, 6.5, 12, G]],
         "Hangar Lamp", None, 15, G, "fit_sodium_on", "bunk_glass_off", 13)
    lamp("fit_flood", [[7, 0, 13, 9, 9, 16, "fit_grey"], [3, 6, 5, 13, 14, 12, "fit_flood"],
                       [4, 7, 4.5, 12, 13, 5, G], [2.5, 5.5, 4, 3, 14.5, 5, "fit_grey"],
                       [13, 5.5, 4, 13.5, 14.5, 5, "fit_grey"]],
         "Floodlight", "facing", 15, G, "fit_lens_on", "fit_lens_off")
    add("fit_exit", [[2, 10, 15.4, 14, 14, 16, {"north": "fit_exit", "side": "fit_grey", "up": "fit_grey",
                                                "down": "fit_grey"}]], "Escape Sign", light=3)

    # ---- the overheads
    hang = [[0, 15.5, 7.5, 16, 16, 8.5, "bunk_grey"]]
    add("fit_pipes_a", [[0.5, 11, 0, 5, 15.5, 16, "fit_lagging"], [5, 13, 0, 7.5, 15.5, 16, "bunk_pipe_green"],
                        [9, 11.5, 0, 13.5, 16, 16, "bunk_pipe_grey"]] + hang, "Pipes", rotate="axis")
    add("fit_pipes_b", [[1.5, 11, 0, 6.5, 16, 16, "bunk_pipe_red"], [8, 13, 0, 10.5, 15.5, 16, "bunk_pipe_blue"],
                        [12, 13.5, 0, 14, 15.5, 16, "bunk_pipe_grey"]] + hang, "Firemain", rotate="axis")
    add("fit_pipes_valve", [[1.5, 11, 0, 6.5, 16, 16, "bunk_pipe_red"], [8, 13, 0, 10.5, 15.5, 16, "bunk_pipe_blue"],
                            [2.5, 10, 6, 5.5, 11, 10, "bunk_pipe_grey"], [0, 6, 4, 8, 6.6, 12, "bunk_wheel"],
                            [3.5, 6.6, 7.5, 4.5, 10, 8.5, "bunk_grey"]] + hang, "Firemain Valve", rotate="axis")
    add("fit_tray", [[2, 14, 0, 14, 14.5, 16, "ext_galv"], [2, 14.5, 0, 2.5, 16, 16, "ext_galv"],
                     [13.5, 14.5, 0, 14, 16, 16, "ext_galv"], [3, 14.5, 0, 13, 15.6, 16, "bunk_cables"],
                     [6, 13.2, 3, 7, 14, 16, "bunk_cables"]], "Cable Tray", rotate="axis")
    add("fit_duct", [[2, 11, 0, 14, 16, 16, "ext_galv"], [1.5, 10.5, 7, 14.5, 16, 8, "ext_galv"]], "Vent Trunk",
        rotate="axis")
    add("fit_duct_grille", [[2, 11, 0, 14, 16, 16, "ext_galv"], [4, 10.9, 4, 12, 11, 12, "bunk_grille"]], "Vent Trunk",
        rotate="axis")
    add("fit_sprinkler", [[7, 14.5, 0, 9, 16, 16, "bunk_pipe_red"], [7.5, 13.5, 7.5, 8.5, 14.5, 8.5, "bunk_grey"],
                          [6.5, 13, 6.5, 9.5, 13.5, 9.5, "bunk_grey"]], "Sprinkler Line", rotate="axis")
    # a deep T-section beam across the run (it takes the axis of the run it crosses), and the same beam
    # with fit_pipes_b's pipes passing through its web
    beam = [[0, 9, 7.25, 16, 16, 8.75, "fit_beam"], [0, 8.5, 5, 16, 9, 11, "fit_beam"]]
    add("fit_beam", beam, "Deck Beam", rotate="axis")
    # the hangar's deep girder: an I-section a metre deep across the run it takes the axis of
    add("fit_girder", [[0, 0, 3.5, 16, 1.5, 12.5, "fit_beam"], [0, 1.5, 7, 16, 14.5, 9, "fit_beam"],
                       [0, 14.5, 3.5, 16, 16, 12.5, "fit_beam"]]
        + [[x, 1.5, 6.5, x + 0.5, 14.5, 9.5, "fit_beam"] for x in (3.75, 11.75)], "Girder", rotate="axis")
    for run in ("pipes_a", "pipes_b", "tray", "duct", "sprinkler"):
        name = "fit_beam_pipes" if run == "pipes_b" else f"fit_beam_{run}"
        add(name, beam + [b for b in out[f"fit_{run}"]["boxes"] if b[1] < 15.5 or b[0] > 0], "Deck Beam", rotate="axis")

    # ---- on the bulkheads
    add("fit_frame", [[6.5, 0, 14, 9.5, 16, 16, "fit_frame"], [5, 0, 13, 11, 16, 14, "fit_frame"]], "Frame")
    add("fit_valve", [[6.5, 0, 13, 9.5, 16, 16, "bunk_pipe_red"], [4, 5, 11.5, 12, 13, 12, "bunk_wheel"],
                      [7.5, 8.5, 12, 8.5, 9.5, 13, "bunk_grey"], [6, 9, 12.5, 10, 9.5, 16, "bunk_grey"]], "Valve")
    add("fit_pipe_drop", [[2, 0, 13.5, 4.5, 16, 16, "bunk_pipe_blue"], [6, 0, 14, 8, 16, 16, "bunk_pipe_grey"],
                          [1.5, 7, 13, 8.5, 8, 16, "bunk_grey"]], "Pipes")
    add("fit_jbox", [[4, 6, 14, 12, 12, 16, {"north": "fit_jbox", "side": "fit_grey", "up": "fit_grey", "down": "fit_grey"}],
                     [7.5, 12, 14.5, 8.5, 16, 15.5, "bunk_grey"], [7.5, 0, 14.5, 8.5, 6, 15.5, "bunk_grey"]],
        "Junction Box")
    add("fit_speaker", [[4, 11, 13, 12, 15, 16, {"north": "fit_grille", "side": "fit_grey", "up": "fit_grey",
                                                  "down": "fit_grey"}], [7, 15, 14, 9, 16, 16, "bunk_grey"]],
        "Loudspeaker")
    add("fit_phone", [[4, 6, 13, 12, 13, 16, {"north": "fit_phone", "side": "fit_grey", "up": "fit_grey",
                                               "down": "fit_grey"}], [7.5, 13, 14.5, 8.5, 16, 15.5, "bunk_grey"]],
        "Sound-Powered Phone")
    add("fit_firestation", [[2, 9, 14, 14, 10, 16, "fit_grey"], [3, 3, 11, 13, 9, 15, "fit_hose"],
                            [3.5, 9, 11.5, 12.5, 11, 14.5, "fit_hose"], [12.5, 1, 12, 14, 7, 13.5, "bunk_grey"],
                            [6, 12, 12, 10, 14, 16, "bunk_pipe_red"], [5, 14, 13, 11, 14.6, 15, "bunk_wheel"]],
        "Fire Station")
    add("fit_dc_locker", [[1, 0, 4, 15, 16, 16, {"north": "fit_dc_locker", "side": "fit_grey", "up": "fit_grey",
                                                  "down": "fit_grey"}]], "Repair Locker", decor=False, loot="home_tools")
    add("fit_dc_locker_b", [[1, 0, 4, 15, 16, 16, {"north": "fit_dc_locker_b", "side": "fit_grey", "up": "fit_grey",
                                                    "down": "fit_grey"}]], "Repair Locker", decor=False,
        loot="home_tools")
    add("fit_eebd", [[2, 5, 12, 14, 6, 16, "fit_grey"]] +
        [[2.5 + i * 4, 6, 12.5, 5.5 + i * 4, 13, 15.5, "fit_eebd"] for i in range(3)], "Escape Breathing Sets")

    # ---- hangar gear: the yellow support equipment, fire bottles, stands, tyres, drop tanks
    wheels = lambda y=3.5: [[0.5, 0, 1.5, 2.5, y, 5, "navy_tyre"], [13.5, 0, 1.5, 15.5, y, 5, "navy_tyre"],
                            [0.5, 0, 11, 2.5, y, 14.5, "navy_tyre"], [13.5, 0, 11, 15.5, y, 14.5, "navy_tyre"]]
    add("fit_gse_power", [[1.5, 2, 1, 14.5, 12, 15, {"north": "fit_gse", "south": "fit_gse", "side": "fit_yellow",
                                                       "up": "fit_yellow", "down": "fit_grey"}],
                          [5, 12, 4, 11, 15, 12, "home_rubber"], [4.5, 12, 7.5, 11.5, 15.5, 8.5, "fit_grey"],
                          [7, 2, -1, 9, 3, 1, "fit_grey"]] + wheels(), "Power Cart", decor=False)
    add("fit_gse_air", [[1.5, 2, 1, 14.5, 9, 15, {"north": "fit_gse", "south": "fit_yellow", "side": "fit_gse",
                                                     "up": "fit_yellow", "down": "fit_grey"}],
                        [3, 9, 3, 13, 13, 13, "fit_yellow"], [6, 13, 6, 10, 14, 10, "fit_grey"],
                        [7, 4, -1, 9, 6, 1, "home_rubber"]] + wheels(), "Start Cart", decor=False)
    add("fit_firebottle", [[5, 2, 5, 11, 14, 11, "fit_bottle"], [6.5, 14, 6.5, 9.5, 15.5, 9.5, "fit_grey"],
                           [3, 0, 6, 5, 4, 10, "navy_tyre"], [11, 0, 6, 13, 4, 10, "navy_tyre"],
                           [4, 1.5, 7.5, 12, 2.5, 8.5, "fit_grey"], [5, 12, 11, 11, 13, 14, "fit_grey"],
                           [4.5, 4, 11, 11.5, 9, 13, "home_rubber"]], "Fire Bottle", decor=False)
    add("fit_stand", [[1, 0, 1, 15, 4, 16, "fit_yellow"], [1, 4, 6, 15, 8, 16, "fit_yellow"],
                      [1, 8, 11, 15, 12, 16, "fit_yellow"], [1, 12, 0, 2, 20, 1, "fit_grey"], [14, 12, 0, 15, 20, 1, "fit_grey"],
                      [1, 19, 0, 2, 20, 16, "fit_grey"], [14, 19, 0, 15, 20, 16, "fit_grey"]], "Work Stand", decor=False)
    add("fit_towbar", [[7, 1, 0, 9, 2.5, 16, "fit_yellow"], [5, 0, 10, 7, 3, 14, "navy_tyre"], [9, 0, 10, 11, 3, 14, "navy_tyre"],
                       [6, 0.5, 0, 10, 3, 1.5, "fit_grey"]], "Tow Bar")
    add("fit_chain_rack", [[1, 12, 13, 15, 13, 16, "fit_grey"]]
        + [[x, 2, 14.5, x + 1, 12, 15.5, "vessel_chain_h"] for x in (2.5, 5.5, 8.5, 11.5)], "Chain Rack")
    add("fit_fod", [[4, 0, 4, 12, 10, 12, "fit_yellow"], [3.5, 10, 3.5, 12.5, 11, 12.5, "fit_grey"]], "FOD Can",
        rotate=None, decor=False)
    add("fit_tyres", [[3, 0, 3, 13, 4, 13, "navy_tyre"], [3, 4.2, 3, 13, 8.2, 13, "navy_tyre"],
                      [3.5, 8.4, 3.5, 12.5, 12.4, 12.5, "navy_tyre"], [6, 12.4, 6, 10, 12.6, 10, "fit_grey"]], "Spare Tyres",
        rotate=None, decor=False)
    add("fit_droptank", [[4, 4, 0, 12, 12, 16, "fit_tank"], [2, 0, 3, 14, 4.5, 5, "fit_yellow"],
                         [2, 0, 11, 14, 4.5, 13, "fit_yellow"]], "Drop Tank", rotate="axis", decor=False)
    add("fit_droptank_nose", [[5, 5, 0, 11, 11, 6, "fit_tank"], [4, 4, 6, 12, 12, 16, "fit_tank"],
                              [2, 0, 11, 14, 4.5, 13, "fit_yellow"]], "Drop Tank", rotate="axis", decor=False)

    add("fit_line", [[0, 0, 0, 16, 0.25, 16, "fit_line"]], "Deck Line", rotate="axis")

    for k in SIGNS:
        add(f"fit_st_{k}", [[0, 0, 15.75, 16, 16, 16, f"fit_st_{k}"]], "Stencil")
    for i in range(len(LABELS)):
        add(f"fit_pl_{i}", [[0, 0, 15.75, 16, 16, 16, f"fit_pl_{i}"]], "Compartment Label")
    return out


def main():
    path = HERE / "blocks.json"
    data = json.loads(path.read_text())
    kept = {k: v for k, v in data["blocks"].items() if not k.startswith(PREFIXES)}
    made = blocks()
    kept.update(made)
    data["blocks"] = kept
    lines = ",\n".join(f"    {json.dumps(k)}: {json.dumps(v)}" for k, v in kept.items())
    rest = {k: v for k, v in data.items() if k != "blocks"}
    head = "".join(f'  {json.dumps(k)}: {json.dumps(v)},\n' for k, v in rest.items())
    path.write_text("{\n" + head + '  "blocks": {\n' + lines + "\n  }\n}\n")
    print(f"{len(made)} fit blocks in {path}")


if __name__ == "__main__":
    main()
