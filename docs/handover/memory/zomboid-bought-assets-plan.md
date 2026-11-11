---
name: zomboid-bought-assets-plan
description: Josiah will buy 3D asset packs (trees first) to replace most of the world; shopping list doc link, intake rules and conversion order
metadata:
  type: project
  modified: 2026-10-26T10:00:03.743Z
---

Josiah 2026-10-26 09:45 (landscape thread): "ill get those tree asset for you, i have no issue replacing the majority of the world with assets that you can modify. give me a list of everything you need for that to happen".

Shopping list doc (Claude Doc): https://claude.ai/artifact/12Gt4fAHLMcTj92hNqrWy6 ("ZomboidCraft asset shopping list"). Priorities: 1 nature (Kentucky broadleaf trees with dead/fallen variants first), 2 vehicles (80s-90s US), 3 props by place, 4 building details (buildings stay procedural blocks). Optional: characters, weapons, CC0 sound.

**Why:** he wants the world to feel real and dense, matching the photo references ([[zomboid-photo-reference-rule]]).

**How to apply:**
- Originals go to /mnt/project-files/zomboid-mod/assets/<pack>/ and NEVER into the repo or jar (licences like CGTrader Royalty Free ban redistribution and AI training). Record each pack's licence link in CREDITS.md.
- Two routes: chunky custom blocks (trees, bushes, rocks, fences, building details) or mesh models (vehicles via the existing .zcar route; furniture mesh loader still to build when the first props pack arrives).
- Everything gets restyled: design palette ([[zomboid-design-palette]]), clean/worn/wrecked variants, no brand logos.
- Order: trees first (restyle one species, show it next to the current tree in a rainy preview, wait for his yes), then street props + vehicles, then interiors. Current trees: county/Trees.java. See [[zomboid-landscape-direction]].
- Ask before any pack over about 1 GB.

**2026-10-26 10:28-10:30:** Josiah linked 7 CGTrader nature packs he may buy (stylized pine forest kit, Big Environment Pack Reforged, ultimate trees pack, dead tree collection low poly, rocks and boulders vol 4, 50 hand-crafted rocks, grass groundcover 01) and asked Claude to gather everything for ALL priorities, "paid or free i dont care", "get as much as you need". The landscape container could not reach cgtrader.com or CC0 sites, so the coordinator was asked for a fresh asset-gathering thread: download CC0 packs itself, list paid ones (links, prices, Royalty Free licence) for Josiah to check out; it cannot log in or pay.

**2026-10-26 10:41 asset thread result:** 253 CC0 Poly Haven models (glTF + 1K JPG, 579 MB) in /mnt/project-files/zomboid-mod/assets/polyhaven-props/ with INDEX.md by place (street, homes, office/shop, garage/industry/military/port, nature) and LICENSE.txt. Quaternius/Kenney skipped (cartoon). Buying list with verdicts on his 7 nature links (buy Ultimate Trees Pack + one rock pack, skip the rest): https://claude.ai/artifact/FCSWHNvVcKoM4WvCe7ivk9. Project files 8.3 of 10 GB.
