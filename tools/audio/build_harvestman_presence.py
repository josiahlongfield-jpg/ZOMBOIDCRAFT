"""Builds the sounds that give the Harvestman away before it's seen, from scratch (no recordings).

    python3 tools/audio/build_harvestman_presence.py

- chitter: a dry, uneven run of knocks, like hard points rattled against each other in another room; muffled and
  given a little room tail, so it reads as heard through a wall (not the bright synthetic clicks Josiah cut).
- creak: boards or a ceiling panel taking weight: a slow stick-slip run of pulses through a woody resonance.
- hum: a low drone it gives off, a few beating partials and a faint buzz, as a seamless 8 second loop (every partial
  makes whole cycles in the loop, so it never clicks where it wraps).

Writes assets/zomboidcraft/sounds/creature/harvestman/<name>/N.ogg and their creature.harvestman.* entries in
assets/zomboidcraft/sounds.json (kept with the creature's other entries at the top).
"""
import json
import subprocess
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources/assets/zomboidcraft"
FOLDER = "creature/harvestman"
RATE = 44100
PEAK = -1.0


def write(x, dst, peak=PEAK):
    """Raises a take to the peak and writes it as Ogg Vorbis."""
    x = np.asarray(x, dtype=float)
    x = x / max(1e-9, np.abs(x).max()) * 10 ** (peak / 20)
    pcm = (x * 32767).astype("<i2").tobytes()
    dst.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "s16le", "-ar", str(RATE), "-ac", "1", "-i", "-",
                    "-c:a", "libvorbis", "-q:a", "5", str(dst)], input=pcm, check=True)


def lowpass(x, cutoff):
    """A gentle one-pole low pass, run twice."""
    a = np.exp(-2 * np.pi * cutoff / RATE)
    for _ in range(2):
        y = np.zeros_like(x)
        s = 0.0
        for i, v in enumerate(x):
            s = (1 - a) * v + a * s
            y[i] = s
        x = y
    return x


def resonator(x, freq, q):
    """A two-pole band resonance (the body of a board, a plate)."""
    w = 2 * np.pi * freq / RATE
    r = np.exp(-w / (2 * q))
    b1, b2 = 2 * r * np.cos(w), -r * r
    y = np.zeros_like(x)
    y1 = y2 = 0.0
    for i, v in enumerate(x):
        y0 = v + b1 * y1 + b2 * y2
        y[i] = y0
        y2, y1 = y1, y0
    return y * (1 - r)


def room(x, seed, wet=0.25):
    """A short, dark room tail: a few filtered, decaying echoes."""
    rng = np.random.default_rng(seed)
    out = np.concatenate([x, np.zeros(int(RATE * 0.5))])
    for k in range(10):
        d = int(RATE * rng.uniform(0.012, 0.18))
        g = wet * np.exp(-k * 0.35) * rng.uniform(0.5, 1.0)
        out[d:d + len(x)] += x * g * (1 if k % 2 else -1)
    return lowpass(out, 2600)


def chitter(seed):
    rng = np.random.default_rng(seed)
    n = int(RATE * 0.9)
    x = np.zeros(n)
    t = 0.03
    for _ in range(rng.integers(6, 12)):
        at = int(t * RATE)
        ln = int(RATE * 0.04)
        k = np.arange(ln) / RATE
        knock = rng.normal(0, 1, ln) * np.exp(-k * rng.uniform(220, 380))
        knock = resonator(knock, rng.uniform(900, 1500), 9) + 0.35 * resonator(knock, rng.uniform(2400, 3200), 14)
        x[at:at + ln] += knock[:max(0, min(ln, n - at))] * rng.uniform(0.4, 1.0)
        # in bursts: quick runs, then a beat
        t += rng.uniform(0.025, 0.05) if rng.random() < 0.7 else rng.uniform(0.09, 0.16)
        if t > 0.75:
            break
    x = lowpass(x, 1800)
    return room(x, seed + 1, 0.3)


def creak(seed):
    rng = np.random.default_rng(seed)
    dur = rng.uniform(0.7, 1.3)
    n = int(RATE * dur)
    x = np.zeros(n)
    t, rate = 0.0, rng.uniform(18, 30)
    while t < dur - 0.02:
        at = int(t * RATE)
        if at < n:
            x[at] += rng.uniform(0.6, 1.0)
        # stick-slip: the pulses speed up and slow again as the weight shifts
        prog = t / dur
        rate = rng.uniform(18, 26) + 40 * np.sin(np.pi * prog) ** 2
        t += 1 / rate * rng.uniform(0.85, 1.15)
    env = np.sin(np.pi * np.linspace(0, 1, n)) ** 0.6
    body = resonator(x, rng.uniform(380, 520), 6) + 0.6 * resonator(x, rng.uniform(820, 1100), 8) \
        + 0.25 * resonator(x, rng.uniform(1700, 2100), 10)
    return room(lowpass(body * env, 2400), seed + 3, 0.18)


def hum():
    dur = 8.0
    n = int(RATE * dur)
    t = np.arange(n) / RATE
    # every partial a whole number of cycles in 8 s (frequencies in steps of 1/8 Hz) so the loop wraps clean
    parts = [(46.0, 1.0), (46.375, 0.7), (92.125, 0.35), (138.25, 0.18), (61.5, 0.25), (184.5, 0.08)]
    x = sum(a * np.sin(2 * np.pi * f * t + i) for i, (f, a) in enumerate(parts))
    # a slow swell (once per loop, twice) and a faint buzz on top
    x *= 0.8 + 0.2 * np.sin(2 * np.pi * t / dur * 2)
    buzz = np.sign(np.sin(2 * np.pi * 92.125 * t)) * 0.04 * (0.5 + 0.5 * np.sin(2 * np.pi * t / dur * 3))
    return x + lowpass(buzz, 900)


SOUNDS = {
    "chitter": (40, [lambda: chitter(101), lambda: chitter(202), lambda: chitter(303)]),
    "creak": (20, [lambda: creak(11), lambda: creak(22), lambda: creak(33)]),
    "hum": (None, [hum]),
}


def main():
    entries = {}
    for name, (dist, takes) in SOUNDS.items():
        rows = []
        for n, make in enumerate(takes, 1):
            write(make(), RES / "sounds" / FOLDER / name / f"{n}.ogg", -3.0 if name == "hum" else PEAK)
            row = {"name": f"zomboidcraft:{FOLDER}/{name}/{n}"}
            if dist:
                row["attenuation_distance"] = dist
            rows.append(row)
        entries[f"creature.harvestman.{name}"] = {"sounds": rows}
    path = RES / "sounds.json"
    sounds = json.loads(path.read_text())
    keys = list(sounds)
    out = {}
    # after the creature's other entries, before everything else
    last = max((i for i, k in enumerate(keys) if k.startswith("creature.harvestman.")), default=-1)
    for i, k in enumerate(keys):
        if k not in entries:
            out[k] = sounds[k]
        if i == last:
            out.update(entries)
    if last < 0:
        out = {**entries, **out}
    path.write_text(json.dumps(out, indent=2) + "\n")
    print(", ".join(entries))


if __name__ == "__main__":
    main()
