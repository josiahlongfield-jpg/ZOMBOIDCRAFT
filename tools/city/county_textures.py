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

    # ---------------------------------------------------------------- farms

    class Big:
        """A canvas cols x rows blocks, drawn once and cut into a Tex per block."""

        def __init__(self, seed, cols, rows, base, alpha=255.0):
            self.rng = np.random.default_rng(seed)
            self.cols, self.rows, self.seed = cols, rows, seed
            W, H = cols * N, rows * N
            self.c = np.zeros((H, W, 3), np.float32) + np.array(base, np.float32)
            self.h = np.full((H, W), 0.75, np.float32)
            self.smooth = np.full((H, W), 0.2, np.float32)
            self.f0 = np.full((H, W), 10.0, np.float32)
            self.poro = np.full((H, W), 40.0, np.float32)
            self.emit = np.zeros((H, W), np.float32)
            self.alpha = np.full((H, W), alpha, np.float32)

        def tile(self, i, j):
            t = P.Tex(self.seed + i * 7 + j, (0, 0, 0))
            for k in ("c", "h", "smooth", "f0", "poro", "emit", "alpha"):
                setattr(t, k, getattr(self, k)[j * N:(j + 1) * N, i * N:(i + 1) * N].copy())
            return t

    def cut(cols, rows, draw):
        """Names a (col, row) tile of a Big drawn by draw() on first use."""
        cache = {}

        def tile(i, j):
            def make():
                if "big" not in cache:
                    cache["big"] = draw()
                return cache["big"].tile(i, j)
            return make
        return {(i, j): tile(i, j) for i in range(cols) for j in range(rows)}

    def paint_line(b, x0, y0, x1, y1, width, col, h=0.95):
        """A straight painted board or stroke from (x0, y0) to (x1, y1), width texels across."""
        n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
        r = width / 2
        for k in range(n):
            x = x0 + (x1 - x0) * k / max(n - 1, 1)
            y = y0 + (y1 - y0) * k / max(n - 1, 1)
            ya, yb = int(max(0, y - r)), int(min(b.c.shape[0], y + r + 1))
            xa, xb = int(max(0, x - r)), int(min(b.c.shape[1], x + r + 1))
            b.c[ya:yb, xa:xb] = col
            b.h[ya:yb, xa:xb] = h
            b.alpha[ya:yb, xa:xb] = 255

    def barn_door(cols, rows, seed, panels):
        """Red board doors in a white frame, a white X across each panel, rain-run and peeling."""
        def draw():
            b = Big(seed, cols, rows, (120, 44, 34))
            for i in range(cols):
                for j in range(rows):
                    t = tone(ns.photo("distressed_painted_planks", span=1, offset=(0.1 + 0.27 * i, 0.15 + 0.2 * j), height=1.3),
                             (122, 44, 34), 1.4)
                    b.c[j * N:(j + 1) * N, i * N:(i + 1) * N] = t.c
                    b.h[j * N:(j + 1) * N, i * N:(i + 1) * N] = t.h
            W, H = cols * N, rows * N
            white = (214, 208, 194)
            fw = 9
            for x0, y0, x1, y1 in ((0, 0, W, fw), (0, H - fw, W, H), (0, 0, fw, H), (W - fw, 0, W, H)):
                b.c[y0:y1, x0:x1] = white
                b.h[y0:y1, x0:x1] = 0.95
            ph = H // panels
            for k in range(panels):
                y0, y1 = k * ph, (k + 1) * ph
                if k:
                    b.c[y0 - fw // 2:y0 + fw // 2 + 1] = white
                    b.h[y0 - fw // 2:y0 + fw // 2 + 1] = 0.95
                paint_line(b, fw, y0 + fw, W - fw, y1 - fw, 8, white)
                paint_line(b, W - fw, y0 + fw, fw, y1 - fw, 8, white)
            # the white has worn back to grey wood in places, dirt splashed up from the yard
            worn = b.rng.random((H, W)) < 0.04
            b.c[worn & (b.c[..., 0] > 180)] = (128, 120, 108)
            y = np.arange(H)[:, None] / H
            b.c *= (1 - 0.35 * np.clip((y - 0.8) * 5, 0, 1) * (0.6 + 0.4 * b.rng.random((H, W))))[..., None]
            b.c *= (1 - 0.18 * b.rng.random((1, W)) ** 6 * np.clip(1 - y * 1.5, 0, 1))[..., None]
            return b
        return cut(cols, rows, draw)

    def windmill_wheel(seed, k=5):
        """A farm windmill's wheel: galvanized sails between two rims round a hub, a few torn off."""
        def draw():
            b = Big(seed, k, k, (168, 170, 166), alpha=0.0)
            H = k * N
            yy2, xx2 = np.mgrid[0:H, 0:H] + 0.5
            cx = cy = H / 2
            r = np.hypot(xx2 - cx, yy2 - cy)
            ang = np.arctan2(yy2 - cy, xx2 - cx)
            r1, r2 = H * 0.17, H * 0.48
            sails = 18
            missing = set(b.rng.choice(sails, 3, replace=False).tolist())
            phase = (ang / (2 * np.pi) * sails) % 1.0
            idx = np.floor((ang / (2 * np.pi) * sails) % sails).astype(int)
            twist = (r - r1) / (r2 - r1)
            sail = (r > r1) & (r < r2) & (np.abs(phase - 0.5 - 0.06 * twist) < 0.28)
            keep = ~np.isin(idx, list(missing))
            m = sail & keep
            b.alpha[m] = 255
            shade = 0.75 + 0.35 * phase
            b.c[m] *= shade[m][..., None]
            for rr, wdt in ((r2 - 2, 3.5), (r1 + (r2 - r1) * 0.45, 2.5), (r1, 3.0)):
                ring = np.abs(r - rr) < wdt
                b.c[ring] = (110, 110, 106)
                b.alpha[ring] = 255
                b.h[ring] = 0.95
            hub = r < H * 0.07
            b.c[hub] = (52, 52, 50)
            b.alpha[hub] = 255
            for a in np.linspace(0, 2 * np.pi, 6, endpoint=False):  # the arms out to the rim
                for s2 in np.linspace(H * 0.05, r2, 160):
                    x, y = int(cx + np.cos(a) * s2), int(cy + np.sin(a) * s2)
                    b.c[y - 1:y + 2, x - 1:x + 2] = (92, 92, 88)
                    b.alpha[y - 1:y + 2, x - 1:x + 2] = 255
            rust = (b.rng.random((H, H)) < 0.05) & (b.alpha > 0)
            b.c[rust] = (120, 74, 46)
            b.smooth[:] = 0.45
            b.f0[:] = 230
            return b
        return cut(k, k, draw)

    def corn(upper, dry, seed):
        """Field corn past harvest: stalks with long arching leaves, tassels gone to seed, ears in husk."""
        def make():
            t = ns.cutout(seed, (90, 110, 50))
            if dry:
                cols = [(150, 128, 82), (128, 104, 66), (170, 150, 102), (110, 92, 62)]
            else:
                cols = [(92, 116, 52), (110, 128, 60), (78, 100, 44), (134, 128, 70)]
            stalks = [5, 16, 27, 38, 49, 59]
            for k, x in enumerate(stalks):
                x += t.rng.uniform(-3, 3)
                lean = t.rng.uniform(-3, 3)
                col = cols[k % len(cols)]
                ns.blade(t, x, N, lean, col, 3)
                # leaves off the stalk, arching out and drooping
                for _ in range(5 if upper else 6):
                    y0 = int(t.rng.uniform(2, 58))
                    side = 1 if t.rng.random() < 0.5 else -1
                    length = int(t.rng.uniform(14, 28))
                    lc = cols[int(t.rng.integers(len(cols)))]
                    for i in range(length):
                        px = int(x + side * i)
                        py = int(N - 1 - y0 - 8 * np.sin(i / length * np.pi * 0.8) + i * 0.25 * (1 if dry else 0.4))
                        if 0 <= px < N and 0 <= py < N:
                            t.c[py, px] = np.array(lc, np.float32) * (0.8 + 0.3 * i / length)
                            t.alpha[py, px] = 255
                            for dy in (1, 2) if i < length * 0.7 else (1,):
                                if py + dy < N:
                                    t.c[py + dy, px] = np.array(lc, np.float32) * (0.75 - 0.08 * dy)
                                    t.alpha[py + dy, px] = 255
                if upper:
                    # the tassel: thin branches fanning from the top
                    for _ in range(5):
                        ns.blade(t, x + t.rng.uniform(-1, 1), int(t.rng.uniform(8, 14)), t.rng.uniform(-6, 6),
                                 (176, 150, 96) if not dry else (120, 96, 64), 1, top=12)
                else:
                    # an ear in its husk, leaning off the stalk
                    ey = int(t.rng.uniform(16, 34))
                    ex = int(x + (4 if k % 2 else -6))
                    husk = (176, 160, 104) if dry else (128, 138, 70)
                    for i in range(10):
                        yy3 = ey + i
                        xa, xb = max(0, ex), min(N, ex + 3 + (1 if 2 < i < 8 else 0))
                        t.c[yy3, xa:xb] = np.array(husk, np.float32) * (0.85 + 0.03 * i)
                        t.alpha[yy3, xa:xb] = 255
            t.smooth[:] = 0.12
            t.poro[:] = 64
            return t
        return make

    def silo_stave():
        """Concrete stave silo: tongue-and-groove staves, steel hoops every few courses, rust run down from them."""
        t = tone(ns.photo("concrete_block_wall_03", span=1, height=1.2), (170, 166, 154), 1.2)
        for x in range(0, N, 16):
            t.rect(x, 0, x + 1, N, col=None, h=0.45)
            t.c[:, x] *= 0.72
        for y in range(5, N, 21):
            t.rect(0, y, N, y + 2, col=(74, 66, 58), h=1.0, smooth=0.35)
            t.c[y + 2:y + 9] *= np.linspace(0.8, 1.0, 7)[:, None, None]
            t.c[y + 2:y + 12, :, 0] *= 1.04
        t.streaks(0.8, 0.7)
        t.grime(0.3)
        return t

    def silo_blue():
        """A glass-lined steel silo, deep blue panels bolted at the seams, chalky and rust-streaked."""
        t = tone(ns.photo("PaintedMetal012", span=2, height=0.8), (40, 62, 92), 0.9)
        for x in range(0, N, 32):
            t.rect(x, 0, x + 2, N, col=(30, 46, 68), h=0.9)
            for y in range(4, N, 8):
                t.c[y, x + 3] = (150, 150, 150)
        for y in (0, 32):
            t.rect(0, y, N, y + 2, col=(30, 46, 68), h=0.9)
        t.smooth[:] = 0.65
        rust_spots(t, 93, 0.12)
        t.streaks(0.7, 0.7)
        return t

    def bin_ribbed():
        """A corrugated grain bin's wall: the ribs run round it, galvanized and dulled to grey."""
        t = ns.photo("worn_corrugated_iron", span=1, sat=0.4, bright=0.95, height=1.6)
        for k in ("c", "h", "smooth"):
            a = getattr(t, k)
            setattr(t, k, np.ascontiguousarray(np.swapaxes(a, 0, 1)))
        for y in range(0, N, 32):
            t.rect(0, y, N, y + 1, col=(80, 80, 78), h=0.95)
            for x in range(2, N, 6):
                t.c[y + 2, x] = (60, 60, 58)
        t.streaks(0.6, 0.75)
        return t

    def bale_wrap():
        """A round bale's white stretch wrap, dirty at the bottom, torn where something got at it."""
        t = tone(ns.photo("Plastic010", span=1, height=0.6), (214, 214, 206), 0.9)
        t.smooth[:] = 0.6
        y = np.arange(N)[:, None] / N
        t.c *= (1 - 0.3 * np.clip((y - 0.7) * 3, 0, 1))[..., None]
        tear = (P.fbm(t.rng, ((4, 1.0), (8, 0.6))) > 0.55)
        t.c[tear] = (170, 150, 80)
        return t

    def straw_floor():
        t = tone(ns.photo("ThatchedRoof002B", span=1, height=1.0), (176, 150, 92), 1.1)
        t.grime(0.25)
        return t

    def apples_on(t, seed, count, rotten=0.15, rmin=2.2, rmax=3.4):
        """Paints apples onto a texture: round, a highlight up and left, a few gone brown."""
        rng = np.random.default_rng(seed)
        yy, xx = np.mgrid[0:N, 0:N]
        reds = [(168, 30, 24), (188, 52, 30), (148, 26, 30), (196, 118, 40), (176, 70, 34)]
        for _ in range(count):
            x, y = rng.uniform(2, N - 2), rng.uniform(2, N - 2)
            r = rng.uniform(rmin, rmax)
            col = np.array((92, 60, 34) if rng.random() < rotten else reds[int(rng.integers(len(reds)))], np.float32)
            d = np.hypot(xx - x, yy - y)
            m = d <= r
            shade = 0.75 + 0.55 * np.clip(1 - np.hypot(xx - x + r * 0.45, yy - y + r * 0.45) / r, 0, 1)
            t.c[m] = col * shade[m][..., None]
            t.alpha[m] = 255
            t.h[m] = 0.7 + 0.3 * np.clip(1 - d[m] / r, 0, 1)
            t.smooth[m] = 0.55
            sx, sy = int(x), int(y - r)  # the stalk
            if 0 <= sy < N and 0 <= sx < N:
                t.c[sy, sx] = (60, 44, 26)
                t.alpha[sy, sx] = 255
        return t

    def apple_leaves():
        """An apple tree's leaves heavy with fruit nobody picked."""
        return apples_on(ns.leaves_photo(), 341, 16)

    def windfall():
        """Windfalls rotting in the grass under the trees, a few leaves among them."""
        t = ns.cutout(342, (120, 60, 30))
        return apples_on(t, 343, 9, rotten=0.55, rmin=2.4, rmax=3.2)

    def crate_apples():
        """The top of a crate heaped with apples."""
        t = P.Tex(344, (110, 40, 28))
        t.alpha[:] = 255
        return apples_on(t, 345, 70, rotten=0.1, rmin=3.0, rmax=4.0)

    farm = {
        "apple_leaves": apple_leaves, "windfall": windfall, "crate_apples": crate_apples,
        "corn_lower": corn(False, False, 301), "corn_upper": corn(True, False, 302),
        "corn_dry_lower": corn(False, True, 303), "corn_dry_upper": corn(True, True, 304),
        "silo_stave": silo_stave, "silo_blue": silo_blue, "bin_ribbed": bin_ribbed,
        "bale_wrap": bale_wrap, "straw_floor": straw_floor,
    }
    for (i, j), make in barn_door(2, 3, 310, 2).items():
        farm[f"barn_door_{i}{j}"] = make
    for (i, j), make in barn_door(2, 2, 320, 1).items():
        farm[f"barn_loft_{i}{j}"] = make
    for (i, j), make in windmill_wheel(330).items():
        farm[f"windmill_{i}{j}"] = make

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
    extra.update(farm)
    ns.EXTRA.update(extra)
