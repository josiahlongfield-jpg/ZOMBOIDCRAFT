---
name: zomboid-wave1-tech
description: How the wave-one lever-action, crossbow, .22/SMG suppressors and revolver hammer cock are built
metadata:
  type: project
---

Part of [[zomboid-wave1-guns]]; base tech in [[zomboid-guns-tech]] and [[zomboid-opening-guns-tech]].

Lever tech: GunRig.lever flag; LA.bolt is the kit's `slide` piece (<gun>_loose_slide, rides back lever*handleTravel); lever turns openAngle about the pivot via openLocal; right hand follows it; rounds go in the right gate (RifleView.gateLocal, yaw about the nose, GATE_TUCK). lever_ammo .30-30, ZcConfig leverAction.
Crossbow tech: GunRig.crossbow flag; the handle is the string (XB.string), squashed toward the slack line by stringLocal(cocked()); cocked() ramps from COCK_FROM over the raise, flies forward STRING_FLY ticks after a shot. Draw timed to the real cock take (Gamemaster draw_stretch, 1.85 s, creak from 0.10 s, seat hit 1.66 s): GunType.COCK_AT 5 (sound fires as the hand takes the string), COCK_CREAK/COCK_STRETCHED/COCK_SEAT ticks into the take, RifleView.draw(); raise 40. Retime there if the audio thread swaps the take. Bolt laid BOLT_AHEAD px ahead of the latch then slid back (railLocal). Attachments.key(MAGAZINE) returns "bolt" when loaded so the item shows it. Guns.java drops a CROSSBOW_BOLT ItemEntity where the shot lands unless it breaks (ZcConfig crossbow.boltBreakChance 0.2). kit.Gun has `tip` for guns with no flash.

2026-10-26 11:40 (Josiah's sound audit): .22 and SMG take the pistol suppressor (RM.suppressor slim .22 can, SM.suppressor 9mm can on the lugs) so their shot_suppressed picks play; revolver/magnum cock plays via GunType.hammer() when a loaded revolver is raised to aim (Guns.aim from RifleItem.use, State.hammerBack until the next trigger pull). Every weapon.* event in sounds.json is registered and played (checked).

Stuck bolts (Josiah 12:31 ask, commit b76d4428): combat/StuckBolts.java keeps BOLTS (synced, saved attachment, List<Bolt(part, along, round, du, df, dr)>) on the zombie. Guns calls StuckBolts.stick(zombie, eye, dir) BEFORE hitEntity, so a killed zombie's AFTER_DEATH handler drops them as items at the body; Dismember.sever calls StuckBolts.shed when a limb comes off. Uses Dismember.strike() (part, capsule ends, entry point, all in the zombie frame) and Dismember.gone(). Part frame = capsule axis + StuckBolts.front() (forward rotated from the REST lie), mirrored exactly on the client by client/zombie/body/BoltsLayer (rebuilds each part's segment from ZBodyLayer.pose bones, flip y/z between model and zombie frame, texture zbody/bolt.png). ZcConfig crossbow.maxStuckBolts 6. If the characters thread changes Dismember capsule tables or bone ids, BoltsLayer.draw's segment table must follow.
