---
name: zomboid-wave1-guns
description: Weapons wave one (Josiah's pick 2026-10-26): six new guns, their modules, preview page, planned rarity and numbers, in-game wiring status
metadata:
  type: project
  modified: 2026-10-26T08:49:21.518Z
---

Josiah picked "New ways to fight, mixed rarity" on 2026-10-26 08:34. Branch `guns-wave1` (WEAPONS thread). Preview page sent: https://claude.ai/artifact/BsXevvmzSThmP1JxtfEUtP (Armoury Wave One).

Models in tools/weapons/ (registered in weapons.py as fn -> kit.Gun key):
- rimfire.py `rimfire` (.22 Rifle, flush 10-rd rotary mag) - Common
- smg.py `smg` (Submachine Gun, 9mm, 30-rd curved mag, full auto planned) - Prized
- sawn_off.py `sawn_off`: private copy of double_barrel.py (importlib) with its _stock/_stock_line/_guard swapped; 12.5" barrels - Scarce
- crossbow.py `crossbow`: hseg() helper draws bars in the top (x-z) plane (rot axis y, angle = -turn); bolt is its 'magazine' part (slot at the latch); no flash - Scarce
- lever_action.py `lever_action` (.30-30, tube-fed, lever = handle turning about LEVER_PIVOT; finishes field/case_hardened) - Common
- magnum.py `magnum`: revolver.py source exec'd with ScaledInch (1.18x) and swapped constants; finishes blued/nickel - Rare
Starting numbers on the page: damage .22 7, SMG 7, sawn-off 9x3.8, crossbow 30, lever 22, magnum 18; noise 45/75/130/8/130/125.

Preview loop (scratchpad wave/): write.py <fn> -> prep.py assets <fns> -> build.py template_wave.html wave-one.html -> side.js / full.js renders.

Sounds: the sound thread owns audio and now uses Josiah's Gamemaster packs; register events + stand-ins, then ask it to fill them. See [[zomboid-sound-design]], [[zomboid-guns-tech]], [[zomboid-opening-guns-tech]], [[zomboid-loot-rarity-direction]].

Status 2026-10-26 ~11:05: all six shipped in playtest b8858d60. .22, SMG, sawn-off, magnum went in at 10:34 (91ca38c2); lever-action and crossbow followed. All six have their own sound events (weapon.<id>.*) on stand-ins; the audio thread swaps in Josiah's picks. None previewed in first person, so in-game feedback decides tweaks.

Lever, crossbow and suppressor tech: [[zomboid-wave1-tech]].
tools/weapons/model.py dump() refuses elements outside -16..32, so oversize parts fail at generation.


Balance 2026-10-26 12:30: jams were too frequent (found guns spawn at 10-70% CONDITION via set_damage, old wear term 0.08*wear^2). Now ZcConfig guns.jamNew 0.001 + guns.jamWorn 0.015*wear^2 (rolls.jamFloor removed; old saved values never update, so new numbers need new field names). Zombie toughness card posted (aim for the head / 2.5x health / varied); Josiah PICKED 'aim for the head' 12:29: combat.zombieBodyShots 0.33 (Guns.pellet; Pellet.tears keeps full force for Dismember limb odds) and combat.zombieMelee 0.5 (CombatEvents.meleeDamage, stomps too), playtest 4cd2f63c.
