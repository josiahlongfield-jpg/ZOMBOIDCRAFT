"""Writes the rain texture: thin, faint streaks you can see through, in place of vanilla's.

Vanilla draws the rain as a ring of tall sheets around you, one block wide, scrolling this
texture down them; the more it rains, the more sheets it draws. So each sheet only needs a
few hairline streaks, and a downpour still fills the air because many sheets overlap.

python3 tools/weather/gen_rain.py
"""
import random
from pathlib import Path

from PIL import Image

W, H = 64, 256  # vanilla's size: one block wide, scrolled vertically (so it must wrap)
OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/minecraft/textures/environment/rain.png"


def main():
    rnd = random.Random(26)
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    used = set()
    for _ in range(9):
        x = rnd.randrange(W)
        while x in used or x - 1 in used or x + 1 in used:
            x = rnd.randrange(W)
        used.add(x)
        top = rnd.randrange(H)
        length = rnd.randint(14, 34)
        peak = rnd.randint(70, 120)
        for i in range(length):
            t = i / (length - 1)
            # faint tail fading in, brightest near the falling head
            a = int(peak * (t ** 1.6))
            y = (top + i) % H
            px[x, y] = (196, 208, 222, a)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
