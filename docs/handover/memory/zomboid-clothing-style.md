---
name: zomboid-clothing-style
description: Josiah's standing art direction (2026-10-25) for ZomboidCraft clothing and accessories: chunky 3D blocks, Borderlands-like cartoonish but believable
metadata:
  type: feedback
  modified: 2026-10-25T23:45:31.742Z
---

Josiah 2026-10-25 23:44: "I very much like that style ... for the 3d parts, almost a cartoonish but believable design, somewhat like a 'borderlands' game artwork feel. I want you to set that as the new design preference for all clothing, including clothing attachments like helmet, watches etc. also can you do the same for all the other enemies."

**What the style is** (from the soldier preview https://claude.ai/artifact/Pp1bQP48f9LZxhCEhK3v2L):
- Every garment and accessory is its own 3D block over the body, not paint on the skin: shirt/sleeve shells, trouser shells, pockets with flaps and buttons, cuffs, collars, belts, buckles, straps, boots built from sole + lugs + toe cap + heel + upper + laces.
- Each block has its own flat colour or pattern (camo, plaid...), a THIN (1px) dark outline on outer edges only, a 1px light line under the top edge, a brighter top face and darker underside, stitching just inside the edge on fabric, metal parts in steel grey, and grime toward the bottom. Blood follows the zombie's damage level.
- Only hands, bare skin and the face come from the skin texture.
- The body is the mockup-1 "player" body: slim, tapered torso, separate thigh/shin/foot and upper/fore/hand, shoulders flush with the torso top, no neck, no face blocks, no thumbs.

**How to apply:** use this for every outfit, every enemy type, and the player's clothes. Code lives in the preview tools (scratchpad player/skinned.js gear()), to be ported to Java models. Related: [[zomboid-characters-direction]], [[josiah-world-art-direction]].

**All-outfit preview (2026-10-25):** https://claude.ai/artifact/Mnik6r9hRkYoARfsLTdBxU shows all 15 zombie types in kits (kits.js in the scratchpad: shared shirt/pants/boots/sneakers/hair/hat/watch/glove builders, then KITS per outfit). Not built into the mod; waiting for Josiah's feedback and go.

**Josiah 2026-10-26 00:02:** no black lines where garment pieces join (e.g. sleeve to forearm, thigh to shin, chest to waist). Clothing must flow as one piece; thin outline only on outer edges, cuffs, attachments. Implemented via gear() `join: 't'/'b'` (suppresses edge line, stitching and grime band at that edge) and one-box shoulder-to-elbow sleeves.

**Josiah 2026-10-26 00:10:** no 3D hair (removed; heads use the skin). Kits must be roughed up to match the aesthetic: dirty, torn, bloody, zombie damage; clean 3D items looked out of place. Done in gear(): wear wash, mud, stains, holes cut through fabric, claw rakes, ragged hems, heavier blood; metal gets scratches and rust; hanging torn strips (tatters) and ripped-off sleeves. Wear scales with damage (KIT.wear 0.4/0.7/1.0).

**Josiah 2026-10-26 00:44:** damage must be 3D layers, not painted discolouration/blood. Rebuilt (scratchpad damage.js): garment shells = 4 panels of cells (cloth/faded/stained/blood-soaked/hole) merged into blocks, real holes, frayed thread blocks, peeled flaps, raised blood clots + drips, caked mud blocks on footwear, askew headgear; colours themselves faded. Painted overlays off (KIT.paintFx=false). Java port note: ~450-870 boxes per outfit in preview; in-game needs per-zombie baked variants.

**Josiah 2026-10-26 01:01:** "still too clean and bright, doesn't match the moody aesthetic". Added grade(): every colour desaturated ~50-60% and darkened toward sooty brown #1f1b15 (hi-vis orange ends ~#7a553a), highlights cut to a third, skin tinted darker, preview lit overcast-cold + dim sodium lamp on dark wet asphalt. Default for all character art: muted, dark, never bright.

**01:06:** Josiah: current darkness level is right ("not too dark, that's a better take"). Asked for a Photon example: added a "Photon-style" lighting mode to the kits preview (ACES, exposure 0.82, saturation .8/contrast 1.1, blue-grey exp fog, rain, wet road + puddles, sodium lamp, hanging froglight, additive glow sprites). It is a browser approximation; a real look needs it in game.

**01:24:** Preview now runs the in-game walker anims (scratchpad zanim.js). Josiah: shamble looked like a moonwalk; fixed knee phase (knee bends while leg swings forward: shin = max(0, ±sin(step)), body lowest with legs spread). He approved ("much better"). The shipped WalkerModel still has the old cos(step) knee timing; offered to fix in playtest, no answer yet.
