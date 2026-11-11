---
name: zomboid-design-palette
description: RULE from Josiah: every ZomboidCraft design (in-game UI and preview pages) matches the kit screen's palette and template; the tokens to use
metadata:
  type: feedback
  modified: 2026-10-25T23:28:34.837Z
---

Josiah 2026-10-25 23:13 (weapons thread): "Make sure your designs match the design palette and template that the UI is now set in."

**Why:** the Destiny-style kit screen (key I, shipped 23:03, [[zomboid-kit-screen-destiny]]) is now the house look; anything that looks different (old purple tooltips, other page themes) reads as off.

**How to apply:**
- In game: draw with the kit's own pieces in client/kit/KitDraw (bone text on dark glass, letter-spaced caps in the hud font, rarity bands from KitItems.Rarity, diamond tier pips, thin bars, key caps). Don't invent new colours.
- Tokens: background #0b0c0e; glass rgba(12,13,15,.96) with edge rgba(232,228,218,.18); text #e8e4da, bright #f2efe8, dim ~68%, faint ~30%; good #8fc46a, bad #d0675e, warn #d9a33a, link/gold #e0c25e.
- Rarity band / ink / tile gradient: Common #c9c0ad on #1d1b16 (tile #5c5850 to #34312c); Scarce #5d6a34 on #f2efe8 (#525d30 to #23281a); Rare #4a6d97 on #f2efe8 (#3d5a7d to #1b2636); Prized #6e2626 on #f2efe8 (#5c2424 to #2a1212); Grail #b08d3e on #1d1b16 (#6b5626 to #2f2611). Rarity words in lists: #e8dfc8, #a8bc6e, #7faedb, #d0675e, #e2b84c.
- Preview pages: Barlow + Barlow Condensed (uppercase, letter-spaced) from Google Fonts, the dark palette above, section headings with a hairline running off them, cards like KitCard. Reference mockup source: project files zomboid-mod/inventory-ui/destiny-v1/Main.dc.html. Example page: https://claude.ai/artifact/T8LZVy8JASLZqNcu3RVWdo
- The older field kit (E) uses a canvas/tape look (KitArt); new work follows the kit screen unless Josiah says otherwise.
