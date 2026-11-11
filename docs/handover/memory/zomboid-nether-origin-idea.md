---
name: zomboid-nether-origin-idea
description: DECIDED 2026-10-29 23:30: the Nether is where the Harvestman and the infection came from (lab-breach lore + ground-zero zone now, trip later); Nether rebuilt DOOM-inspired
metadata:
  type: project
---

Josiah 2026-10-29 23:26 (Harvestman thread): use the Nether to show where the Harvestman and the infection came from.

Three options were offered. DECIDED 23:30: "Definitely 1 and 2. 3 can be worked out later." He also wants the Nether COMPLETELY RECONSTRUCTED, inspired by the recent DOOM games (inspiration only, no ripped assets). A dedicated thread was requested from the coordinator.
1. Lore only: the underground lab studied a breach into the Nether, the Harvestmen came through, and the infection is a Nether spore. Told through notes and bunker files, plus a sealed, broken portal room in the lab.
2. Ground zero: a quarantine zone round the breach with ash, red fungal growth, blackened buildings and Harvestman dens.
3. A late-game trip through the breach to their home world (nests, the oldest ones, the infection's source).

Caveat given: the vanilla Nether look clashes with the grounded style, so anything shown must be re-dressed to the detail rules ([[zomboid-building-detail-direction]]). Builds on the existing lore in [[zomboid-harvestman-direction]] (known entity, lab with a stasis tank).

PLAN 2026-10-29 23:45 (thread "Build the Nether origin and ground zero"): doc https://claude.ai/artifact/37TYpSoi4BxcjaNM9E1ugj. Breach under Ridgeview Labs (NW, unbuilt) with B1-B4 levels, B4 = sealed ring under a cracked concrete plug; ~30 files in 4 sets; ground zero = 3 rings (cordon 120-150, burn 50-120, core 0-50) with a red-growth block kit and spore haze; new Nether = 5 regions (Ash Wastes, Spore Forest, Molten Rift, Bone Cathedrals, Brood Hollows) via worldgen data overrides, vanilla portals off. ALL SIX RECOMMENDATIONS APPROVED 23:37: breach at Ridgeview Labs; Nether = Eternal gothic mega-fortresses + 2016 grimy rocky hell; haze infects slowly near the core only, gas mask/respirator stops it; Files page in the kit screen; order lab -> ground zero -> Nether; vanilla Nether portals off now. Photos requested from REFERENCES (refs/groundzero, refs/nether). Nothing built yet; no vanilla Nether code exists in the mod.
- Josiah 23:39: the breach explains respawning: the ring still flickers and more zombies/Harvestmen pour through, when enemy respawn is on (ZcConfig population respawnDays, default 0 = off). Plan: "breach surges" (red flare, hum, tremor) drive the respawn refill and new Harvestman arrivals after the 48 h grace; with respawn off it only flickers. Seen/heard at ground zero; lab notes log surges.

**PAUSED 2026-10-30 01:25** (Josiah: "Pause work for now"). Done: lab, files, Nether seal (playtest 35b50e5, sent); breach surges + spore haze merged into playtest 00a4bfbf7 (CI was running, not yet verified or announced to him). Not started: ground zero rings/dens, rebuilt Nether. On resume: check that build, tell him about surges/haze (/zc breach surge), then ground zero.
Photos for it landed 2026-10-30 05:45: refs/nether/ (lava-fields, sulphur-obsidian, gothic-cathedrals, catacombs-ossuaries, red-fungi-moulds) and refs/groundzero/ (wildfire-burned-towns, burned-forest-ash, exclusion-zones, blast-doors-seals, accelerator-tunnels, sinkholes), each with sources.md.
