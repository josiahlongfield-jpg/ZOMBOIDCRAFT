---
name: zomboid-loop-bench
description: Loop Bench page where Josiah edits any mod sound on his phone; how to apply his edits; -18 dB means remove
metadata:
  type: project
---

Part of [[zomboid-sound-design]]; rounds log [[zomboid-sound-history]].
- 2026-10-28 LOOP BENCH (Josiah edits any mod sound on his phone): https://claude.ai/artifact/7Fx7rYyRohb7bb2nQLf1JT. db collection "edits", doc id = sound path with "/" -> "~". Apply: save the docs to tools/audio/bench_edits/<date>.json, run tools/audio/apply_bench_edits.py on it ONCE, then rebuild the page audio (scratch bench_build.py) and clear the db, since edits are relative to the files the page shows. RULE (Josiah 23:16): a sound set to -18 dB means remove it entirely (the script does it). Round 1 applied 6886622b (playtest c6b3ec01); page refreshed, db cleared.
- Far-off gun shots once used aecho 140/330/610 ms = heard as 3 shots. DISTANT_AF in build_gamemaster_sounds.py now keeps reflections under 60 ms. Never use echo taps over ~60 ms on a one-shot.
- 2026-10-29: Josiah's trims on the 11 rebuilt far-off shots were removed at his request (they were only to cut to one shot); rebuilt files ship whole (b165d47c). Rifle suppressed far-off keeps his trim.
