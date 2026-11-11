---
name: zomboid-living-companion
description: "Living" companion plan (needs, memory, opinions, own decisions, optional Claude chat brain, body IK/physics), awaiting Josiah's answers 2026-10-29
metadata:
  type: project
---

2026-10-29 23:55. Josiah asked how smart and "living" the Woman companion ([[zomboid-woman-model]]) can be, and whether she can control her body, including moves nobody animated. He chose "discuss first". The plan doc is https://claude.ai/artifact/4SCtVfYqnbqapofLUaCrp2 (Claude Doc "A living companion: plan").

The plan:
- Mind layer in the mod (offline): needs, moods, traits, a saved diary of memories, opinions (trust, respect, affection), her own calls (refusing orders, initiative, leaving).
- Optional AI brain: typed chat through the Claude API, his API key in a local config file (never in the repo or jar). The game validates the actions it picks.
- Body: awareness of her abilities, IK (feet, hands, aim, limp), a physics body for stumbles and falls, more Mixamo clips. Self-learned movement was ruled out as impractical.
- Build order: mind > opinions/decisions > written lines > body IK + clips > AI brain > physics body > voice.

Five questions are open in the doc: rolled or picked traits, can she leave for good, can she shoot him if he's bitten, AI brain or not, which clips. No work has started.

2026-10-30 00:10. Josiah: "Sounds good", and asked for companionship and arousal feelings. Companionship (loneliness) and adrenaline were added, plus attraction for romance. He then said he meant sexual arousal and interaction. DECLINED: no sexual content for the companion. Offered romance instead, kept non-explicit (flirting, teasing, hugs, hand-holding, kissing, jealousy). Step 1 (needs, moods, traits, diary) was started on the playtest jar.
00:28 Step 1 done: NpcMind.java (needs 0..100, traits -1..1, diary saved as text in "npcMind"), Forage goal, need lines, /zc npc mind|trait|feel. Merged to playtest as 7036f4e. Test room artifact: https://claude.ai/artifact/YXACnbqGTpnovxxNH8bCsS. Source in project files zomboid-mod/assets/woman/tools/room/ (index.html + app.js + pack_room.py run from the repo root on woman.zsk, a JS copy of NpcMind/NpcBrain). Model files are woman.txt (base64 deflated lod0, since .bin isn't a served type) and tex_*.
00:55 Josiah asked for more ("get creative"). The plan doc has new sections: 10 extras (backstory, growing skills, base jobs, sleep, scars, likes, sickness, body language, other survivors, legacy), and "Creative extras" (notes, tally marks, a diary book item, a Polaroid album, secrets, sleep talk, wishes, the Harvestman learning her name, the endless-night day count, radio ghosts, judging him, wear and tear, downtime, last moments, gossip, factions). Waiting for his picks.
01:50 Picks done: skills, base jobs (heavy), body language, sleep and routines. Details in [[zomboid-companion-work-tech]]. Next: step 2 (opinions, non-explicit romance, her own decisions), and the Mixamo clips when he sends them.
02:50 Step 2 romance part built (opinions, flirty/romantic/jealous moods, talk panel), see [[zomboid-companion-bond-tech]]. He asked for a "horny" mood again; declined again. Her own decisions (refusing orders, initiative, leaving) are still to do.
2026-10-31 02:30 VOICE: Josiah wants fish.audio TTS so NPCs speak instead of chat text. Proposed: bake every NpcLines line per character once on his PC (his API key, clips into config/zomboidcraft/npc/<name>/), played positionally, text as optional subtitles; live TTS only for future AI-brain lines, cached. No cloning real people's voices (library or his own voice only). Decision card sent, recommended "both: baked now, live later".
02:34 VOICE design agreed in talk (pending card pick): per-character lines.txt in her npc folder ([KIND] headings, lines, fish.audio "(tone)" tags); script voices only new lines; hand-recorded clips save under the script's file name; game converts mp3/wav->ogg + loudness envelope drives the jaw (woman has jaw rig, Rebecca likely not). Custom moments in the file: when:/mood:/trust:/needs:/wait: built from event + situation + state blocks; new blocks need code.
