---
name: zomboid-companion-ui
description: Companion (Rebecca) UI page request (Josiah 2026-10-30) built 2026-10-30: CompanionScreen (G) + OrderWheel (middle-click)
metadata:
  type: project
  modified: 2026-10-30T11:07:26.765Z
---

Josiah 2026-10-30 11:03: G on a companion's green dot opens her own page like his Character page: her animated idle filling most of the height in the middle; left = every command/request/job/animation prompt; right = traits, health, stats, mood; a tab named after her for clothing/armory: give/take clothes and weapons, trade.

UI thread mocks (2026-10-30), at project files zomboid-mod/ui/companion/ (HTML source in UI scratch npcmock/):
- "Rebecca" tab: stats strip (health, wounds, job, shift, following); left Orders/Job/Shift/Talk/Ask her to; right mood + trust/affection + 8 needs, 5 trait sliders, 6 skills.
- "Gear & trade" tab: her 11 clothing slots, weapons, bag | your pack, trade box with her verdict.
- Proposed a "Diary" tab (NpcMind diary).
Built 2026-10-30 (commit 061a1868, merged to playtest): kit/CompanionScreen (tabs her name / Gear & trade / Diary), kit/OrderWheel (middle mouse while her dot shows, 8 quick orders), server npc/NpcPanel (Watch/Order/Gear/State payloads, state every 10 ticks within 8 blocks). Josiah asked that "what she wears" be fully accessible (give/take her clothes, offer found clothes). Only clothes matching her 7 model pieces can go on; whole-outfit models (Shion) pick from @look list. Taking her things needs trust 45. Test shots in KitUiTest companionTest.
Update 2026-10-30 13:10 (db782a21): Gear tab lists a whole-outfit model's own .zsk pieces (State.wears = Npcs.pieces; WEAR_ON 700+i / WEAR_OFF 800+i via Npcs.wear, which applies the slot "top." and CLASH rules), found clothes match a piece by its last word (NpcPanel.part; shoes = any footwear, pants = jeans/trousers). Portrait: Josiah wants her fixed in idle, filling most of the page height: NpcRenderer.portrait flag + separate animator, scale 160, stat strip removed (health beside mood). Server side (packets, NpcMind/NpcBond/NpcWardrobe/NpcWork) is owned by the NPC thread (cmsg_01LvQiYGyHLhwyRNcM6wP1DpQe2uB5nT1vLu576kkiJ7q8); existing client TalkScreen/WardrobeScreen. Animation slots in NpcSkinAnimator.

Related: [[zomboid-ui-pages-tech]].

RESUMED 18:46, MERGED 19:30 (playtest 5245a489) after his "Go" on the in-game clip (project files zomboid-mod/ui/companion/portrait-in-game.mp4). Was paused 13:37. State then: portrait rework (detached idle facing you, drag turn, scroll zoom, pose chips play 20 s, framed by NpcSkin.top, nothing cropped) is on branch claude/project-thread-j1d6bj (ff8684bde, adds a 52-frame companion-clip-NN sequence to KitUiTest for a video), NOT merged to playtest. Web preview artifact 3aFSnXW51ZYzPepFRf34t8 (loads his local .zsk in-browser; ripped models never hosted). Next on resume: his feedback on the preview, then stitch the UI-test frames to an mp4 for him ([[zomboid-video-preview-rule]]), then merge.
NPC thread 22:33: new talk act NpcNet.CALM=51 "Calm her down" (fits page Talk chips + order wheel) and NpcWork COWER=14; NpcPanel watch tick calls npc.attend(p), Order receiver calls npc.ordered() first: keep both. Not added to the page yet (not asked; offer it next time Josiah is on the UI).
BONDS (Josiah 2026-10-30 22:36): first G on an NPC offers "Bond with X?"; bond with many; hotkey (proposed B) opens her page from anywhere (1 bond) or a selection carousel (centre big, neighbours small + faded, scroll/A-D, click opens her page, back to selection any time); Unbond on her page. Web preview artifact KCoozsssBMARhmqpPX4C2X sent 23:00, awaiting his go before the game build.
BONDS BUILT 2026-10-30 ~23:50 (branch b9981edb, clip zomboid-mod/ui/companion/bonds-in-game.mp4 sent, MERGED 2026-10-31 ~01:05, playtest 73150599): NpcEntity.bonds (Set<UUID>, saved "npcBonds"); NpcPanel Bond/Bonds payloads (Bonds sent every 40 ticks, loaded NPCs in player's level), near() allows bonded at any range; client kit/Bonds (key K, B is radio; stand-in NpcEntity for unloaded-on-client), BondPrompt (first G), BondsScreen (carousel, NpcRenderer.portraitLight + soft veil), CompanionScreen Bond chips (All companions / Unbond), Backspace back. KitUiTest bondsTest films bonds-clip-NN.
