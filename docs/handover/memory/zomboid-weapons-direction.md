---
name: zomboid-weapons-direction
description: Josiah's direction for ZomboidCraft weapons and combat (branch combat, started 2026-10-25)
metadata:
  type: feedback
---

Started 2026-10-25 on branch `combat`.
- Assault rifle is the base system for all guns (mags, ammo, reload, recoil, ADS, noise). Real recorded sounds (CC0 preferred) with a credits file. "As detailed as possible": 3D models from REAL measurements, blocky style, not smooth meshes. Previews before shipping, every number in settings.
- Melee roster of 15 (bat, crowbar, knife, machete, hammer, pan, golf club, pipe, wrench, fire axe, sledge, shovel, hockey stick, spiked bat, katana) plus stamina, knockdown/stomp, shove, grabs, crawlers.
- Rifle shot = take 3 (SuperPhat 421710) for everyone, loud. Attachments: mounting an optic folds the iron sights. Realism pass he chose: "Finer detail + real materials" (2x geometry from real dimensions + LabPBR _n/_s).
- True-to-life third-person holds (low ready, shouldered aim, two-handed melee) in tools/weapons/poses.py + HoldPoses. Two-handed straight-arm pistol hold still to do.
- Never buy model packs (licences forbid redistribution in a public repo); catalogues are shopping lists only.
- Modular guns: weapon tab shows empty slots as green translucent ghosts. Attachments API (combat 0296b9a): Attachments.Slot {OPTIC, MUZZLE, UNDERBARREL, SIDE_RAIL, STOCK, MAGAZINE, BIPOD}; slots/get/slotOf/fits/canMount/mount/unmount; data in assets/zomboidcraft/gun_slots/<gun>.json. See [[zomboid-inventory-ui-direction]].
- Loot crates he likes: pistol case, long rifle case, ammo can, military footlocker (foam inside, scuffed). He chose to CARRY cases like Zomboid (off hand, found packed); see [[zomboid-cases-tech]].
- Sight pictures: red dot/holo big and uncrowded (drawn closer to the eye when aiming, MRO-style red dot, shallow holo hood); 4x stays a sniper overlay. He approved: "much better sight pictures well done". Stock left as is unless he asks to slim it.
- Aimed views raised to bench detail (texture density from the aiming eye, rifle sheet 2048px). All 15 melee weapons have LabPBR maps (pbr.melee_layers, MELEE_PBR kinds in weapons.py, 16 px/unit, porosity for rain).
- Previews: Gunsmith Bench (rifle) https://claude.ai/artifact/AEwQwvHABaZw7EvgLs9y6Q; Armoury (melee, shaders on/off) https://claude.ai/artifact/NMxfr8BG9BAruWfKkg4Bj3. Photon needs Materials > Normal/Specular Mapping on to show the maps ([[photon-shader-settings]]).
- First-person guns: his own skin's arms (RifleArms), a real reload (old mag drops away first, charging handle only on empty or jam), very light ADS sway, separate ADS and 4x sensitivity sliders, L light / J laser with a faint beam. He wants every one of these toggleable.
- Tech: [[zomboid-guns-tech]].
- In-hand look (Josiah 2026-10-25: weapons were too small and drifted right; guns sat low): melee first-person pose is solved in weapons.py (FP_* constants: hand at screen 0.47,-0.82, leaning toward the crosshair, scale 0.52-0.92, pan spun and capped). Rifle hip: HIP_GRIP/HIP_TURN/HIP_SCALE. Gun PBR (fixed 2026-10-25 after he saw silver twice): finishes are non-metal satin (~0.44); never mark worn edges as named metals (230-237 shine silver whatever the albedo) and keep wear rims thin, or mipmaps turn the whole gun grey. Steel parts use tinted metal (255) with dark albedo. Shipped in playtest a5fafd4.
- Art style (Josiah 2026-10-25 23:45): the rifle remade in the characters' Borderlands-like block style. Preview https://claude.ai/artifact/5QwwMvHvoMPKSSn7syQLCf. Follows [[zomboid-clothing-style]] incl. the 00:02 outline rule: thin outline only round each part/attachment, no seams inside a part, edge wear as chips not lines. 00:40 he loved it: ALL guns and attachments rebuilt in game in this style (black mag), shipped 01:47. 01:56: every gun and attachment LIKE NEW always (no wear by condition); revolver reshaped from the real Model 10. Tech and rule: [[zomboid-gun-art-style]]. Step 3 (perk columns) still on hold.
