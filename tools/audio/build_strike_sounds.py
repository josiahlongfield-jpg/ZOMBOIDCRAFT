"""Builds the guns of the air strikes (world.strike.*): the jets' rotary cannon and the Apache's chain gun.

    python3 tools/audio/build_strike_sounds.py SONNISS_DIR

Both are made from one real 30 mm automatic cannon shot (Fascinated Sound's MK44, the Gun Locker pack): the
jet's cannon fires about fifty rounds a second, so the shot's crack is laid down that fast, a little uneven, into
the tearing "brrrt", with the full report rolling out after the last round; the chain gun fires ten a second, so
whole shots are laid over each other at that rate. Mono for 3D, peak -1 dB, not streamed (short). The Sonniss
originals stay out of this repo.
"""
import json
import subprocess
import sys
from pathlib import Path

import numpy as np

from build_flyby_sounds import load, RATE
from build_zombie_sounds import RES, ROOT

SHOT = "627539ff"
ONSET = 0.30        # where the shot starts in the recording (s)
# event: (rounds a second, rounds, how much of each shot to lay down (s), takes, attenuation distance)
GUNS = {
    "world.strike.cannon": (52, 70, 0.09, 3, 640),
    "world.strike.chaingun": (10.4, 18, 2.2, 3, 480),
}


def burst(shot, rate, rounds, length, rng):
    """rounds of shot, rate a second, each length long but the last, which rings out in full."""
    gap = RATE / rate
    total = int(gap * rounds) + len(shot) + RATE // 2
    out = np.zeros(total, np.float32)
    n = int(length * RATE)
    fade = np.ones(n, np.float32)
    k = min(n // 3, int(0.02 * RATE))
    fade[-k:] = np.linspace(1, 0, k, dtype=np.float32)
    for i in range(rounds):
        at = int(i * gap + rng.uniform(-0.12, 0.12) * gap) if i else 0
        piece = shot if i == rounds - 1 else shot[:n] * fade[:len(shot[:n])]
        gain = rng.uniform(0.8, 1.0) * (0.75 + 0.25 * min(1, i / 4))    # the barrels spinning up
        out[at:at + len(piece)] += piece * gain
    return out


def main():
    sx_dir = Path(sys.argv[1])
    sx = json.loads((sx_dir / "manifest.json").read_text())
    info = sx["sx" + SHOT]
    shot = load(sx_dir / info["file"])[int(ONSET * RATE):]
    sj = RES / "zomboidcraft/sounds.json"
    ours = json.loads(sj.read_text())
    rng = np.random.default_rng(7)
    rows = []
    for event, (rate, rounds, length, takes, reach) in GUNS.items():
        folder = "world/strike/" + event.split(".")[-1]
        out = RES / "zomboidcraft/sounds" / folder
        out.mkdir(parents=True, exist_ok=True)
        for old in out.glob("*.ogg"):
            old.unlink()
        names = []
        for i in range(1, takes + 1):
            a = burst(shot, rate * rng.uniform(0.95, 1.05), rounds, length, rng)
            n = int(0.6 * RATE)
            a[-n:] *= np.linspace(1, 0, n, dtype=np.float32) ** 2
            a *= 10 ** (-1 / 20) / max(np.abs(a).max(), 1e-6)
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(RATE), "-ac", "1", "-i", "-",
                            "-c:a", "libvorbis", "-q:a", "5", str(out / f"{i}.ogg")], input=a.tobytes(), check=True)
            names.append({"name": f"zomboidcraft:{folder}/{i}", "attenuation_distance": reach})
            print(event, i, round(len(a) / RATE, 1), "s")
        ours[event] = {"sounds": names}
        rows.append(f"| {event} | {info['title']} ({info['pack']}), laid down {rate:g} rounds a second | "
                    f"{info['bundle']} | Sonniss royalty-free |")
    sj.write_text(json.dumps(ours, indent=2) + "\n")

    path = ROOT / "CREDITS.md"
    text = path.read_text()
    head, sep, rest = text.partition("\n## Air strikes\n")
    if sep:
        nxt = rest.find("\n## ")
        rest = rest[nxt:] if nxt >= 0 else ""
    section = ("\n## Air strikes\n\nThe jets' cannon and the Apache's chain gun on their strike runs (world events), built by "
               "tools/audio/build_strike_sounds.py from one real 30 mm cannon shot. The bombs use the world explosion "
               "sounds. The Sonniss originals are not included.\n\n| Sound | Recording | By | Licence |\n|---|---|---|---|\n"
               + "\n".join(rows) + "\n")
    path.write_text(head.rstrip("\n") + "\n" + section + rest)


if __name__ == "__main__":
    main()
