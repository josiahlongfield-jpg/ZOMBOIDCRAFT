---
name: zomboid-gun-art-style
description: RULE + tech: every ZomboidCraft gun and attachment is painted in the character (Borderlands-like) style by tools/weapons/toon.py, always like new
metadata:
  type: project
  modified: 2026-10-26T01:48:05.087Z
---

**RULE (Josiah 2026-10-26 00:40):** "I want all guns and attachments made with this same design aesthetic (even wood grain or differing coloured ones). I prefer the black mag." **01:56 he reversed wear-by-condition: "I'd actually prefer all the weapons in their like new form at all times, inclusive of their attachments."** Approved concept: https://claude.ai/artifact/5QwwMvHvoMPKSSn7syQLCf. Every new gun, attachment, finish and camo must go through the same painter. Follows [[zomboid-clothing-style]]: no lines where a part's blocks join, thin outline only round each part's outside and each attachment.

**Why:** weapons must match the characters and gear. Condition shows in the tooltip, not the paint.

**How it's built (branch `restyle`, merged into playtest 2026-10-26 01:47):**
- tools/weapons/toon.py, called from kit.paint() for every face (guns, part icons, ammo). Pieces = touching boxes of one material family (union-find, FAMILY folds 'mark' into 'anod' etc). cover() probes past each edge (DEPTH 0.03 sideways, DEPTH and STEP 0.08 under the face) for a same-piece box: hit = joint, no ink/bevel/edge wear. Rod long edges are joints. step_face(): faces >50% buried in a same-piece box (slice risers) get nothing, or sliced stocks get stripes. outer edge distance replaces Face.edge, so the materials' own edge wear follows it too.
- style(): lit tops +6%, undersides -15%, near-black lifted to charcoal (LIFT 0.55 below lum 0.3), chips as clustered flecks just inside the outline (per-face quantile, CHIPS 5%+25%*wear, none on faces under 0.3 units), ink width ~1-1.6 texels, none on faces under 4 texels; ink is matt in _s. pbr.normal takes `cover` (no bevel at joints). paint(cache=) shares joint maps between skins.
- kit.WEARS is one skin now, [('', LIKE_NEW 0.12, 0)]; by_wear() returns the plain model when there's one. The machinery stays: adding ('worn', 0.55, 0.4), ('battered', 1.0, 0.65) back makes textures item/weapon/{gun}[_{fin}][_{wear}], child models {base}/{skin}/... and a range_dispatch on minecraft:damage (RifleView.drawPiece would then need to copy DAMAGE/MAX_DAMAGE again; it was reverted). Never delete the melee *_worn textures: those are separate melee items.
- Look `_after` grime/scratch lambdas must read f.wear (not a closure) so skins differ. Rifle mag is black ('#202124'), laser body is 'poly' (black).
- Revolver (01:56 ask, reshaped 02:40): square butt with 45-degree cut corners (_end), stocks over the frame with a thicker core, guard polyline sweeping into the front strap, hammer spur under the sight line. sil.py side silhouettes in the weapons scratchpad are a fast check.
- Check renders: scratchpad armoury/ (prep.py packs game models + webp skins, template.html three.js gallery, shots.js Playwright). Gallery: see [[zomboid-weapons-direction]].
- Lesson (M9, 02:34): Model.seg snaps every lean to 22.5 degrees about the bar's middle, and a bar's ends are square to it, so a leaned grip leaves a notch under the frame and level parts (well lip, guard bow) miss it. pistol.grip_axes() gives the grip as drawn; build joins from that and check side silhouettes (scratchpad sil/sil.py <out> right <module>).
- Triggers RULE (Josiah 03:08): every trigger sits in the BACK part of its guard, straight down from the frame then its tip curving FORWARD slightly (two segs: vertical, then 22.5 degrees forward). New guns follow it.
- Sight-line RULE (2026-10-26, carbine): nothing between the eye and the rear sight may rise over the sight line. The lever-action's cocked hammer spur stood 0.5" over it 6" from the eye and Josiah saw a "big vertical tab" on ADS; hammers now lie back under the line (revolver too). Check every new gun's hammer, bolt handle and rear tang against SIGHT.
