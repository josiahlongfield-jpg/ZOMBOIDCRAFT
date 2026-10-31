"""Builds the throwables' sounds (weapon.throw.*) and the world-event explosions, from Josiah's picks on the Dead Air
Throwables page (2026-10-28).

    python3 tools/audio/build_throw_sounds.py FREESOUND_DIR SONNISS_DIR GAMEMASTER_DIR

FREESOUND_DIR is a checkout of the sounds-raw branch; SONNISS_DIR holds the Sonniss downloads and GAMEMASTER_DIR the
unzipped Gamemaster Gun Sound Pack (its ProSoundCollection_BonusSounds folder); neither goes in this repo. Mono, peak
-1 dB. WEAPONS plays these (combat/throwing); the cuts follow how often each one is played:
  flare_hiss   every 2 s while a flare burns          -> 2.2 s pieces, faded so they overlap smoothly
  fire_burn    every 3 s from one of a fire's flames  -> 3.2 s pieces, the same way
  alarm_tick   once a second while the clock winds down
  alarm_ring   every 0.5 s while it rings            -> 0.55 s pieces of the ringing
  firecracker  a single pop, at random, as often as every 0.15 s -> the string cut at each bang
  fuse         once, when it is lit                  -> 1.5 s of steady burning
  smoke_pop    once, for the 20 s the smoke pours out
pin_pull and ear_ring came from a second page (2026-10-29), after none of the first round fitted; the ear ringing is
made here from tones (ring() below), since real recordings of tinnitus are rare.
"""
import json
import subprocess
import sys
from pathlib import Path

import numpy as np

from build_zombie_sounds import RES, ROOT

RATE = 44100
# a piece: (source, how to cut). source is "fs:<id>", "sx:<id>" (Sonniss), "gm:<file name>" (Gamemaster) or "syn:ring"
# cut: None = the whole recording; ("span", start, end); ("chunks", start, end, length) = back-to-back pieces;
# ("hits", gap, longest) = one piece per bang, bangs at least gap s apart, each at most longest s
EVENTS = {
    "weapon.throw.swing": ("weapon/throw/swing", 12, [("gm:foley_jump_movement_throw_06", None)]),
    "weapon.throw.bottle_smash": ("weapon/throw/bottle", 32, [("sx:170f543c", None)]),
    # Josiah picked a spray can's rattle: its loudest shakes, one per landing
    "weapon.throw.can_clatter": ("weapon/throw/can", 20, [("fs:390334", ("hits", 0.8, 0.7))]),
    "weapon.throw.brick_thud": ("weapon/throw/brick", 16, [("sx:211e09ed", None), ("sx:3cb19ea7", None), ("sx:cb328f25", None)]),
    "weapon.throw.firecracker": ("weapon/throw/firecracker", 64, [("fs:651328", ("hits", 0.15, 0.4))]),
    "weapon.throw.fuse": ("weapon/throw/fuse", 12, [("fs:637527", ("chunks", 5.0, 8.0, 1.5)), ("fs:637527", ("chunks", 20.0, 23.0, 1.5))]),
    # "this does both": the strike is its first second and a half, the hiss the steady burn after it
    "weapon.throw.flare_strike": ("weapon/throw/flare_strike", 12, [("fs:348767", ("span", 0.0, 1.6))]),
    "weapon.throw.flare_hiss": ("weapon/throw/flare_hiss", 16, [("fs:348767", ("chunks", 1.8, 6.25, 2.2))]),
    "weapon.throw.alarm_tick": ("weapon/throw/alarm_tick", 6, [("fs:139958", ("span", 0.0, 1.0))]),
    "weapon.throw.alarm_ring": ("weapon/throw/alarm_ring", 48, [("fs:209536", ("chunks", 0.1, 4.0, 0.55)),
                                                                ("fs:459701", ("chunks", 0.7, 5.1, 0.55))]),
    "weapon.throw.pin_pull": ("weapon/throw/pin", 8, [("gm:gun_grenade_launcher_trigger_02", None)]),
    # a 4.5 kHz ring over a muffled rumble, both fading over 8 s: "ring with muffled rumble" on the second page
    "weapon.throw.ear_ring": ("weapon/throw/ear_ring", 4, [("syn:ring", None)]),
    "weapon.throw.molotov_burst": ("weapon/throw/molotov", 32, [("fs:534702", None)]),
    "weapon.throw.smoke_pop": ("weapon/throw/smoke", 24, [("fs:483320", ("span", 0.0, 20.0))]),
    "weapon.throw.flashbang": ("weapon/throw/flashbang", 96, [("fs:870713", None)]),
    "weapon.throw.blast": ("weapon/throw/blast", 128, [("fs:737321", None), ("sx:f2cec45b", None)]),
    "weapon.throw.blast_far": ("weapon/throw/blast_far", 256, [("gm:explosion_far_distant_02", None), ("sx:e9187543", None)]),
    "weapon.throw.fire_burn": ("weapon/throw/fire", 16, [("fs:249418", ("chunks", 0.0, 16.0, 3.2))]),
    # the shared world events (WorldEvents): near ones are the throwables' blasts, the far ones his three picks
    "world.explosion": ("world/explosion/near", 192, [("fs:737321", None), ("sx:f2cec45b", None)]),
    "world.explosion.far": ("world/explosion/far", 640, [("fs:149966", None), ("fs:156500", None), ("fs:372086", None)]),
}


def load(path):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-ac", "1", "-ar", str(RATE), "-f", "f32le", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).copy()


def ring():
    """Ears ringing after a flashbang: a steady 4.5 kHz tone that holds 1.5 s then fades, over low-passed noise (the
    world sounding muffled) that dies away faster. Seeded, so a rebuild gives the same file."""
    t = np.arange(8 * RATE) / RATE
    noise = np.random.default_rng(3).standard_normal(len(t))
    f = np.fft.rfft(noise)
    f /= 1 + (np.fft.rfftfreq(len(t), 1 / RATE) / 250) ** 4
    rumble = np.fft.irfft(f, len(t))
    rumble /= np.abs(rumble).max()
    tone = np.sin(2 * np.pi * 4500 * t) * np.minimum(1, t / 0.05) * np.exp(-np.maximum(0, t - 1.5) / (7 / 3))
    return (tone * 0.6 + rumble * 0.5 * np.exp(-t / 2.5) * np.minimum(1, t / 0.02)).astype(np.float32)


def hits(a, gap):
    """Start of each bang: 5 ms peaks crossing 35% of the loudest, at least gap s apart."""
    w = int(0.005 * RATE)
    env = np.array([np.abs(a[i:i + w]).max() for i in range(0, len(a) - w, w)])
    out, last = [], -1e9
    for i, v in enumerate(env):
        if v > 0.35 * env.max() and i * w - last > gap * RATE:
            out.append(max(0, i * w - int(0.01 * RATE)))
            last = i * w
    return out


def fade(a, fin, fout):
    n = min(len(a), int(fin * RATE))
    a[:n] *= np.linspace(0, 1, n, dtype=np.float32)
    n = min(len(a), int(fout * RATE))
    a[-n:] *= np.linspace(1, 0, n, dtype=np.float32)
    return a


def trim(a):
    """Drop the silence after the sound and fade the last 30 ms so nothing clicks."""
    loud = np.nonzero(np.abs(a) > np.abs(a).max() * 10 ** (-50 / 20))[0]
    a = a[:loud[-1] + 1] if len(loud) else a
    return fade(a, 0.002, 0.03)


def pieces(a, cut):
    if cut is None:
        return [trim(a)]
    if cut[0] == "span":
        b = a[int(cut[1] * RATE):int(cut[2] * RATE)].copy()
        return [fade(b, 0.005, 1.5 if cut[2] - cut[1] > 10 else 0.08)]
    if cut[0] == "chunks":
        _, start, end, length = cut
        out, t = [], start
        while t + length <= end + 1e-6:
            # long pieces overlap the next play by their fades, so a repeated sound runs on without a gap
            f = 0.2 if length > 1 else 0.02
            out.append(fade(a[int(t * RATE):int((t + length) * RATE)].copy(), f, f))
            t += length
        return out
    _, gap, longest = cut
    starts = hits(a, gap)
    out = []
    for i, s in enumerate(starts):
        end = min(starts[i + 1] if i + 1 < len(starts) else len(a), s + int(longest * RATE))
        out.append(trim(a[s:end].copy()))
    return out


def main():
    fs_dir, sx_dir, gm_dir = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    fs = json.loads((fs_dir / "manifest.json").read_text())
    sx = json.loads((sx_dir / "manifest.json").read_text())
    sj = RES / "zomboidcraft/sounds.json"
    ours = json.loads(sj.read_text())
    credits = {}
    for event, (folder, rng, sources) in EVENTS.items():
        out = RES / "zomboidcraft/sounds" / folder
        out.mkdir(parents=True, exist_ok=True)
        for old in out.glob("*.ogg"):
            old.unlink()
        takes = []
        for src, cut in sources:
            kind, sid = src.split(":", 1)
            if kind == "fs":
                info, path = fs[sid], fs_dir / fs[sid]["file"]
                rec = f"[{info['title']}]({info['page']})"
                credits.setdefault(rec, (info["user"], "Freesound, " + info.get("license", "CC0 1.0"), []))
            elif kind == "sx":
                info, path = sx["sx" + sid], sx_dir / sx["sx" + sid]["file"]
                rec = f"{info['title']} ({info['pack']})"
                credits.setdefault(rec, (info["bundle"], "Sonniss royalty-free", []))
            elif kind == "syn":
                takes += pieces(ring(), cut)
                continue
            else:
                path = next(gm_dir.rglob(sid + ".wav"))
                rec = f"{sid} (Gun Sound Pack bonus sounds)"
                credits.setdefault(rec, ("Gamemaster Audio", "Gamemaster Audio licence (bought)", []))
            if event not in credits[rec][2]:
                credits[rec][2].append(event)
            takes += pieces(load(path), cut)
        names = []
        for i, a in enumerate(takes, 1):
            a *= 10 ** (-1 / 20) / max(np.abs(a).max(), 1e-6)
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(RATE), "-ac", "1", "-i", "-",
                            "-c:a", "libvorbis", "-q:a", "6", str(out / f"{i}.ogg")], input=a.tobytes(), check=True)
            names.append({"name": f"zomboidcraft:{folder}/{i}", "attenuation_distance": rng})
        ours[event] = {"sounds": names}
        print(event, len(takes), "takes", [round(len(t) / RATE, 2) for t in takes])
    sj.write_text(json.dumps(ours, indent=2) + "\n")

    path = ROOT / "CREDITS.md"
    text = path.read_text()
    head, sep, rest = text.partition("\n## Throwables\n")
    if sep:
        nxt = rest.find("\n## ")
        rest = rest[nxt:] if nxt >= 0 else ""
    rows = "\n".join(f"| {', '.join(evs)} | {rec} | {by} | {lic} |" for rec, (by, lic, evs) in credits.items())
    section = ("\n## Throwables\n\nPicked by Josiah Longfield on the Dead Air Throwables page, cut by "
               "tools/audio/build_throw_sounds.py. The Sonniss and Gamemaster originals are not included.\n\n"
               "| Sound | Recording | By | Licence |\n|---|---|---|---|\n" + rows + "\n")
    path.write_text(head.rstrip("\n") + "\n" + section + rest)


if __name__ == "__main__":
    main()
