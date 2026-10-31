"""Builds the house alarm sounds, synthesised (no samples): stand-ins until Josiah picks recordings.

    python3 tools/audio/build_alarm_sounds.py

Writes sounds/world/alarm/house/<kind>/1.ogg, sounds/block/alarm_panel/<kind>/1.ogg and their sounds.json entries.
They are not the air raid siren, which is only ever the world event (Josiah).

    house.bell    the outdoor bell box: a striker hammering a steel gong ~18 times a second, a seamless 4 s loop
    house.siren   the electronic box: a square wave through a horn sweeping up and down, a seamless 4 s loop
    alarm_panel.beep    one keypad press: a short piezo beep
    alarm_panel.disarm  the panel accepting the code: two quick rising beeps
"""
import json
import os
import sys

import numpy as np

from build_radio_sounds import RATE, ROOT, band, secs, tone, write_ogg

LOOP = 4.0


def bell(rng):
    """Strikes wrap round the loop's end, so the ringing of the last ones carries into the start without a seam."""
    n = secs(LOOP)
    k = np.arange(secs(1.2)) / RATE
    # a steel gong's partials are inharmonic; each strike rings them with a fast attack and a ~0.3 s decay
    partials = [(820, 1.0, 9), (2010, 0.55, 14), (2750, 0.4, 18), (3900, 0.25, 26), (5300, 0.12, 34)]
    ring = sum(a * np.sin(2 * np.pi * f * k) * np.exp(-k * d) for f, a, d in partials)
    out = np.zeros(n)
    strikes = 72  # 18 a second, a whole number in the loop
    for i in range(strikes):
        at = int(i * n / strikes) + int(rng.integers(-40, 40))
        hit = ring * (0.8 + 0.2 * rng.random())
        idx = (at + np.arange(len(hit))) % n
        np.add.at(out, idx, hit)
        # the striker's own clack
        cl = rng.standard_normal(secs(0.004)) * 0.6
        np.add.at(out, (at + np.arange(len(cl))) % n, cl)
    out = np.tanh(out * 0.6)
    return out


def siren(rng):
    """The sweep's phase is scaled so the loop holds a whole number of cycles: no click where it wraps."""
    n = secs(LOOP)
    t = np.arange(n) / RATE
    sweeps = 8  # up and down every half second
    f = 1050 + 380 * np.sin(2 * np.pi * sweeps * t / LOOP - np.pi / 2)
    phase = np.cumsum(f) / RATE
    phase *= np.round(phase[-1]) / phase[-1]
    sq = np.sign(np.sin(2 * np.pi * phase))
    # the horn: a resonant band, and the driver pushed into grit
    out = np.tanh(band(sq, 500, 4200, order=2) * 1.8)
    return out


def beep(rng):
    return tone(2400, 0.09, square=0.6)


def disarm(rng):
    gap = np.zeros(secs(0.05))
    return np.concatenate([tone(2400, 0.07, square=0.6), gap, tone(3200, 0.09, square=0.6), np.zeros(secs(0.02))])


# event: (builder, folder, peak dB, attenuation distance)
SOUNDS = {
    "world.alarm.house.bell": (bell, "world/alarm/house/bell", -1.0, 160),
    "world.alarm.house.siren": (siren, "world/alarm/house/siren", -1.0, 160),
    "block.alarm_panel.beep": (beep, "block/alarm_panel/beep", -10.0, 8),
    "block.alarm_panel.disarm": (disarm, "block/alarm_panel/disarm", -10.0, 8),
}


def main():
    rng = np.random.default_rng(1993)
    sounds_json = os.path.join(ROOT, "sounds.json")
    with open(sounds_json) as f:
        table = json.load(f)
    for event, (build, folder, peak, dist) in SOUNDS.items():
        write_ogg(build(rng), os.path.join(ROOT, "sounds", folder, "1.ogg"), peak)
        table[event] = {"sounds": [{"name": f"zomboidcraft:{folder}/1", "attenuation_distance": dist}]}
        print(event)
    with open(sounds_json, "w") as f:
        json.dump(table, f, indent=2, ensure_ascii=False)
        f.write("\n")


if __name__ == "__main__":
    sys.exit(main())
