"""Textures for the county's blocks (suburbs, farms, the port, the military): painted lap
siding, corrugated metal, barn boards, shipping containers, ship hulls, crane steel, hesco,
canvas and camouflage net, garage doors and warning plates. Like the city's, each comes from a
CC0 photo graded toward a wet, sun-bleached, neglected look.

photo_textures.py calls register() with itself, adding these to its PHOTO table (so the
weathered variants in VARIANTS work on them) and to EXTRA.
"""
import numpy as np

SIDING = {"white": (206, 203, 192), "blue": (118, 138, 156), "sage": (128, 144, 116), "yellow": (198, 178, 112),
          "grey": (134, 136, 134), "brown": (112, 88, 66), "red": (138, 62, 50)}
CONTAINERS = {"red": (146, 52, 40), "blue": (44, 76, 116), "green": (62, 98, 68), "orange": (190, 102, 40),
              "white": (192, 192, 186), "grey": (108, 112, 114)}


def register(ns):
    P, N = ns.P, ns.N

    def tone(t, rgb, contrast=1.0):
        """Recolours a photo to a mean colour, keeping its light and dark detail."""
        lum = t.c @ np.array([0.299, 0.587, 0.114], np.float32)
        rel = (lum / max(float(lum.mean()), 1.0) - 1) * contrast + 1
        t.c = np.clip(rel[..., None] * np.array(rgb, np.float32), 0, 255)
        return t

    def rust_spots(t, seed, amount=0.35):
        """Rust blooming through the paint: orange-brown patches, rougher than the paint."""
        rng = np.random.default_rng(seed)
        n = P.fbm(rng, ((4, 1.0), (8, 0.7), (16, 0.5), (32, 0.3)))
        m = np.clip((n - (0.55 - amount)) * 4, 0, 1)
        rust = np.array((104, 58, 34), np.float32) * (0.8 + 0.4 * (P.fbm(rng) * 0.5 + 0.5))[..., None]
        t.c = t.c * (1 - m[..., None]) + rust * m[..., None]
        t.smooth = t.smooth * (1 - m * 0.6)
        return t

    def siding(colour):
        def make():
            t = tone(ns.photo("exterior_wall_cladding_03", span=2, sat=0.5, bright=1.0, height=1.2), colour, 1.3)
            # six laps a block: each board's lower edge throws a shadow on the one below
            for y0 in range(0, N, N // 6 + 1):
                for k, shade in enumerate((0.55, 0.72, 0.86)):
                    if y0 + k < N:
                        t.c[y0 + k] *= shade
                        t.h[y0 + k] -= 0.12 - k * 0.04
                if y0 + 3 < N:
                    t.c[y0 + 3] *= 1.08
            t.grime(0.35)
            return ns.rain_streaks(t, 0.55)
        return make

    def container(colour, seed, door=False):
        def make():
            t = tone(ns.photo("container_side", span=1.5, offset=(0.3, 0.2), height=1.4), colour, 1.2)
            rust_spots(t, seed, 0.3)
            t.streaks(0.8, 0.7)
            if door:
                # the two locking bars and their handles, the hinge line down one side
                for x in (18, 44):
                    t.rect(x, 0, x + 3, N, col=(60, 58, 54), h=0.95, smooth=0.4)
                    t.rect(x - 4, 30, x + 7, 34, col=(70, 66, 60), h=1.0, smooth=0.4)
                t.rect(31, 0, 33, N, col=tuple(np.array(colour) * 0.45), h=0.4)
                t.rect(2, 0, 4, N, col=(52, 50, 48), h=0.9)
            return t
        return make

    def painted_metal(material, colour, seed, rust=0.25, span=2):
        def make():
            t = tone(ns.photo(material, span=span, height=1.0), colour, 0.9)
            rust_spots(t, seed, rust)
            t.streaks(0.6, 0.75)
            return t
        return make

    def hesco():
        t = tone(ns.photo("hessian_380", span=1, height=1.0), (168, 150, 112), 1.4)
        t.grime(0.4)
        for i in range(0, N, 16):  # the wire mesh cage over the fabric
            t.rect(i, 0, i + 1, N, col=(96, 96, 92), h=1.0, smooth=0.5)
            t.rect(0, i, N, i + 1, col=(96, 96, 92), h=1.0, smooth=0.5)
        t.rect(0, 0, N, 2, col=(110, 108, 100), h=1.0)
        return t

    def camo_net():
        """Scrim strips of olive, brown and black knotted into a net, with gaps of sky."""
        rng = np.random.default_rng(71)
        t = P.Tex(71, (74, 78, 52))
        a = P.fbm(rng, ((4, 1.0), (8, 0.8), (16, 0.6), (32, 0.4)))
        b = P.fbm(rng, ((4, 1.0), (8, 0.6), (16, 0.3)))
        t.c[b > 0.25] = (88, 72, 50)
        t.c[b < -0.3] = (36, 38, 30)
        t.vary(0.2)
        t.alpha = np.where(a > -0.35, 255, 0).astype(np.float32)
        t.smooth[:] = 0.1
        return t

    def plate(material):
        def make():
            t = ns.photo(material, span=1, sat=0.85, bright=0.85)
            t.streaks(0.6, 0.8)
            t.grime(0.2)
            return t
        return make

    def garage(material, colour):
        def make():
            t = tone(ns.photo(material, span=1, height=1.2), colour, 1.0)
            t.grime(0.3)
            return ns.rain_streaks(t, 0.4)
        return make

    def pump():
        """A petrol pump's face: white body, red band, a dead price display and the nozzle bay."""
        t = tone(ns.photo("PaintedMetal012", span=2), (198, 196, 190), 0.8)
        t.rect(0, 40, N, 52, col=(150, 40, 34))
        t.rect(14, 8, 50, 22, col=(28, 30, 30), smooth=0.8)
        for i in range(3):
            t.rect(17 + i * 11, 12, 25 + i * 11, 18, col=(52, 60, 50))
        t.rect(20, 28, 44, 36, col=(60, 60, 62), h=0.5)
        t.streaks(0.8, 0.75)
        t.grime(0.3)
        return t

    yy, xx = np.mgrid[0:N, 0:N] + 0.5

    def clock_face():
        """The courthouse clock: a cream dial in a black rim, its hands stopped at ten past eight."""
        t = P.Tex(91, (212, 204, 180))
        r = np.hypot(xx - N / 2, yy - N / 2)
        t.alpha = np.where(r < 31.5, 255, 0).astype(np.float32)
        rim = (r >= 27) & (r < 31.5)
        t.c[rim] = (38, 36, 32)
        t.h[rim] = 0.95
        for k in range(12):
            a = k * np.pi / 6
            for s in np.linspace(21, 25.5, 10):
                x, y = int(N / 2 + np.sin(a) * s), int(N / 2 - np.cos(a) * s)
                t.c[y - 1:y + 1, x - 1:x + 1] = (30, 30, 28)
        for a, length, half in ((np.radians(8 * 30 + 5), 13, 2), (np.radians(10 * 6), 20, 1)):
            for s in np.linspace(0, length, 60):
                x, y = int(N / 2 + np.sin(a) * s), int(N / 2 - np.cos(a) * s)
                t.c[y - half:y + half, x - half:x + half] = (24, 24, 22)
        t.vary(0.12)
        t.grime(0.45)
        t.streaks(0.7, 0.65)
        return t

    def octagon(r):
        dx, dy = np.abs(xx - N / 2), np.abs(yy - N / 2)
        return (dx < r) & (dy < r) & (dx + dy < r * 1.414)

    def stop_sign():
        """A stop sign: red octagon, white border and letters, sun-faded and peppered with shot."""
        from signs import lettering
        t = P.Tex(92, (166, 30, 28))
        outer, inner = octagon(30), octagon(27)
        t.alpha = np.where(outer, 255, 0).astype(np.float32)
        t.c[outer & ~inner] = (226, 222, 214)
        mask, _ = lettering("STOP", "black", N, N, fill=0.36)
        t.mix(mask, (236, 232, 222), 0.95)
        t.mix(np.full((N, N), 1.0), (190, 150, 140), 0.12)
        t.speckle(0.004, (40, 36, 34), 0.9)
        t.grime(0.25)
        t.streaks(0.6, 0.75)
        t.smooth[:] = 0.5
        return t

    def stop_sign_back():
        t = tone(ns.photo("PaintedMetal012", span=1), (128, 130, 128), 0.8)
        t.alpha = np.where(octagon(30), 255, 0).astype(np.float32)
        t.grime(0.4)
        return t

    photos = {f"siding_{k}": siding(v) for k, v in SIDING.items()}
    photos.update({
        "corrugated_grey": lambda: ns.rain_streaks(ns.photo("worn_corrugated_iron", span=2, sat=0.6, bright=0.85, height=1.5), 0.5),
        "corrugated_rust": lambda: ns.photo("rusty_corrugated_iron", span=2, sat=0.75, bright=0.82, height=1.5),
        "corrugated_green": lambda: ns.photo("CorrugatedSteel006C", span=2, sat=0.6, bright=0.75, height=1.5),
        "corrugated_red": lambda: ns.photo("box_profile_metal_sheet", span=2, sat=0.7, bright=0.85, height=1.5),
        "corrugated_blue": lambda: ns.photo("CorrugatedSteel007C", span=2, sat=0.6, bright=0.8, height=1.5),
        "barn_red": lambda: ns.rain_streaks(tone(ns.photo("distressed_painted_planks", span=2, height=1.3), (122, 44, 34), 1.4), 0.6),
        "barn_grey": lambda: ns.rain_streaks(tone(ns.photo("wooden_gate", span=2, height=1.3), (118, 110, 100), 1.3), 0.5),
        "fence_boards": lambda: ns.photo("WoodSiding011", span=2, sat=0.5, bright=0.95, height=1.2),
    })
    for i, (k, v) in enumerate(CONTAINERS.items()):
        photos[f"container_{k}"] = container(v, 60 + i)
    variants = {f"siding_{k}": ["b", "crack", "moss", "soot", "stain"] for k in SIDING}
    variants.update({k: ["b", "stain"] for k in ("corrugated_grey", "corrugated_rust", "corrugated_green",
                                                 "corrugated_red", "corrugated_blue")})
    variants.update({"barn_red": ["b", "moss"], "barn_grey": ["b", "moss"]})
    ns.PHOTO.update(photos)
    ns.VARIANTS.update(variants)

    extra = {f"container_{k}_door": container(v, 60 + i, door=True) for i, (k, v) in enumerate(CONTAINERS.items())}
    extra.update({
        "steel_yellow": painted_metal("PaintedMetal001", (200, 150, 40), 80, 0.2),
        "steel_red": painted_metal("PaintedMetal004", (150, 44, 36), 81, 0.25),
        "steel_olive": lambda: ns.photo("green_metal_rust", span=2, sat=0.8, bright=0.85),
        "hull_black": painted_metal("PaintedMetal014", (48, 50, 52), 82, 0.3),
        "hull_red": painted_metal("PaintedMetal013", (118, 40, 34), 83, 0.3),
        "deck_steel": lambda: ns.photo("DiamondPlate008C", span=2, sat=0.6, bright=0.8, metal=True),
        "hesco": hesco,
        "tarp_green": lambda: tone(ns.photo("Fabric028", span=2), (86, 90, 62), 1.2),
        "camo_net": camo_net,
        "hazard_stripes": plate("PaintedMetal016"),
        "plate_biohazard": plate("Sign023"),
        "plate_danger": plate("PaintedMetal017"),
        "plate_skull": plate("Sign022"),
        "garage_door": garage("painted_metal_shutter", (182, 180, 172)),
        "garage_door_wood": garage("wooden_garage_door", (118, 84, 58)),
        "pole_wood": lambda: tone(ns.photo("Wood089", span=2), (78, 64, 50), 1.3),
        "pump_face": pump,
        "propane": painted_metal("PaintedMetal012", (206, 204, 198), 84, 0.15),
        "mailbox": painted_metal("PaintedMetal010", (58, 60, 62), 85, 0.2),
        "tank_white": painted_metal("PaintedMetal012", (196, 198, 192), 86, 0.3, span=3),
        "tank_blue": painted_metal("PaintedMetal012", (104, 134, 158), 87, 0.3, span=3),
        "lamp_iron": painted_metal("PaintedMetal014", (36, 36, 34), 88, 0.15),
        "garage_door_red": garage("painted_metal_shutter", (148, 40, 34)),
        "clock_face": clock_face,
        "stop_sign": stop_sign,
        "stop_sign_back": stop_sign_back,
    })
    ns.EXTRA.update(extra)
