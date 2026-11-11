---
name: zomboid-corpses-direction
description: Josiah's 2026-10-27 ask that ZomboidCraft zombies fall to their death per movement and leave a corpse with its shot-off parts missing (no vanilla poof)
metadata:
  type: feedback
  modified: 2026-10-27T07:50:00.000Z
---

Josiah 2026-10-27 07:47 (characters thread, after the head-burst preview): "i need you to move on to changing it so that the zombies dont dissapear into a puff of smoke. right now upon death they just go poof into smaking a disappear. i want the zombies to have an animated fall to their death thats different for whether its running, shambling, hopping on a single leg, crawling without legs. and then i want that body to stay where it lays, missing whatever body parts were shot off it"

**Why:** vanilla death (tip onto the side, vanish in a puff after a second) breaks the Zomboid feel; Zomboid leaves bodies lying where they fell.
**How to apply:** distinct death falls for chase (running), shamble, one-leg hop and legless crawl (plus sensible ones for idle/attack/crawler/knocked down); the body then stays as a corpse showing stumps and a burst head. Builds on [[zomboid-dismember-direction]], [[zomboid-zbody-tech]], [[zomboid-hitbox-tech]].

07:49 on the preview's Minecraft-style tip-over after a head burst: "the body drop after needs to be more natural and not just have the zombie stay in the same position it died in while it falls to thr ground" (it must go limp and collapse, not topple rigid).
07:49 also: "they can also be relative to where they got shot from, their body reacts to the direction of the shot and subsequently causes their death animation to be built around it" (hit reaction away from the shot, the fall built around that direction, still per movement).
07:51: "that should also apply to living zombies too, their bodies should react to the shots, especially for weapons that arent one shot kills. having the body jerk in accordance to where it was hit and be pushed back (naturally) a little" (hit reactions on living zombies: the hit part jerks, small natural push back).

08:53 (after the v18 preview, "theyre good"): only a head hit kills. Red eyes keep glowing until the zombie is shot in the head or its head is blown off. A zombie with every limb shot off stays alive, wriggling on the ground, and one that can crawl keeps following you if you're in range, until it's finished with a headshot. Built: melee, fire and explosions still kill (my reading, told him). See [[zomboid-corpses-tech]].
08:54: "there needs to be more of the sprinting zombies". zombies.sprinterChance went 0.05 -> 0.15 (defaults round 6).
