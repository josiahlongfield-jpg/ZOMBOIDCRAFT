---
name: zomboid-corpses-tech
description: How ZomboidCraft death falls, lasting bodies (Corpse entity) and hit jerks are built, plus the offline and preview checks
metadata:
  type: project
  modified: 2026-10-27T08:45:32.577Z
---

Built 2026-10-27 in the characters thread for [[zomboid-corpses-direction]]. Builds on [[zomboid-zbody-tech]] and [[zomboid-dismember-tech]].

**Server**
- combat/Deaths: Deaths.died (LivingEntityMixin die HEAD) packs the death into a synced, persistent long attachment `zomboidcraft:death`: bits 0-2 kind (STILL 0, SHAMBLE 1, RUN 2, LUNGE 3, HOP 4, CRAWL 5, DOWN 6, from BodyTrack.deathKind), 3-10 heading (256ths, from its front toward its left), 11-12 force (0 handgun to 3 buckshot), 13 pushed, 14-17 part+1, 18-25 seed, 32-47 body yaw.
- The tickDeath mixin calls Corpses.holds: it holds the dead walker HOLD=36 ticks, lays the Corpse at tick 34 and removes the zombie at 36 with KILLED. Event 60 is never sent, so there's no puff.
- zombie/Corpse: an entity that stores outfit, cut bits, look, the death long, variant and size. It can't be hit, pushed or picked, it falls if the ground goes, and it saves with the world. FROM (the zombie's id) hides it on clients until the zombie is gone. zombie/Corpses keeps a count per level; corpses.maxBodies (default 300) trims the oldest by laidAt. `Corpses.clear(level)` exists, but no command uses it yet.
- Living hits: Dismember.settle → Deaths.jerk sends the `body_hit` payload (entity, parts bits, yaw256, force) to everyone tracking the zombie. Bullets already shove zombies back through Guns → Shove.push.
- Config `corpses.*`: enabled, maxBodies, minutes (0 = forever), hitReactions, drawDistance (client side).

**Client**
- ZDeath plans the fall from the death long: direction = shot push plus kind momentum. It topples standing kinds with a tilt (Pose.fallDir/tilt, applied about the hips before their YXZ turn) and springs on the limbs, or slumps the ground kinds. It drapes the rest pose on the ground with the body mesh. SETTLED = 1.6 s.
- ZHit adds the per-part jerk pulse.
- ZBodyAnimator: hits come from the payload (registered in ZBodyAnimator.register); a dying zombie uses dead().
- CorpseRenderer draws the body in ZDeath.rest through ZBodyDraw, the shared drawing code split out of ZBodyLayer.
- CI smoke test: a killed zombie must leave a corpse ("corpse here").

**Checks**
- jtest (scratchpad, offline Java2D): `DeathTest strip`. MeshDump/DeathCases dump the body plus Java results, and the preview's test_deaths.cjs checks the JS port against them (it matched to 1e-5).
- Preview v18 (kits page): deaths.js and hits.js are the ports, applied with patch_deaths.py. Sources are in /mnt/project-files/zomboid-mod/characters/preview-src/.
- The preview lays a fall from where the zombie was. The game's slack pose uses hz -7 for a crawl and 6 for knocked down.

**Only headshots kill (Josiah 08:53)**: Dismember.onlyTheHead (from the LivingEntityMixin hurtServer hook) caps BULLET damage at health-1 unless the head was struck this tick (Wounds.struck HEAD bit, or HEAD_GONE). Head hits get max-health x25, which gets through armour. Config: combat.onlyHeadshotsKill. Crawlers with one hand use limbs.oneArmCrawl; with none, limbs.wriggleSpeed (Dismember.crawlSpeed, the "wriggle" speed modifier). The client draws ZMove.wriggle when Input.armless is set; it keeps the head on line so the CRAWL_* hitboxes still fit. CI: `damage ... 500 zomboidcraft:bullet` must leave the zombie alive. Preview v19 is patch_headshots.py.
