---
name: zomboid-reloads-ingame-tech
description: How Josiah's edited reloads play in the game (baked clip files, ReloadClips.java), plus the 2026-11-04 local-gun fixes, attachments install and DShK removal
metadata:
  type: project
  modified: 2026-11-04T10:13:08.107Z
---
2026-11-04 09:46 Josiah: "put these weapon animations in the game" (all edited except DShK); DShK removed from the game entirely; check all guns usable; 09:56 all attachments in the game too.

- Bake: scratchpad redit/bake_game.js OUTDIR (node; reads dbsnap2/reloads/<gid>.json = his db docs, kit/g_<gid>.json, kit/static.json). Output config/zomboidcraft/guns/reloads/<id>.json (LOCAL ONLY; copies in /mnt/project-files/zomboid-mod/brita/local-pc/reloads/): tex (gun png), groups (kit pieces, item px, uv or colour), views fp/tp/ta {dur, frames[{g: gun 3x4 rows px->frame, b: bones [x,y,z,qx,qy,qz,qw], p: piece rel to gun 3x4}]}. Frames are in the pose files' frames (FP hand frame R_/L_ names, TP Spine1 Bip01_ names); gun matches game-v2 pose exactly. Re-bake after he edits more (re-snapshot his db docs first).
- Game: client/combat/ReloadClips.java (read, sample, draw pieces, per-entity progress from the gun's model-data step, stretched over the game's reload length; tube guns estimate rounds). RifleView (FP) draws the clip instead of the old reload when posed + clip; Survivors.posedGun/SurvivorLayer (TP): still = 'tp', moving = 'ta'. Arms ease in/out (blend) since his first keys differ from the static pose (AR57 FP up to 26 cm).
- DECIDED (Josiah 10:38, card): reloads take HIS CLIP LENGTH. Server Guns steps reloadTick at LocalGuns.reloadPace = gameReloadTicks/clipTicks (fp dur from reloads/<id>.json, streamed); client ReloadClips divides its estimate by the same pace. Commit 2153ce777.
- Fixes same day: LocalGuns.loaded gave tube guns (S&W 500, 870, Winchester, crossbow, RPG) mag=false -> dry clicks forever (now true; Guns tick mends held ones). Retired swept local sights/grips (they ride on retired red_dot_sight/foregrip items) -> exempt LocalParts.ID. LocalGuns spec "removed": true -> no gun, copies + mags vanish (DSHK.json on his PC is now that marker; DSHK.zmesh/poses left, harmless). GunLoot extras {part, p}.
- His PC now has: game-v2 poses (tpready/tpaim/att) for 13 guns, *_noirons.zmesh, guns/parts/* (parts.json minus DSHK), loot/places.json without DSHK + every sight/grip in gunshop p .015, pawn/surplus .005 (my numbers, not Arsenal's).
- Scope A black lens under Photon (10:01 screenshot): copy moved from the HUD pass (gathered before the world is drawn) to capture() after applyPostEffects; lensReady kept across frames. Unverified until he tries it.
- 10:35 AR-15 (Bush_AR15_MOE) installed on his PC ("AR-15 now, rest later" card): gun json/zmesh/noirons, pose = scratchpad redit/posing/game (no att: sights/grips need his posing-tool placement; parts.json mounts lack it), reload baked (bake_game.js OUTDIR Bush_AR15_MOE), loot via make_places.py rerun with 14 guns + part extras re-added (rarity S), sounds = copy of aac_honey (Brita gives it the same M16 set; soundpack.py PICKS updated). All 13 first-batch guns already have pack sounds (S&W 500 shots only).
Related: [[zomboid-reloads-tech]], [[zomboid-reload-rules]], [[zomboid-brita-wave2]], [[zomboid-scope-zoom-direction]].
