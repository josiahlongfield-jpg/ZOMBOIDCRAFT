---
name: zomboid-brita-loader-tech
description: Design for loading Brita guns locally (2026-11-01): component on our anchor gun items, local spec+zmesh in config/zomboidcraft/guns, MeshGunRenderer swaps meshes
metadata:
  type: project
  modified: 2026-11-01T06:14:07.598Z
---

Started 2026-11-01 06:20 ([[zomboid-brita-weapons]]).

Design (nothing Brita in repo/jar):
- A Brita gun is one of OUR gun item stacks (its anchor: pistol, magnum, smg, assault_rifle, battle_rifle, heavy_sniper, shotgun, lever_action, rimfire, grenade_launcher, crossbow) carrying data component zomboidcraft:local_gun = Brita id.
- LocalGuns (common) reads config/zomboidcraft/guns/<id>.json (name, anchor, calibre, GunStats overrides, slots/parts, sounds). Friends without files see the plain anchor gun.
-
- - DECIDED 06:14 (Josiah): Brita guns use THEIR OWN calibres and magazines (new ammo items for .45 ACP, 5.7, .500, .45 Colt, .50 BMG, rockets etc.; each gun its own mag like Arsenal). Loot by ARSENAL'S PLACES (CIV/LEO/MIL/SEC/HNT/SUR lists) with our Common-to-Grail rarity, plus a /zc give command (tell COMMANDS). Ammo item models need a toon preview before building.
- BUILT 2026-11-01 06:40 on claude/project-thread-5nbhdi (green Build, not in playtest yet): LocalGuns + /zc localgun <id>|reload (COMMANDS told). MeshGunRenderer is SpecialModelRenderer<String> (26.3 submit has NO display-context arg); spec.anchorMesh says which of our meshes it replaces. Block anchors: skin.py LOCAL_BLOCK wraps lever_action/rimfire block pieces in condition zomboidcraft:local_gun (LocalGunProperty) with mesh ids block_lever_action/block_rimfire; run skin.apply('lever_action'/'rimfire') only, never skin.py wholesale (it rewrites other threads' magazine items). Converter: scratch local/build_local.py -> pack.py `dest`. Film: LocalGunFilmTest + localfilm.yml.
- AMMO (Josiah 06:39-06:41): every ammo item is a single round, no cartons. tools/meshguns/rounds.py packs our lathe rounds (jar) to meshes/ammo/<item>.zmesh; --arsenal/--brita/--dest packs Arsenal rounds (.30-30 uses .45-70) + Brita rocket for his PC only. MeshGuns.OVERRIDES reads config/zomboidcraft/meshes/<id>.zmesh first. shotgun_shell sits on its own carton base (shotgun_shell_box) since its model is the loose shell. MeshGunRenderer.getExtents applies scale (dropped rounds sank otherwise). New items acp45/fn57/colt45/sw500_ammo, pg7v_rocket. Local guns: own ammo, own mag (anchor mag + local_gun id), no our attachments.
- 2026-11-01 08:16: merged to playtest (27f7ce0c6); 14 guns + 16 rounds copied into MEDIEVAL GLOOM (1)/config/zomboidcraft/{guns,meshes/ammo}. Sources: /mnt/project-files/zomboid-mod/brita/local-pc/.
- SOUNDS 2026-11-01 09:00: LocalGuns.sound(gun, ourEvent) -> zomboidcraft:weapon.local/<gun lower>/<our event path> (keeps range); client SoundManagerLocalMixin falls back to our event if no pack has it. Pack "ZomboidCraft Brita Sounds" (75 oggs, built by /mnt/project-files/zomboid-mod/brita/tools/soundpack.py from pilot-sound-picks.txt) is in his profile resourcepacks; he must enable it.
- IRONS (Josiah 2026-11-01 09:02/09:15): every base gun needs iron sights fitting its design; no red dot/optic without a fitted part. Phantom reticle came from anchor factory optic. Crossbow NEEDS irons. "Forget what I've said about our old guns, they don't matter now we have these packs" (old rules like "crossbow doesn't need irons" are void). Details: [[zomboid-brita-irons-tech]].
