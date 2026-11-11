---
name: zomboid-weapons-review-share
description: Weapons thread's share of the Project Zomboid review (2026-10-31): section 6 + Firearms settings; plan doc, his answers, what is built
metadata:
  type: project
---

Josiah approved handing out the PZ review (artifact 7rkGg3jdUDUZuHf1QeEvVE) on 2026-10-31. His standing rule: Zomboid's version of every feature unless he confirmed a change.

The weapons thread owns:
- section 6: melee durability with tape/glue repairs, stomp, reloading bench (our addition), B42 primitive weapons (spear etc.), fire spread from Molotovs;
- the Firearms group of section 20: Use Damage Chance, Noise Multiplier (1, 0.2-2), Jam Multiplier (default 0 since he cut jams), Moodle Multiplier (1, 0-10), Weather Multiplier (1, 0-10), Headgear Effect (on).

Plan doc: https://claude.ai/artifact/WWzGJEyT25vTMRJN5FTbm5 (sent 2026-10-31 03:55).

His answers (2026-10-31 04:09):
1. repairs anywhere (not at a bench);
2. Zomboid's wear numbers for all melee;
3. scrap metal weapons later, with welding;
4. add Zomboid fire spread, with adjustable settings and an on/off toggle;
5. weather does affect aim, but only slightly, since the rain never stops;
6. Firearm Use Damage Chance applies to Both (zombies and players).

Built and merged into playtest (e9172080e, green, jar sent 2026-10-31 06:05):
- combat/Wear.java (Zomboid condition + 1-in-N `lower` in WeaponSpec, old stacks migrated via zomboid_condition component);
- combat/Firearms.java with config fields in ZcConfig.Guns (useDamageChance, noise/jam/moodle/weather multipliers, headgearEffect);
- Shove.stomp (V over a downed zombie, footwear multiplier);
- ContainedFire fireZomboid mode + extinguisher spray;
- SpearItem + 8 primitive weapons + branch/stone/sharp_stone, Skill.SPEAR.

COMMANDS owns the per-world Sandbox backbone (SandboxOptions.wires() in com.zomboidcraft.sandbox). The 6 Firearms settings are registered there as "coming", owner WEAPONS; wire them once the backbone is in playtest, e.g. w.put("FirearmNoiseMultiplier", b -> b.number("guns.noiseMultiplier", 0, 10).sameScale().owner("WEAPONS")).

Next: reloading bench batch (discuss first).

See [[zomboid-attachment-colours-tech]], [[zomboid-irons-tech]], [[zomboid-sandbox-settings]].

AK-47 (planned by Josiah, not built): AUDIO holds Freesound 855843 "An AK-47 being shot" (CC0) for it; when the AK-47 is added, send AUDIO its sound ids.
Preview clip of the primitives: /mnt/project-files/zomboid-mod/previews/weapons-review/primitive-weapons.mp4 (sent 2026-10-31 ~05:50). Branch merged playtest at 048341a05 with the sandbox wires (Firearms 6 + FireSpread, owner WEAPONS).

Reloading bench answers (Josiah 2026-10-31 08:55), all my picks yes:
1. a placeable reloading press, found in gun shops, hunters' garages and police stations;
2. spent casings drop when firing (shotgun hulls too, revolvers keep theirs until reload), and dropped casings clear after 5 minutes;
3. powder + primers + bullet heads/lead shot as loot; casting bullets waits for metalworking;
4. "all the ones you can and that make sense" (no rimfire, no grenades; bolts already recoverable);
5. Reloading skill + gunsmith magazine; low skill means slower and fewer rounds; no duds.
Model previews first, before wiring.
Reloading models: tools/weapons/reloading.py (kit.drawn + kit.paint = gun toon painter; Josiah 09:16 "make sure they have the same toon look"). Bench = block model models/block/reloading_bench.json (blockstate facing). Items: spent_{9mm,380,38,44,556,308,3030,50}, spent_hulls, smokeless_powder, primers, bullets_{9mm,38,44,556,30,50}, lead_shot, gunsmith_magazine. Wired in Java 2026-10-31 (combat/Reloading, ReloadingBenchBlock, client ReloadingClient); trains Zomboid's existing Skill.RELOADING; loot via MODIFY_DROPS. LANDSCAPE places benches once in playtest. Approved; in playtest aa12829c5 (green, jar sent 2026-10-31 23:10). TODO after SURVIVAL wave 3 merges: trait axpert (Traits.has(player,"axpert")) = faster axe swings + tree chopping, WEAPONS owns it.
