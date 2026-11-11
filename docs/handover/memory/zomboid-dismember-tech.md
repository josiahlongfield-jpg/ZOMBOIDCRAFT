---
name: zomboid-dismember-tech
description: How ZomboidCraft limb shooting works in code (Dismember.java, LIMBS attachment, capsule hit test, Gibs, stumps, hop/stumble/fall)
metadata:
  type: project
  modified: 2026-10-27T07:53:58.871Z
---

Built 2026-10-26 in the characters thread (branch claude/project-thread-wfvzb7). Direction: [[zomboid-dismember-direction]]. Body tech: [[zomboid-zbody-tech]].

- `combat/Dismember.java`: int attachment LIMBS (persistent, synced): bits 0-7 cuts EL_R=1 EL_L=2 SH_R=4 SH_L=8 KN_R=16 KN_L=32 HIP_R=64 HIP_L=128, bits 8-15 shot heading (256ths of a turn), bits 16-17 fling force (0 handgun..3 buckshot).
- Guns fire loop (weapon thread's file): `Dismember.shot(player, eye, stop, filter)` -> `BodyHit` (EntityHitResult + part) replaces ProjectileUtil (walker box far narrower than the drawn body). Guns.pellet takes head/legs from the part, even when down; then `Dismember.pellet(shooter, target, hit, dir, tears, head, legs, type)`. Keep these if the loop is reworked.
- Wounds add up per zombie (WeakHashMap, not saved); settle at END_SERVER_TICK so a whole blast lands first; whole limb checked before the lower half.
- Capsules: fitted to the mesh per pose, picked like the client animator, rewound for lag: see [[zomboid-hitbox-tech]] (old hand-measured STAND/WALK/... tables and jtest Caps.java are gone).
- Config `limbs`: toughness forearm 20, arm 28, shin 24, leg 34 (`Limbs.retune()` in ZcConfig.load swaps an untouched old 24/34/28/40, since saved configs keep old defaults); tearing shotguns 2.0 (pellets>1), handguns 0.8, rifles 1.0, huntingRifles 1.4, crossbows 0.3, otherGuns 1.0 (new guns need a case in tearing/force); hopSpeed 0.55; limbSeconds 60; maxLimbs 64; showHits (hotbar readout "Hit: right shin 12/24").
- Effects: one leg -> Downed.stagger 34 ticks + permanent "hop" speed modifier; both -> Downed.makeCrawler (LEGLESS stays false). Grabs need Dismember.hasHands. `/zc zombie hopper` test kind.
- Client: ZBodyLayer hides bones per cut and draws Gore.rag stumps (gore.png). ZBodyAnimator returns newly cut bits + `before` pose; WalkerRenderer calls Gibs.spawn (camera-relative, block-collision ground, sink in last 2 s).
- ZMove HOP=11, STAND1=12; stumble()/fall() keyframes from limbs.js. Hop fix 2026-10-27: hoppers move under the 0.08 walk threshold, so they slid in STAND1; now moving = walkSpeed > 0.02 on one leg, Input.hop phase follows ground covered (stride clamp(3.9*walkSpeed, 0.3, 0.9) blocks), and the animator adds hz = (hopAdvance(q) - q) * stride * 17.6 so the foot stays planted.
- Crossbow hooks (weapon thread): Dismember.strike()/Strike(+distance)/gone(), StuckBolts.shed in sever, BoltsLayer. See [[zomboid-wave1-tech]].
- Offline checks: scratchpad jtest/src/LimbTest.java (renders), HopTest.java (planted foot).
- Head burst (2026-10-27): LIMBS bit 18 HEAD_GONE. Head hits add tears x bursting(gun) to Wounds.head; settle bursts if >= limbs.headBurst 240 (factors burstShotguns 10 per pellet so about 3 close pellets burst, burstHuntingRifle 4.0, lever/magnum/other 0; headBursts toggle). burst(): particles, ModSounds.HEAD_BURST (weapon.bullet.head_burst, tools/audio/build_gore_sounds.py from Sonniss gore), head Bleed, kill. Client: ZBodyLayer hides HEAD, neck rag; Gibs.head throws hat/hair (kitOnly) + skin/gore cubes.
