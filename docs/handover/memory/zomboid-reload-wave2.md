---
name: zomboid-reload-wave2
description: Wave-2 reloads (26 local guns) ported from Josiah's own edited reloads; pipeline files, reach finding, per-gun choices (2026-11-04)
metadata:
  type: project
  modified: 2026-11-04T12:10:13.389Z
---

2026-11-04: all 26 wave-2 guns have first-pass reloads in his reload editor (artifact T7GqDNuxrnjgeSJZbEM4fL, version 16), awaiting his review. LOCAL-ONLY data (scratch/project files/private artifact only).

Pipeline (scratchpad, not in repo): wave2/specs2.py (per-gun G dict: template, parts, extra prims, anchors, match windows, 'after' JS), wave2/export_kit2.py OUT gid... (runs redit/port.js then redit/fit.js), redit/chk2.js (misses + finger depth vs template), redit/pent.js gid view (when fingers sink >30 mm), redit/frames.js / sheet1.sh (renders). Templates = his saved views in dbsnap2/reloads/<gun>.json.

Key finding: the FP upper-arm length differs per gun (posing tool), so a ported key can be unreachable; fit.js adds shoulder offsets ('sho') and 'mid' keys until misses are under ~2 cm.

Per-gun choices (his rule: right hand actions unless the piece is on the left):
- AR15 template: CAR15, SCAR-L/H, FAL. M14 (AssaultRifle2): AK, M82, SV-98 (then right-hand bolt), M60, PKM (box swap + right charge; feed cover not a separate mesh piece). Honey Badger: G36C.
- 1911: G17, USP, AutoMag (right thumb drops slide stop); Colt 1903 has no slide stop -> left-hand overhand rack (written by me); UZI (open bolt, no flick); MP7.
- M500: S&W 327 (8 shots), Colt Service .38. M870: SPAS-12, M1014 (right hand on bolt handle), UTS-15 (top port, palm-down; rough FP).
- L86: AUG9 (left slaps bolt release), F2000 (left pulls left-side handle).
- BOAR: M40A3 (4 rounds thumbed in), M1 (clip + op-rod ride), Ruger No.1 (lever loop as his Winchester).
Rough spots to expect feedback on: UTS-15 and MG first-person framing, TP finger depth ~-56 mm on MGs comes from their posing holds.
Related: [[zomboid-reloads-tech]], [[zomboid-reload-rules]], [[zomboid-reloads-ingame-tech]].
