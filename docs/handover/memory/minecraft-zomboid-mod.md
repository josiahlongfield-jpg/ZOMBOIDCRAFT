---
name: minecraft-zomboid-mod
description: Josiah's Project-Zomboid-style survival mod for Minecraft (started 2026-10-24) — scope and status
metadata:
  type: project
  modified: 2026-10-24T10:39:46.081Z
---

Started 2026-10-24 in its own thread ("Build Zomboid-style Minecraft survival mod"); [[minecraft-gloomy-pack]] is paused for this.
Platform: Fabric mod on 26.3 (same setup as the medieval pack).

Multiplayer: works with e4mc (host runs it, every friend installs it). Realms can't run mods; a 24/7 server needs a Fabric host.
Code needs a GitHub repo Josiah creates; then attach it with add_repo.
Status 2026-10-24: Stage 1 v0.1.0 COMPILES and boots a dedicated server in CI (in-game play untested). Repo: github.com/josiahlongfield-jpg/ZOMBOIDCRAFT (saved to project). Jar (now v0.1.1): /mnt/project-files/zomboid-mod/ and the repo's "latest" release (CI republishes on each push to main).
Roadmap (design doc, 2026-10-24, replaces the earlier 5-stage plan): 1 Survive the dead (v0.1, done); 2 Hold out (v0.2, done); 3 The dead (zombie overhaul: own look/outfits, sight, memory, lunge, drag-down, crawlers, hordes, sandbox config) NEXT; 4 The world (NOT Zomboid-like low towns; Josiah wants major cities with fully explorable skyscrapers, smaller cities, towns, suburbs, forests, ports with boardable boats, military bases, post-apoc streets; custom city generator since jigsaw caps at 128 blocks; modular stacked floor kits for towers; room+container loot; district density); 5 Body and mind (body-part injuries, moods, calories, temperature, occupations/traits); 6 Arms and gear (40 melee weapons, Zomboid condition/repair, melee weapon mods, crafting/repair/gunsmith stations, own guns + attachments, bags, clothing); 7 Living on (farming, cooking, spoilage, generators, walls, radio, helicopter); 8 Vehicles.
Design doc 'ZomboidCraft Design Bible' (Claude Doc, all sections filled 2026-10-24): https://claude.ai/artifact/Y2XxL5R2jdVjsL9L11foiH
Vision (Josiah, 2026-10-24): "reskin Minecraft over Project Zomboid": only the blocky art style should feel like Minecraft. Wants max Zomboid intricacy: tall detailed buildings with full interiors, dense zombies in towns/cities that don't look like MC zombies, weapon mods, plus extras (weapon crafting stations, repair stations, QoL).

Settings rule (Josiah, 2026-10-24): a Zomboid-style sandbox settings menu where EVERY feature is adjustable or can be toggled off; presets + per-world config, screen ships with Stage 3, every new feature reads its numbers from the config.
Graphics: must work with Sodium+Iris+Photon, no custom shaders; LabPBR maps planned. Art direction = rainy ruined overgrown skyscraper city at night (ref /mnt/project-files/zomboid-mod/reference/art-direction-city.png); needs Distant Horizons + 32-128x pack.
Military theme (Josiah): lean into military gear (NVGs, armour, webbing/MOLLE, packs, suppressors, attachments). Doc has "Military gear" + "Ideas from popular Zomboid mods" sections.

v0.3.0 (2026-10-24) delivered: settings via /zc config, new zombie look (untested in game client), sight/memory. Area concept sheets received (reference/areas-concepts-a.png, -b.png: port, suburbs, military base/quarantine zone, small town, countryside, skyscraper lobby/offices/stairwell, downtown day with elevated rail, hospital, police station, subway) = Stage 4 kit briefs. v0.3.1 = zombie look v2 (brims/visor/pack model parts).

Zombie art (2026-10-24): Josiah made 3D concept renders of all outfits (/mnt/project-files/zomboid-mod/reference/zombie-outfits-3d-concepts.png) = target look (white eyes, open bloody mouth, heavy blood, hoods, hat brims, backpack). Agreed workflow: Claude ships Blockbench .bbmodel files per outfit; Josiah repaints in Blockbench and sends PNGs back. Don't reuse other mods' art.

Build/CI notes and 26.3 API gotchas: [[zomboid-mod-build-notes]].
Vanilla mobs (Josiah, 2026-10-24): vanilla overworld monsters are removed for now (v0.3.10, setting spawning.vanillaMonsters=false, world/MobFilter.java). Later he wants to reuse those mobs as bases for new, different enemy types.
Mood/weather/streets (Josiah, 2026-10-24): world must be permanently dark and moody: NO sunny days (day = dark grey clouds/storms, night = dark but the moon may break through clouds at times for a little ambient light; days must never be bright), harsh weather events; wants more colour from neon signs, plus shops (convenience, grocery, restaurants) and street clutter to fill empty streets. Ideas doc: https://claude.ai/artifact/MMoKYZqhTfLHeSzWq8RH7S (proposed v0.4.0 weather, v0.4.1 shops+signs, v0.4.2 street fill; awaiting his answers).
Decisions (Josiah, 2026-10-24, on the mood doc): days a bit darker than vanilla thunderstorm light; made-up shop brands OK (he may add names later); storms vary from ambient to dangerous (lightning strikes, hail, cyclone winds), dangerous ones rarer; weather = rain, fog, wind, storms now, winter cycle later; corpses ON by default, toggleable, tastefully sparse; keep streets light on cars until cars get a proper model (current blocky cars "dont look like cars"); BUG: buildings lack real doors, zombies must be able to get in/out.
