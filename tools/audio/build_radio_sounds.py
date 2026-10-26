"""Builds the radio sounds, synthesised (no samples): the clicks, beeps and bursts of static a handheld radio makes.

    python3 tools/audio/build_radio_sounds.py

Writes sounds/item/radio/<event>/<n>.ogg and the item.radio.* entries in sounds.json. A radio's own noises are
simple enough to make from scratch: static is filtered noise, the beeps are tones, the buttons short damped clicks.

    key_up     someone keys up on your channel: a click as the squelch opens, a short rush of static, then quiet
    squelch    they let go: the burst of static as the carrier drops ("kssht")
    ptt        your own push-to-talk button going down
    channel    the channel knob's detent click and a short beep
    power_on   the power knob's click, a rising two-tone chirp and the hiss coming up
    power_off  the click and the hiss cut off
"""
import json
import os
import subprocess
import sys
import tempfile

import numpy as np
from scipy.signal import butter, sosfilt

RATE = 48000
ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "zomboidcraft")


def secs(t):
    return int(round(t * RATE))


def band(x, lo, hi, order=4):
    sos = butter(order, [lo, hi], btype="bandpass", fs=RATE, output="sos")
    return sosfilt(sos, x)


def noise(rng, t, lo=300, hi=3200):
    """Radio static: white noise squeezed into the speaker's narrow band, a little gritty."""
    n = band(rng.standard_normal(secs(t)), lo, hi)
    n = np.tanh(n * 2.2)
    return n / (np.abs(n).max() + 1e-9)


def click(rng, t=0.012, pitch=3200, body=900, crack=0.6):
    """A plastic button or detent: a sharp crack and a quick damped ring of the case."""
    n = secs(t)
    k = np.arange(n) / RATE
    env = np.exp(-k * 900)
    crackle = rng.standard_normal(n) * np.exp(-k * 2500) * crack
    ring = np.sin(2 * np.pi * pitch * k) * env * 0.6 + np.sin(2 * np.pi * body * k) * np.exp(-k * 350) * 0.5
    out = crackle + ring
    return out / (np.abs(out).max() + 1e-9)


def tone(f, t, attack=0.004, release=0.012, square=0.35):
    """A radio beep: a sine with a touch of square (the little speaker), soft edges so it doesn't tick."""
    k = np.arange(secs(t)) / RATE
    s = np.sin(2 * np.pi * f * k)
    s = (1 - square) * s + square * np.sign(s) * 0.7
    env = np.minimum(1, np.minimum(k / attack, (t - k) / release))
    return band(s * np.clip(env, 0, 1), 400, 4000, order=2)


def fade(x, a=0.002, r=0.02):
    n = len(x)
    env = np.ones(n)
    na, nr = min(n, secs(a)), min(n, secs(r))
    if na:
        env[:na] = np.linspace(0, 1, na)
    if nr:
        env[-nr:] *= np.linspace(1, 0, nr)
    return x * env


def place(parts, length):
    out = np.zeros(secs(length))
    for at, x, gain in parts:
        i = secs(at)
        j = min(len(out), i + len(x))
        out[i:j] += x[: j - i] * gain
    return out


def key_up(rng):
    rush = noise(rng, 0.09) * np.linspace(1, 0.55, secs(0.09))
    hush = noise(rng, 0.16, 500, 2400) * np.exp(-np.arange(secs(0.16)) / RATE * 22) * 0.18
    return place([(0, click(rng, 0.01, 2600, 700, 0.4), 0.5), (0.006, fade(rush, 0.003, 0.012), 0.75),
                  (0.096, hush, 1.0)], 0.28)


def squelch(rng):
    t = rng.uniform(0.16, 0.24)
    burst = noise(rng, t, 250, 3600)
    # the tail isn't flat: it swells for a moment, then the receiver cuts it dead
    k = np.arange(len(burst)) / RATE
    swell = 0.8 + 0.2 * np.sin(2 * np.pi * rng.uniform(5, 9) * k + rng.uniform(0, 6))
    burst = fade(burst * swell, 0.004, 0.006)
    return place([(0, burst, 1.0), (t - 0.004, click(rng, 0.008, 2200, 600, 0.3), 0.25)], t + 0.03)


def ptt(rng):
    down = click(rng, 0.014, rng.uniform(2700, 3300), rng.uniform(700, 900), 0.7)
    return place([(0, down, 1.0)], 0.05)


def channel(rng):
    return place([(0, click(rng, 0.01, 3600, 1100, 0.5), 0.7), (0.03, tone(1450, 0.06), 0.55)], 0.11)


def power_on(rng):
    hiss = noise(rng, 0.35, 400, 2800) * np.linspace(0, 1, secs(0.35)) ** 2
    hiss = fade(hiss * np.exp(-np.arange(secs(0.35)) / RATE * 4), 0.01, 0.06)
    return place([(0, click(rng, 0.016, 2400, 650, 0.8), 0.9), (0.05, tone(1200, 0.07), 0.5), (0.13, tone(1800, 0.09), 0.5),
                  (0.2, hiss, 0.12)], 0.6)


def power_off(rng):
    hiss = fade(noise(rng, 0.12, 400, 2800), 0.005, 0.004)
    return place([(0, hiss, 0.14), (0.11, click(rng, 0.016, 2300, 600, 0.8), 0.9), (0.14, tone(900, 0.05), 0.35)], 0.24)


# event: (builder, takes, peak dB, attenuation distance)
SOUNDS = {
    "key_up": (key_up, 3, -6.0, 16),
    "squelch": (squelch, 4, -5.0, 16),
    "ptt": (ptt, 3, -12.0, 6),
    "channel": (channel, 1, -12.0, 8),
    "power_on": (power_on, 1, -10.0, 8),
    "power_off": (power_off, 1, -10.0, 8),
}


def write_ogg(x, path, peak_db):
    x = x / (np.abs(x).max() + 1e-9) * 10 ** (peak_db / 20)
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".raw", delete=False) as f:
        f.write(pcm.tobytes())
        raw = f.name
    try:
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "s16le", "-ar", str(RATE), "-ac", "1", "-i", raw,
                        "-c:a", "libvorbis", "-q:a", "6", path], check=True)
    finally:
        os.remove(raw)


def main():
    rng = np.random.default_rng(1983)
    sounds_json = os.path.join(ROOT, "sounds.json")
    with open(sounds_json) as f:
        table = json.load(f)
    for event, (build, takes, peak, dist) in SOUNDS.items():
        entries = []
        for n in range(1, takes + 1):
            write_ogg(build(rng), os.path.join(ROOT, "sounds", "item", "radio", event, f"{n}.ogg"), peak)
            entries.append({"name": f"zomboidcraft:item/radio/{event}/{n}", "attenuation_distance": dist})
        table[f"item.radio.{event}"] = {"sounds": entries}
        print(f"item.radio.{event}: {takes} take(s)")
    with open(sounds_json, "w") as f:
        json.dump(table, f, indent=2, ensure_ascii=False)


if __name__ == "__main__":
    sys.exit(main())
