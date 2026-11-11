---
name: zomboid-opening-guns-tech
description: How ZomboidCraft's hand-loaded guns work (double-barrel, revolver cylinder, finishes) and their first-person lessons
metadata:
  type: project
  modified: 2026-10-25T21:40:00.000Z
---

Branch `classics` (weapons thread), 2026-10-25. Part of [[zomboid-guns-tech]]; models in tools/weapons/double_barrel.py and revolver.py.
- Guns that open to load (GunType opens ctor: raise, perShell, close): DOUBLE_BARREL (8,10,6) and REVOLVER (10,10,5). Rig fields hinge/openAxis/openAngle, cylinder + chamberStep (deg) + cylAxis. kit.Gun `opened=('shift'|axis, angle, origin)` draws third person open; `finishes` = one texture per finish, picked by model-data string index 8 (GunFinish, component zomboidcraft:finish, odds in ZcConfig.finishes).
- Revolver cylinder (RifleView): cylAngle always a whole number of chambers at rest (16-stave shape never jumps); each shot cylAngle -= chamberStep; loading turns chamber `shells` to LOAD_SPOT (-60) and settles to the nearest chamber on stop. Its rounds come up in SCREEN space from lower left (cylRound, CYL_HAND offset), not the gun frame: after the crane swing gun-frame "down" points sideways and the hand box covered everything. Shooting forearm drops to RIGHT_LOAD while open. Blocky fists (0.15 blocks) dwarf a revolver cylinder (0.09): keep the fist under the round, never over the cylinder.
- kit.flash_tip takes the flash builder's bounds: a second flash (revolver gap) must go in gun.gap_flash, or it drags the muzzle point back.
- Sights: line of sight = rear notch TOP, front blade top level with it (blade at the notch floor is invisible).
- Hammer (2026-10-27, Josiah): revolver.py/pistol.py hammer_piece is the kit's `slide` (the pistol's own slide is its `handle`); rig hammer (pin, item px) + hammerCock. REVOLVER/MAGNUM (his 08:27 ask): rests DOWN, pin at the hammer's foot (y 0.0, 22 deg) so only the top swings; each shot RifleView.hammerAmount snaps it back 0.03 s and drops it 0.025 s, inside the 80 ms flash. A higher pin or bigger angle drops the spur into the frame/grip. PISTOL: cocked while loaded, falls with the shot, slide recocks (rig.cycles). Server aim-raise cock click removed. weapons.py regen rewrites lang/en_us.json: git checkout it after.
- Tiny round parts: model.rod draws ONE square box under 0.3 px (cartridges looked square). Use Inch.round_rod / Inch.ogive (stepped round, any size) for rounds and cases.
- Ground/item-frame display of parts (Josiah 10-27: crossbow bolts lay 4 blocks long, then 'all attachments expanded when dropped'): small_display(real=...) draws attachments, mags and bolts at TRUE size, 16/39.37/IN = 0.496 (builds are in inches); model.fit has depth/scale args. Ammo boxes still fit-to-size.
