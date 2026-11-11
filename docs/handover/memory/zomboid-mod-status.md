---
name: zomboid-mod-status
description: ZomboidCraft current status and priorities (latest release, what's paused, what's next)
metadata:
  type: project
  modified: 2026-10-27T09:40:00.000Z
---

Status 2026-10-25 (see [[minecraft-zomboid-mod]] for scope, [[zomboid-mod-build-notes]] for tooling):
- History: v0.3.6-0.3.11 (Oct 24) zombie skins + 3D add-ons, /zc city, real doors. Still missing skins: survivor, security, jogger, patient. v0.4.x weather, shop signs, searchable shop interiors (a bad item id in a loot table stops the server; CI rolls every table). v0.5.0-0.5.2 assault rifle, 15 melee weapons, shove, stamina, knockdowns, crawlers, grabs, shop loot, CC0 sounds.
- Delivering jars: send links, not project-file paths ("i cant download that"). RULE (Josiah): every jar holds all current work from every thread, so he only installs one jar. Shared build: branch playtest (push with git push origin HEAD:refs/heads/playtest, merges only) at https://github.com/josiahlongfield-jpg/ZOMBOIDCRAFT/releases/download/playtest-build/zomboidcraft-playtest.jar. See [[zomboid-playtest-build]].
- Audio: the sounds thread owns .ogg, sounds.json, CREDITS.md, tools/audio/; weapons keeps ModSounds.java + playSound calls (merge: their sounds.json entries for existing guns, keep new gun events and item.radio.*).
- Weapons in playtest by 2026-10-26 (weapons thread, mostly untested in game): rifle + attachments, pistol, L light / J laser, pump shotgun, bolt hunting rifle + suppressor, gun cases + ammo can, double-barrel and .38 revolver with finishes (413eb8b).
- 2026-10-25 23:43: rarity + tier 1-5 rolls, flaws, stat rolls, kit-card tooltips, /zc roll (playtest c5fdab2). Tech: [[zomboid-rolls-tech]]. Roster target about 70 guns. Step 3 (perk columns) on hold.
- Landscape thread 10-25: house interiors a60ceb9, exteriors 8083e3e (approved), basements 003e25d, bunker kit 66e8b38 ([[zomboid-houses-tech]], [[zomboid-bunkers-direction]]).
- 2026-10-26 02:45: guns restyled in the character art style and always like new; revolver reshaped (playtest 8df10fc). [[zomboid-gun-art-style]].
- 2026-10-26 04:41: night vision (helmet mount, three goggle kinds, N flips, AA batteries), playtest 049820b. [[zomboid-nightvision-tech]].
- 2026-10-26 06:20: radios with in-game voice (optional voice chat plugin) + comms headset, playtest 8c1abfd, untested with real voice. [[zomboid-radios-tech]].
- 10-26 08:17: new zombie bodies in playtest, awaiting feedback ([[zomboid-zbody-tech]]).
- Weapons thread 2026-10-26: wave one (.22, SMG full auto, sawn-off, magnum, lever-action, crossbow) in game since b8858d60 ([[zomboid-wave1-guns]]); light/laser Photon fixes and the item-atlas filter dropping `_[ns]$` sprites (1acf297, keeps the atlas under 16384); hip aim + AR full auto (cc50e1cb); sound audit, hammer/cock, suppressors (5a08a248; mod list shows 0.5.2+<sha>); jams cut + aim-for-the-head toughness (4cd2f63c); crossbow bolts stick in zombies (40da8c98, [[zomboid-wave1-tech]]).
- 2026-10-26 11:07 playtest 965dcdf (characters thread): shoot limbs off zombies (per-gun tearing incl. wave-one guns, stumps, flying limbs, stumble+hop, fall to crawl, no hands = no grabs, /zc zombie hopper). 2026-10-27 00:01 he said limbs never came off; fixed in playtest 59b4c2b (body-shaped hits, easier toughness, limbs.showHits readout); 03:15 he confirmed limbs come off; one-legged hop fixed in playtest d52bf97 (they slid). [[zomboid-dismember-tech]]. 03:48 zombies still spawned on roofs/trees; fixed in playtest 1353024 ([[zomboid-zombie-spawning-tech]]).
- 2026-10-27 09:58 playtest 277251f (characters): death falls, bodies that stay, hit jerks, only headshots kill (armless crawlers wriggle), sprinters 1 in 7, point-blank shotgun chest hole. Sent, awaiting his feedback. [[zomboid-corpses-tech]], [[zomboid-chest-hole]].
- Weapons thread (paused 13:16, resumable since 18:29 2026-10-27; nothing queued, waiting on his feedback). Last in playtest e066e9a1: third-person holds/reloads, reticle fix, scope from third person, untested ([[zomboid-camera-tech]]).
- 2026-10-29 02:44: throwables (10, no pipe bomb) in playtest (merge 818c2d570, green 26f51b3e), sent to Josiah, untested in game. Weapons work now lives in thread "Finish the weapons work" ([[zomboid-throwables-direction]]).
