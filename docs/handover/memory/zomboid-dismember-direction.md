---
name: zomboid-dismember-direction
description: Josiah's 2026-10-26 ask to shoot limbs off ZomboidCraft zombies (per-gun odds, limb comes off where hit) and the plan proposed in the characters thread
metadata:
  type: feedback
  modified: 2026-10-27T07:30:00.000Z
---

Josiah 2026-10-26 09:42 (characters thread): "i want to be able to shoot limbs off the zombies. if a headshot isnt hit, but an arm or leg is instead, depending on the type of gun and amount of shots i want the zombies to have limbs come off relative to where they have been shot. a shotgun will tear off limbs almost everytime up close, pistols are a lot harder to do that with, assault rifles take a few shots, a sniper could take off a limb. ill get the weapon thread to do up proper damage scaling but is that something you can do visually?"

**Plan posted 09:45 (awaiting his answers):** per-limb hit zones (forearm, upper arm, shin, thigh, from the hit point in the zombie's frame and whether its arms are out); per-limb health with per-gun multipliers in config (weapon thread tunes the numbers); limb breaks at the joint above the hit, flies along the shot, tumbles and lies on the ground; torn stump with bone + blood burst stays on the zombie; no arms = no grab, one leg = crawler, both = legless crawl. Asked: melee dismemberment too? Killing headshots sometimes blow off head or jaw? Preview shipped 09:58 in kits artifact v15 (https://claude.ai/artifact/Mnik6r9hRkYoARfsLTdBxU): "Shoot it" gun picker, click to shoot; limb toughness el 10 / sh 15 / kn 12 / hip 18, limb damage per hit pistol 3, AR 5, hunting rifle 14, shotgun 9 pellets x 3.2 (placeholders). Sources limbs.js + patch_limbs.py in preview-src. 10:00 he said the instant drop to the floor was too abrupt: one leg off = stumble, then get up and hop; both off = a realistic fall. v16 (10:12): stumble onto one knee with a hand reaching down, push up, hop on the remaining leg with arms out (chase/attack reach forward); both legs = gravity drop + forward pitch, arms brace, bounce, then legless crawl (limbMove in limbs.js). 10:09 he approved ("thats great, you can load that in"); building in game, guns only (melee dismemberment and headshots blowing off the head or jaw still unanswered). Tech: [[zomboid-dismember-tech]].

Guns.java (weapon thread's file) only knows head/legs/body today (Guns.pellet, hitEntity). Builds on [[zomboid-zbody-tech]].

Shipped in playtest 965dcdf (11:07). Josiah 11:44 on melee dismemberment and headshots blowing off the head or jaw: "we can work that in later" (deferred, not dropped; don't ask again until he brings it up or limbs come up again).

Josiah 2026-10-27 06:39 brought headshots back: "i want to have the zombies heads get blown off as well as the limbs. say from like a powerful sniper rifle and or a heavy shotgun clean blast to the head, it makes their heads explode. can we also look at the hitboxes and make sure they cover the character well". Built: head bursts from the hunting rifle (one headshot) and close shotgun blasts (about 3 pellets in the head), lever-action and magnum off by default; hitboxes refitted ([[zomboid-hitbox-tech]]). Melee dismemberment still deferred.
