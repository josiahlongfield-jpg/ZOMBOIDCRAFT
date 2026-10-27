"""Builds the flybys of the shared world events (world.flyby.*), from Josiah's picks on the Dead Air Aircraft page
(2026-10-27 06:32: "use this for the fly-bys too ... have the sound trail off rather than cut out suddenly").

    python3 tools/audio/build_flyby_sounds.py SONNISS_DIR

Each is a whole recording of an aircraft going past, Doppler and all. WorldEventSound starts it for each player so
that its loudest moment (the aircraft overhead in the recording) lands when the aircraft in the world is nearest
them, as heard from there, so every take is shifted to put that moment at exactly PEAK seconds in. Mono for 3D,
the tail faded out over its last seconds, peak -1 dB. The Sonniss originals stay out of this repo.
"""
import json
import subprocess
import sys
from pathlib import Path

import numpy as np

from build_zombie_sounds import RES, ROOT

RATE = 44100
PEAK = 10.0     # WorldEventSound.PEAK
FADE = 6.0
# event: [(Sonniss id, where its loudest moment is (s), cut off after (s) or None)]
EVENTS = {
    # the two F-16 passes first: Josiah wants them the common ones (WorldEvents.sound)
    "world.flyby.jet": [("0d2dc01b", 4.5, None), ("aac4a615", 14.0, 34.0),
                        ("bd9b93ee", 11.5, None), ("ce7962b6", 5.0, None), ("dee4b56e", 4.5, 20.0)],
    "world.flyby.heli": [("76994416", 9.0, None)],
}


def load(path):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(path), "-ac", "1", "-ar", str(RATE), "-f", "f32le", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, np.float32).copy()


def main():
    sx_dir = Path(sys.argv[1])
    sx = json.loads((sx_dir / "manifest.json").read_text())
    sj = RES / "zomboidcraft/sounds.json"
    ours = json.loads(sj.read_text())
    rows = []
    for event, takes in EVENTS.items():
        folder = "world/flyby/" + event.split(".")[-1]
        out = RES / "zomboidcraft/sounds" / folder
        out.mkdir(parents=True, exist_ok=True)
        for old in out.glob("*.ogg"):
            old.unlink()
        names = []
        for i, (sid, peak, cut) in enumerate(takes, 1):
            info = sx["sx" + sid]
            a = load(sx_dir / info["file"])
            if cut:
                a = a[:int(cut * RATE)]
            shift = int((PEAK - peak) * RATE)
            if shift >= 0:
                a = np.concatenate([np.zeros(shift, np.float32), a])
            else:
                a = a[-shift:]
                n = int(1.5 * RATE)
                a[:n] *= np.linspace(0, 1, n, dtype=np.float32)
            n = min(len(a) - int(PEAK * RATE), int(FADE * RATE))
            a[-n:] *= np.linspace(1, 0, n, dtype=np.float32) ** 2
            a *= 10 ** (-1 / 20) / max(np.abs(a).max(), 1e-6)
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(RATE), "-ac", "1", "-i", "-",
                            "-c:a", "libvorbis", "-q:a", "5", str(out / f"{i}.ogg")], input=a.tobytes(), check=True)
            names.append({"name": f"zomboidcraft:{folder}/{i}", "attenuation_distance": 800, "stream": True})
            rows.append(f"| {event} {i} | {info['title']} ({info['pack']}) | {info['bundle']} | Sonniss royalty-free |")
            print(event, i, round(len(a) / RATE, 1), "s")
        # one event per take, so every player hears the same aircraft's take
        for i, n in enumerate(names, 1):
            ours[f"{event}.{i}"] = {"sounds": [n]}
    sj.write_text(json.dumps(ours, indent=2) + "\n")

    path = ROOT / "CREDITS.md"
    text = path.read_text()
    head, sep, rest = text.partition("\n## Flybys\n")
    if sep:
        nxt = rest.find("\n## ")
        rest = rest[nxt:] if nxt >= 0 else ""
    section = ("\n## Flybys\n\nJets and helicopters crossing the map (world events), picked by Josiah Longfield on the Dead Air "
               "Aircraft page, lined up and faded by tools/audio/build_flyby_sounds.py. The Sonniss originals are not "
               "included.\n\n| Sound | Recording | By | Licence |\n|---|---|---|---|\n" + "\n".join(rows) + "\n")
    path.write_text(head.rstrip("\n") + "\n" + section + rest)


if __name__ == "__main__":
    main()
