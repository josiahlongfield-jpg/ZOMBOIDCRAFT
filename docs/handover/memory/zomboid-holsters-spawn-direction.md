---
name: zomboid-holsters-spawn-direction
description: Josiah's 2026-10-27 ask for worn quick slots (belt, holster, sheath, wrist) and a spawn picker before entering the world; mockup link, plan, open questions
metadata:
  type: project
---

Josiah 2026-10-27 22:19 (UI thread) picked "Holsters, sheaths and wrist slot" and added: "Work that and the spawn picker screen before you load into the game".

Mockup v1 (Design canvas, 3 boards): https://claude.ai/artifact/Sfr793TzeSendKLXEUPD3K. Sources are in project files zomboid-mod/inventory-ui/holsters-spawn-v1/. The map is county.svg from make_map.py over plan_dump.js output of grayhaven-plan-draft3.

Plan shown:
- Quick slots on keys 1-8 come from what you wear: Hands (always), Hip x2 (any belt), Holster (pistol/revolver/magnum; a hip holster needs a belt, shoulder and thigh holsters don't), Sheath (knife sheath or machete scabbard), Back (the existing sling), Pouch x2 (duty or tool belt). The new gear slots are Belt, Holster, Sheath and Wrist. The watch HUD shows only when a watch is on the wrist.
- Spawn picker: shown before the world, on first join and after death. Place list from county_plan.txt (towns, cities, suburbs, districts, hamlets; 23 entries), danger 1-5 from population rules. You wake inside a zombie-free building. There's a "Pick for me" button, and you can wake near a friend.
- Reference photos asked from the REFERENCES thread: refs/items/{belts,holsters,sheaths,wristwatches,road-maps} (spec refs/_tools/gear.json). Models must wait for them.

Status (2026-10-27 23:30):
- Josiah approved the plan ("Work that and the spawn picker"), plus my three defaults: no belt means Hands and Back only, with a starter leather belt for everyone; the picker shows again after death; "wake near a friend" is in.
- The HUD row shows every slot at once (his ask) and is in playtest.
- The spawn picker was merged into playtest 2026-10-27 (merge 2ad658e9). /zc wakeup reopens it.
- Quick slots + belt models merged into playtest 2026-10-28 00:10; hip holster model in playtest 00:40. PAUSED by Josiah 2026-10-28 01:16 ("Pause this for a bit"); left: shoulder/thigh holsters + both sheaths (out of loot until modelled). Resume only when he says go. Photos are ready (2026-10-28 11:03): refs/items/holsters (23), sheaths (10), wristwatches (10), road-maps (6); raw hits, skim and skip misses. See [[zomboid-quickslots-tech]].

Related: [[zomboid-spawn-select-direction]], [[zomboid-hud-direction]], [[zomboid-kit-carry-rules]].
