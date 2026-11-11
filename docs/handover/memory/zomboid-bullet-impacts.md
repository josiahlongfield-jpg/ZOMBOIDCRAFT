---
name: zomboid-bullet-impacts
description: Surface-specific bullet strike sounds (Josiah 2026-10-27: old hits "cartoonish"): WEAPONS event names, sources, picker status
metadata:
  type: project
---

Josiah 2026-10-27 03:47 (via WEAPONS): bullet hits sound cartoonish; the game should know ground/cement/building/wood/metal incl. cars and play realistic hits.
WEAPONS registered (guns-wave1 cf273c32) 8 events on vanilla stand-ins: weapon.bullet.impact.{ground (40), concrete (40), brick (40), wood (40), metal (48), car (48), glass (48), soft (24)}; one impact per shot (per shotgun blast), pitch 0.92-1.08. weapon.bullet.ricochet now only on glancing hard hits; keep a real ricochet whine. Replace the entries + oggs with several mono short takes each, no whizz; keep the ranges.
Sources: Freesound groups imp_* (fetch_freesound.py, run from de376b6c), Sonniss sel_imp.json (9 files: Gamemaster/Olivier Girardot/PMSFX/Pole Position). Gamemaster's dedicated Bullet Impact pack is paid (not owned).
Related: [[zomboid-sound-design]], [[zomboid-wave1-guns]].
Picker (2026-10-27 04:40): https://claude.ai/artifact/31ibYXHDmAv1VbZgwGMTdm 'Dead Air Bullet Strikes', 70 recordings; db picks roles = ground/concrete/brick/wood/metal/car/glass/soft/ricochet + notes with hit times. Builder scratch imptools/imppage.py. fetch_freesound.py now allows CC-BY for imp_* groups (ATTRIBUTION_OK) - credit those in CREDITS.md. Concrete/car candidates thin.
SHIPPED 2026-10-27 (playtest d0823f85): tools/audio/build_impact_sounds.py builds every event from Josiah's picks (cuts multi-hit takes at each hit). Wood is still a stand-in (fs:237447, he picked none) - offer a wood re-pick. New events, played by WEAPONS: weapon.bullet.impact.nearby (24, non-shooters near the hit) and weapon.casing.shotgun (16, every shotgun hull).
- 2026-11-02 (Josiah): surface strikes too loud. All weapon.bullet.impact.* (incl. nearby) and weapon.bullet.ricochet now have "volume": 0.42 in sounds.json (asked -30%, then another -30%). STRIKE_VOLUME in build_impact_sounds.py keeps it on rebuild; flesh, head_burst, whizz and casing untouched. In playtest release ffcebf27.
