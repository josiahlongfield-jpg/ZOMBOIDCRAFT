---
name: zomboid-render-distance-limit
description: Josiah's RTX 4070 (12 GB) with Photon + DH 128: render distance 12 left the card on the edge; set to 8 on 2026-11-04 (his pick)
metadata:
  type: project
  modified: 2026-11-04T10:01:52.917Z
---

2026-11-04 16:46/16:48 profiles (his clock, UTC+10): card 220-275 MB free, 376-593 evictions/30 s, GPU 57 ms/frame, freezes even with few zombies. Render distance had been 12 since at least 3 Nov (the smooth 15:47 run also had 12 and 2.5 GB free), so 12 was on the edge rather than the single cause. Scope A (WEAPONS) ruled out (tens of MB). Josiah picked "back to 8"; RC backed up options.txt as options.txt.20261104.bak in the profile folder.

Live test 19:57 (build 6cd103c, rd 8): idle flat ~7.0 GB javaw, 3.5 GB free, no leak; 600 zombies +0.7-1.2 GB dedicated + 0.6-1.8 GB shared, lowest 2.15 GB free. NVIDIA Instant Replay and Discord Clips run in the background on his PC (suggested turning them off for tests).

**How to apply:** when a profile shows the card full, check render distance, DH radius, background recorders and other GPU apps before blaming mod code. BodyJob/FastVerts buffers are nmemAlloc system RAM, not card memory. Related: [[zomboid-vram-tech]], [[zomboid-profiler]].

2026-11-06 17:39 profile (800 zombies, rd 8): card fine (1.7 GB free), GPU 25.9 ms; server 88% busy (Zombie.tick 77%: move/collide 34%, pathing 14%, entity lookups 14%). Gun went silent = Minecraft's ~247 sound channels full of zombie sounds (SoundEngine.play returns PlayResult.NOT_STARTED silently outside the IDE). Fix playtest 824eb21c7: client/sound/ZombieSoundCap (performance.zombieSoundCap 48, nearest kept) + LivingEntityMixin skips pushEntities for walkers 32+ blocks from players (performance.farPushDistance). Body textures at cap (510/512 MB).
