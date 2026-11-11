---
name: zomboid-chest-hole
description: Point-blank shotgun chest hole in ZomboidCraft: Josiah's ask (27 Oct 09:08) and how the see-through hole is built and checked
metadata:
  type: project
---

**Ask (Josiah 2026-10-27 09:08):** point-blank shotgun shots to the chest that land most of the pellets blow a big hole in the chest. Built the same morning in the characters thread; with [[zomboid-corpses-tech]]'s only-headshots rule it keeps coming.

**Server (Dismember):** pellet() counts buckshot pellets in torso parts 1-2 from within limbs.chestHoleRange (3 blocks, shooter eye to hit point). settle() blows it open when they're more than limbs.chestHoleShare (0.5) of gun.pellets(). Sets LIMBS bit 19 CHEST_HOLE (MARKS = HEAD_GONE|CHEST_HOLE, kept by sever/burst, copied to Corpse). Effects: blood/bone particles out the back, HEAD_BURST sound at pitch ~0.7, chest bleed, 14-tick stagger. Readout says "chest BLOWN OPEN". Test: /zc zombie [outfit] holed (Dismember.blowOpen(zombie)).

**Client:** ChestHole.of(mesh) cuts a holed copy once per outfit mesh (also the _low baked one), cached on ZBodyMesh.holed. It cuts every SPINE-bone quad inside 3 overlapping rects (spine space x/y, z -2.62..4.0 so backpacks/air tanks at z<-2.62 stay). Axis-aligned quads are split in texture space; slanted ones are dropped if their middle is in the hole. ChestHole.draw adds the flesh lining (grid outline walls, depth from what's worn over the core), and at lod<2 ribs, spine stubs, rim lips (z from surface()), and shreds (Gore.box). Gibs.chest throws kit/skin rags + gore out of the back.

**Checks:** jtest HoleTest (scratchpad, backface-culled Java2D render, sky background shows see-through). Preview v21 (patch_chesthole.py, "Shotgun, point-blank" button). JS polygon cut in hole_logic.cjs/hole_run.cjs (stand-in THREE). Sources are in /mnt/project-files/zomboid-mod/characters/preview-src/.
