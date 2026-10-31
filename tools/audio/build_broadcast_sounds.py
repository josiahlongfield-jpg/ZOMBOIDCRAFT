"""Builds the broadcast sounds, synthesised: the two alert tones Josiah kept on the Dead Air Broadcasts page (2026-10-31).

    python3 tools/audio/build_broadcast_sounds.py

The alert tones are made to the published Emergency Alert System spec: SAME header bursts (AFSK at 520.83 baud, mark
2083.3 Hz, space 1562.5 Hz, three bursts a second apart) and the two-tone attention signal (853 Hz + 960 Hz), all
heard through a cheap receiver. world/Broadcasts plays them on [EAS] and [EOM] lines.

    radio.eas.header     the three header bursts, clean (Josiah's pick)
    radio.eas.eom        8 s of the two-tone attention signal, clean, closing a message (Josiah's pick)
"""
import json
import os
import sys

import numpy as np

from build_radio_sounds import RATE, ROOT, band, noise, write_ogg

HEADER = "ZCZC-CIV-EVI-025001+0600-3040900-WGHN/AM -"


def same(msg):
    data = bytes([0xAB] * 16) + msg.encode()
    bits = np.array([(b >> i) & 1 for b in data for i in range(8)])
    spb = RATE / 520.83
    idx = (np.arange(int(round(len(bits) * spb))) / spb).astype(int).clip(0, len(bits) - 1)
    return np.sin(np.cumsum(2 * np.pi * np.where(bits[idx] == 1, 2083.3, 1562.5) / RATE))


def gap(t):
    return np.zeros(int(t * RATE))


def bursts(msg):
    return np.concatenate([part for _ in range(3) for part in (same(msg), gap(1.0))])


def attention(t=8.0):
    k = np.arange(int(t * RATE)) / RATE
    return 0.5 * (np.sin(2 * np.pi * 853 * k) + np.sin(2 * np.pi * 960 * k))


def receiver(x, rng, hiss=0.04):
    """Through a cheap radio: hiss, the speaker's band, a little drive."""
    x = band(x + rng.standard_normal(len(x)) * hiss, 300, 3400)
    return np.tanh(x * 1.4)


def static(rng):
    t = 1.4
    n = noise(rng, t, 400, 3600)
    k = np.arange(len(n)) / RATE
    env = np.minimum(1, k / 0.15) * np.minimum(1, (t - k) / 0.5) * (0.75 + 0.25 * np.sin(2 * np.pi * 3.1 * k))
    return n * env


def tune(rng):
    t = 1.1
    k = np.arange(int(t * RATE)) / RATE
    # heterodyne whistles sliding past as the dial turns, over static that settles once it's on the station
    f = 2600 * np.exp(-k * 2.2) + 300
    whistle = np.sin(2 * np.pi * np.cumsum(f) / RATE) * np.exp(-k * 1.5) * 0.5
    hiss = noise(rng, t, 300, 3400) * (0.6 + 0.4 * np.exp(-k * 3))
    out = receiver(whistle + hiss * 0.7, rng, 0.0)
    return out * np.minimum(1, (t - k) / 0.2)


# Josiah's picks (2026-10-31): the clean header bursts, and the clean two-tone signal as the end of a message. The
# attention alarm, static and tuning are recordings (build_broadcast_picks.py); the synth versions stay for the page.
SOUNDS = {
    "radio.eas.header": (lambda rng: bursts(HEADER), -3.0, 16),
    "radio.eas.eom": (lambda rng: attention(), -3.0, 16),
}


def main():
    rng = np.random.default_rng(1993)
    sounds_json = os.path.join(ROOT, "sounds.json")
    with open(sounds_json) as f:
        table = json.load(f)
    for event, (build, peak, dist) in SOUNDS.items():
        folder = event.replace(".", "/")
        write_ogg(build(rng), os.path.join(ROOT, "sounds", folder, "1.ogg"), peak)
        table[event] = {"sounds": [{"name": f"zomboidcraft:{folder}/1", "attenuation_distance": dist}]}
        print(event)
    with open(sounds_json, "w") as f:
        json.dump(table, f, indent=2, ensure_ascii=False)
        f.write("\n")


if __name__ == "__main__":
    sys.exit(main())
