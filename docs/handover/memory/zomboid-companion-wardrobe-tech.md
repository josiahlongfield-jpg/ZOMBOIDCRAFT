---
name: zomboid-companion-wardrobe-tech
description: Companion wardrobe (2026-10-30): pack clothes added to woman.zsk, NpcWardrobe outfits she picks herself, opinion/suggest panel, green dot at her waist, topless-reaction ask declined
metadata:
  type: project
  modified: 2026-10-30T03:59:18.434Z
---

2026-10-30 03:24-04:00. Josiah asked for more of the pack's clothes, for her to know what she wears and change on her own, ask his opinion, a talk-panel answer, and a green dot to pick her outfit himself. He also asked for a reaction when she's "made topless": DECLINED (same no-sexual-content line as [[zomboid-companion-bond-tech]]). Instead she always puts a whole outfit back on (no special reaction).

Model: woman.zsk v2 (17 MB, project files zomboid-mod/assets/woman/woman.zsk). New pieces: sweater (mesh shirt), shorts (short, denim hot pants), skirt, dress (Dress, a white slip with garter look), boots (boot). Red briefs (pant) left OUT: it's underwear. Pipeline: page pieces.html/pieces.js (project files assets/woman/tools/, run from scratch woman/page with app.js+model.txt) poses the original exactly like the Mixamo upload; tools/npc/skinned/add_pieces.py adds them to mix.json (tight pieces copy the Mixamo body's skin by IDW over 8 nearest body verts; skirt/slip keep the artist's UE weights renamed to Mixamo). Spec: assets/woman/tools/woman_spec_wardrobe.json. Checked standing and walking/running in the room: clothes follow well.

Code (merged to playtest a0052466d):
- NpcWardrobe: outfits WORK (sweater, shorts, stockings, boots), SKIRT, DRESS (slip + heels), DRESS_BOOTS, DATE (sweater, skirt, stockings, heels), SLEEP (slip). Bra and hair are always in every outfit.
  - Scoring: taste (rolled), his opinion, practical (working/following), pretty (romantic/flirty), cosy (low/lonely), +2.5 for what he picked (12000 ticks).
  - She checks every 30 s, at most one change per 2 min, never in danger. A change is 2 s kneeling. She sleeps in the slip. After running in heels she wants boots for 20 min.
  - Pieces changed by command get fixed after 10 s.
  - Synced in the BOND int: bits 26-28 outfit, bit 29 asking. Saved as "npcWardrobe". Only for skinned models (Npcs.skinned).
- Talk acts: LIKE 100, DISLIKE 101, SUGGEST 200+o, DRESS 300+o.
- Client: TalkScreen row 7 "Her outfit..." opens WardrobeScreen(false). Looking below 0.6 of her height moves the green dot to her waist; G there opens WardrobeScreen(true). Barefoot drop only when heels and boots are both off.
- /zc npc outfit [work|skirt|dress|dress_boots|date].
- Room v4 has the "Her clothes" panel.

04:15 PIECE BY PIECE (Josiah: "all the clothing options individually and a green dot on each item she's wearing (except underwear) so I can add and remove... in accordance with her choices"; then "the bra doesn't count as underwear", "she can start at that option"). Done (branch 0809f805a):
- NpcWardrobe.Piece: SWEATER, DRESS, SKIRT, SHORTS, STOCKINGS, BOOTS, HEELS (practical/pretty/cosy). Clashes: the slip with sweater/skirt/shorts, skirt with shorts, boots with heels. Taste and his opinion are per piece. Outfits are only her own candidate sets. worn is an EnumSet. Save "2\t...".
- His asks are acts PUT_ON 300+piece and TAKE_OFF 400+piece. She decides through willing(): affection, trust, taste, loyal, together. At work she refuses heels or the slip and keeps her boots. Putting on a piece that clashes swaps pieces and adds a sweater/bottom if needed.
- The BRA counts as a top, so the sweater or slip can come off. Going down to bra/underwear needs together, or trust 60+ and affection 50+, plus her willingness. The BRA ITSELF STAYS ON: removing it is the declined topless line, told him 04:17. Underwear and hair stay on too.
- Client NpcClient zones by crosshair height (fraction of the bbox): feet <0.14 (boots/heels), legs to 0.46 (stockings), hips to 0.62 (skirt/shorts/slip), torso to 0.80 (sweater/slip). The dot sits on the worn piece facing you; G opens WardrobeScreen(npc, piece) with take off / put on rows. The follow/talk dot moved to her collar (0.84).
