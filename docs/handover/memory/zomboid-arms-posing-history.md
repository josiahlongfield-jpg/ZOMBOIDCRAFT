---
name: zomboid-arms-posing-history
description: Version history and Josiah's rules for the arms posing tool (v1 2026-11-02 to v40 2026-11-06), summarised
metadata:
  type: project
  modified: 2026-11-06T07:34:42.511Z
---

History for [[zomboid-arms-posing-tool]].

- v1-v4 (2026-11-02): built when he wanted to pose the FP arms himself. Online for phone use. Hip/ADS switch. Brita guns only (gzip+b64 Int16 tris, int8 clash field from brita/build_guns.py).
- v5 RULE: in hip fire the bore always runs through the crosshair, meeting view centre 2.7 m ahead; sliders are offsets. The game must do the same.
- v6: per-finger curl (Finger1 split into 4 groups by k-means; the game must match). v9: forearm twist.
- v7: ADS scope lens size (numbers.guns[id].ads.lens). v8: game FOV slider; hands stay at a fixed 70 deg (he does NOT want them to scale with FOV).
- v10: third-person modes tpready/tpaim on the female body, two-bone IK, real lengths.
- v13: light grey background, "Street behind" checkbox, red crosshair.
- v14 (11-03): templates: one pose per style carried to the others by parts; review grid (later hidden at his ask).
- Foregrips are posed relative to the grip; scope classes give one optic per magnification class per gun.
- v15/16: copy/paste per group.
- v17: ADS 0.0 is dead centre, with a one-time migrate.
- v34 (11-03): 27 wave-2 guns.
- v35-v37: poses looked reset because:
  - the cloud load came late;
  - the doc then hit the 256 KiB limit at 354 KB ('invalid_argument', nothing saved).
  - Fixed with a state-only doc, the T0 rule, the restore button and the wave-2 DEFAULT_FOLLOW. He confirmed saving works (3319).
- v38-v40 (2026-11-06): 88 wave-3 guns (two publish passes), compactState plus the 250000-byte guard, backup refreshed to 3474.
- HOLD 11-03 04:13: no Brita gun files to his PC or the game until he says ready.
- 380-gun plan: he will not hand-pose every gun; templates per class.
